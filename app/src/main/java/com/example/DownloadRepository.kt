package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a track's audio to app-private storage (`filesDir/downloads`) for offline playback,
 * and tracks the outcome in Room via [DownloadedTrackDao] - that's the source of truth for
 * "is this downloaded" everywhere in the app (Library's Downloads tab, the Offline mode filter,
 * and [PlayerViewModel]'s decision to play from a local file instead of streaming). Per-byte
 * progress while a download is in flight is kept in memory only ([inProgress]); persisting that
 * to Room on every chunk would be pure churn for a number nothing needs after the download ends.
 *
 * A process-wide singleton (see [getInstance]) rather than a ViewModel-owned instance, since
 * downloads must keep running (and stay visible as "downloading") across whichever screen the
 * user navigates to next.
 *
 * Progress and completion are surfaced both in-app (via [inProgress] StateFlow, observed by
 * [DownloadButton]) and as Android system notifications (a progress bar while downloading, a
 * "Download complete" notification when done) so the user sees what's happening even when the
 * app is backgrounded.
 */
class DownloadRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = MuseFlowDatabase.getInstance(appContext).downloadedTrackDao()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _inProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Key -> percent complete (0..100), or -1 if the server didn't report a content length. */
    val inProgress: StateFlow<Map<String, Int>> = _inProgress

    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Key -> error message for the most recent failed download attempt, so the UI can tell the
     * user *why* their download vanished instead of it just silently reverting to "not
     * downloaded". Cleared as soon as that track's download is retried. */
    val failures: StateFlow<Map<String, String>> = _failures

    val completedDownloads: Flow<List<DownloadedTrackEntity>> = dao.observeCompleted()

    private val notificationManager = NotificationManagerCompat.from(appContext)
    /** Monotonically increasing id so each download gets its own notification slot. */
    private val nextNotificationId = AtomicInteger(NOTIFICATION_ID_BASE)
    /** Tracks which notification id was assigned to which download key, so we can update/dismiss. */
    private val notificationIds = ConcurrentHashMap<String, Int>()

    init { ensureNotificationChannel() }

    fun isDownloading(track: Track): Boolean = activeJobs.containsKey(track.downloadKey())

    fun startDownload(track: Track) {
        val key = track.downloadKey()
        if (activeJobs.containsKey(key)) return
        _failures.update { it - key }

        val notifId = nextNotificationId.getAndIncrement()
        notificationIds[key] = notifId

        activeJobs[key] = repositoryScope.launch(downloadDispatcher) {
            _inProgress.update { it + (key to 0) }
            showProgressNotification(notifId, track.title, 0)
            try {
                val streamUrl = StreamUrlResolver.resolve(appContext, track)
                    ?: error("No playable stream found for \"${track.title}\"")
                val targetFile = File(downloadsDir(appContext), "$key.audio")
                downloadToFile(streamUrl, targetFile) { percent ->
                    _inProgress.update { it + (key to percent) }
                    showProgressNotification(notifId, track.title, percent)
                }
                dao.upsert(
                    DownloadedTrackEntity(
                        key = key,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        duration = track.duration,
                        gradientIndex = track.gradientIndex,
                        imageUrl = track.imageUrl,
                        filePath = targetFile.absolutePath,
                        status = DownloadStatus.COMPLETED.name,
                        updatedAt = System.currentTimeMillis(),
                        sourceId = track.sourceId,
                        sourceType = track.sourceType?.name
                    )
                )
                showCompletionNotification(notifId, track.title)
            } catch (e: CancellationException) {
                // A user-initiated cancel (see cancelDownload) - not a failure, just clean up
                // the partial file below and let the cancellation propagate as normal.
                File(downloadsDir(appContext), "$key.audio").delete()
                dismissNotification(notifId)
                throw e
            } catch (e: Exception) {
                // Don't leave a stale/broken row around - a partial file is useless, and the user
                // can just tap download again.
                File(downloadsDir(appContext), "$key.audio").delete()
                _failures.update { it + (key to (e.message ?: "Download failed")) }
                showFailureNotification(notifId, track.title)
            } finally {
                _inProgress.update { it - key }
                activeJobs.remove(key)
                notificationIds.remove(key)
            }
        }
    }

    fun cancelDownload(track: Track) {
        val key = track.downloadKey()
        notificationIds.remove(key)?.let { dismissNotification(it) }
        activeJobs.remove(key)?.cancel()
    }

    suspend fun deleteDownload(track: Track) {
        val key = track.downloadKey()
        cancelDownload(track)
        dao.getByKey(key)?.let { File(it.filePath).delete() }
        dao.deleteByKey(key)
    }

    // ── Notification helpers ────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW   // no sound/vibration — purely visual
            ).apply {
                description = "Track download progress and completion"
                setShowBadge(false)
            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun baseNotificationBuilder(title: String): NotificationCompat.Builder =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)

    private fun showProgressNotification(id: Int, title: String, percent: Int) {
        val indeterminate = percent < 0
        val builder = baseNotificationBuilder(title)
            .setContentText(if (indeterminate) "Downloading…" else "$percent%")
            .setProgress(100, if (indeterminate) 0 else percent, indeterminate)
            .setOngoing(true)
        try { notificationManager.notify(id, builder.build()) } catch (_: SecurityException) {}
    }

    private fun showCompletionNotification(id: Int, title: String) {
        val builder = baseNotificationBuilder(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentText("Download complete")
            .setProgress(0, 0, false)  // removes the progress bar
            .setOngoing(false)
            .setAutoCancel(true)
        try { notificationManager.notify(id, builder.build()) } catch (_: SecurityException) {}
    }

    private fun showFailureNotification(id: Int, title: String) {
        val builder = baseNotificationBuilder(title)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentText("Download failed")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
        try { notificationManager.notify(id, builder.build()) } catch (_: SecurityException) {}
    }

    private fun dismissNotification(id: Int) {
        try { notificationManager.cancel(id) } catch (_: SecurityException) {}
    }

    // ── File download ───────────────────────────────────────────────────────

    private fun downloadToFile(url: String, targetFile: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val body = response.body ?: error("Empty download response")
            val contentLength = body.contentLength()
            targetFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var totalRead = 0L
                    var lastReportedPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        } else if (lastReportedPercent != -1) {
                            // No content-length header — report indeterminate once.
                            lastReportedPercent = -1
                            onProgress(-1)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "museflow_downloads"
        private const val NOTIFICATION_ID_BASE = 9000

        fun downloadsDir(context: Context): File = File(context.filesDir, "downloads")

        @Volatile private var instance: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
    }
}
