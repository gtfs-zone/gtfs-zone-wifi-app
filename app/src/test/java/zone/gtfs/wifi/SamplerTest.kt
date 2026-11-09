package zone.gtfs.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zone.gtfs.wifi.record.cleanBssid

class SamplerTest {
    @Test
    fun bssidIsLowerCasedAndRedactionDropped() {
        assertEquals("06:aa:bb:cc:dd:01", cleanBssid("06:AA:BB:CC:DD:01"))
        assertNull(cleanBssid("02:00:00:00:00:00"))
        assertNull(cleanBssid("not a mac"))
        assertNull(cleanBssid(null))
    }
}
