import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val signingStoreFile = providers.environmentVariable("SIGNING_STORE_FILE").orNull
val signingStorePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.tallybook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tallybook"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(signingStoreFile))
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

tasks.register("verifyReleaseSigning") {
    doLast {
        check(hasReleaseSigning) {
            "Missing release signing environment variables: " +
                "SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD"
        }
        check(file(requireNotNull(signingStoreFile)).isFile) {
            "SIGNING_STORE_FILE does not point to a file"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.core.database)

    implementation(projects.feature.transactions)
    implementation(projects.feature.insights)
    implementation(projects.feature.budget)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // WorkManager + Hilt集成：worker 需要构造注入，必须由 Hilt 提供工厂
    implementation(libs.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
