package com.chloemlla.seal.download

/** Pure control-flow seam for C section concat followed by D full-source post-processing. */
internal object StripFallbackController {
    fun <T> execute(
        primary: () -> Result<T>,
        resetForFallback: () -> Result<Unit>,
        fallback: () -> Result<T>,
        cleanup: () -> Unit,
        isCancellation: (Throwable) -> Boolean,
        onFallback: (Throwable) -> Unit = {},
    ): Result<T> {
        try {
            val primaryResult = primary()
            if (primaryResult.isSuccess) return primaryResult

            val primaryFailure = primaryResult.exceptionOrNull()
                ?: IllegalStateException("Section strip failed without an error")
            if (isCancellation(primaryFailure)) return Result.failure(primaryFailure)
            onFallback(primaryFailure)

            val resetResult = resetForFallback()
            resetResult.exceptionOrNull()?.let { resetFailure ->
                return failure(
                    message = "Unable to reset strip workspace for full-source fallback",
                    cause = resetFailure,
                    priorFailure = primaryFailure,
                    isCancellation = isCancellation,
                )
            }

            val fallbackResult = fallback()
            fallbackResult.exceptionOrNull()?.let { fallbackFailure ->
                return failure(
                    message = "Full-source strip fallback failed",
                    cause = fallbackFailure,
                    priorFailure = primaryFailure,
                    isCancellation = isCancellation,
                )
            }
            return fallbackResult
        } finally {
            cleanup()
        }
    }

    private fun <T> failure(
        message: String,
        cause: Throwable,
        priorFailure: Throwable,
        isCancellation: (Throwable) -> Boolean,
    ): Result<T> {
        if (isCancellation(cause)) return Result.failure(cause)
        val error = IllegalStateException("$message; retry the strip task", cause)
        if (priorFailure !== cause) error.addSuppressed(priorFailure)
        return Result.failure(error)
    }
}
