@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import java.io.FileInputStream
import java.util.Properties
import java.net.URI
import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
    alias(libs.plugins.ktfmt.gradle)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")

val splitApks = !project.hasProperty("noSplits")

val abiFilterList = (properties["ABI_FILTERS"] as String).split(';')

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

val baseVersionName = currentVersion.name
val currentVersionCode = currentVersion.code.toInt()

android {
    compileSdk = 37

    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        signingConfigs {
            create("githubPublish") {
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
                storeFile = file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"].toString()
            }
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    defaultConfig {
        applicationId = "com.chloemlla.seal"
        minSdk = 26
        targetSdk = 37
        versionCode = 200_000_150
        check(versionCode == currentVersionCode)

        versionName = baseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        if (splitApks) {
            splits {
                abi {
                    isEnable = true
                    reset()
                    include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                    isUniversalApk = true
                }
            }
        } else {
            ndk { abiFilters.addAll(abiFilterList) }
        }
    }

    room { schemaDirectory("$projectDir/schemas") }
    ksp { arg("room.incremental", "true") }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val name =
                    if (splitApks) {
                        output.filters
                            .find { it.filterType == FilterConfiguration.FilterType.ABI }
                            ?.identifier
                    } else {
                        abiFilterList.firstOrNull()
                    }

                val baseAbiCode = abiCodes[name]

                if (baseAbiCode != null) {
                    output.versionCode.set(baseAbiCode + (output.versionCode.get() ?: 0))
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("githubPublish")
            }
        }
        debug {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("githubPublish")
            }
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Seal Debug")
        }
    }

    flavorDimensions += "publishChannel"

    productFlavors {
        create("generic") {
            dimension = "publishChannel"
            isDefault = true
        }

        create("githubPreview") {
            dimension = "publishChannel"
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "Seal Preview")
        }

        create("fdroid") {
            dimension = "publishChannel"
            versionName = "$baseVersionName-(F-Droid)"
        }
    }

    lint { disable.addAll(listOf("MissingTranslation", "ExtraTranslation", "MissingQuantity")) }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs {
            // Replaces android:extractNativeLibs=true (AGP forbids that manifest attr).
            useLegacyPackaging = true
            // youtubedl-android ships zip payloads named *.zip.so; llvm-strip cannot process them
            keepDebugSymbols += setOf("**/libaria2c.zip.so", "**/libffmpeg.zip.so", "**/libpython.zip.so")
        }
    }
    androidResources { generateLocaleConfig = true }

    namespace = "com.chloemlla.seal"
}

ktfmt { kotlinLangStyle() }

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Migrated from deprecated android.kotlinOptions.freeCompilerArgs
        optIn.add("kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(project(":color"))

    implementation(libs.bundles.core)
    // Pinned to latest Project Lumen main auto release (lumen-crash-v0.1.0-2528201a).
    implementation("com.chloemlla.lumen:lumen-crash:0.1.0-2528201a")

    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidxCompose)
    implementation(libs.bundles.accompanist)

    implementation(libs.coil.kt.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)

    implementation(libs.bundles.youtubedlAndroid)

    implementation(libs.mmkv)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.compose.ui.tooling)
}

// Bundle latest Stable yt-dlp over youtubedl-android res/raw/ytdlp at packaging time.
val skipYtDlpDownload: Boolean = project.hasProperty("skipYtDlpDownload")
val ytDlpRawFile: File = file("src/main/res/raw/ytdlp")
val ytDlpVersionFile: File = file("ytdlp.version") // keep outside res/raw (name would collide with ytdlp)

