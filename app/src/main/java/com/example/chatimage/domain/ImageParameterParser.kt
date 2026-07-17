package com.example.chatimage.domain

import com.example.chatimage.data.api.ImageRequestOptions
import com.example.chatimage.data.model.AppSettings

data class ParsedImageParameters(
    val size: String,
    val quality: String,
    val count: Int,
    val responseFormat: String,
    val detectedChanges: List<String>
)

class ImageParameterParser {

    fun parse(
        prompt: String,
        settings: AppSettings,
        model: String,
        sourceImagePath: String?
    ): Pair<
        ImageRequestOptions,
        ParsedImageParameters
        > {
        val defaults =
            settings.imageParameters

        var size = defaults.size
        var quality = defaults.quality
        var count = defaults.count
            .coerceIn(1, 10)

        val changes =
            mutableListOf<String>()

        val cleanPrompt =
            prompt.trim()

        detectExplicitSize(
            cleanPrompt
        )?.let {
            size = it
            changes +=
                "从提示词识别尺寸：$it"
        }

        if (
            containsAny(
                cleanPrompt,
                listOf(
                    "16:9",
                    "16：9",
                    "横屏",
                    "宽屏",
                    "横向构图"
                )
            ) &&
            detectExplicitSize(
                cleanPrompt
            ) == null
        ) {
            size = choosePreferredSize(
                presets =
                    defaults
                        .generatedSizePresets,
                preferred =
                    listOf(
                        "1792x1024",
                        "1536x1024",
                        "1024x576"
                    ),
                fallback =
                    "1792x1024"
            )

            changes +=
                "识别为横屏构图：$size"
        }

        if (
            containsAny(
                cleanPrompt,
                listOf(
                    "9:16",
                    "9：16",
                    "竖屏",
                    "竖向构图",
                    "手机壁纸"
                )
            ) &&
            detectExplicitSize(
                cleanPrompt
            ) == null
        ) {
            size = choosePreferredSize(
                presets =
                    defaults
                        .generatedSizePresets,
                preferred =
                    listOf(
                        "1024x1792",
                        "1024x1536",
                        "576x1024"
                    ),
                fallback =
                    "1024x1792"
            )

            changes +=
                "识别为竖屏构图：$size"
        }

        if (
            containsAny(
                cleanPrompt,
                listOf(
                    "1:1",
                    "1：1",
                    "正方形",
                    "方形构图"
                )
            ) &&
            detectExplicitSize(
                cleanPrompt
            ) == null
        ) {
            size = choosePreferredSize(
                presets =
                    defaults
                        .generatedSizePresets,
                preferred =
                    listOf(
                        "1024x1024"
                    ),
                fallback =
                    "1024x1024"
            )

            changes +=
                "识别为正方形构图：$size"
        }

        if (
            containsAny(
                cleanPrompt,
                listOf(
                    "高清",
                    "高质量",
                    "超清",
                    "精细",
                    "high quality",
                    "high"
                )
            )
        ) {
            quality = choosePreferredQuality(
                presets =
                    defaults.qualityPresets,
                preferred =
                    listOf(
                        "high",
                        "hd"
                    ),
                fallback = "high"
            )

            changes +=
                "识别为高质量：$quality"
        }

        if (
            containsAny(
                cleanPrompt,
                listOf(
                    "标准质量",
                    "普通质量",
                    "standard"
                )
            )
        ) {
            quality = choosePreferredQuality(
                presets =
                    defaults.qualityPresets,
                preferred =
                    listOf(
                        "standard",
                        "medium"
                    ),
                fallback = "standard"
            )

            changes +=
                "识别为标准质量：$quality"
        }

        detectCount(
            cleanPrompt
        )?.let {
            count = it.coerceIn(1, 10)
            changes +=
                "从提示词识别数量：$count 张"
        }

        val options =
            ImageRequestOptions(
                model = defaults
                    .modelOverride
                    .ifBlank {
                        model
                    },
                prompt = cleanPrompt,
                size = size,
                quality = quality,
                count = count,
                responseFormat =
                    defaults.responseFormat,
                sourceImagePath =
                    sourceImagePath
            )

        val parsed =
            ParsedImageParameters(
                size = size,
                quality = quality,
                count = count,
                responseFormat =
                    defaults.responseFormat,
                detectedChanges =
                    changes
            )

        return options to parsed
    }

    private fun detectExplicitSize(
        prompt: String
    ): String? {
        val match = Regex(
            """(?i)(\d{3,5})\s*[x×X]\s*(\d{3,5})"""
        ).find(prompt)
            ?: return null

        val width = match.groupValues
            .getOrNull(1)
            ?.toIntOrNull()
            ?: return null

        val height = match.groupValues
            .getOrNull(2)
            ?.toIntOrNull()
            ?: return null

        if (
            width !in 128..8192 ||
            height !in 128..8192
        ) {
            return null
        }

        return "${width}x$height"
    }

    private fun detectCount(
        prompt: String
    ): Int? {
        val digitCount = Regex(
            """(\d{1,2})\s*(张|幅|个版本|张图片)"""
        ).find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        if (digitCount != null) {
            return digitCount
        }

        val mappings = listOf(
            "十张" to 10,
            "九张" to 9,
            "八张" to 8,
            "七张" to 7,
            "六张" to 6,
            "五张" to 5,
            "四张" to 4,
            "三张" to 3,
            "两张" to 2,
            "二张" to 2,
            "一张" to 1
        )

        return mappings
            .firstOrNull {
                prompt.contains(it.first)
            }
            ?.second
    }

    private fun choosePreferredSize(
        presets: List<String>,
        preferred: List<String>,
        fallback: String
    ): String {
        preferred.forEach {
            if (it in presets) {
                return it
            }
        }

        return fallback
    }

    private fun choosePreferredQuality(
        presets: List<String>,
        preferred: List<String>,
        fallback: String
    ): String {
        preferred.forEach {
            if (
                presets.any { preset ->
                    preset.equals(
                        it,
                        ignoreCase = true
                    )
                }
            ) {
                return it
            }
        }

        return fallback
    }

    private fun containsAny(
        value: String,
        candidates: List<String>
    ): Boolean {
        return candidates.any {
            value.contains(
                it,
                ignoreCase = true
            )
        }
    }
}
