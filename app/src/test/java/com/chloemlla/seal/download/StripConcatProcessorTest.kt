package com.chloemlla.seal.download

import java.io.File
import com.chloemlla.seal.util.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Test

class StripConcatProcessorTest {
    @Test
    fun concatSpecPreservesKeepRangeOrderAndEscapesQuotes() {
        val spec =
            StripConcatProcessor.buildConcatSpec(
                listOf(
                    File("/tmp/part 0.mp4"),
                    File("/tmp/part's 1.mp4"),
                )
            )

        assertEquals(
            "ffconcat version 1.0\n" +
                "file '/tmp/part 0.mp4'\n" +
                "file '/tmp/part'\\''s 1.mp4'\n",
            spec,
        )
    }

    @Test
    fun fullSourceSpecRepeatsInputWithEveryKeepRange() {
        val spec =
            StripConcatProcessor.buildRangeConcatSpec(
                source = File("/tmp/full source.mp4"),
                keepSections =
                    listOf(
                        VideoClip(start = 0, end = 12),
                        VideoClip(start = 45, end = 120),
                    ),
            )

        assertEquals(
            "ffconcat version 1.0\n" +
                "file '/tmp/full source.mp4'\n" +
                "inpoint 0\n" +
                "outpoint 12\n" +
                "file '/tmp/full source.mp4'\n" +
                "inpoint 45\n" +
                "outpoint 120\n",
            spec,
        )
    }
}
