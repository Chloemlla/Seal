package com.chloemlla.seal.util

/**
 * Blocks known-dangerous yt-dlp option tokens in free-form custom command templates.
 * Full option allowlisting is a longer-term goal; this is a fail-closed denylist for high-risk ops.
 */
object CommandTemplateSanitizer {
    private val dangerousOptionPatterns =
        listOf(
            Regex("""--exec(?:-before-download|-after-download|-before-pp)?\b""", RegexOption.IGNORE_CASE),
            Regex("""--config-locations?\b""", RegexOption.IGNORE_CASE),
            Regex("""--load-info-json\b""", RegexOption.IGNORE_CASE),
            Regex("""--batch-file\b""", RegexOption.IGNORE_CASE),
            Regex("""--ffmpeg-location\b""", RegexOption.IGNORE_CASE),
            Regex("""--plugin(?:-dirs)?\b""", RegexOption.IGNORE_CASE),
            Regex("""--js-runtimes?\b""", RegexOption.IGNORE_CASE),
            Regex("""--remote-components\b""", RegexOption.IGNORE_CASE),
            Regex("""--print-to-file\b""", RegexOption.IGNORE_CASE),
            Regex("""--use-postprocessor\b""", RegexOption.IGNORE_CASE),
            Regex("""--alias\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:^|\s)-a(?:\s|=)""", RegexOption.IGNORE_CASE),
        )

    data class ValidationResult(val ok: Boolean, val blockedOptions: List<String>)

    fun validate(templateText: String): ValidationResult {
        val blocked =
            dangerousOptionPatterns.mapNotNull { pattern ->
                pattern.find(templateText)?.value?.trim()
            }.distinct()
        return ValidationResult(ok = blocked.isEmpty(), blockedOptions = blocked)
    }

    fun requireSafe(templateText: String) {
        val result = validate(templateText)
        if (!result.ok) {
            throw IllegalArgumentException(
                "Custom command template contains blocked option(s): ${result.blockedOptions.joinToString()}"
            )
        }
    }
}
