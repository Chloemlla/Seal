package com.chloemlla.seal.util

import android.os.Build
import androidx.annotation.DeprecatedSinceApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.google.android.material.color.DynamicColors
import com.chloemlla.seal.App
import com.chloemlla.seal.App.Companion.applicationScope
import com.chloemlla.seal.App.Companion.context
import com.chloemlla.seal.App.Companion.isDebugBuild
import com.chloemlla.seal.App.Companion.isFDroidBuild
import com.chloemlla.seal.R
import com.chloemlla.seal.database.objects.CommandTemplate
import com.chloemlla.seal.download.Task
import com.chloemlla.seal.ui.theme.DEFAULT_SEED_COLOR
import com.chloemlla.seal.util.PreferenceUtil.getInt
import com.kyant.monet.PaletteStyle
import com.tencent.mmkv.MMKV
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val CUSTOM_COMMAND = "custom_command"
const val CONCURRENT = "concurrent_fragments"
const val EXTRACT_AUDIO = "extract_audio"
const val THUMBNAIL = "create_thumbnail"
const val YT_DLP_VERSION = "yt-dlp_init"
const val YT_DLP_AUTO_UPDATE = "yt-dlp_update"
const val DEBUG = "debug"
const val CONFIGURE = "configure"
const val DARK_THEME_VALUE = "dark_theme_value"
const val AUDIO_CONVERT = "audio_convert"
const val AUDIO_CONVERSION_FORMAT = "audio_convert_format"
const val AUDIO_FORMAT = "audio_format_preferred"
const val AUDIO_QUALITY = "audio_quality"
const val VIDEO_FORMAT = "video_format"
const val VIDEO_QUALITY = "quality"

const val FORMAT_SORTING = "format_sorting"
const val SORTING_FIELDS = "sorting_fields"

const val WELCOME_DIALOG = "welcome_dialog"
const val VIDEO_DIRECTORY = "download_dir"
const val AUDIO_DIRECTORY = "audio_dir"
const val COMMAND_DIRECTORY = "command_directory"
const val SDCARD_DOWNLOAD = "sdcard_download"
const val SDCARD_URI = "sd_card_uri"
const val SUBDIRECTORY_EXTRACTOR = "sub-directory"
const val SUBDIRECTORY_PLAYLIST_TITLE = "subdirectory_playlist_title"
const val PLAYLIST = "playlist"
private const val LANGUAGE = "language"
const val NOTIFICATION = "notification"
private const val THEME_COLOR = "theme_color"
const val PALETTE_STYLE = "palette_style"
const val SUBTITLE = "subtitle"
const val EMBED_SUBTITLE = "embed_subtitle"
const val KEEP_SUBTITLE_FILES = "keep_subtitle"
const val SUBTITLE_LANGUAGE = "sub_lang"
const val AUTO_SUBTITLE = "auto_subtitle"
const val CONVERT_SUBTITLE = "convert_subtitle"
const val AUTO_TRANSLATED_SUBTITLES = "translated_subs"

const val TEMPLATE_ID = "template_id"
const val MAX_FILE_SIZE = "max_file_size"
const val SPONSORBLOCK = "sponsorblock"
const val SPONSORBLOCK_CATEGORIES = "sponsorblock_categories"
const val ARIA2C = "aria2c"
const val COOKIES = "cookies"
const val USER_AGENT = "user_agent"
const val USER_AGENT_STRING = "user_agent_string"
const val AUTO_UPDATE = "auto_update"
const val UPDATE_CHANNEL = "update_channel"
const val PRIVATE_MODE = "private_mode"
private const val DYNAMIC_COLOR = "dynamic_color"
const val CELLULAR_DOWNLOAD = "cellular_download"
const val RATE_LIMIT = "rate_limit"
const val MAX_RATE = "max_rate"
private const val HIGH_CONTRAST = "high_contrast"
const val DISABLE_PREVIEW = "disable_preview"
const val PRIVATE_DIRECTORY = "private_directory"
const val CROP_ARTWORK = "crop_artwork"
const val EMBED_THUMBNAIL = "embed_thumbnail"
const val FORMAT_SELECTION = "format_selection"
const val VIDEO_CLIP = "video_clip"
const val SHOW_SPONSOR_MSG = "sponsor_msg_v1"
const val PROXY = "proxy"
const val PROXY_URL = "proxy_url"
const val OUTPUT_TEMPLATE = "output_template"
const val CUSTOM_OUTPUT_TEMPLATE = "custom_output_template"
const val DOWNLOAD_ARCHIVE = "download_archive"
const val EMBED_METADATA = "embed_metadata"
const val RESTRICT_FILENAMES = "restrict_filenames"
const val AV1_HARDWARE_ACCELERATED = "av1_hardware_accelerated"
const val FORCE_IPV4 = "force_ipv4"
const val MERGE_OUTPUT_MKV = "merge_to_mkv"
const val USE_CUSTOM_AUDIO_PRESET = "custom_audio_preset"

