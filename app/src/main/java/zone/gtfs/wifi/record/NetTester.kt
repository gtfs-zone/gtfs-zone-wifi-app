package zone.gtfs.wifi.record

import android.net.Network
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import zone.gtfs.wifi.Config

data class NetResult(
    val reachable: Boolean,
    val captivePortal: Boolean?,
    val latencyMs: Double?,
    val fetchBytes: Long?,
    val fetchMs: Double?,
)

/**
 * One network test: a plain HTTP 204 probe, then a timed fetch when it
 * comes back clean. A 204 with an empty body is reachable; any other
 * answer is a captive portal; no answer is unreachable.
 */
class NetTester(
    private val base: OkHttpClient,
    private val probeUrl: String = Config.probeUrl,
    private val fetchUrl: String = Config.fetchUrl,
    private val clock: () -> Long = System::nanoTime,
) {
    private fun client(network: Network?): OkHttpClient = base.newBuilder()
        .apply {
            if (network != null) {
                socketFactory(network.socketFactory)
                dns(Dns { host -> network.getAllByName(host).toList() })
            }
        }
        // No reuse, so every test pays for its own connection.
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun request(url: String) = Request.Builder().url(url)
        .header("Cache-Control", "no-cache")
        .header("Accept-Encoding", "identity")
        .build()

    private fun ms(start: Long) = (clock() - start) / 1_000_000.0

    suspend fun run(network: Network?): NetResult = withContext(Dispatchers.IO) {
        val http = client(network)
        val start = clock()
        val probe = try {
            http.newCall(request(probeUrl)).execute().use { resp ->
                val latency = ms(start)
                val body = resp.body.bytes()
                Pair(resp.code == 204 && body.isEmpty(), latency)
            }
        } catch (_: IOException) {
            return@withContext NetResult(false, null, null, null, null)
        }
        if (!probe.first) return@withContext NetResult(false, true, null, null, null)
        val fetchStart = clock()
        val fetched = try {
            http.newCall(request(fetchUrl)).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                var total = 0L
                val buf = ByteArray(16 * 1024)
                resp.body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                    }
                }
                Pair(total, ms(fetchStart))
            }
        } catch (_: IOException) {
            null
        }
        NetResult(true, false, probe.second, fetched?.first, fetched?.second)
    }
}
