package com.le0xff.plauncher.model

/**
 * pLauncher Companion App — Data model for launcher entries. Defines the LaunchApp data class and SortOrder enum.
 *
 * @author Le0xFF
 */

enum class SortOrder {
    Ascending,
    Descending
}

data class LaunchApp(
    val packageName: String,
    val displayName: String,
    val iconColorData: ByteArray? = null,
    val iconBwData: ByteArray? = null
)
