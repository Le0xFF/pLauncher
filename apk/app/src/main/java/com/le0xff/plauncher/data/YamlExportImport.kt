package com.le0xff.plauncher.data

/**
 * pLauncher Companion App — YAML-based export/import of app lists. Validates duplicates, positions, and auto-launch conflicts on import.
 *
 * @author Le0xFF
 */

import android.content.pm.PackageManager
import com.le0xff.plauncher.model.LaunchApp
import org.yaml.snakeyaml.Yaml

private data class ValidatedEntries(
    val valid: List<Map<String, Any>>,
    val skippedPackages: List<String>,
    val duplicatePackages: List<String>,
    val duplicatePositionPackages: List<String>,
    val outOfRangePackages: List<String>,
    val autoLaunchCount: Int,
    val maxAppsExceeded: Boolean
)

private data class BuiltApps(
    val apps: List<LaunchApp>,
    val autoLaunchTarget: Int,
    val autoLaunchPackages: List<String>,
    val extraSkipped: List<String>
)

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

    private const val MaxApps = 20

    // Format app list as YAML with package, custom_name, position, and auto_launch fields.
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

    // Parse YAML, validate duplicates/positions/auto-launch conflicts, resolve display names from PackageManager.
    fun importAppsFromYaml(
        yamlContent: String,
        packageManager: PackageManager,
        @Suppress("UnusedParameter") contextPackageName: String
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

        val validated = validateEntries(parsedList)
        val multipleAutoLaunch = validated.autoLaunchCount > 1

        val sortedEntries = validated.valid.sortedWith(compareBy { getIntValue(it, "position") ?: 0 })

        val built = buildAppList(sortedEntries, packageManager)

        val allSkipped = validated.skippedPackages + built.extraSkipped

        val finalTarget = clampAutoLaunchTarget(built.autoLaunchTarget, built.apps.size)

        return ImportResult(
            apps = built.apps,
            skippedPackages = allSkipped,
            duplicatePackages = validated.duplicatePackages,
            duplicatePositionPackages = validated.duplicatePositionPackages,
            outOfRangePackages = validated.outOfRangePackages,
            multipleAutoLaunch = multipleAutoLaunch,
            multipleAutoLaunchPackages = built.autoLaunchPackages,
            maxAppsExceeded = validated.maxAppsExceeded,
            autoLaunchTarget = finalTarget
        )
    }

    @Suppress("CognitiveComplexMethod", "CyclomaticComplexMethod")
    private fun validateEntries(parsedList: List<Map<String, Any>>): ValidatedEntries {
        val skippedPackages = mutableListOf<String>()
        val seenPackages = mutableSetOf<String>()
        val seenPositions = mutableMapOf<Int, String>()
        val duplicatePackages = mutableListOf<String>()
        val duplicatePositionPackages = mutableListOf<String>()
        val outOfRangePackages = mutableListOf<String>()
        var autoLaunchCount = 0
        val validEntries = mutableListOf<Map<String, Any>>()

        for (entry in parsedList) {
            val pkg = getStringValue(entry, "package") ?: continue
            if (pkg.isBlank()) continue
            if (validEntries.size >= MaxApps) break
            if (seenPackages.contains(pkg)) {
                duplicatePackages.add(pkg)
                continue
            }
            val position = getIntValue(entry, "position")
            if (position != null && position >= MaxApps) {
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

        val maxAppsExceeded = parsedList.size > MaxApps

        return ValidatedEntries(
            valid = validEntries,
            skippedPackages = skippedPackages,
            duplicatePackages = duplicatePackages,
            duplicatePositionPackages = duplicatePositionPackages,
            outOfRangePackages = outOfRangePackages,
            autoLaunchCount = autoLaunchCount,
            maxAppsExceeded = maxAppsExceeded
        )
    }

    private fun buildAppList(
        sortedEntries: List<Map<String, Any>>,
        packageManager: PackageManager
    ): BuiltApps {
        var firstAutoLaunchFound = false
        val resultApps = mutableListOf<LaunchApp>()
        var autoLaunchTarget = 0
        val autoLaunchPackages = mutableListOf<String>()
        val extraSkipped = mutableListOf<String>()

        sortedEntries.forEachIndexed { newIndex, entry ->
            val pkg = getStringValue(entry, "package") ?: return@forEachIndexed
            val customName = getStringValue(entry, "custom_name")
            val autoLaunchRaw = getBoolValue(entry, "auto_launch")

            if (autoLaunchRaw == true && !firstAutoLaunchFound) {
                firstAutoLaunchFound = true
                autoLaunchTarget = newIndex
            }

            val displayName = resolveDisplayName(customName, pkg, packageManager)
            if (displayName == null) {
                extraSkipped.add(pkg)
                return@forEachIndexed
            }

            if (autoLaunchRaw == true) {
                autoLaunchPackages.add(pkg)
            }

            resultApps.add(LaunchApp(pkg, displayName))
        }

        return BuiltApps(resultApps, autoLaunchTarget, autoLaunchPackages, extraSkipped)
    }

    private fun resolveDisplayName(
        customName: String?,
        pkg: String,
        packageManager: PackageManager
    ): String? {
        if (!customName.isNullOrBlank()) return customName
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            info.loadLabel(packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun clampAutoLaunchTarget(target: Int, size: Int): Int {
        if (target >= size) return if (size > 0) 0 else 0
        return target
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