const val MERGE_MULTI_AUDIO_STREAM = "multi_audio_stream"

const val DOWNLOAD_TYPE_INITIALIZATION = "download_type_init"
private const val DOWNLOAD_TYPE = "download_type"

const val YT_DLP_UPDATE_CHANNEL = "yt-dlp_update_channel"
const val YT_DLP_UPDATE_TIME = "yt-dlp_last_update"
const val YT_DLP_UPDATE_INTERVAL = "yt-dlp_update_interval"

private const val INTERVAL_DAY = 86_400_000L
private const val INTERVAL_WEEK = 86_400_000L * 7
private const val INTERVAL_MONTH = 86_400_000L * 30

const val DEFAULT_INTERVAL = INTERVAL_WEEK // every week

val UpdateIntervalList =
    mapOf(
        INTERVAL_DAY to R.string.every_day,
        INTERVAL_WEEK to R.string.every_week,
        INTERVAL_MONTH to R.string.every_month,
    )

const val NOT_SPECIFIED = 0
const val DEFAULT = NOT_SPECIFIED
const val SYSTEM_DEFAULT = NOT_SPECIFIED
const val NOT_CONVERT = NOT_SPECIFIED

const val NONE = NOT_SPECIFIED
const val USE_PREVIOUS_SELECTION = 1

enum class DownloadType {
    Audio,
    Video,
    Playlist,
    Command,
}

const val CONVERT_ASS = 1
const val CONVERT_LRC = 2
const val CONVERT_SRT = 3
const val CONVERT_VTT = 4

const val STABLE = 0
const val PRE_RELEASE = 1

const val YT_DLP_STABLE = 0
const val YT_DLP_NIGHTLY = 1

const val OPUS = 1
const val M4A = 2

const val FORMAT_COMPATIBILITY = 1
const val FORMAT_QUALITY = 2

const val CONVERT_MP3 = 0
const val CONVERT_M4A = 1

const val HIGH = 1
const val MEDIUM = 2
const val LOW = 3
const val ULTRA_LOW = 4

const val RES_HIGHEST = 0
const val RES_2160P = 1
const val RES_1440P = 2
const val RES_1080P = 3
const val RES_720P = 4
const val RES_480P = 5
const val RES_360P = 6
const val RES_LOWEST = 7

const val TEMPLATE_EXAMPLE = """--no-mtime -S "ext""""

const val TEMPLATE_SHORTCUTS = "template_shortcuts"

const val TASK_LIST = "task_list"
const val SAVED_LINKS = "saved_links"
private const val MMKV_STORAGE_MIGRATED = "mmkv_storage_migrated_v1"

// Third-party delegate-only integration (see docs/third-party-delegate-integration-TODO.md)
const val EXTERNAL_DELEGATE_ENABLED = "external_delegate_enabled"
const val EXTERNAL_AUTO_START_ENABLED = "external_auto_start_enabled"
const val EXTERNAL_WHITELIST_MODE = "external_whitelist_mode"
const val EXTERNAL_CALLER_WHITELIST = "external_caller_whitelist"

val paletteStyles =
    listOf(
        PaletteStyle.TonalSpot,
        PaletteStyle.Spritz,
        PaletteStyle.FruitSalad,
        PaletteStyle.Vibrant,
        PaletteStyle.Monochrome,
    )

const val STYLE_TONAL_SPOT = 0
const val STYLE_SPRITZ = 1
const val STYLE_FRUIT_SALAD = 2
const val STYLE_VIBRANT = 3
const val STYLE_MONOCHROME = 4

private val StringPreferenceDefaults =
    mapOf(
        SPONSORBLOCK_CATEGORIES to "default",
        MAX_RATE to "1000",
        SUBTITLE_LANGUAGE to "en.*,.*-orig",
        OUTPUT_TEMPLATE to DownloadUtil.OUTPUT_TEMPLATE_ID,
        CUSTOM_OUTPUT_TEMPLATE to DownloadUtil.OUTPUT_TEMPLATE_ID,
        EXTERNAL_CALLER_WHITELIST to "",
    )

private val BooleanPreferenceDefaults =
    mapOf(
        FORMAT_SELECTION to true,
        CONFIGURE to true,
        CELLULAR_DOWNLOAD to false,
        YT_DLP_AUTO_UPDATE to true,
        NOTIFICATION to true,
        EMBED_METADATA to true,
        USE_CUSTOM_AUDIO_PRESET to false,
        EXTERNAL_DELEGATE_ENABLED to true,
        EXTERNAL_AUTO_START_ENABLED to false,
        EXTERNAL_WHITELIST_MODE to false,
    )

