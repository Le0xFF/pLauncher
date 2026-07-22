package com.le0xff.plauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppPickerDialog(onDismiss: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Text("App Picker")
    }
}
