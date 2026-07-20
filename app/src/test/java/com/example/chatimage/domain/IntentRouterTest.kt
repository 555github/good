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
}