private val IntPreferenceDefaults =
    mapOf(
        TEMPLATE_ID to 0,
        CONCURRENT to 8,
        LANGUAGE to SYSTEM_DEFAULT,
        PALETTE_STYLE to 0,
        DARK_THEME_VALUE to DarkThemePreference.FOLLOW_SYSTEM,
        WELCOME_DIALOG to 1,
        AUDIO_CONVERSION_FORMAT to NOT_SPECIFIED,
        VIDEO_QUALITY to NOT_SPECIFIED,
        VIDEO_FORMAT to FORMAT_QUALITY,
        UPDATE_CHANNEL to STABLE,
        SHOW_SPONSOR_MSG to 0,
        CONVERT_SUBTITLE to NOT_SPECIFIED,
        DOWNLOAD_TYPE_INITIALIZATION to USE_PREVIOUS_SELECTION,
        YT_DLP_UPDATE_CHANNEL to YT_DLP_STABLE,
        DOWNLOAD_TYPE to DownloadType.Video.ordinal,
    )

private val LongPreferenceDefaults = mapOf(YT_DLP_UPDATE_INTERVAL to DEFAULT_INTERVAL)

fun String.getStringDefault() = StringPreferenceDefaults.getOrElse(this) { "" }

/**
 * Pure classification of preference keys for multi-mmap MMKV layout.
 * Runtime keys are high-churn and isolated from stable settings.
 */
object PreferenceStorageKeys {
    const val PREFS_MMAP_ID = "seal_prefs"
    const val RUNTIME_MMAP_ID = "seal_runtime"

    val runtimeKeys: Set<String> =
        setOf(
            TASK_LIST,
            SAVED_LINKS,
            YT_DLP_VERSION,
            YT_DLP_UPDATE_TIME,
            SHOW_SPONSOR_MSG,
            WELCOME_DIALOG,
        )

    fun isRuntimeKey(key: String): Boolean = key in runtimeKeys

    fun isDownloadPreferenceKey(key: String): Boolean = key in downloadPreferenceKeys

    private val downloadPreferenceKeys =
        setOf(
            EXTRACT_AUDIO,
            THUMBNAIL,
            PLAYLIST,
            SUBDIRECTORY_EXTRACTOR,
            SUBDIRECTORY_PLAYLIST_TITLE,
            COMMAND_DIRECTORY,
            SUBTITLE,
            EMBED_SUBTITLE,
            KEEP_SUBTITLE_FILES,
            SUBTITLE_LANGUAGE,
            AUTO_SUBTITLE,
            AUTO_TRANSLATED_SUBTITLES,
            CONVERT_SUBTITLE,
            CONCURRENT,
            SPONSORBLOCK,
            SPONSORBLOCK_CATEGORIES,
            COOKIES,
            ARIA2C,
            USE_CUSTOM_AUDIO_PRESET,
            AUDIO_FORMAT,
            AUDIO_QUALITY,
            AUDIO_CONVERT,
            FORMAT_SORTING,
            SORTING_FIELDS,
            AUDIO_CONVERSION_FORMAT,
            VIDEO_FORMAT,
            VIDEO_QUALITY,
            PRIVATE_MODE,
            RATE_LIMIT,
            MAX_RATE,
            PRIVATE_DIRECTORY,
            CROP_ARTWORK,
            SDCARD_DOWNLOAD,
            SDCARD_URI,
            EMBED_THUMBNAIL,
            DEBUG,
            PROXY,
            PROXY_URL,
            USER_AGENT,
            USER_AGENT_STRING,
            OUTPUT_TEMPLATE,
            DOWNLOAD_ARCHIVE,
            EMBED_METADATA,
            RESTRICT_FILENAMES,
            AV1_HARDWARE_ACCELERATED,
            FORCE_IPV4,
            MERGE_OUTPUT_MKV,
            MERGE_MULTI_AUDIO_STREAM,
            CUSTOM_COMMAND,
            FORMAT_SELECTION,
            VIDEO_CLIP,
        )
}

object PreferenceUtil {
    private const val PREFS_MMAP_ID = PreferenceStorageKeys.PREFS_MMAP_ID
    private const val RUNTIME_MMAP_ID = PreferenceStorageKeys.RUNTIME_MMAP_ID
    private const val TAG = "PreferenceUtil"

    /** Stable user settings / download preferences / theme. */
    private val prefs: MMKV = MMKV.mmkvWithID(PREFS_MMAP_ID)

