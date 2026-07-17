package com.example.chatimage.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll as composeHorizontalScroll
import androidx.compose.ui.Modifier

fun Modifier.horizontalScroll(
    state: ScrollState
): Modifier {
    return this.composeHorizontalScroll(
        state = state
    )
}
