import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.gradle.api.Project

/**
 * Resolves the latest Project Lumen main auto-release for `lumen-crash`.
 *
 * Preference order:
 * 1. `-PlumenCrashVersion=...` / `LUMEN_CRASH_VERSION`
 * 2. GitHub Releases API tags prefixed with `lumen-crash-v`
 *
 * Credentials (optional for public rate limits, required if API needs auth):
 * - Gradle props: `gpr.user` / `gpr.key`
 * - Env: `GITHUB_ACTOR` / `GITHUB_TOKEN` / `GH_TOKEN`
 */
object LumenCrashVersion {
    private const val OWNER_REPO = "Chloemlla/Project-Lumen"
    private const val TAG_PREFIX = "lumen-crash-v"
    private const val CACHE_TTL_MS = 30L * 60L * 1000L

    fun resolve(project: Project): String {
        val override =
            (project.findProperty("lumenCrashVersion") as String?)?.trim().orEmpty().ifEmpty {
                System.getenv("LUMEN_CRASH_VERSION")?.trim().orEmpty()
            }
        if (override.isNotEmpty()) {
            project.logger.lifecycle("Using overridden lumen-crash version: $override")
            return override
        }

        val cacheFile = cacheFile(project)
        readFreshCache(cacheFile)?.let { cached ->
            project.logger.lifecycle("Using cached lumen-crash version: $cached")
            return cached
        }

        val resolved = fetchLatestVersion(project)
        writeCache(cacheFile, resolved)
        project.logger.lifecycle("Resolved latest lumen-crash version: $resolved")
        return resolved
    }

    private fun cacheFile(project: Project): File {
        val dir = File(project.rootProject.layout.projectDirectory.asFile, ".gradle/lumen-crash")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "latest-version.txt")
    }

    private fun readFreshCache(cacheFile: File): String? {
        if (!cacheFile.isFile) return null
        val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
        if (ageMs > CACHE_TTL_MS) return null
        val version = cacheFile.readText(Charsets.UTF_8).trim()
        return version.takeIf { it.isNotEmpty() }
    }

    private fun writeCache(cacheFile: File, version: String) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(version + "\n", Charsets.UTF_8)
    }

    private fun fetchLatestVersion(project: Project): String {
        val token =
            (project.findProperty("gpr.key") as String?)?.trim().orEmpty().ifEmpty {
                System.getenv("GITHUB_TOKEN")?.trim().orEmpty().ifEmpty {
                    System.getenv("GH_TOKEN")?.trim().orEmpty()
                }
            }

        val first = requestReleases(token.takeIf { it.isNotEmpty() })
        val response =
            if (first.code in setOf(401, 403) && token.isNotEmpty()) {
                // Invalid/local-stale tokens should not block public release discovery.
                project.logger.warn(
                    "GitHub Releases auth failed (HTTP ${first.code}); retrying without token"
                )
                requestReleases(token = null)
            } else {
                first
            }

        if (response.code !in 200..299) {
            error(
                "Failed to resolve latest lumen-crash version from GitHub Releases " +
                    "(HTTP ${response.code}). Set -PlumenCrashVersion=... or provide gpr.key/GITHUB_TOKEN. " +
                    "Body: ${response.body.take(300)}"
            )
        }

        return parseLatestVersion(response.body)
            ?: error(
                "No non-draft GitHub release found with tag prefix '$TAG_PREFIX'. " +
                    "Set -PlumenCrashVersion=... to pin manually."
            )
    }

    private data class ApiResponse(val code: Int, val body: String)

    private fun requestReleases(token: String?): ApiResponse {
        val url =
            URI.create("https://api.github.com/repos/$OWNER_REPO/releases?per_page=100").toURL()
        val connection =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TimeUnit.SECONDS.toMillis(20).toInt()
                readTimeout = TimeUnit.SECONDS.toMillis(30).toInt()
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "seal-lumen-crash-version-resolver")
                if (!token.isNullOrEmpty()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
        return try {
            val code = connection.responseCode
            val body =
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            ApiResponse(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseLatestVersion(json: String): String? {
        // Minimal parser: walk release objects and pick newest by published_at/created_at.
        data class Release(val tag: String, val draft: Boolean, val at: Instant?)

        val releases = mutableListOf<Release>()
        var index = 0
        while (true) {
            val objStart = json.indexOf('{', index)
            if (objStart < 0) break
            val objEnd = findMatchingBrace(json, objStart) ?: break
            val obj = json.substring(objStart, objEnd + 1)
            index = objEnd + 1

            // Only top-level release objects contain tag_name + draft together.
            val tag = stringField(obj, "tag_name") ?: continue
            if (!tag.startsWith(TAG_PREFIX)) continue
            // Avoid nested objects that happen to mention tag_name: require draft field nearby.
            if (!obj.contains("\"draft\"")) continue
            val draft = booleanField(obj, "draft") ?: continue
            val published = stringField(obj, "published_at")
            val created = stringField(obj, "created_at")
            val at =
                listOfNotNull(published, created)
                    .firstOrNull { it.isNotBlank() && it != "null" }
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            releases += Release(tag = tag, draft = draft, at = at)
        }

        val latest =
            releases
                .asSequence()
                .filter { !it.draft }
                .sortedWith(
                    compareByDescending<Release> { it.at ?: Instant.EPOCH }
                        .thenByDescending { it.tag }
                )
                .firstOrNull()
                ?: return null

        return latest.tag.removePrefix(TAG_PREFIX).takeIf { it.isNotEmpty() }
    }

    private fun findMatchingBrace(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun stringField(obj: String, name: String): String? {
        val key = "\"$name\""
        val keyIndex = obj.indexOf(key)
        if (keyIndex < 0) return null
        val colon = obj.indexOf(':', keyIndex + key.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length) return null
        if (obj.startsWith("null", i)) return null
        if (obj[i] != '"') return null
        i++
        val sb = StringBuilder()
        var escape = false
        while (i < obj.length) {
            val c = obj[i]
            when {
                escape -> {
                    sb.append(c)
                    escape = false
                }
                c == '\\' -> escape = true
                c == '"' -> return sb.toString()
                else -> sb.append(c)
            }
            i++
        }
        return null
    }

    private fun booleanField(obj: String, name: String): Boolean? {
        val key = "\"$name\""
        val keyIndex = obj.indexOf(key)
        if (keyIndex < 0) return null
        val colon = obj.indexOf(':', keyIndex + key.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < obj.length && obj[i].isWhitespace()) i++
        return when {
            obj.startsWith("true", i) -> true
            obj.startsWith("false", i) -> false
            else -> null
        }
    }
}