    /** High-churn runtime data (queue backup, saved links). Isolated from settings. */
    private val runtime: MMKV = MMKV.mmkvWithID(RUNTIME_MMAP_ID)

    /** Legacy single-store instance used before multi-mmap split. */
    private val legacy: MMKV = MMKV.defaultMMKV()

    private val json = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }

    @Volatile private var downloadPreferencesSnapshot: DownloadUtil.DownloadPreferences? = null

    private val runtimeKeys = PreferenceStorageKeys.runtimeKeys

    private val booleanPreferenceKeys =
        setOf(
            CUSTOM_COMMAND,
            EXTRACT_AUDIO,
            THUMBNAIL,
            YT_DLP_AUTO_UPDATE,
            DEBUG,
            CONFIGURE,
            AUDIO_CONVERT,
            FORMAT_SORTING,
            SDCARD_DOWNLOAD,
            SUBDIRECTORY_EXTRACTOR,
            SUBDIRECTORY_PLAYLIST_TITLE,
            PLAYLIST,
            NOTIFICATION,
            SUBTITLE,
            EMBED_SUBTITLE,
            KEEP_SUBTITLE_FILES,
            AUTO_SUBTITLE,
            AUTO_TRANSLATED_SUBTITLES,
            SPONSORBLOCK,
            ARIA2C,
            COOKIES,
            USER_AGENT,
            AUTO_UPDATE,
            PRIVATE_MODE,
            DYNAMIC_COLOR,
            CELLULAR_DOWNLOAD,
            RATE_LIMIT,
            HIGH_CONTRAST,
            DISABLE_PREVIEW,
            PRIVATE_DIRECTORY,
            CROP_ARTWORK,
            EMBED_THUMBNAIL,
            FORMAT_SELECTION,
            VIDEO_CLIP,
            PROXY,
            DOWNLOAD_ARCHIVE,
            EMBED_METADATA,
            RESTRICT_FILENAMES,
            AV1_HARDWARE_ACCELERATED,
            FORCE_IPV4,
            MERGE_OUTPUT_MKV,
            USE_CUSTOM_AUDIO_PRESET,
            MERGE_MULTI_AUDIO_STREAM,
            EXTERNAL_DELEGATE_ENABLED,
            EXTERNAL_AUTO_START_ENABLED,
            EXTERNAL_WHITELIST_MODE,
        )

    private val intPreferenceKeys =
        setOf(
            CONCURRENT,
            AUDIO_CONVERSION_FORMAT,
            AUDIO_FORMAT,
            AUDIO_QUALITY,
            VIDEO_FORMAT,
            VIDEO_QUALITY,
            DARK_THEME_VALUE,
            THEME_COLOR,
            PALETTE_STYLE,
            CONVERT_SUBTITLE,
            TEMPLATE_ID,
            UPDATE_CHANNEL,
            DOWNLOAD_TYPE_INITIALIZATION,
            DOWNLOAD_TYPE,
            YT_DLP_UPDATE_CHANNEL,
            WELCOME_DIALOG,
            SHOW_SPONSOR_MSG,
            LANGUAGE,
        )

    private val longPreferenceKeys = setOf(YT_DLP_UPDATE_INTERVAL, YT_DLP_UPDATE_TIME)

    private val stringPreferenceKeys =
        setOf(
            YT_DLP_VERSION,
            VIDEO_DIRECTORY,
            AUDIO_DIRECTORY,
            COMMAND_DIRECTORY,
            SDCARD_URI,
            SUBTITLE_LANGUAGE,
            SPONSORBLOCK_CATEGORIES,
            USER_AGENT_STRING,
            MAX_RATE,
            PROXY_URL,
            OUTPUT_TEMPLATE,
            CUSTOM_OUTPUT_TEMPLATE,
            SORTING_FIELDS,
            EXTERNAL_CALLER_WHITELIST,
        )

    init {
        migrateLegacyIfNeeded()
    }

    private fun storeFor(key: String): MMKV =
        if (key in runtimeKeys) runtime else prefs

    private fun migrateLegacyIfNeeded() {
        if (prefs.decodeBool(MMKV_STORAGE_MIGRATED, false)) return
        val keys = legacy.allKeys()
        if (keys.isNullOrEmpty()) {
            prefs.encode(MMKV_STORAGE_MIGRATED, true)
            return
        }
        var migrated = 0
        for (key in keys) {
            if (key == MMKV_STORAGE_MIGRATED) continue
            val target = storeFor(key)
            if (target.containsKey(key)) continue
            if (copyLegacyKey(key, target)) migrated++
        }
        prefs.encode(MMKV_STORAGE_MIGRATED, true)
        android.util.Log.i(TAG, "Migrated $migrated preference keys from default MMKV to split stores")
    }

    private fun copyLegacyKey(key: String, target: MMKV): Boolean {
        // Known typed keys first for correctness.
        when {
            key == SAVED_LINKS -> {
                target.encode(key, legacy.decodeStringSet(key) ?: emptySet())
                return true
            }
            key == TASK_LIST -> {
                legacy.decodeString(key)?.let {
                    target.encode(key, it)
                    return true
                }
            }
            key in BooleanPreferenceDefaults || key in booleanPreferenceKeys -> {
                target.encode(key, legacy.decodeBool(key, BooleanPreferenceDefaults[key] ?: false))
                return true
            }
            key in IntPreferenceDefaults || key in intPreferenceKeys -> {
                target.encode(key, legacy.decodeInt(key, IntPreferenceDefaults[key] ?: 0))
                return true
            }
            key in LongPreferenceDefaults || key in longPreferenceKeys -> {
                target.encode(key, legacy.decodeLong(key, LongPreferenceDefaults[key] ?: 0L))
                return true
            }
            key in StringPreferenceDefaults || key in stringPreferenceKeys -> {
                target.encode(
                    key,
                    legacy.decodeString(key) ?: StringPreferenceDefaults[key].orEmpty(),
                )
                return true
            }
        }

        // Generic fallback: string, then string-set, then int/long/bool.
        legacy.decodeString(key)?.let {
            target.encode(key, it)
            return true
        }
        legacy.decodeStringSet(key)?.let {
            target.encode(key, it)
            return true
        }
        // For pure numeric/bool keys without string form.
        if (!legacy.containsKey(key)) return false
        val asInt = legacy.decodeInt(key, Int.MIN_VALUE / 2)
        val asLong = legacy.decodeLong(key, Long.MIN_VALUE / 2)
        return when {
            asLong != Long.MIN_VALUE / 2 && asLong !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() -> {
                target.encode(key, asLong)
                true
            }
            asInt != Int.MIN_VALUE / 2 -> {
                target.encode(key, asInt)
                true
            }
            else -> {
                target.encode(key, legacy.decodeBool(key, false))
                true
            }
        }
    }

    fun String.getInt(default: Int = IntPreferenceDefaults.getOrElse(this) { 0 }): Int {
        val store = storeFor(this)
        return if (store.containsKey(this)) store.decodeInt(this, default)
        else if (legacy.containsKey(this)) legacy.decodeInt(this, default)
        else default
    }

    fun String.getString(
        default: String = StringPreferenceDefaults.getOrElse(this) { "" }
    ): String {
        val store = storeFor(this)
        return store.decodeString(this)
            ?: legacy.decodeString(this)
            ?: default
    }

    fun String.getBoolean(
        default: Boolean = BooleanPreferenceDefaults.getOrElse(this) { false }
    ): Boolean {
        val store = storeFor(this)
        return if (store.containsKey(this)) store.decodeBool(this, default)
        else if (legacy.containsKey(this)) legacy.decodeBool(this, default)
        else default
    }

    fun String.getLong(default: Long = LongPreferenceDefaults.getOrElse(this) { 0L }): Long {
        val store = storeFor(this)
        return if (store.containsKey(this)) store.decodeLong(this, default)
        else if (legacy.containsKey(this)) legacy.decodeLong(this, default)
        else default
    }

    fun String.updateString(newString: String) {
        storeFor(this).encode(this, newString)
        invalidateDownloadPreferencesSnapshotIfNeeded(this)
    }

    fun String.updateInt(newInt: Int) {
        storeFor(this).encode(this, newInt)
        invalidateDownloadPreferencesSnapshotIfNeeded(this)
    }

    fun String.updateLong(newLong: Long) {
        storeFor(this).encode(this, newLong)
        invalidateDownloadPreferencesSnapshotIfNeeded(this)
    }

    fun String.updateBoolean(newValue: Boolean) {
        storeFor(this).encode(this, newValue)
        invalidateDownloadPreferencesSnapshotIfNeeded(this)
    }

    fun updateValue(key: String, b: Boolean) = key.updateBoolean(b)

    fun encodeInt(key: String, int: Int) = key.updateInt(int)

    fun encodeString(key: String, string: String) = key.updateString(string)

    fun containsKey(key: String): Boolean =
        storeFor(key).containsKey(key) || legacy.containsKey(key)

    fun invalidateDownloadPreferencesSnapshot() {
        downloadPreferencesSnapshot = null
    }

    private fun invalidateDownloadPreferencesSnapshotIfNeeded(key: String) {
        if (PreferenceStorageKeys.isDownloadPreferenceKey(key)) {
            downloadPreferencesSnapshot = null
        }
    }

    /**
     * Cached download preferences for hot UI paths.
     * Snapshot is invalidated whenever a related preference key is written.
     */
    fun getCachedDownloadPreferences(): DownloadUtil.DownloadPreferences {
        downloadPreferencesSnapshot?.let { return it }
        val fresh = DownloadUtil.DownloadPreferences.buildFromPreferenceStore()
        downloadPreferencesSnapshot = fresh
        return fresh
    }

    /**
     * Pre-build the download preference snapshot after MMKV is ready.
     * Speeds up the first configure / download UI open after cold start.
     */
    fun warmDownloadPreferencesSnapshot() {
        if (downloadPreferencesSnapshot != null) return
        downloadPreferencesSnapshot = DownloadUtil.DownloadPreferences.buildFromPreferenceStore()
    }

    fun replaceDownloadPreferencesSnapshot(snapshot: DownloadUtil.DownloadPreferences) {
        downloadPreferencesSnapshot = snapshot
    }

    /** Sync MMKV pages for high-churn runtime keys (queue, links). */
    fun syncRuntime() {
        runtime.sync()
    }

    /** Sync MMKV pages for stable settings. */
    fun syncPrefs() {
        prefs.sync()
    }

    fun getAudioConvertFormat(): Int = AUDIO_CONVERSION_FORMAT.getInt()

    fun getVideoResolution(): Int = VIDEO_QUALITY.getInt()

    fun getAudioQuality(): Int = AUDIO_QUALITY.getInt()

    fun getVideoFormat(): Int = VIDEO_FORMAT.getInt()

    fun getAudioFormat(): Int = AUDIO_FORMAT.getInt()

    fun getDownloadType(
        usePreviousType: Boolean = DOWNLOAD_TYPE_INITIALIZATION.getInt() == USE_PREVIOUS_SELECTION
    ): DownloadType? {
        return if (usePreviousType) {
            DownloadType.entries.firstOrNull { it.ordinal == DOWNLOAD_TYPE.getInt() }
                ?: DownloadType.Video
        } else {
            null
        }
    }

    fun updateDownloadType(type: DownloadType) = DOWNLOAD_TYPE.updateInt(type.ordinal)

    fun isNetworkAvailableForDownload() =
        CELLULAR_DOWNLOAD.getBoolean() || !App.connectivityManager.isActiveNetworkMetered

    fun isAutoUpdateEnabled(): Boolean {
        return when {
            isFDroidBuild() -> false
            isDebugBuild() -> false
            else -> AUTO_UPDATE.getBoolean()
        }
    }

    @DeprecatedSinceApi(api = 33)
    fun getLocaleFromPreference(): Locale? {
        val languageCode = LANGUAGE.getInt()
        return LocaleLanguageCodeMap.entries.find { it.value == languageCode }?.key
    }

    fun saveLocalePreference(locale: Locale?) {
        if (Build.VERSION.SDK_INT >= 33) {
            // No op
        } else {
            LANGUAGE.updateInt(locale?.let { LocaleLanguageCodeMap[it] } ?: SYSTEM_DEFAULT)
        }
    }

    fun getConcurrentFragments(level: Int = CONCURRENT.getInt()): Float {
        return when (level) {
            1 -> 0f
            8 -> 0.33f
            16 -> 0.66f
            else -> 1f
        }
    }

    fun getSponsorBlockCategories(): String = SPONSORBLOCK_CATEGORIES.getString()

    const val COOKIE_HEADER =
        "# Netscape HTTP Cookie File\n" + "# Auto-generated by Seal built-in WebView\n"

    val templateListStateFlow: StateFlow<List<CommandTemplate>> =
        DatabaseUtil.getTemplateFlow()
            .stateIn(applicationScope, started = SharingStarted.Eagerly, emptyList())

    private val List<CommandTemplate>.selectedTemplate: CommandTemplate?
        get() = find { it.id == TEMPLATE_ID.getInt() }

    fun getTemplate(): CommandTemplate {
        var template: CommandTemplate? = null
        runBlocking {
            for (cnt in 1..5) {
                template = templateListStateFlow.value.selectedTemplate
                if (template != null) return@runBlocking
                delay(100)
            }
        }
        return template ?: throw NoSuchElementException()
    }

    suspend fun initializeTemplateSample() {
        TEMPLATE_ID.updateInt(
            DatabaseUtil.insertTemplate(
                    CommandTemplate(
                        id = 0,
                        name = context.getString(R.string.custom_command_template),
                        template = TEMPLATE_EXAMPLE,
                    )
                )
                .toInt()
        )
    }

    data class AppSettings(
        val darkTheme: DarkThemePreference = DarkThemePreference(),
        val isDynamicColorEnabled: Boolean = false,
        val seedColor: Int = DEFAULT_SEED_COLOR,
        val paletteStyleIndex: Int = 0,
    )

    fun getMaxDownloadRate(): String = MAX_RATE.getString()

    private val mutableAppSettingsStateFlow =
        MutableStateFlow(
            AppSettings(
                DarkThemePreference(
                    darkThemeValue = DARK_THEME_VALUE.getInt(DarkThemePreference.FOLLOW_SYSTEM),
                    isHighContrastModeEnabled = HIGH_CONTRAST.getBoolean(false),
                ),
                isDynamicColorEnabled =
                    DYNAMIC_COLOR.getBoolean(DynamicColors.isDynamicColorAvailable()),
                seedColor = THEME_COLOR.getInt(DEFAULT_SEED_COLOR),
                paletteStyleIndex = PALETTE_STYLE.getInt(0),
            )
        )
    val AppSettingsStateFlow = mutableAppSettingsStateFlow.asStateFlow()

    fun modifyDarkThemePreference(
        darkThemeValue: Int = AppSettingsStateFlow.value.darkTheme.darkThemeValue,
        isHighContrastModeEnabled: Boolean =
            AppSettingsStateFlow.value.darkTheme.isHighContrastModeEnabled,
    ) {
        applicationScope.launch(Dispatchers.IO) {
            mutableAppSettingsStateFlow.update {
                it.copy(
                    darkTheme =
                        AppSettingsStateFlow.value.darkTheme.copy(
                            darkThemeValue = darkThemeValue,
                            isHighContrastModeEnabled = isHighContrastModeEnabled,
                        )
                )
            }
            prefs.encode(DARK_THEME_VALUE, darkThemeValue)
            prefs.encode(HIGH_CONTRAST, isHighContrastModeEnabled)
        }
    }

    fun modifyThemeSeedColor(colorArgb: Int, paletteStyleIndex: Int) {
        applicationScope.launch(Dispatchers.IO) {
            mutableAppSettingsStateFlow.update {
                it.copy(seedColor = colorArgb, paletteStyleIndex = paletteStyleIndex)
            }
            prefs.encode(THEME_COLOR, colorArgb)
            prefs.encode(PALETTE_STYLE, paletteStyleIndex)
        }
    }

    fun switchDynamicColor(
        enabled: Boolean = !mutableAppSettingsStateFlow.value.isDynamicColorEnabled
    ) {
        applicationScope.launch(Dispatchers.IO) {
            mutableAppSettingsStateFlow.update { it.copy(isDynamicColorEnabled = enabled) }
            prefs.encode(DYNAMIC_COLOR, enabled)
        }
    }

    fun encodeTaskListBackup(map: Map<Task, Task.State>) =
        runCatching { json.encodeToString<Map<Task, Task.State>>(map) }
            .onSuccess { runtime.encode(TASK_LIST, it) }
            .onFailure { it.printStackTrace() }

    fun decodeTaskListBackup(): Map<Task, Task.State> =
        runCatching {
                (runtime.decodeString(TASK_LIST) ?: legacy.decodeString(TASK_LIST))?.let {
                    json.decodeFromString<Map<Task, Task.State>>(it)
                }
            }
            .onFailure { it.printStackTrace() }
            .getOrNull() ?: emptyMap()

    fun getSavedLinks(): Set<String> =
        runtime.decodeStringSet(SAVED_LINKS)
            ?: legacy.decodeStringSet(SAVED_LINKS)
            ?: emptySet()

    fun updateSavedLinks(links: Set<String>) {
        runtime.encode(SAVED_LINKS, links)
    }
}

