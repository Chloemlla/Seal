package com.chloemlla.seal

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.getSystemService
import com.google.android.material.color.DynamicColors
import com.chloemlla.seal.download.DownloaderV2
import com.chloemlla.seal.download.DownloaderV2Impl
import com.chloemlla.seal.ui.page.download.HomePageViewModel
import com.chloemlla.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.chloemlla.seal.ui.page.settings.directory.Directory
import com.chloemlla.seal.ui.page.settings.network.CookiesViewModel
import com.chloemlla.seal.ui.page.videolist.VideoListViewModel
import com.chloemlla.seal.util.AUDIO_DIRECTORY
import com.chloemlla.seal.util.COMMAND_DIRECTORY
import com.chloemlla.seal.util.DownloadUtil
import com.chloemlla.seal.util.FileUtil
import com.chloemlla.seal.util.FileUtil.getCookiesFile
import com.chloemlla.seal.util.FileUtil.getAppSpecificAudioDownloadDirectory
import com.chloemlla.seal.util.FileUtil.getPreferredDownloadDirectory
import com.chloemlla.seal.util.FileUtil.getPreferredPrivateDownloadDirectory
import com.chloemlla.seal.util.StorageAccess
import com.chloemlla.seal.util.NotificationUtil
import com.chloemlla.seal.util.PreferenceUtil
import com.chloemlla.seal.util.PreferenceUtil.getString
import com.chloemlla.seal.util.PreferenceUtil.updateString
import com.chloemlla.seal.util.SDCARD_URI
import com.chloemlla.seal.util.UpdateUtil
import com.chloemlla.seal.util.VIDEO_DIRECTORY
import com.chloemlla.seal.util.YT_DLP_VERSION
import com.tencent.mmkv.MMKV
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                module {
                    single<DownloaderV2> { DownloaderV2Impl(androidContext()) }
                    viewModel { DownloadDialogViewModel(downloader = get()) }
                    viewModel { HomePageViewModel() }
                    viewModel { CookiesViewModel() }
                    viewModel { VideoListViewModel() }
                }
            )
        }

        context = applicationContext
        packageInfo = com.chloemlla.seal.util.PackageManagerCompat.getPackageInfo(this)
        applicationScope = CoroutineScope(SupervisorJob())
        DynamicColors.applyToActivitiesIfAvailable(this)

        clipboard = getSystemService()!!
        connectivityManager = getSystemService()!!

        applicationScope.launch((Dispatchers.IO)) {
            try {
                YoutubeDL.init(this@App)
                FFmpeg.init(this@App)
                Aria2c.init(this@App)
                DownloadUtil.getCookiesContentFromDatabase().getOrNull()?.let {
                    FileUtil.writeContentToFile(it, getCookiesFile())
                }
                UpdateUtil.deleteOutdatedApk()
            } catch (th: Throwable) {
                withContext(Dispatchers.Main) { startCrashReportActivity(th) }
            }
        }

        val preferredDownloadDir = getPreferredDownloadDirectory()
        val preferredAudioDir =
            File(preferredDownloadDir, "Audio").also { runCatching { it.mkdirs() } }

        videoDownloadDir =
            StorageAccess.resolveUsableDirectory(
                VIDEO_DIRECTORY.getString(preferredDownloadDir.absolutePath),
                preferredDownloadDir,
            )
        if (videoDownloadDir != VIDEO_DIRECTORY.getString(preferredDownloadDir.absolutePath)) {
            PreferenceUtil.encodeString(VIDEO_DIRECTORY, videoDownloadDir)
        }

        audioDownloadDir =
            StorageAccess.resolveUsableDirectory(
                AUDIO_DIRECTORY.getString(preferredAudioDir.absolutePath),
                getAppSpecificAudioDownloadDirectory(),
            )
        if (audioDownloadDir != AUDIO_DIRECTORY.getString(preferredAudioDir.absolutePath)) {
            PreferenceUtil.encodeString(AUDIO_DIRECTORY, audioDownloadDir)
        }

        if (!PreferenceUtil.containsKey(COMMAND_DIRECTORY)) {
            COMMAND_DIRECTORY.updateString(videoDownloadDir)
        } else {
            val commandDir = COMMAND_DIRECTORY.getString(videoDownloadDir)
            val resolvedCommand =
                StorageAccess.resolveUsableDirectory(commandDir, File(videoDownloadDir))
            if (resolvedCommand != commandDir) {
                COMMAND_DIRECTORY.updateString(resolvedCommand)
            }
        }
        if (Build.VERSION.SDK_INT >= 26) NotificationUtil.createNotificationChannel()

        Thread.setDefaultUncaughtExceptionHandler { _, e -> startCrashReportActivity(e) }
    }

    private fun startCrashReportActivity(th: Throwable) {
        th.printStackTrace()
        startActivity(
            Intent(this, CrashReportActivity::class.java)
                .setAction("$packageName.error_report")
                .apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("error_report", getVersionReport() + "\n" + th.stackTraceToString())
                }
        )
    }

    companion object {
        lateinit var clipboard: ClipboardManager
        lateinit var videoDownloadDir: String
        lateinit var audioDownloadDir: String
        lateinit var applicationScope: CoroutineScope
        lateinit var connectivityManager: ConnectivityManager
        lateinit var packageInfo: PackageInfo

        var isServiceRunning = false

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    val binder = service as DownloadService.DownloadServiceBinder
                    isServiceRunning = true
                }

                override fun onServiceDisconnected(arg0: ComponentName) {}
            }

        fun startService() {
            if (isServiceRunning) return
            Intent(context.applicationContext, DownloadService::class.java).also { intent ->
                context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }

        fun stopService() {
            if (!isServiceRunning) return
            try {
                isServiceRunning = false
                context.applicationContext.run { unbindService(connection) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val privateDownloadDir: String
            get() = context.getPreferredPrivateDownloadDirectory().absolutePath

        fun updateDownloadDir(uri: Uri, directoryType: Directory) {
            when (directoryType) {
                Directory.AUDIO -> {
                    val path = FileUtil.getRealPath(uri)
                    audioDownloadDir = path
                    PreferenceUtil.encodeString(AUDIO_DIRECTORY, path)
                }

                Directory.VIDEO -> {
                    val path = FileUtil.getRealPath(uri)
                    videoDownloadDir = path
                    PreferenceUtil.encodeString(VIDEO_DIRECTORY, path)
                }

                Directory.CUSTOM_COMMAND -> {
                    val path = FileUtil.getRealPath(uri)
                    PreferenceUtil.encodeString(COMMAND_DIRECTORY, path)
                }

                Directory.SDCARD -> {
                    context.contentResolver?.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    PreferenceUtil.encodeString(SDCARD_URI, uri.toString())
                }
            }
        }

        fun getVersionReport(): String {
            val versionName = packageInfo.versionName
            val page = packageInfo
            val versionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            val release =
                if (Build.VERSION.SDK_INT >= 30) {
                    Build.VERSION.RELEASE_OR_CODENAME
                } else {
                    Build.VERSION.RELEASE
                }
            return StringBuilder()
                .append("App version: $versionName ($versionCode)\n")
                .append("Device information: Android $release (API ${Build.VERSION.SDK_INT})\n")
                .append("Supported ABIs: ${Build.SUPPORTED_ABIS.contentToString()}\n")
                .append("Yt-dlp version: ${YT_DLP_VERSION.getString()}\n")
                .toString()
        }

        fun isFDroidBuild(): Boolean = BuildConfig.FLAVOR == "fdroid"

        fun isDebugBuild(): Boolean = BuildConfig.DEBUG

        @SuppressLint("StaticFieldLeak") lateinit var context: Context
    }
}
