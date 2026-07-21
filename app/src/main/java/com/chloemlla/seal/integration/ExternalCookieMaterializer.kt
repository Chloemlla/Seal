package com.chloemlla.seal.integration

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chloemlla.seal.ui.page.settings.network.Cookie
import com.chloemlla.seal.util.FileUtil.deleteExternalTaskCookiesFile
import com.chloemlla.seal.util.FileUtil.getExternalTaskCookiesFile
import com.chloemlla.seal.util.connectWithDelimiter
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Materializes inbound external cookies into a **task-scoped** Netscape file under app cache.
 * Never logs cookie values; never writes into global cookies.txt or CookieProfile.
 */
object ExternalCookieMaterializer {
    private const val TAG = "ExternalCookieMat"

    sealed interface Result {
        data class Success(val filePath: String, val cookieCount: Int) : Result

        data class Failure(val errorCode: String, val message: String) : Result
    }

    fun materialize(
        context: Context,
        request: ExternalDownloadRequest,
        taskId: String = request.callerRequestId?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString(),
    ): Result {
        val format = request.cookiesFormat?.trim()?.lowercase().orEmpty()
        val payload = request.cookiesPayload
        val uriString = request.cookiesUri

        if (payload.isNullOrBlank() && uriString.isNullOrBlank()) {
            return Result.Failure(
                ExternalDownloadProtocol.ERROR_COOKIE_INVALID,
                "Cookie payload is empty",
            )
        }

        if (!payload.isNullOrBlank() &&
            payload.length > ExternalDownloadProtocol.MAX_COOKIES_PAYLOAD_CHARS
        ) {
            return Result.Failure(
                ExternalDownloadProtocol.ERROR_COOKIE_TOO_LARGE,
                "cookies payload exceeds ${ExternalDownloadProtocol.MAX_COOKIES_PAYLOAD_CHARS} chars",
            )
        }

        val netscapeBody =
            when {
                !uriString.isNullOrBlank() -> {
                    readUriAsNetscape(context, uriString)
                        ?: return Result.Failure(
                            ExternalDownloadProtocol.ERROR_COOKIES_URI_DENIED,
                            "Cannot open cookies_uri",
                        )
                }
                format == ExternalDownloadProtocol.COOKIES_FORMAT_NETSCAPE ||
                    (format.isEmpty() && looksLikeNetscape(payload!!)) -> {
                    normalizeNetscape(payload!!)
                }
                format == ExternalDownloadProtocol.COOKIES_FORMAT_NAME_VALUE -> {
                    nameValueToNetscape(
                        payload!!,
                        request.cookiesDomainHint
                            ?: ExternalDownloadProtocol.DEFAULT_COOKIES_DOMAIN,
                    )
                }
                format == ExternalDownloadProtocol.COOKIES_FORMAT_JSON_MAP ||
                    format.isEmpty() ||
                    format == "json" -> {
                    jsonMapToNetscape(
                        payload!!,
                        request.cookiesDomainHint
                            ?: ExternalDownloadProtocol.DEFAULT_COOKIES_DOMAIN,
                    )
                        ?: return Result.Failure(
                            ExternalDownloadProtocol.ERROR_COOKIE_INVALID,
                            "Invalid json_map cookies",
                        )
                }
                else -> {
                    return Result.Failure(
                        ExternalDownloadProtocol.ERROR_COOKIES_UNSUPPORTED,
                        "Unknown cookies_format=$format",
                    )
                }
            }

        if (netscapeBody.isBlank() || !netscapeBody.lineSequence().any { isCookieLine(it) }) {
            return Result.Failure(
                ExternalDownloadProtocol.ERROR_COOKIE_INVALID,
                "No valid cookie lines after conversion",
            )
        }

        val lineCount = netscapeBody.lineSequence().count { isCookieLine(it) }
        if (lineCount > 200) {
            return Result.Failure(
                ExternalDownloadProtocol.ERROR_COOKIE_INVALID,
                "Too many cookies ($lineCount)",
            )
        }

        return runCatching {
                val file = context.getExternalTaskCookiesFile(taskId)
                file.parentFile?.mkdirs()
                val header =
                    if (netscapeBody.startsWith("#")) {
                        netscapeBody
                    } else {
                        "# Netscape HTTP Cookie File\n# Injected by external delegate (task-scoped)\n$netscapeBody"
                    }
                file.writeText(header.trimEnd() + "\n")
                Log.i(
                    TAG,
                    "materialized taskCookies taskId=$taskId count=$lineCount " +
                        "bytes=${file.length()} format=${format.ifEmpty { "auto" }} " +
                        "mid=${request.cookiesMid}",
                )
                Result.Success(filePath = file.absolutePath, cookieCount = lineCount)
            }
            .getOrElse {
                Log.e(TAG, "materialize failed (no secrets logged)", it)
                Result.Failure(
                    ExternalDownloadProtocol.ERROR_INTERNAL,
                    "Failed to write task cookie file",
                )
            }
    }

