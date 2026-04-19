plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

base {
    archivesName.set("android-phone-api")
}

val versionNameOverride = providers.gradleProperty("versionNameOverride")
val releaseStoreFile = providers.environmentVariable("ANDROID_PHONE_API_RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("ANDROID_PHONE_API_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_PHONE_API_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_PHONE_API_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isPresent }

android {
    namespace = "com.jatm.androidphoneapi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jatm.androidphoneapi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = versionNameOverride.orElse("0.1.1").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }

        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
}
