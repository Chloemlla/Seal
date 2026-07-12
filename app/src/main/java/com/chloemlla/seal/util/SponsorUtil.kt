package com.chloemlla.seal.util

import android.util.Log
import androidx.annotation.CheckResult

/**
 * Sponsor list fetching used to embed a GitHub PAT in the APK (security issue).
 * Client-side authenticated GraphQL is disabled; use public sponsor links in UI instead.
 * A future backend/proxy may restore structured sponsor data without shipping secrets.
 */
object SponsorUtil {
    private const val TAG = "SponsorUtil"

    @CheckResult
    fun getSponsors(): Result<SponsorData> {
        Log.i(TAG, "Client-side GitHub GraphQL sponsor fetch is disabled (no embedded credentials).")
        return Result.failure(
            UnsupportedOperationException(
                "Sponsor list API is unavailable in-app. Open the GitHub sponsor page instead."
            )
        )
    }
}
