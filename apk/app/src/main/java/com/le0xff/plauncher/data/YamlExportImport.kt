package com.le0xff.plauncher.data

import android.content.pm.PackageManager
import com.le0xff.plauncher.model.LaunchApp
import org.yaml.snakeyaml.Yaml

data class ImportResult(
    val apps: List<LaunchApp>,
    val skippedPackages: List<String>,
    val duplicatePackages: List<String>,
    val duplicatePositionPackages: List<String>,
    val outOfRangePackages: List<String>,
    val multipleAutoLaunch: Boolean,
    val multipleAutoLaunchPackages: List<String>,
    val maxAppsExceeded: Boolean,
    val autoLaunchTarget: Int
)

object YamlExportImport {

    fun exportAppsToYaml(
        apps: List<LaunchApp>,
        originalNames: Map<String, String>,
        autoLaunchTarget: Int
    ): String {
        val lines = mutableListOf<String>()
        apps.forEachIndexed { index, app ->
            val originalName = originalNames[app.packageName] ?: app.packageName
            val customName = if (app.displayName != originalName) app.displayName else ""
            val autoLaunch = index == autoLaunchTarget
            lines.add("- package: \"${escapeYamlString(app.packageName)}\"")
            lines.add("  custom_name: \"${escapeYamlString(customName)}\"")
            lines.add("  position: $index")
            lines.add("  auto_launch: $autoLaunch")
        }
        return lines.joinToString("\n")
    }

    fun importAppsFromYaml(
        yamlContent: String,
        packageManager: PackageManager,
        contextPackageName: String
    ): ImportResult {
        val emptyResult = ImportResult(
            apps = emptyList(),
            skippedPackages = emptyList(),
            duplicatePackages = emptyList(),
            duplicatePositionPackages = emptyList(),
            outOfRangePackages = emptyList(),
            multipleAutoLaunch = false,
            multipleAutoLaunchPackages = emptyList(),
            maxAppsExceeded = false,
            autoLaunchTarget = 0
        )

        if (yamlContent.isBlank()) {
            return emptyResult
        }

        val yaml = Yaml()
        val parsedList: List<Map<String, Any>> = yaml.load(yamlContent)
            ?: return emptyResult

        val skippedPackages = mutableListOf<String>()
        val seenPackages = mutableSetOf<String>()
        val seenPositions = mutableMapOf<Int, String>()
        val duplicatePackages = mutableListOf<String>()
        val duplicatePositionPackages = mutableListOf<String>()
        val outOfRangePackages = mutableListOf<String>()
        var autoLaunchCount = 0
        var firstAutoLaunchFound = false
        val autoLaunchPackages = mutableListOf<String>()
        val validEntries = mutableListOf<Map<String, Any>>()

        for (entry in parsedList) {
            val pkg = getStringValue(entry, "package")
                ?: continue

            if (pkg.isBlank()) {
                continue
            }

            if (validEntries.size >= 20) {
                break
            }

            if (seenPackages.contains(pkg)) {
                duplicatePackages.add(pkg)
                continue
            }

            val position = getIntValue(entry, "position")
            if (position != null && position >= 20) {
                outOfRangePackages.add(pkg)
                continue
            }

            if (position != null && seenPositions.containsKey(position)) {
                duplicatePositionPackages.add(pkg)
                continue
            }

            seenPackages.add(pkg)
            if (position != null) {
                seenPositions[position] = pkg
            }

            val autoLaunch = getBoolValue(entry, "auto_launch")
            if (autoLaunch == true) {
                autoLaunchCount++
            }

            validEntries.add(entry)
        }

        val maxAppsExceeded = parsedList.size > 20
        val multipleAutoLaunch = autoLaunchCount > 1

        val sortedEntries = validEntries.sortedWith(compareBy { getIntValue(it, "position") ?: 0 })

        firstAutoLaunchFound = false
        val resultApps = mutableListOf<LaunchApp>()
        var autoLaunchTarget = 0

        sortedEntries.forEachIndexed { newIndex, entry ->
            val pkg = getStringValue(entry, "package") ?: return@forEachIndexed
            val customName = getStringValue(entry, "custom_name")
            val autoLaunchRaw = getBoolValue(entry, "auto_launch")

            if (autoLaunchRaw == true && !firstAutoLaunchFound) {
                firstAutoLaunchFound = true
                autoLaunchTarget = newIndex
            }

            val displayName = if (!customName.isNullOrBlank()) {
                customName
            } else {
                try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    info.loadLabel(packageManager).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    skippedPackages.add(pkg)
                    return@forEachIndexed
                }
            }

            if (autoLaunchRaw == true) {
                autoLaunchPackages.add(pkg)
            }

            resultApps.add(LaunchApp(pkg, displayName))
        }

        if (autoLaunchTarget >= resultApps.size) {
            autoLaunchTarget = if (resultApps.isNotEmpty()) 0 else 0
        }

        return ImportResult(
            apps = resultApps,
            skippedPackages = skippedPackages,
            duplicatePackages = duplicatePackages,
            duplicatePositionPackages = duplicatePositionPackages,
            outOfRangePackages = outOfRangePackages,
            multipleAutoLaunch = multipleAutoLaunch,
            multipleAutoLaunchPackages = autoLaunchPackages,
            maxAppsExceeded = maxAppsExceeded,
            autoLaunchTarget = autoLaunchTarget
        )
    }

    private fun escapeYamlString(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun getStringValue(map: Map<String, Any>, key: String): String? {
        val value = map[key] ?: return null
        return value.toString()
    }

    private fun getIntValue(map: Map<String, Any>, key: String): Int? {
        val value = map[key] ?: return null
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Number -> value.toInt()
            else -> null
        }
    }

    private fun getBoolValue(map: Map<String, Any>, key: String): Boolean? {
        val value = map[key] ?: return null
        return when (value) {
            is Boolean -> value
            is String -> value.toBoolean()
            else -> null
        }
    }
}
