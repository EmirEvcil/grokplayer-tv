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
}
