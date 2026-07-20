package com.example.chatimage.domain

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.RequestRoute
import com.example.chatimage.data.model.RouteDecision
import com.example.chatimage.data.model.WebSearchMode

class IntentRouter {

    fun decideRoute(
        prompt: String,
        manuallySelectedRoute:
            RequestRoute,
        attachedImagePath: String?,
        explicitlyReferencedImagePath: String?,
        latestGeneratedImagePath: String?,
        settings: AppSettings
    ): RouteDecision {
        val cleanPrompt = prompt.trim()

        val imageIntent =
            settings.imageIntent

        /*
         * 用户明确指定的引用图片优先级最高。
         */
        if (
            !explicitlyReferencedImagePath
                .isNullOrBlank()
        ) {
            return RouteDecision(
                route =
                    RequestRoute.IMAGE_EDIT,
                reason =
                    "已指定一张图片作为编辑源图",
                sourceImagePath =
                    explicitlyReferencedImagePath,
                requiresConfirmation =
                    imageIntent
                        .showSourceImageBeforeSend
            )
        }

        /*
         * 视觉分析必须由用户手动选择。它优先使用本次通过
         * 回形针附加的图片，不改变自动模式默认进入图生图的行为。
         */
        if (
            manuallySelectedRoute ==
            RequestRoute.VISION_CHAT
        ) {
            return RouteDecision(
                route =
                    RequestRoute.VISION_CHAT,
                reason =
                    "使用用户手动选择的视觉分析模式",
                sourceImagePath =
                    attachedImagePath,
                requiresConfirmation =
                    attachedImagePath.isNullOrBlank()
            )
        }

        /*
         * 回形针上传的图片优先于自动引用上文图片。
         */
        if (!attachedImagePath.isNullOrBlank()) {
            return RouteDecision(
                route =
                    RequestRoute.IMAGE_EDIT,
                reason =
                    "本次消息附加了图片",
                sourceImagePath =
                    attachedImagePath,
                requiresConfirmation =
                    imageIntent
                        .showSourceImageBeforeSend
            )
        }

        /*
         * 用户在顶部手动指定图生图，但没有提供图片。
         * 如果允许自动引用，则尝试使用最近生成图片。
         */
        if (
            manuallySelectedRoute ==
            RequestRoute.IMAGE_EDIT
        ) {
            if (
                imageIntent
                    .autoReferenceRecentImage &&
                !latestGeneratedImagePath
                    .isNullOrBlank()
            ) {
                return RouteDecision(
                    route =
                        RequestRoute.IMAGE_EDIT,
                    reason =
                        "手动选择图生图，已引用最近生成图片",
                    sourceImagePath =
                        latestGeneratedImagePath,
                    requiresConfirmation =
                        imageIntent
                            .showSourceImageBeforeSend
                )
            }

            return RouteDecision(
                route =
                    RequestRoute.IMAGE_EDIT,
                reason =
                    "手动选择了图生图，但没有可用源图片",
                sourceImagePath = null,
                requiresConfirmation = true
            )
        }

        /*
         * 非自动模式尊重用户手动选择。
         */
        if (
            manuallySelectedRoute !=
            RequestRoute.AUTO
        ) {
            return RouteDecision(
                route =
                    manuallySelectedRoute,
                reason =
                    "使用用户手动选择的模式",
                sourceImagePath = null,
                requiresConfirmation = false
            )
        }

        /*
         * 自动意图识别被关闭时，默认按普通聊天处理。
         */
        if (!imageIntent.enabled) {
            return RouteDecision(
                route = RequestRoute.CHAT,
                reason =
                    "自动图片意图识别已关闭",
                requiresConfirmation = false
            )
        }

        val visualObject = visualObjectKeywords
            .firstOrNull {
                cleanPrompt.contains(it, ignoreCase = true)
            }

        val explicitImageRequest = explicitImageRequestPrefixes
            .any {
                cleanPrompt.startsWith(it, ignoreCase = true)
            }

        if (
            isImageKnowledgeQuestion(
                prompt = cleanPrompt,
                hasVisualObject = visualObject != null,
                explicitImageRequest = explicitImageRequest
            )
        ) {
            return RouteDecision(
                route = RequestRoute.CHAT,
                reason = "识别为图片、模型或 API 相关问题",
                requiresConfirmation = false
            )
        }

        val editKeyword =
            (imageIntent.editKeywords + builtInEditKeywords)
                .distinct()
                .firstOrNull {
                    it.isNotBlank() && cleanPrompt.contains(
                        it,
                        ignoreCase = true
                    )
                }

        val recentImageKeyword =
            imageIntent
                .recentImageKeywords
                .firstOrNull {
                    cleanPrompt.contains(
                        it,
                        ignoreCase = true
                    )
                }

        val generationKeyword =
            (imageIntent.generationKeywords + builtInGenerationKeywords)
                .distinct()
                .firstOrNull {
                    it.isNotBlank() && cleanPrompt.contains(
                        it,
                        ignoreCase = true
                    )
                }

        val generationAction = generationActionKeywords
            .firstOrNull {
                cleanPrompt.contains(it, ignoreCase = true)
            }

        val hasGenerationIntent =
            generationKeyword != null ||
                generationAction != null && visualObject != null ||
                explicitImageRequest &&
                (
                    generationAction != null ||
                        visualObject != null ||
                        cleanPrompt.contains("画")
                    )

        /*
         * 同时存在图片编辑词和上文图片时，优先图生图。
         */
        if (
            editKeyword != null &&
            (visualObject != null ||
                recentImageKeyword != null ||
                builtInVisualEditKeywords.any {
                    cleanPrompt.contains(it, ignoreCase = true)
                }) &&
            !latestGeneratedImagePath
                .isNullOrBlank() &&
            imageIntent
                .autoReferenceRecentImage
        ) {
            return RouteDecision(
                route =
                    RequestRoute.IMAGE_EDIT,
                reason =
                    "识别到图片编辑词“$editKeyword”，已使用最近生成图片",
                sourceImagePath =
                    latestGeneratedImagePath,
                requiresConfirmation =
                    imageIntent
                        .showSourceImageBeforeSend
            )
        }

        if (hasGenerationIntent) {
            return RouteDecision(
                route =
                    RequestRoute.IMAGE_GENERATION,
                reason =
                    "识别到图片生成请求“${generationKeyword ?: generationAction ?: visualObject ?: "绘画"}”",
                requiresConfirmation = false
            )
        }

        /*
         * 存在编辑指令但没有可编辑图片时，不擅自执行文生图，
         * 由界面提示用户选择图片。
         */
        if (
            editKeyword != null &&
            (visualObject != null ||
                builtInVisualEditKeywords.any {
                    cleanPrompt.contains(it, ignoreCase = true)
                })
        ) {
            return RouteDecision(
                route =
                    RequestRoute.IMAGE_EDIT,
                reason =
                    "识别到图片编辑词“$editKeyword”，但没有找到源图片",
                sourceImagePath = null,
                requiresConfirmation = true
            )
        }

        return RouteDecision(
            route = RequestRoute.CHAT,
            reason = "未识别到图片生成或编辑意图",
            requiresConfirmation = false
        )
    }

