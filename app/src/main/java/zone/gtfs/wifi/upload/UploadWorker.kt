package zone.gtfs.wifi.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zone.gtfs.wifi.Config
import zone.gtfs.wifi.WifiApp

/** Posts every queued session; retries with backoff while any is left. */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as WifiApp
        val uploader = Uploader(app.http, Config.uploadUrl)
        var retry = false
        for (s in app.sessions.queued()) {
            val payload = s.payload ?: continue
            when (val r = uploader.upload(payload)) {
                UploadResult.Stored -> app.sessions.markSent(s.id)
                is UploadResult.Rejected -> app.sessions.markRejected(s.id, r.reason)
                is UploadResult.Retry -> {
                    app.sessions.noteError(s.id, r.reason)
                    retry = true
                }
            }
        }
        if (retry) Result.retry() else Result.success()
    }
}
