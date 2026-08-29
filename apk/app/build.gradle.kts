import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.le0xff.plauncher"
    compileSdk = 36

    val localPropsFile = File("${System.getenv("HOME")}/ANDROID/local.properties")
    val localProps = if (localPropsFile.exists()) {
        Properties().apply { localPropsFile.inputStream().use { load(it) } }
    } else {
        Properties()
    }
    // Base dir for resolving relative paths in the external local.properties.
    val propsBaseDir = File(System.getenv("HOME") ?: "", "ANDROID").absoluteFile

    signingConfigs {
        create("release") {
            val jksPath = localProps.getProperty("release.jks.file")
                ?: error("release.jks.file not set in ${localPropsFile}")
            val resolved = jksPath.replace("\${HOME}", System.getenv("HOME") ?: "")
            storeFile = if (resolved.startsWith("/")) File(resolved).absoluteFile
            else File(propsBaseDir, resolved).canonicalFile
            storePassword = localProps.getProperty("release.jks.password", "")
            keyAlias = localProps.getProperty("release.jks.key.alias", "release")
            keyPassword = localProps.getProperty("release.jks.key.password", "")
        }
    }

    val majorVersion = "1"
    val minorVersion = "0"
    val patchVersion = "0"

    defaultConfig {
        applicationId = "com.le0xff.plauncher"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "$majorVersion.$minorVersion.$patchVersion"

        buildConfigField("String", "MAJOR_VERSION", "\"$majorVersion\"")
        buildConfigField("String", "MINOR_VERSION", "\"$minorVersion\"")
        buildConfigField("String", "PATCH_VERSION", "\"$patchVersion\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.pebblekit2.client)
    implementation(libs.reorderable)
    implementation(libs.snakeyaml)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    add("detektPlugins", libs.detekt.compose.rules)
}

val pbwDir = rootProject.projectDir.parentFile?.resolve("pbw") ?: file("../pbw")
val watchPbw = pbwDir.resolve("build/pbw.pbw")
val generateIconScript = rootProject.projectDir.parentFile?.resolve("generate_icon.sh") ?: file("../generate_icon.sh")

// val exportPbwIcon = tasks.register<Exec>("exportPbwIcon") {
//     workingDir = rootProject.projectDir.parentFile
//     commandLine = listOf(generateIconScript.toString(), "pbw/pLauncher_pbw.kra", "--pbw")
//     isIgnoreExitValue = true
//     standardOutput = System.out
//     errorOutput = System.err
//
//     doLast {
//         val exitCode = executionResult.get().exitValue
//         if (exitCode != 0) {
//             logger.warn("WARNING: exportPbwIcon failed (exit code $exitCode). The watchapp menu icon will not be updated.")
//         }
//     }
// }

val lintPbw = tasks.register<Exec>("lintPbw") {
    // dependsOn(exportPbwIcon)
    workingDir = pbwDir
    commandLine = listOf("sh", "-c", "find src/c -name \"*.c\" -o -name \"*.h\" -print0 | xargs -0 clang-format --dry-run --Werror --style=file")
    isIgnoreExitValue = true
    standardOutput = System.out
    errorOutput = System.err

    doLast {
        val exitCode = executionResult.get().exitValue
        if (exitCode != 0) {
            logger.warn("WARNING: lintPbw failed (exit code $exitCode). C code is not properly formatted.")
        }
    }
}

val buildWatchapp = tasks.register<Exec>("buildWatchapp") {
    dependsOn(lintPbw)
    workingDir = pbwDir
    commandLine = listOf("pebble", "build")
    isIgnoreExitValue = true
    standardOutput = System.out
    errorOutput = System.err

    doLast {
        val exitCode = executionResult.get().exitValue
        if (exitCode != 0) {
            logger.warn("WARNING: buildWatchapp failed (exit code $exitCode). The bundled watchapp will be stale or missing.")
        }
    }
}

val bundleWatchPbw = tasks.register<Copy>("bundleWatchPbw") {
    from(watchPbw)
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "plauncher.pbw" }
    dependsOn(buildWatchapp)
    onlyIf {
        if (!watchPbw.exists()) {
            logger.warn("WARNING: bundleWatchPbw skipped — pbw.pbw not found. Watchapp not bundled.")
        }
        watchPbw.exists()
    }
}

val generatePbwInfo = tasks.register("generatePbwInfo") {
    dependsOn(buildWatchapp)
    onlyIf {
        if (!watchPbw.exists()) {
            logger.warn("WARNING: generatePbwInfo skipped — pbw.pbw not found. PBW info will not be generated.")
        }
        watchPbw.exists()
    }

    doLast {
        val appinfoFile = pbwDir.resolve("build/appinfo.json")
        val versionLabel = if (appinfoFile.exists()) {
            val content = appinfoFile.readText()
            val pattern = Pattern.compile("\"versionLabel\"\\s*:\\s*\"([^\"]+)\"")
            val matcher = pattern.matcher(content)
            if (matcher.find()) matcher.group(1) else "unknown"
        } else {
            "unknown"
        }

        val md5 = MessageDigest.getInstance("MD5").digest(watchPbw.readBytes()).joinToString("") { "%02x".format(it) }

        val infoFile = layout.projectDirectory.file("src/main/assets/pbw_info.txt").asFile
        infoFile.writeText("version=$versionLabel\nmd5=$md5\n")
    }
}

// val exportApkIcon = tasks.register<Exec>("exportApkIcon") {
//     workingDir = rootProject.projectDir.parentFile
//     commandLine = listOf(generateIconScript.toString(), "apk/pLauncher_apk.kra", "--apk")
//     isIgnoreExitValue = true
//     standardOutput = System.out
//     errorOutput = System.err
//
//     doLast {
//         val exitCode = executionResult.get().exitValue
//         if (exitCode != 0) {
//             logger.warn("WARNING: exportApkIcon failed (exit code $exitCode). The Android app icons will not be updated.")
//         }
//     }
// }

detekt {
    config.setFrom("$rootDir/config/detekt.yml")
    baseline = file("$rootDir/config/detekt-baseline.xml")
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = false
    parallel = true
}

// tasks.named("detektMain") {
//     dependsOn(exportApkIcon)
// }

val lintApk = tasks.register("lintApk") {
    // dependsOn(exportApkIcon)
    dependsOn(tasks.named("detektMain"))
}

val commitHooks = tasks.register<Copy>("commitHooks") {
    from("../../config/hooks/")
    into("../../.git/hooks")
    filePermissions {
        unix("rwxr-xr-x")
    }
}

tasks.named("preBuild").configure {
    dependsOn(commitHooks)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(bundleWatchPbw)
    dependsOn(generatePbwInfo)
    dependsOn(lintApk)
}

// The lint vital analysis task (part of assembleRelease) reads the merged assets
// directory that bundleWatchPbw / generatePbwInfo write into; declare an explicit
// dependency so Gradle orders them before it.
tasks.matching { it.name == "lintVitalAnalyzeRelease" || it.name == "generateReleaseLintVitalReportModel" }.configureEach {
    dependsOn(bundleWatchPbw)
    dependsOn(generatePbwInfo)
}