    fun deleteTaskCookies(context: Context, taskId: String?) {
        if (taskId.isNullOrBlank()) return
        context.deleteExternalTaskCookiesFile(taskId)
    }

    /**
     * Converts a name→value JSON object (PiliPlus [cookieJar.toJson]) to Netscape lines.
     * Pure helper for unit tests; does not touch disk.
     */
    fun jsonMapToNetscape(json: String, domain: String): String? {
        return runCatching {
                val obj = JSONObject(json)
                val lines = mutableListOf<String>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val value = obj.opt(name)?.toString() ?: continue
                    if (name.isBlank()) continue
                    if (name.contains('\t') || name.contains('\n') || name.contains('\r')) continue
                    if (value.contains('\t') || value.contains('\n') || value.contains('\r')) {
                        return@runCatching null
                    }
                    if (value.length > 8 * 1024) return@runCatching null
                    lines +=
                        Cookie(
                                domain = domain.ifBlank { ExternalDownloadProtocol.DEFAULT_COOKIES_DOMAIN },
                                name = name,
                                value = value,
                                includeSubdomains = true,
                                path = "/",
                                secure = true,
                                expiry = 0L,
                            )
                            .toNetscapeCookieString()
                }
                if (lines.isEmpty()) null else lines.joinToString("\n")
            }
            .getOrNull()
    }

    fun nameValueToNetscape(header: String, domain: String): String {
        val pairs =
            header
                .split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains('=') }
        val lines = mutableListOf<String>()
        for (pair in pairs) {
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=').trim()
            if (name.isEmpty()) continue
            if (name.contains('\t') || value.contains('\t')) continue
            lines +=
                Cookie(
                        domain = domain.ifBlank { ExternalDownloadProtocol.DEFAULT_COOKIES_DOMAIN },
                        name = name,
                        value = value,
                        includeSubdomains = true,
                        path = "/",
                        secure = true,
                        expiry = 0L,
                    )
                    .toNetscapeCookieString()
        }
        return lines.joinToString("\n")
    }

    private fun normalizeNetscape(body: String): String = body.trim()

    private fun looksLikeNetscape(body: String): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("# Netscape") ||
            trimmed.lineSequence().any { line ->
                !line.startsWith("#") && line.split('\t').size >= 7
            }
    }

    private fun isCookieLine(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) return false
        return t.split('\t').size >= 7
    }

    private fun readUriAsNetscape(context: Context, uriString: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (uri.scheme != "content") return null
        return runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
            }
            .onFailure { Log.w(TAG, "cookies_uri open failed scheme=${uri.scheme}") }
            .getOrNull()
            ?.also { body ->
                if (body.length > ExternalDownloadProtocol.MAX_COOKIES_PAYLOAD_CHARS) {
                    return null
                }
            }
    }
}

/**
 * Converts keep_sections JSON (array of {start,end} in **seconds**) to VideoClip list.
 * Start floors and end ceils so cuts are not tighter than the caller planned.
 * Returns empty list when missing/invalid (caller may ignore).
 */
fun parseKeepSectionsJson(raw: String?): List<com.chloemlla.seal.util.VideoClip> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val startSec = item.optDouble("start", Double.NaN)
                    val endSec = item.optDouble("end", Double.NaN)
                    if (startSec.isNaN() || endSec.isNaN()) continue
                    if (endSec <= startSec) continue
                    val start = kotlin.math.floor(startSec).toInt().coerceAtLeast(0)
                    val end = kotlin.math.ceil(endSec).toInt().coerceAtLeast(start + 1)
                    add(com.chloemlla.seal.util.VideoClip(start = start, end = end))
                }
            }
        }
        .getOrDefault(emptyList())
}

/** Tab-joined helper used by [Cookie.toNetscapeCookieString] style conversion tests. */
fun netscapeLine(
    domain: String,
    includeSubdomains: Boolean,
    path: String,
    secure: Boolean,
    expiry: Long,
    name: String,
    value: String,
): String =
    connectWithDelimiter(
        domain,
        includeSubdomains.toString().uppercase(),
        path,
        secure.toString().uppercase(),
        expiry.toString(),
        name,
        value,
        delimiter = "	",
    )