data class DarkThemePreference(
    val darkThemeValue: Int = FOLLOW_SYSTEM,
    val isHighContrastModeEnabled: Boolean = false,
) {
    companion object {
        const val FOLLOW_SYSTEM = 1
        const val ON = 2
        const val OFF = 3
    }

    @Composable
    fun isDarkTheme(): Boolean {
        return if (darkThemeValue == FOLLOW_SYSTEM) isSystemInDarkTheme() else darkThemeValue == ON
    }

    @Composable
    fun getDarkThemeDesc(): String {
        return when (darkThemeValue) {
            FOLLOW_SYSTEM -> stringResource(R.string.follow_system)
            ON -> stringResource(R.string.on)
            else -> stringResource(R.string.off)
        }
    }
}

object PreferenceStrings {
    fun getSubtitleConversionFormat(subtitleFormat: Int = CONVERT_SUBTITLE.getInt()): String =
        when (subtitleFormat) {
            CONVERT_LRC -> context.getString(R.string.convert_to, "lrc")
            CONVERT_ASS -> context.getString(R.string.convert_to, "ass")
            CONVERT_SRT -> context.getString(R.string.convert_to, "srt")
            CONVERT_VTT -> context.getString(R.string.convert_to, "vtt")
            else -> context.getString(R.string.not_convert)
        }

