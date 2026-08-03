import java.security.MessageDigest
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.le0xff.plauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.le0xff.plauncher"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
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
}

val pbwDir = rootProject.projectDir.parentFile?.resolve("pbw") ?: file("../pbw")
val watchPbw = pbwDir.resolve("build/pbw.pbw")

val buildWatchapp = tasks.register<Exec>("buildWatchapp") {
    workingDir = pbwDir
    commandLine = listOf("pebble", "build")
    isIgnoreExitValue = true
    standardOutput = System.out
    errorOutput = System.err
}

val bundleWatchPbw = tasks.register<Copy>("bundleWatchPbw") {
    from(watchPbw)
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "plauncher.pbw" }
    dependsOn(buildWatchapp)
    onlyIf { watchPbw.exists() }
}

val generatePbwInfo = tasks.register("generatePbwInfo") {
    dependsOn(buildWatchapp)
    onlyIf { watchPbw.exists() }

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

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(bundleWatchPbw)
    dependsOn(generatePbwInfo)
}
