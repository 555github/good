package com.example.chatimage.domain

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.RequestRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {

    private val router = IntentRouter()

    @Test
    fun manualVisionModeUsesAttachedImage() {
        val result = router.decideRoute(
            prompt = "这张图片里有什么？",
            manuallySelectedRoute =
                RequestRoute.VISION_CHAT,
            attachedImagePath = "/tmp/image.png",
            explicitlyReferencedImagePath = null,
            latestGeneratedImagePath = null,
            settings = AppSettings()
        )

        assertEquals(
            RequestRoute.VISION_CHAT,
            result.route
        )
        assertEquals(
            "/tmp/image.png",
            result.sourceImagePath
        )
    }

    @Test
    fun autoModeKeepsAttachedImageOnImageEditRoute() {
        val result = router.decideRoute(
            prompt = "换成雪山背景",
            manuallySelectedRoute =
                RequestRoute.AUTO,
            attachedImagePath = "/tmp/image.png",
            explicitlyReferencedImagePath = null,
            latestGeneratedImagePath = null,
            settings = AppSettings()
        )

        assertEquals(
            RequestRoute.IMAGE_EDIT,
            result.route
        )
    }

    @Test
    fun manualVisionModeWithoutImageRequiresInput() {
        val result = router.decideRoute(
            prompt = "分析图片",
            manuallySelectedRoute =
                RequestRoute.VISION_CHAT,
            attachedImagePath = null,
            explicitlyReferencedImagePath = null,
            latestGeneratedImagePath = null,
            settings = AppSettings()
        )

        assertEquals(
            RequestRoute.VISION_CHAT,
            result.route
        )
        assertNull(result.sourceImagePath)
        assertTrue(result.requiresConfirmation)
    }

    @Test
    fun autoModeRecognizesNaturalImageRequests() {
        val prompts = listOf(
            "帮我做一张赛博朋克手机壁纸",
            "给我画一只猫",
            "画一只猫"
        )

        prompts.forEach { prompt ->
            val result = decideAuto(prompt)
            assertEquals(prompt, RequestRoute.IMAGE_GENERATION, result.route)
        }
    }

    @Test
    fun autoModeKeepsImageKnowledgeQuestionsInChat() {
        val prompts = listOf(
            "图片生成模型有哪些",
            "怎么用 API 生成图片",
            "这个画面的构图怎么样"
        )

        prompts.forEach { prompt ->
            val result = decideAuto(prompt)
            assertEquals(prompt, RequestRoute.CHAT, result.route)
        }
    }

    @Test
    fun autoModeDoesNotTreatGenericModificationAsImageEdit() {
        val prompts = listOf(
            "帮我修改这段 Kotlin 代码",
            "帮我生成一个模型列表"
        )

        prompts.forEach { prompt ->
            val result = decideAuto(prompt)
            assertEquals(prompt, RequestRoute.CHAT, result.route)
        }
    }

    @Test
    fun autoModeEditsRecentImageForNaturalFollowUp() {
        val result = router.decideRoute(
            prompt = "把上一张背景换成雪山",
            manuallySelectedRoute = RequestRoute.AUTO,
            attachedImagePath = null,
            explicitlyReferencedImagePath = null,
            latestGeneratedImagePath = "/tmp/generated.png",
            settings = AppSettings()
        )

        assertEquals(RequestRoute.IMAGE_EDIT, result.route)
        assertEquals("/tmp/generated.png", result.sourceImagePath)
    }

    private fun decideAuto(prompt: String) = router.decideRoute(
        prompt = prompt,
        manuallySelectedRoute = RequestRoute.AUTO,
        attachedImagePath = null,
        explicitlyReferencedImagePath = null,
        latestGeneratedImagePath = null,
        settings = AppSettings()
    )
}
