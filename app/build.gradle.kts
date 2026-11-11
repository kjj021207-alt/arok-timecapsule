import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // 화면 스크린샷 (에뮬레이터 없이 JVM에서 렌더링): ./gradlew recordPaparazziDebug
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.arok.timecapsule"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.arok.timecapsule"
        // Pleos Connect 요구사항: minSdk API 28 이상 (차량 사이버보안 요건)
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-prototype"

        // Pleos Playground 프로젝트 키 (음성 인식 서버 모드용). 저장소에 올리지 않도록 local.properties에서 읽는다.
        //   pleos.clientId=...
        //   pleos.clientSecret=...
        // 비워 두면 음성 인식은 기기 내장(ON_DEVICE) 모드로 동작한다.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${local.getProperty("pleos.clientId", "")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${local.getProperty("pleos.clientSecret", "")}\"")
    }

    buildTypes {
        release {
            // 안 쓰는 코드·아이콘을 걷어내 APK 크기를 줄인다. Pleos SDK 등은 proguard-rules.pro에서 보존.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 프로토타입 배포용: 디버그 키로 서명해 바로 설치 가능한 release APK를 만든다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.osmdroid)
    implementation(libs.coil.compose)

    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.fusedlocation)
}
