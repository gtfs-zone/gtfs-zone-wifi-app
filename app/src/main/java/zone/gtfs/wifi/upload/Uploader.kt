package zone.gtfs.wifi.upload

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface UploadResult {
    data object Stored : UploadResult

    /** Ingest will never take it as is (schema, size, encoding). */
    data class Rejected(val reason: String) : UploadResult

    /** Worth trying again later: network, rate limit or server error. */
    data class Retry(val reason: String) : UploadResult
}

fun gzip(data: ByteArray): ByteArray =
    ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

class Uploader(private val http: OkHttpClient, private val url: String) {
    fun upload(payload: String): UploadResult {
        val body = gzip(payload.toByteArray()).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).header("Content-Encoding", "gzip").build()
        return try {
            http.newCall(request).execute().use { resp ->
                when {
                    resp.code == 200 || resp.code == 201 -> UploadResult.Stored
                    resp.code == 429 || resp.code >= 500 -> UploadResult.Retry("HTTP ${resp.code}")
                    else -> UploadResult.Rejected("HTTP ${resp.code}: ${resp.body.string().take(400)}")
                }
            }
        } catch (e: IOException) {
            UploadResult.Retry(e.message ?: e.javaClass.simpleName)
        }
    }
}
