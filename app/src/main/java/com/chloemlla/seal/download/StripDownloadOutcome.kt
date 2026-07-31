package com.chloemlla.seal.download

import kotlinx.serialization.Serializable

@Serializable
enum class StripResult {
    NotRequested,
    Applied,
}

data class DownloadOutcome(
    val filePaths: List<String>,
    val stripResult: StripResult = StripResult.NotRequested,
    val stripMessage: String? = null,
)
