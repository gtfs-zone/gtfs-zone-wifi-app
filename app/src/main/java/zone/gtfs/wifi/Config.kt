package zone.gtfs.wifi

object Config {
    const val SAMPLE_INTERVAL_MS = 10_000L
    const val NET_TEST_INTERVAL_MS = 60_000L

    /** A location fix older than this is not used for a sample. */
    const val MAX_FIX_AGE_MS = 30_000L

    /** Samples are kept within this distance of the trip line or station. */
    const val TRIM_RADIUS_M = 300.0

    /** Net tests are kept within this time of the first and last kept sample. */
    const val NET_TEST_SLACK_MS = 30_000L

    const val NEARBY_STOPS_RADIUS_M = 600.0

    val userAgent = "gtfs-zone-wifi/${BuildConfig.VERSION_NAME} (+${BuildConfig.REPO_URL})"
    val probeUrl = BuildConfig.PROBE_URL
    val fetchUrl = "${BuildConfig.DATA_URL}/probe/100k.bin"
    val uploadUrl = "${BuildConfig.INGEST_URL}/v1/sessions"
}
