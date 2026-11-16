import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.x 内置 Kotlin，仅保留 Compose 编译器插件（版本 = Kotlin 版本，传递锁定内置编译器）
    alias(libs.plugins.compose.compiler)
}

// 从本地 local.properties 读取签名信息；文件被 gitignore，密钥不会进公开仓库。
// 其他人 clone 后没有这些配置时，release 会退化为未签名（仅本地调试用），不影响 debug 构建。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val ksPath = localProps.getProperty("liyibook.keystore.file")
val ksPass = localProps.getProperty("liyibook.keystore.password")
val ksAlias = localProps.getProperty("liyibook.keystore.keyAlias")
val ksKeyPass = localProps.getProperty("liyibook.keystore.keyPassword")
val hasSigning = !ksPath.isNullOrBlank() && !ksPass.isNullOrBlank()
        && !ksAlias.isNullOrBlank() && rootProject.file(ksPath!!).exists()

android {
    namespace = "com.example.liyibook"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.liyibook"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(ksPath!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有本地签名配置时才启用，避免其他人 clone 后构建失败
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 内置 Kotlin 的 jvmTarget 自动取 compileOptions.targetCompatibility（17），无需 kotlinOptions

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.gson)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
