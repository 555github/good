package com.example.chatimage.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.chatimage.ChatImageApplication
import com.example.chatimage.data.api.ApiOutcome
import com.example.chatimage.data.model.RequestError
import com.example.chatimage.data.model.RequestRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ImageGenerationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val assistantId = inputData.getString(KEY_ASSISTANT_ID)
            ?: return Result.failure()
        val userMessageId = inputData.getString(KEY_USER_MESSAGE_ID)
            ?: return Result.failure()

        val container = (applicationContext as ChatImageApplication).container
        val repository = container.conversationRepository
        val assistant = repository.getMessageById(assistantId)
            ?: return Result.failure()
        val userMessage = repository.getMessageById(userMessageId)
            ?: return Result.failure()
        val profileId = assistant.apiProfileId
            ?: return fail(assistantId, "图片任务缺少 API 线路")
        val profile = container.apiProfileRepository.resolveById(profileId)
            ?: return fail(assistantId, "图片任务的 API 线路已不存在")
        val settings = container.settingsStore.settingsFlow.first()
        val prompt = userMessage.optimizedPrompt
            ?.takeIf(String::isNotBlank)
            ?: userMessage.originalPrompt
                ?.takeIf(String::isNotBlank)
            ?: userMessage.text
        val originalPrompt = userMessage.originalPrompt
            ?.takeIf(String::isNotBlank)
            ?: userMessage.text
        val route = runCatching {
            RequestRoute.valueOf(assistant.route ?: RequestRoute.IMAGE_GENERATION.name)
        }.getOrDefault(RequestRoute.IMAGE_GENERATION)
        val sourceImagePath = if (route == RequestRoute.IMAGE_EDIT) {
            userMessage.referencedImagePath ?: userMessage.attachedImagePath
        } else {
            null
        }
        val model = assistant.model.orEmpty()

        return try {
            val options = container.imageParameterParser.parse(
                prompt = prompt,
                settings = settings,
                model = model,
                sourceImagePath = sourceImagePath
            ).first
            when (
                val outcome = container.aiRequestRepository.generateImage(
                    apiProfile = profile,
                    settings = settings,
                    options = options
                )
            ) {
                is ApiOutcome.Failure -> {
                    val maxAttempts = settings.retry.maximumAttempts.coerceAtLeast(1)
                    if (outcome.error.retryable && runAttemptCount + 1 < maxAttempts) {
                        Result.retry()
                    } else {
                        repository.failMessage(
                            assistantId,
                            outcome.error,
                            outcome.diagnostics
                        )
                        Result.failure()
                    }
                }

                is ApiOutcome.Success -> {
                    val result = outcome.value
                    repository.completeImageMessage(
                        messageId = assistantId,
                        text = if (sourceImagePath == null) {
                            result.revisedPrompt
                                ?.takeIf(String::isNotBlank)
                                ?.let { "图片生成完成\n\n优化后的提示词：$it" }
                                ?: "图片生成完成"
                        } else {
                            "图片编辑完成"
                        },
                        images = result.images,
                        prompt = originalPrompt,
                        model = options.model,
                        size = options.size,
                        quality = options.quality,
                        diagnostics = outcome.diagnostics,
                        sourceType = if (sourceImagePath == null) "GENERATED" else "EDITED"
                    )
                    Result.success()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            repository.failMessage(
                assistantId,
                RequestError(
                    code = "BACKGROUND_IMAGE_ERROR",
                    message = exception.message ?: "后台图片任务失败",
                    retryable = true
                )
            )
            Result.failure()
        }
    }

    private suspend fun fail(messageId: String, message: String): Result {
        val container = (applicationContext as ChatImageApplication).container
        container.conversationRepository.failMessage(
            messageId,
            RequestError(
                code = "INVALID_BACKGROUND_IMAGE_TASK",
                message = message,
                retryable = false
            )
        )
        return Result.failure()
    }

    companion object {
        const val KEY_ASSISTANT_ID = "assistant_id"
        const val KEY_USER_MESSAGE_ID = "user_message_id"
        const val WORK_PREFIX = "image-generation-"
    }
}
