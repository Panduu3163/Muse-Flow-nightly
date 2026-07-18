package com.example

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** A single sung syllable, with start time and duration, parsed from Enhanced LRC tags. */
data class LyricSyllable(val timeMs: Long, val text: String, val durationMs: Long)

/** A single synced lyric line, in playback order, ready to be time-matched against position. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val syllables: List<LyricSyllable> = emptyList(),
    val durationMs: Long = 0
)

sealed interface LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult
    data class PlainOnly(val text: String) : LyricsResult
    data object Instrumental : LyricsResult
    data object NotFound : LyricsResult
    data class Error(val message: String) : LyricsResult
}

interface LyricsProvider {
    suspend fun fetchLyrics(trackName: String, artistName: String, durationMs: Long? = null): LyricsResult
}

/**
 * Fetches lyrics from LRCLib (https://lrclib.net) - a free, public, no-API-key lyrics database
 * keyed by track/artist (optionally narrowed by duration). LRCLib returns line-by-line synced
 * timing ("syncedLyrics", standard LRC format) when available, which is what makes a real
 * karaoke-style highlight-as-you-play view possible; [parseLrc] turns that into [LyricLine]s.
 */
class LrcLibProvider : LyricsProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private object Api {
        const val BASE_URL = "https://lrclib.net/api"
        // LRCLib asks integrating clients to identify themselves via User-Agent.
        const val USER_AGENT = "MuseFlow/1.0 (Android; +https://lrclib.net)"
    }

    /**
     * [durationMs], when known, narrows LRCLib's `/get` exact-match endpoint (it matches
     * within a couple of seconds of tolerance). If that 404s - wrong duration hint, or just no
     * exact match - falls back to `/search`, taking the first result with real timing data.
     */
    override suspend fun fetchLyrics(trackName: String, artistName: String, durationMs: Long?): LyricsResult =
        withContext(Dispatchers.IO) {
            try {
                val durationSeconds = (durationMs?.div(1000))?.toInt()
                getExact(trackName, artistName, durationSeconds)
                    ?: searchFallback(trackName, artistName)
                    ?: LyricsResult.NotFound
            } catch (e: Exception) {
                LyricsResult.Error(e.message ?: "Unknown error")
            }
        }

    private fun getExact(title: String, artist: String, durationSeconds: Int?): LyricsResult? {
        val url = buildString {
            append("${Api.BASE_URL}/get")
            append("?track_name=${encode(title)}")
            append("&artist_name=${encode(artist)}")
            if (durationSeconds != null && durationSeconds > 0) append("&duration=$durationSeconds")
        }
        val response = execute(url) ?: return null
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string() ?: return null
            return parseTrackObject(JSONObject(body))
        }
    }

    private fun searchFallback(title: String, artist: String): LyricsResult? {
        val url = "${Api.BASE_URL}/search?track_name=${encode(title)}&artist_name=${encode(artist)}"
        val response = execute(url) ?: return null
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string() ?: return null
            val candidates = JSONArray(body)
            val parsed = (0 until candidates.length()).mapNotNull { i ->
                candidates.optJSONObject(i)?.let(::parseTrackObject)
            }
            return parsed.firstOrNull { it is LyricsResult.Synced }
                ?: parsed.firstOrNull { it is LyricsResult.Instrumental }
                ?: parsed.firstOrNull { it is LyricsResult.PlainOnly }
        }
    }

    private fun parseTrackObject(track: JSONObject): LyricsResult {
        if (track.optBoolean("instrumental")) return LyricsResult.Instrumental
        track.optString("syncedLyrics").takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Synced(parseLrc(it))
        }
        track.optString("plainLyrics").takeIf { it.isNotBlank() }?.let {
            return LyricsResult.PlainOnly(it)
        }
        return LyricsResult.NotFound
    }

    private fun execute(url: String): Response? {
        val request = Request.Builder().url(url).header("User-Agent", Api.USER_AGENT).build()
        return try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        private val timeTagRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val wordTagRegex = Regex("""<(\d{2}):(\d{2})(?:[.:](\d{1,3}))?>""")

        private fun parseTimeMs(minutes: String, seconds: String, fraction: String): Long {
            val min = minutes.toLong()
            val sec = seconds.toLong()
            val millis = when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.substring(0, 3).toLong()
            }
            return (min * 60 + sec) * 1000 + millis
        }

        /**
         * Parses standard LRC timing tags - "[mm:ss.xx]lyric text" and Enhanced LRC word tags
         * "<mm:ss.xx>" into a flat, time-sorted list of [LyricLine]s with [LyricSyllable]s.
         */
        fun parseLrc(lrc: String): List<LyricLine> {
            val lines = mutableListOf<LyricLine>()
            for (rawLine in lrc.lineSequence()) {
                val lineMatches = timeTagRegex.findAll(rawLine).toList()
                if (lineMatches.isEmpty()) continue
                
                val rawContent = rawLine.substring(lineMatches.last().range.last + 1).trim()
                
                val wordMatches = wordTagRegex.findAll(rawContent).toList()
                val syllables = mutableListOf<LyricSyllable>()
                var cleanedText = ""
                
                if (wordMatches.isNotEmpty()) {
                    var lastIndex = 0
                    for (i in wordMatches.indices) {
                        val match = wordMatches[i]
                        val preText = rawContent.substring(lastIndex, match.range.first)
                        
                        val wordTime = parseTimeMs(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                        
                        if (preText.isNotEmpty()) {
                            if (syllables.isEmpty()) {
                                syllables.add(LyricSyllable(0, preText, 0))
                            } else {
                                val prev = syllables.last()
                                syllables[syllables.lastIndex] = prev.copy(text = prev.text + preText)
                            }
                            cleanedText += preText
                        }
                        
                        syllables.add(LyricSyllable(wordTime, "", 0))
                        lastIndex = match.range.last + 1
                    }
                    val tailText = rawContent.substring(lastIndex)
                    if (tailText.isNotEmpty()) {
                        if (syllables.isEmpty()) {
                            syllables.add(LyricSyllable(0, tailText, 0))
                        } else {
                            val prev = syllables.last()
                            syllables[syllables.lastIndex] = prev.copy(text = prev.text + tailText)
                        }
                        cleanedText += tailText
                    }
                    
                    val validSyllables = syllables.filter { it.text.isNotEmpty() }.toMutableList()
                    syllables.clear()
                    syllables.addAll(validSyllables)
                } else {
                    cleanedText = rawContent
                }
                
                for (match in lineMatches) {
                    val lineTime = parseTimeMs(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    
                    val lineSyllables = syllables.map { it.copy() }.toMutableList()
                    if (lineSyllables.isNotEmpty() && lineSyllables[0].timeMs == 0L) {
                        lineSyllables[0] = lineSyllables[0].copy(timeMs = lineTime)
                    }
                    
                    for (i in 0 until lineSyllables.size - 1) {
                        if (lineSyllables[i + 1].timeMs > lineSyllables[i].timeMs) {
                            lineSyllables[i] = lineSyllables[i].copy(durationMs = lineSyllables[i + 1].timeMs - lineSyllables[i].timeMs)
                        }
                    }
                    
                    lines += LyricLine(lineTime, cleanedText, lineSyllables)
                }
            }
            
            val sortedLines = lines.sortedBy { it.timeMs }.toMutableList()
            
            for (i in sortedLines.indices) {
                val current = sortedLines[i]
                val nextTime = if (i + 1 < sortedLines.size) sortedLines[i + 1].timeMs else current.timeMs + 5000L
                val lineDuration = (nextTime - current.timeMs).coerceAtLeast(0L)
                
                if (current.syllables.isNotEmpty()) {
                    val lastSyl = current.syllables.last()
                    if (lastSyl.durationMs == 0L) {
                        val newSylDuration = ((current.timeMs + lineDuration) - lastSyl.timeMs).coerceAtLeast(0L)
                        val updatedSyllables = current.syllables.toMutableList()
                        updatedSyllables[updatedSyllables.lastIndex] = lastSyl.copy(durationMs = newSylDuration)
                        sortedLines[i] = current.copy(durationMs = lineDuration, syllables = updatedSyllables)
                        continue
                    }
                }
                
                sortedLines[i] = current.copy(durationMs = lineDuration)
            }
            
            return sortedLines
        }

        /** Index of the line that should be highlighted at [positionMs], or -1 before the first
         * line's timestamp. [lines] must be sorted ascending by [LyricLine.timeMs] (as returned
         * by [parseLrc]). */
        fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int =
            lines.indexOfLast { it.timeMs <= positionMs }
    }
}
