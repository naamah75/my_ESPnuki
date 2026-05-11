import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

val buildTimestamp = ZonedDateTime.now(ZoneId.systemDefault())
  .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
val localProperties = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.exists()) {
    file.inputStream().use { load(it) }
  }
}
val bleSharedSecret = (localProperties.getProperty("bleSharedSecret") ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
val repositoryUrl = "https://github.com/naamah75/my_ESPnuki"

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.example.doorapp"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.example.doorapp"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "0.9 beta"
    buildConfigField("String", "BUILD_TIMESTAMP", "\"$buildTimestamp\"")
    buildConfigField("String", "BLE_SHARED_SECRET", "\"$bleSharedSecret\"")
    buildConfigField("String", "REPOSITORY_URL", "\"$repositoryUrl\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
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

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    buildConfig = true
    compose = true
  }

    packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.activity:activity-compose:1.9.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
  implementation("androidx.biometric:biometric:1.1.0")
  implementation(composeBom)
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")

  debugImplementation(composeBom)
  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
