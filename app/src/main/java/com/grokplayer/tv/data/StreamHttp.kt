package com.grokplayer.tv.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.grokplayer.tv.R
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.io.OutputStream
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

object StreamHttp {
    @Volatile
    private var client: OkHttpClient? = null

    internal fun attach(okHttp: OkHttpClient) {
        client = okHttp
    }

    fun client(context: Context): OkHttpClient {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }
            val trust = trustManager(context.applicationContext)
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf(trust), null)
            val built = OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, trust)
                .cookieJar(HostCookieJar)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            client = built
            return built
        }
    }

    @OptIn(UnstableApi::class)
    fun dataSourceFactory(context: Context): OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(client(context)).setUserAgent(StreamProbe.USER_AGENT)

    @OptIn(UnstableApi::class)
    fun playerDataSourceFactory(context: Context): OkHttpDataSource.Factory {
        val play = client(context).newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return applyPlayHeaders(
            OkHttpDataSource.Factory(play),
            referer = null,
            userAgent = StreamProbe.USER_AGENT,
        )
    }

    @OptIn(UnstableApi::class)
    fun applyPlayHeaders(
        factory: OkHttpDataSource.Factory,
        referer: String?,
        userAgent: String?,
    ): OkHttpDataSource.Factory {
        factory.setUserAgent(userAgent?.takeIf { it.isNotBlank() } ?: StreamProbe.USER_AGENT)
        val ref = referer?.takeIf { it.isNotBlank() }
        val headers = if (ref == null) {
            emptyMap()
        } else {
            val origin = runCatching {
                val uri = android.net.Uri.parse(ref)
                if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else null
            }.getOrNull()
            buildMap {
                put("Referer", ref)
                if (origin != null) put("Origin", origin)
            }
        }
        return factory.setDefaultRequestProperties(headers)
    }

    fun readBytes(url: String): ByteArray {
        request(url).use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    fun copyTo(
        url: String,
        out: OutputStream,
        cancelled: () -> Boolean = { false },
        onBytes: ((copied: Long, contentLength: Long) -> Unit)? = null,
    ): Long {
        request(url).use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("Boş yanıt")
            val contentLength = body.contentLength()
            return body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    if (cancelled()) error("İptal edildi")
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    onBytes?.invoke(copied, contentLength)
                }
                copied
            }
        }
    }

    private fun request(url: String): okhttp3.Response {
        val c = client ?: error("StreamHttp not ready")
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", StreamProbe.USER_AGENT)
            .build()
        return c.newCall(req).execute()
    }

    private fun trustManager(context: Context): X509TrustManager {
        val system = systemTrustManager()
        val extraStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        val factory = CertificateFactory.getInstance("X.509")
        context.resources.openRawResource(R.raw.digicert_global_root_g3).use { input ->
            extraStore.setCertificateEntry("digicert-g3", factory.generateCertificate(input))
        }
        val extraTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        extraTmf.init(extraStore)
        val extra = extraTmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                system.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    system.checkServerTrusted(chain, authType)
                } catch (_: CertificateException) {
                    extra.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                system.acceptedIssuers + extra.acceptedIssuers
        }
    }

    private fun systemTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private object HostCookieJar : CookieJar {
        private val lock = Any()
        private val store = LinkedHashMap<String, MutableMap<String, Cookie>>()

        init {
            val youtube = HttpUrl.Builder().scheme("https").host("www.youtube.com").build()
            val seed = listOf(
                Cookie.Builder().domain("youtube.com").name("CONSENT").value("YES+").build(),
                Cookie.Builder().domain("youtube.com").name("SOCS").value("CAI").build(),
            )
            saveFromResponse(youtube, seed)
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(lock) {
                val host = url.topPrivateDomain() ?: url.host
                val map = store.getOrPut(host) { mutableMapOf() }
                cookies.forEach { map[it.name] = it }
                android.util.Log.i("GrokPlayer", "cookies $host +${cookies.size} total=${map.size}")
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            synchronized(lock) {
                val host = url.topPrivateDomain() ?: url.host
                val now = System.currentTimeMillis()
                return store[host]?.values?.filter { it.expiresAt >= now }.orEmpty()
            }
        }
    }
}
