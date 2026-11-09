package zone.gtfs.wifi

import java.util.zip.GZIPInputStream
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zone.gtfs.wifi.record.NetResult
import zone.gtfs.wifi.record.NetTester
import zone.gtfs.wifi.upload.UploadResult
import zone.gtfs.wifi.upload.Uploader

class NetworkTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun tester() = NetTester(http, server.url("/generate_204").toString(), server.url("/probe/100k.bin").toString())

    @Test
    fun cleanProbeThenFetch() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(ByteArray(102_400))).build())
        val r = tester().run(null)
        assertTrue(r.reachable)
        assertEquals(false, r.captivePortal)
        assertEquals(102_400L, r.fetchBytes)
        assertTrue(r.latencyMs!! >= 0 && r.fetchMs!! >= 0)
        assertEquals("/generate_204", server.takeRequest().url.encodedPath)
    }

    @Test
    fun aRedirectIsACaptivePortal() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://portal.example/").build())
        assertEquals(NetResult(false, true, null, null, null), tester().run(null))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aLoginPageIsACaptivePortal() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("<html>login</html>").build())
        assertEquals(NetResult(false, true, null, null, null), tester().run(null))
    }

    @Test
    fun noAnswerIsUnreachable() = runTest {
        val url = server.url("/generate_204").toString()
        server.close()
        val r = NetTester(http, url, url).run(null)
        assertEquals(NetResult(false, null, null, null, null), r)
    }

    @Test
    fun failedFetchKeepsTheProbe() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(404).build())
        val r = tester().run(null)
        assertTrue(r.reachable)
        assertNull(r.fetchBytes)
    }

    private fun upload(code: Int): UploadResult {
        server.enqueue(MockResponse.Builder().code(code).body("{}").build())
        return Uploader(http, server.url("/v1/sessions").toString()).upload("{\"a\":1}")
    }

    @Test
    fun uploadsGzippedJson() {
        assertEquals(UploadResult.Stored, upload(201))
        val req = server.takeRequest()
        assertEquals("gzip", req.headers["Content-Encoding"])
        assertTrue(req.headers["Content-Type"]!!.startsWith("application/json"))
        val body = GZIPInputStream(req.body!!.toByteArray().inputStream()).readBytes()
        assertArrayEquals("{\"a\":1}".toByteArray(), body)
    }

    @Test
    fun uploadStatuses() {
        assertEquals(UploadResult.Stored, upload(200))
        assertTrue(upload(422) is UploadResult.Rejected)
        assertTrue(upload(413) is UploadResult.Rejected)
        assertTrue(upload(429) is UploadResult.Retry)
        assertTrue(upload(503) is UploadResult.Retry)
    }
}
