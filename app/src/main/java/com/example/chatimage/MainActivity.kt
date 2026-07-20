package com.example.chatimage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
