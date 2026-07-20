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

        val editKeyword =
            imageIntent.editKeywords
                .firstOrNull {
                    cleanPrompt.contains(
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
            imageIntent
                .generationKeywords
                .firstOrNull {
                    cleanPrompt.contains(
                        it,
                        ignoreCase = true
                    )
                }

        /*
         * 同时存在图片编辑词和上文图片时，优先图生图。
         */
        if (
            editKeyword != null &&
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

        /*
         * “上图、刚才图片、上一张”等明确引用也优先编辑。
         */
        if (
            recentImageKeyword != null &&
            !latestGeneratedImagePath
                .isNullOrBlank() &&
            imageIntent
                .autoReferenceRecentImage
        ) {
            return RouteDecision(
                route =
                    RequestRoute.IMAGE_EDIT,
                reason =
                    "识别到上文图片引用“$recentImageKeyword”",
                sourceImagePath =
                    latestGeneratedImagePath,
                requiresConfirmation =
                    imageIntent
                        .showSourceImageBeforeSend
            )
        }

        if (generationKeyword != null) {
            return RouteDecision(
                route =
                    RequestRoute.IMAGE_GENERATION,
                reason =
                    "识别到图片生成词“$generationKeyword”",
                requiresConfirmation = false
            )
        }

        /*
         * 存在编辑指令但没有可编辑图片时，不擅自执行文生图，
         * 由界面提示用户选择图片。
         */
        if (editKeyword != null) {
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
}
