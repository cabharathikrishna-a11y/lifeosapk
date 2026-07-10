import java.util.Base64
import java.security.KeyStore
import java.io.FileInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "life.os"
    minSdk = 24
    targetSdk = 36
    val customCode = project.findProperty("customVersionCode")?.toString()?.toIntOrNull()
    val customName = project.findProperty("customVersionName")?.toString()

    versionCode = customCode ?: 19
    versionName = customName ?: "19.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val kFile = rootProject.file("debug.keystore")
      println("DEBUG: kFile path = ${kFile.absolutePath}, exists = ${kFile.exists()}")
      val base64File = rootProject.file("debug.keystore.base64")
      println("DEBUG: base64File path = ${base64File.absolutePath}, exists = ${base64File.exists()}")
      var isKeystoreValid = false
      if (kFile.exists() && kFile.length() > 0) {
        try {
          val ks = KeyStore.getInstance("PKCS12")
          FileInputStream(kFile).use { fis ->
            ks.load(fis, "android".toCharArray())
          }
          isKeystoreValid = true
          println("DEBUG: Existing debug.keystore loaded successfully and is valid!")
        } catch (e: Exception) {
          println("DEBUG: Existing debug.keystore is invalid or corrupted: ${e.message}")
        }
      }

      if (!isKeystoreValid) {
        if (base64File.exists() && base64File.length() > 0) {
          try {
            val base64Text = base64File.readText().replace(Regex("\\s"), "")
            if (base64Text.isNotEmpty()) {
              println("DEBUG: Attempting to decode debug.keystore.base64 to recreate debug.keystore...")
              val decoded = Base64.getDecoder().decode(base64Text)
              kFile.writeBytes(decoded)
              println("DEBUG: Decoded and wrote debug.keystore successfully! File size: ${kFile.length()}")
              try {
                val ks = KeyStore.getInstance("PKCS12")
                FileInputStream(kFile).use { fis ->
                  ks.load(fis, "android".toCharArray())
                }
                isKeystoreValid = true
                println("DEBUG: Decoded debug.keystore verified successfully!")
              } catch (ev: Exception) {
                println("DEBUG: Decoded keystore is still invalid! Error: ${ev.message}")
              }
            }
          } catch (e: Exception) {
            println("DEBUG: Error decoding base64: ${e.message}")
            e.printStackTrace()
          }
        }
      }

      if (!isKeystoreValid) {
        println("DEBUG: WARNING - debug.keystore is still invalid! The build may fail if debug.keystore is not created externally.")
      }

      storeFile = kFile
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

ksp {
  arg("room.schemaLocation", "${projectDir}/schemas")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.startup)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.database)
  implementation(libs.firebase.functions)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.inappmessaging.display)
  implementation(libs.firebase.perf)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.appdistribution)
  implementation(libs.play.services.auth)
  implementation(libs.mediapipe.tasks.genai)
  implementation(libs.mapsforge.map.android)
  implementation(libs.mapsforge.map)
  implementation(libs.mapsforge.themes)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}




