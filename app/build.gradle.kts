import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Debug endpoints come from local.properties or -P, so each dev machine can
// point at its own LAN ingest.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}

fun prop(name: String, default: String): String =
    (findProperty(name) as String?) ?: localProps.getProperty(name) ?: default

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "zone.gtfs.wifi"
    compileSdk = 37

    defaultConfig {
        applicationId = "zone.gtfs.wifi"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "TRANSITOUS_URL", quoted("https://api.transitous.org"))
        buildConfigField("String", "REPO_URL", quoted("https://github.com/gtfs-zone/gtfs-zone-wifi-app"))
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            val ingest = prop("wifi.ingestUrl", "http://10.0.2.2:8000")
            buildConfigField("String", "INGEST_URL", quoted(ingest))
            buildConfigField("String", "PROBE_URL", quoted("$ingest/generate_204"))
            buildConfigField("String", "DATA_URL", quoted(prop("wifi.dataUrl", "https://data.wifi.gtfs.zone")))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "INGEST_URL", quoted("https://ingest.wifi.gtfs.zone"))
            // Plain HTTP, so a captive portal can answer it.
            buildConfigField("String", "PROBE_URL", quoted("http://ingest.wifi.gtfs.zone/generate_204"))
            buildConfigField("String", "DATA_URL", quoted("https://data.wifi.gtfs.zone"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        // Newer versions are picked up on purpose, not on every lint run.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.okhttp.mockwebserver)
}
