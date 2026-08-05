import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionPropsFile = file("version.properties")

fun loadVersionProperties(): Properties {
    val props = Properties()
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { props.load(it) }
    }
    return props
}

fun readVersionCode(): Int =
    loadVersionProperties().getProperty("versionCode")?.toIntOrNull() ?: 1

fun writeVersionCode(code: Int) {
    val props = loadVersionProperties()
    props.setProperty("versionCode", code.toString())
    versionPropsFile.outputStream().use {
        props.store(it, "Build number (auto incremented on each build)")
    }
}

val currentVersionCode = readVersionCode()

android {
    namespace = "com.centollu.comicreader"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.centollu.comicreader"
        minSdk = 26
        targetSdk = 37
        versionCode = currentVersionCode
        versionName = "1.0.$currentVersionCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.register("incrementVersionCode") {
    doLast {
        writeVersionCode(readVersionCode() + 1)
    }
}

afterEvaluate {
    tasks.named("preBuild") {
        dependsOn("incrementVersionCode")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Room (Local DB)
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Archive handlers (CBZ / CBR)
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.github.junrar:junrar:8.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
