plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lyon.wearinstaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lyon.wearinstaller"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("$rootDir/keystore/wearinstaller.jks")
            storePassword = "wearinstaller"
            keyAlias = "wearinstaller"
            keyPassword = "wearinstaller"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 方便直接产出可安装的 release 包
            signingConfig = signingConfigs.getByName("debug")
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Wear Compose 1.6.0：TransformingLazyColumn / SurfaceTransformation 等新列表 API 已具备，
    // 且不需要 AGP 9 / compileSdk 37。1.7.0 要求 AGP 9.1+ 与 compileSdk 37，暂不升级。
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material3:1.6.0")
    implementation("androidx.wear.compose:compose-foundation:1.6.0")
    // 官方页面管理器：SwipeDismissableNavHost / rememberSwipeDismissableNavController，
    // 让右滑/返回键回到上一页而不是直接退出应用。
    implementation("androidx.wear.compose:compose-navigation:1.6.0")
    // SwipeDismissableNavHost 的图构建使用标准 NavGraphBuilder DSL（composable(...)）。
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.wear.compose:compose-ui-tooling:1.6.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 直连 adbd 的 ADB 协议实现（无需 adb 可执行文件 / adb server）
    implementation("dev.mobile:dadb:2.0.0")
}