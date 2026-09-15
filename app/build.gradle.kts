import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.lifefreshcrm.pro.inkgql"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.0.1"

    // AI features enabled
    buildConfigField("boolean", "AI_FEATURES_ENABLED", "true")

    // Read .env safely without breaking syntax if empty or missing
    val envProps = Properties()
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
      FileInputStream(envFile).use { stream ->
        envProps.load(stream)
      }
    }
    val geminiKey = (System.getenv("GEMINI_API_KEY") ?: envProps.getProperty("GEMINI_API_KEY") ?: "")
      .trim().replace("\"", "\\\"")
    val groqKey = (System.getenv("GROQ_API_KEY") ?: envProps.getProperty("GROQ_API_KEY") ?: "")
      .trim().replace("\"", "\\\"")

    buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/lifefresh-quicknote-pro-upload.jks"
  val releaseKeystoreFile = file(releaseKeystorePath)
  val hasReleaseKeystore = releaseKeystoreFile.exists()

  signingConfigs {
    if (hasReleaseKeystore) {
      create("release") {
        storeFile = releaseKeystoreFile
        storePassword = System.getenv("PRO_KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "lifefreshpro"
        keyPassword = System.getenv("PRO_KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD") ?: (System.getenv("PRO_KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD"))
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseKeystore) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
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
  implementation(libs.androidx.core.splashscreen)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // Firebase AI dependency intentionally disabled for the non-AI release.
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.play.services.auth)
  implementation(libs.play.feature.delivery)
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
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  implementation(libs.androidx.work.runtime.ktx)
}

abstract class GenerateRingtonesTask : DefaultTask() {
  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun generate() {
    val assetsDir = outputDir.get().asFile
    assetsDir.mkdirs()
    
    fun intToBytes(value: Int): ByteArray = byteArrayOf(
      (value and 0xFF).toByte(),
      ((value shr 8) and 0xFF).toByte(),
      ((value shr 16) and 0xFF).toByte(),
      ((value shr 24) and 0xFF).toByte()
    )

    fun shortToBytes(value: Short): ByteArray = byteArrayOf(
      (value.toInt() and 0xFF).toByte(),
      ((value.toInt() shr 8) and 0xFF).toByte()
    )

    fun writeWav(name: String, sampleRate: Int, duration: Double, generator: (Int, Int) -> Short) {
      val outputFile = File(assetsDir, "$name.wav")
      val numChannels = 1
      val bitsPerSample = 16
      val blockAlign = numChannels * bitsPerSample / 8
      val byteRate = sampleRate * blockAlign
      val numSamples = (sampleRate * duration).toInt()
      val subChunk2Size = numSamples * blockAlign
      val chunkSize = 36 + subChunk2Size

      outputFile.outputStream().use { out ->
        out.write("RIFF".toByteArray())
        out.write(intToBytes(chunkSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToBytes(16))
        out.write(shortToBytes(1))
        out.write(shortToBytes(numChannels.toShort()))
        out.write(intToBytes(sampleRate))
        out.write(intToBytes(byteRate))
        out.write(shortToBytes(blockAlign.toShort()))
        out.write(shortToBytes(bitsPerSample.toShort()))
        out.write("data".toByteArray())
        out.write(intToBytes(subChunk2Size))

        for (i in 0 until numSamples) {
          out.write(shortToBytes(generator(i, sampleRate)))
        }
      }
    }

    // 1. Holiday (Simple cheerful chime notes)
    writeWav("holiday", 8000, 2.0) { i, sr ->
      val t = i.toDouble() / sr
      val freq = when {
        t < 0.5 -> 523.25
        t < 1.0 -> 659.25
        t < 1.5 -> 784.00
        else -> 1046.50
      }
      val vol = 1.0 - (t % 0.5) * 1.5
      val angle = 2.0 * Math.PI * freq * t
      (Math.sin(angle) * 32767 * vol.coerceIn(0.0, 1.0)).toInt().toShort()
    }

    // 2. Morning Bell (Ringing bells every 0.5s)
    writeWav("morning_bell", 8000, 2.0) { i, sr ->
      val t = i.toDouble() / sr
      val bellTime = t % 0.5
      val decay = Math.exp(-6.0 * bellTime)
      val angle1 = 2.0 * Math.PI * 440.0 * bellTime
      val angle2 = 2.0 * Math.PI * 880.0 * bellTime
      val sample = (Math.sin(angle1) * 0.7 + Math.sin(angle2) * 0.3)
      (sample * 32767 * decay).toInt().toShort()
    }

    // 3. Soft Chime (Fading high bell chime)
    writeWav("soft_chime", 8000, 2.0) { i, sr ->
      val t = i.toDouble() / sr
      val decay = Math.exp(-3.0 * t)
      val angle = 2.0 * Math.PI * 1200.0 * t
      (Math.sin(angle) * 32767 * decay).toInt().toShort()
    }

    // 4. Medical Reminder (Alarm/Warning beep series)
    writeWav("medical_reminder", 8000, 2.0) { i, sr ->
      val t = i.toDouble() / sr
      val cycle = t % 0.6
      val soundOn = cycle < 0.15 || (cycle >= 0.20 && cycle < 0.35)
      if (soundOn) {
        val angle = 2.0 * Math.PI * 950.0 * t
        (Math.sin(angle) * 32767).toInt().toShort()
      } else {
        0.toShort()
      }
    }

    // 5. Fresh Alert (Cute double-chirp alarm)
    writeWav("fresh_alert", 8000, 1.5) { i, sr ->
      val t = i.toDouble() / sr
      val cycle = t % 0.5
      val soundOn = cycle < 0.25
      if (soundOn) {
        val currentFreq = 400.0 + (cycle / 0.25) * 800.0
        val angle = 2.0 * Math.PI * currentFreq * t
        (Math.sin(angle) * 32767).toInt().toShort()
      } else {
        0.toShort()
      }
    }

    // 6. Nature Bell (Low peaceful bell sound)
    writeWav("nature_bell", 8000, 2.5) { i, sr ->
      val t = i.toDouble() / sr
      val decay = Math.exp(-1.5 * t)
      val a1 = 2.0 * Math.PI * 261.63 * t
      val a2 = 2.0 * Math.PI * 329.63 * t
      val a3 = 2.0 * Math.PI * 392.00 * t
      val sample = (Math.sin(a1) * 0.4 + Math.sin(a2) * 0.3 + Math.sin(a3) * 0.3)
      (sample * 32767 * decay).toInt().toShort()
    }
  }
}

val generateRingtones = tasks.register<GenerateRingtonesTask>("generateRingtones") {
  outputDir.set(project.layout.projectDirectory.dir("src/main/assets/sounds"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  dependsOn(generateRingtones)
  if (name.contains("Test", ignoreCase = true)) {
    exclude("**/VoiceIntelligenceTest.kt")
    exclude("**/BulkIntelligenceTest.kt")
    exclude("**/AIReminderIntelligenceTest.kt")
    exclude("**/AIConversationEngineTest.kt")
    exclude("**/AISchedulerEngineTest.kt")
    exclude("**/DocumentIntelligenceTest.kt")
    exclude("**/AIAnalyticsEngineTest.kt")
    exclude("**/OcrIntelligenceTest.kt")
    exclude("**/HybridRuntimeTest.kt")
    exclude("**/AIKnowledgeCacheEngineTest.kt")
    exclude("**/AIAuditEngineTest.kt")
    exclude("**/AIWorkflowEngineTest.kt")
    exclude("**/LLMIntegrationFrameworkTest.kt")
  }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
  dependsOn(generateRingtones)
}