abstract class DownloadStableYtDlpTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:OutputFile
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val skipDownload: Property<Boolean>

    @TaskAction
    fun run() {
        val out = outputFile.get().asFile
        val ver = versionFile.get().asFile
        out.parentFile.mkdirs()
        ver.parentFile?.mkdirs()

        // Android raw resources strip extensions: ytdlp.version would collide with ytdlp.
        // Also never leave download temps inside res/raw.
        sequenceOf(
                File(out.parentFile, "ytdlp.version"),
                File(out.parentFile, "ytdlp.download.tmp"),
                File(out.parentFile, "ytdlp.tmp"),
            )
            .forEach { stray ->
                if (stray.exists() && stray != out) {
                    logger.lifecycle("Removing stray raw resource: ${stray.absolutePath}")
                    stray.delete()
                }
            }

        if (skipDownload.get()) {
            if (!out.isFile || out.length() < 100_000L) {
                throw GradleException(
                    "skipYtDlpDownload is set but ${out.absolutePath} is missing/too small. " +
                        "Run a full build once without -PskipYtDlpDownload."
                )
            }
            logger.lifecycle(
                "skipYtDlpDownload: reusing ${out.absolutePath} (${out.length()} bytes)"
            )
            return
        }

        val apiUrl = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        val assetUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
        val userAgent = "Seal-Gradle-YtDlp-Bundler"

        fun connect(url: String): java.net.URLConnection =
            URI(url).toURL().openConnection().apply {
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/octet-stream, application/json")
                connectTimeout = 30_000
                readTimeout = 180_000
            }

        var tag =
            if (ver.isFile) ver.readText().trim().ifEmpty { "unknown" } else "unknown"
        try {
            connect(apiUrl).getInputStream().bufferedReader().use { reader ->
                val body = reader.readText()
                val match = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)
                if (match != null) {
                    tag = match.groupValues[1]
                }
            }
        } catch (t: Throwable) {
            logger.warn("Could not resolve yt-dlp latest tag: ${t.message}")
        }

        // Keep temp outside res/raw so interrupted downloads cannot create duplicate raw names.
        val tmp =
            File(ver.parentFile ?: project.layout.projectDirectory.asFile, "ytdlp.download.tmp")
        try {
            logger.lifecycle("Downloading yt-dlp Stable (${tag}) ...")
            connect(assetUrl).getInputStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmp.length() < 100_000L) {
                throw GradleException("Downloaded yt-dlp is too small: ${tmp.length()} bytes")
            }
            val prefixLen = minOf(64, tmp.length().toInt())
            val prefix = Files.readAllBytes(tmp.toPath()).copyOfRange(0, prefixLen)
            val hasShebang =
                prefix.size >= 2 &&
                    prefix[0] == '#'.code.toByte() &&
                    prefix[1] == '!'.code.toByte()
            val hasZipMagic = prefix.toString(Charsets.ISO_8859_1).contains("PK")
            if (!hasShebang && !hasZipMagic) {
                throw GradleException("Downloaded file does not look like a yt-dlp zipapp")
            }
            tmp.copyTo(out, overwrite = true)
            ver.writeText("${tag}\n")
            logger.lifecycle(
                "Bundled yt-dlp Stable ${tag} -> ${out.absolutePath} (${out.length()} bytes)"
            )
        } catch (t: Throwable) {
            if (out.isFile && out.length() > 100_000L) {
                logger.warn(
                    "yt-dlp download failed (${t.message}); reusing cached ${out.absolutePath}"
                )
            } else {
                throw GradleException(
                    "Failed to download Stable yt-dlp for packaging: ${t.message}",
                    t,
                )
            }
        } finally {
            if (tmp.exists()) {
                tmp.delete()
            }
        }
    }
}

val downloadStableYtDlp by
    tasks.registering(DownloadStableYtDlpTask::class) {
        group = "build"
        description =
            "Download latest Stable yt-dlp into res/raw/ytdlp (overrides library bundle)"
        outputFile.set(ytDlpRawFile)
        versionFile.set(ytDlpVersionFile)
        skipDownload.set(skipYtDlpDownload)
    }

tasks.named("preBuild").configure { dependsOn(downloadStableYtDlp) }

run {
    val bundled =
        if (ytDlpVersionFile.isFile) {
            ytDlpVersionFile.readText().trim().ifEmpty { "pending" }
        } else {
            "pending"
        }
    android.defaultConfig.buildConfigField(
        "String",
        "YT_DLP_BUNDLED_VERSION",
        "\"$bundled\"",
    )
}