    @Composable
    fun getAudioFormatDesc(audioFormatCode: Int = PreferenceUtil.getAudioFormat()): String =
        when (audioFormatCode) {
            M4A -> "M4A"
            OPUS -> "OPUS"
            else -> stringResource(R.string.not_specified)
        }

    @Composable
    fun getAudioQualityDesc(audioQualityCode: Int = PreferenceUtil.getAudioQuality()): String =
        when (audioQualityCode) {
            NOT_SPECIFIED -> stringResource(R.string.best_quality)
            HIGH -> "192 Kbps"
            MEDIUM -> "128 Kbps"
            LOW -> "64 Kbps"
            ULTRA_LOW -> "32 Kbps"
            else -> stringResource(R.string.lowest_bitrate)
        }

    @Composable
    fun getAudioConvertDesc(audioFormatCode: Int = PreferenceUtil.getAudioConvertFormat()): String {
        return when (audioFormatCode) {
            0 -> stringResource(R.string.convert_to).format("mp3")
            else -> stringResource(R.string.convert_to).format("m4a")
        }
    }

    @Composable
    fun getVideoFormatDescComp(videoFormatCode: Int = PreferenceUtil.getVideoFormat()): String {
        return when (videoFormatCode) {
            FORMAT_COMPATIBILITY -> stringResource(R.string.prefer_compatibility_desc)
            FORMAT_QUALITY -> stringResource(R.string.prefer_quality_desc)
            else -> stringResource(R.string.not_specified)
        }
    }

