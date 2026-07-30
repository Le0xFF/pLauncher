package com.le0xff.plauncher.model

enum class SortOrder {
    Ascending,
    Descending
}

data class LaunchApp(
    val packageName: String,
    val displayName: String
)
