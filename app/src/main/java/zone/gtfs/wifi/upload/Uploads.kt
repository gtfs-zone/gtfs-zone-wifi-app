package zone.gtfs.wifi.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Uploads {
    private const val QUEUED = "upload"
    private const val NOW = "upload-now"

    /** Uploads queued sessions once on an unmetered network. */
    fun schedule(context: Context) = enqueue(context, QUEUED, NetworkType.UNMETERED, ExistingWorkPolicy.KEEP)

    /** Uploads queued sessions on any network. */
    fun now(context: Context) = enqueue(context, NOW, NetworkType.CONNECTED, ExistingWorkPolicy.REPLACE)

    private fun enqueue(context: Context, name: String, network: NetworkType, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }
}
