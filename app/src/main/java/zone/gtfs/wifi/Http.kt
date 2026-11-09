package zone.gtfs.wifi

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Shared client; every request carries the app's User-Agent. */
fun baseHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", Config.userAgent).build())
    }
    .build()