    private fun isImageKnowledgeQuestion(
        prompt: String,
        hasVisualObject: Boolean,
        explicitImageRequest: Boolean
    ): Boolean {
        val hasImageTopic = hasVisualObject ||
            imageTopicKeywords.any {
                prompt.contains(it, ignoreCase = true)
            }
        if (!hasImageTopic) {
            return false
        }

        val startsAsQuestion = questionPrefixes.any {
            prompt.startsWith(it, ignoreCase = true)
        }
        val hasTechnicalTopic = technicalQuestionKeywords.any {
            prompt.contains(it, ignoreCase = true)
        }

        return startsAsQuestion ||
            hasTechnicalTopic && !explicitImageRequest
    }

    fun shouldSearch(
        prompt: String,
        settings: AppSettings
    ): Boolean {
        return when (
            settings.search.mode
        ) {
            WebSearchMode.OFF -> false

            WebSearchMode.ALWAYS -> true

            WebSearchMode.AUTO -> {
                settings.search
                    .searchKeywords
                    .any {
                        prompt.contains(
                            it,
                            ignoreCase = true
                        )
                    }
            }
        }
    }

    fun shouldUseToolCalls(
        route: RequestRoute,
        settings: AppSettings
    ): Boolean {
        if (
            route != RequestRoute.CHAT &&
            route !=
            RequestRoute.VISION_CHAT
        ) {
            return false
        }

        if (
            settings.search.mode ==
            WebSearchMode.OFF
        ) {
            return false
        }

        return settings.search
            .toolCallMode !=
            com.example.chatimage
                .data.model
                .ToolCallMode.DISABLED
    }

    private companion object {
        val builtInGenerationKeywords = listOf(
            "生成图片", "生成图像", "画一张", "绘制一张",
            "来一张", "给我一张", "帮我画", "出一张",
            "制作海报", "设计海报", "创建插画"
        )

        val generationActionKeywords = listOf(
            "生成", "绘制", "创作", "设计", "制作",
            "创建", "做一张", "来一张", "出一张"
        )

        val visualObjectKeywords = listOf(
            "图片", "图像", "照片", "海报", "插画", "壁纸",
            "头像", "封面", "logo", "图标", "表情包", "画面",
            "漫画", "油画", "水彩画", "素描", "流程图", "效果图",
            "渲染图", "背景", "前景", "构图"
        )

        val explicitImageRequestPrefixes = listOf(
            "请帮我画", "请帮我绘制", "请帮我生成一张", "请帮我做一张",
            "帮我画", "帮我绘制", "帮我生成一张", "帮我做一张",
            "给我画", "给我绘制", "给我生成一张", "给我一张",
            "为我画", "替我画", "我想要一张", "我想画", "想画",
            "请画", "请绘制", "请生成一张", "请生成一幅", "请生成一只",
            "来一张", "出一张", "画一", "画个", "绘制一", "生成一张",
            "生成一幅", "生成一只", "制作一张", "设计一张", "创建一张",
            "做一张"
        )

        val imageTopicKeywords = listOf(
            "文生图", "图生图", "生图", "图片生成", "图像生成"
        )

        val questionPrefixes = listOf(
            "怎么", "如何", "怎样", "为什么", "什么", "哪些",
            "是否", "能否", "能不能", "可不可以", "介绍", "解释"
        )

        val technicalQuestionKeywords = listOf(
            "api", "接口", "模型", "参数", "教程", "文档", "调用",
            "原理", "区别", "支持哪些", "有哪些", "怎么用", "如何用",
            "代码", "函数", "程序", "算法", "报错", "失败", "无法",
            "不能", "技巧"
        )

        val builtInEditKeywords = listOf(
            "换成", "改成", "添加", "去掉", "移除", "擦除",
            "扩图", "局部重绘", "裁剪", "调亮", "调暗", "增强"
        )

        val builtInVisualEditKeywords = listOf(
            "换背景", "改风格", "删除画面", "增加画面", "调整颜色",
            "继续编辑", "换成", "改成", "扩图", "局部重绘", "裁剪",
            "调亮", "调暗"
        )
    }
}
