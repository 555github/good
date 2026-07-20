package com.example.chatimage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.chatimage.ui.AppViewModel
import com.example.chatimage.ui.ChatImageApp
import com.example.chatimage.ui.ChatImageTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                3102
            )
        }

        setContent {
            val state by viewModel.uiState.collectAsState()

            ChatImageTheme(
                appearance = state.appSettings.appearance
            ) {
                ChatImageApp(
                    viewModel = viewModel
                )
            }
        }
    }
}