    @Composable
    fun getVideoResolutionDesc(
        videoQualityCode: Int = PreferenceUtil.getVideoResolution()
    ): String {
        return when (videoQualityCode) {
            1 -> "2160p"
            2 -> "1440p"
            3 -> "1080p"
            4 -> "720p"
            5 -> "480p"
            6 -> "360p"
            7 -> stringResource(R.string.lowest_quality)
            else -> stringResource(R.string.best_quality)
        }
    }

    @Composable
    fun getVideoFormatLabel(videoFormatPreference: Int = PreferenceUtil.getVideoFormat()): String {
        return when (videoFormatPreference) {
            FORMAT_COMPATIBILITY -> stringResource(id = R.string.legacy)
            else -> stringResource(id = R.string.quality)
        }
    }

    @Composable
    fun getUpdateIntervalText(interval: Long): String {
        return stringResource(
            id =
                when (interval) {
                    INTERVAL_DAY -> R.string.every_day
                    INTERVAL_WEEK -> R.string.every_week
                    INTERVAL_MONTH -> R.string.every_month
                    else -> R.string.disabled
                }
        )
    }

    @Composable
    fun getAudioPresetText(preferences: DownloadUtil.DownloadPreferences): String {
        return with(preferences) {
            when {
                formatSorting -> {
                    sortingFields
                }

                !useCustomAudioPreset -> {
                    stringResource(R.string.best_quality)
                }

                convertAudio -> {
                    when (audioConvertFormat) {
                        CONVERT_MP3 -> stringResource(R.string.convert_to, "MP3")
                        else -> stringResource(R.string.convert_to, "M4A")
                    }
                }

                else -> {
                    val preferredFormat =
                        when (audioFormat) {
                            M4A -> stringResource(R.string.prefer_placeholder, "M4A")
                            OPUS -> stringResource(R.string.prefer_placeholder, "OPUS")
                            else -> null
                        }
                    val preferredQuality =
                        when (audioQuality) {
                            NOT_SPECIFIED -> stringResource(R.string.best_quality)
                            HIGH -> "192 Kbps"
                            MEDIUM -> "128 Kbps"
                            LOW -> "64 Kbps"
                            ULTRA_LOW -> "32 Kbps"
                            else -> stringResource(R.string.lowest_bitrate)
                        }
                    listOfNotNull(preferredFormat, preferredQuality).joinToString(separator = ", ")
                }
            }
        }
    }

    @Composable
    fun getVideoPresetText(preferences: DownloadUtil.DownloadPreferences): String {
        return with(preferences) {
            when {
                formatSorting -> {
                    sortingFields
                }

                else -> {
                    val preferredFormat =
                        stringResource(
                            id = R.string.prefer_placeholder,
                            stringResource(
                                id =
                                    if (videoFormat == FORMAT_QUALITY) R.string.quality
                                    else R.string.legacy
                            ),
                        )
                    val preferredResolution = getVideoResolutionDesc(videoResolution)
                    listOf(preferredFormat, preferredResolution).joinToString(separator = ", ")
                }
            }
        }
    }
}

