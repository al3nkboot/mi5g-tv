package tv.cleanyour.core.registry

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

// The real HttpURLConnection client against a local HTTPS server.
//
// MITM proof (W5 definition of done, SECURITY 2.4 T-S1): mitmproxy is not installed on the
// build machines, so the same property is shown on the JVM. The server's certificate is issued by a fresh
// "user CA" (made with keytool for this run, like the CA a proxy asks the user to install). A
// client that trusts that CA gets the file (control). The fetcher as the app builds it, with the
// platform's trust store only, refuses the handshake and no request reaches the server. On the
// phone the same holds through the network security config (system anchors only), which W8 ships.
class UrlConnectionFetcherTest {

    companion object {
        private lateinit var server: HttpsServer
        private var port = 0
        private lateinit var trustsUserCa: SSLSocketFactory
        val payload = ByteArray(50_000) { (it % 199).toByte() }
        const val ETAG = "\"e1\""
        val seen = CopyOnWriteArrayList<Map<String, String?>>()

        @BeforeClass @JvmStatic fun start() {
            val dir = Files.createTempDirectory("cyt-tls").toFile()
            val pass = "fixture-only"
            fun keytool(vararg args: String) {
                val exe = File(System.getProperty("java.home"), "bin/keytool").path
                val p = ProcessBuilder(listOf(exe) + args).directory(dir).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                check(p.waitFor() == 0) { "keytool ${args.first()} failed: $out" }
            }
            val ks = listOf("-storetype", "PKCS12", "-storepass", pass)
            keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=Test user CA",
                "-ext", "bc:c", "-validity", "2", "-keystore", "ca.p12", *ks.toTypedArray())
            keytool("-genkeypair", "-alias", "leaf", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=localhost",
                "-validity", "2", "-keystore", "leaf.p12", *ks.toTypedArray())
            keytool("-certreq", "-alias", "leaf", "-keystore", "leaf.p12", "-file", "leaf.csr", *ks.toTypedArray())
            keytool("-gencert", "-alias", "ca", "-keystore", "ca.p12", "-infile", "leaf.csr", "-outfile", "leaf.cer", "-rfc",
                "-ext", "san=dns:localhost,ip:127.0.0.1", "-ext", "eku=serverAuth", "-validity", "2", *ks.toTypedArray())
            keytool("-exportcert", "-alias", "ca", "-keystore", "ca.p12", "-file", "ca.cer", "-rfc", *ks.toTypedArray())
            keytool("-importcert", "-alias", "ca", "-file", "ca.cer", "-keystore", "leaf.p12", "-noprompt", *ks.toTypedArray())
            keytool("-importcert", "-alias", "leaf", "-file", "leaf.cer", "-keystore", "leaf.p12", "-noprompt", *ks.toTypedArray())

            val leaf = KeyStore.getInstance("PKCS12").apply { FileInputStream(File(dir, "leaf.p12")).use { load(it, pass.toCharArray()) } }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(leaf, pass.toCharArray()) }
            val serverCtx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }

            val ca = CertificateFactory.getInstance("X.509").generateCertificate(FileInputStream(File(dir, "ca.cer")))
            val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("user-ca", ca) }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
            trustsUserCa = SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }.socketFactory
            dir.deleteRecursively()

            server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.httpsConfigurator = HttpsConfigurator(serverCtx)
            server.createContext("/") { ex -> handle(ex) }
            server.start()
            port = server.address.port
        }

        @AfterClass @JvmStatic fun stop() { server.stop(0) }

        private fun handle(ex: HttpExchange) {
            val h = ex.requestHeaders
            seen += mapOf("path" to ex.requestURI.path, "range" to h.getFirst("Range"), "if-range" to h.getFirst("If-Range"),
                "if-none-match" to h.getFirst("If-None-Match"), "user-agent" to h.getFirst("User-Agent"), "accept-encoding" to h.getFirst("Accept-Encoding"))
            ex.responseHeaders.add("Connection", "close")
            fun redirect(to: String) { ex.responseHeaders.add("Location", to); ex.sendResponseHeaders(302, -1); ex.close() }
            when (ex.requestURI.path) {
                "/file" -> {
                    ex.responseHeaders.add("ETag", ETAG)
                    val range = h.getFirst("Range")
                    val ifRange = h.getFirst("If-Range")
                    when {
                        range == null && h.getFirst("If-None-Match") == ETAG -> { ex.sendResponseHeaders(304, -1); ex.close() }
                        range != null && (ifRange == null || ifRange == ETAG) -> {
                            val from = range.removePrefix("bytes=").removeSuffix("-").toInt()
                            ex.responseHeaders.add("Content-Range", "bytes $from-${payload.size - 1}/${payload.size}")
                            ex.sendResponseHeaders(206, (payload.size - from).toLong())
                            ex.responseBody.use { it.write(payload, from, payload.size - from) }
                        }
                        else -> { ex.sendResponseHeaders(200, payload.size.toLong()); ex.responseBody.use { it.write(payload) } }
                    }
                }
                "/relative" -> redirect("/file")
                "/absolute" -> redirect("https://localhost:$port/file")
                "/off-github" -> redirect("https://evil.example/file")
                "/plain-http" -> redirect("http://localhost:$port/file")
                "/loop" -> redirect("/loop")
                else -> { ex.sendResponseHeaders(404, -1); ex.close() }
            }
        }
    }

    private val local: (String) -> Boolean = { u -> URI(u).let { it.scheme == "https" && it.host == "localhost" && it.port == port } }
    private fun url(path: String) = "https://localhost:$port$path"
    private val trusted get() = UrlConnectionFetcher(urlAllowed = local, sslSocketFactory = trustsUserCa)

    @Before fun clear() { seen.clear() }

    private fun expectFailure(failure: FetchFailure, block: suspend () -> Unit): FetchException {
        try { runBlocking { block() }; fail("expected $failure") } catch (e: FetchException) { assertEquals(e.message, failure, e.failure); return e }
        error("unreachable")
    }

    @Test fun userCaIsRefusedByThePlatformTrustStore() {
        val asShipped = UrlConnectionFetcher(urlAllowed = local)   // no socket factory: the platform's trust anchors
        val e = expectFailure(FetchFailure.HTTP_ERROR) { asShipped.get(url("/file"), null, null) }
        assertNull(e.httpCode)
        assertTrue(e.cause.toString(), e.cause is SSLException)
        assertTrue("no request reached the server", seen.isEmpty())
    }

    @Test fun controlAClientTrustingTheUserCaGetsTheFile() = runBlocking {
        val r = trusted.get(url("/file"), null, null)
        assertEquals(200, r.code)
        assertEquals(ETAG, r.etag)
        assertEquals(payload.size.toLong(), r.length)
        assertArrayEquals(payload, r.body!!.use { it.readBytes() })
        assertEquals(UrlConnectionFetcher.USER_AGENT, seen.single()["user-agent"])
        assertEquals("identity", seen.single()["accept-encoding"])
    }

    @Test fun redirectsAreFollowedByHand() = runBlocking {
        for (p in listOf("/relative", "/absolute")) {
            val r = trusted.get(url(p), null, null)
            assertEquals(200, r.code)
            r.body!!.close()
        }
        assertEquals(listOf("/relative", "/file", "/absolute", "/file"), seen.map { it["path"] })
    }

    @Test fun redirectOffTheAllowlistIsRefusedBeforeConnecting() {
        expectFailure(FetchFailure.HOST_NOT_ALLOWED) { trusted.get(url("/off-github"), null, null) }
        expectFailure(FetchFailure.HOST_NOT_ALLOWED) { trusted.get(url("/plain-http"), null, null) }
        assertEquals(listOf("/off-github", "/plain-http"), seen.map { it["path"] })
    }

    @Test fun redirectLoopStops() {
        val e = expectFailure(FetchFailure.HTTP_ERROR) { trusted.get(url("/loop"), null, null) }
        assertNull(e.httpCode)
        assertEquals(6, seen.size)
    }

    @Test fun rangeAndValidators() = runBlocking {
        val part = trusted.get(url("/file"), ETAG, 40_000)
        assertEquals(206, part.code)
        assertEquals(10_000L, part.length)
        assertArrayEquals(payload.copyOfRange(40_000, payload.size), part.body!!.use { it.readBytes() })
        assertEquals("bytes=40000-", seen.last()["range"])
        assertEquals(ETAG, seen.last()["if-range"])

        val changed = trusted.get(url("/file"), "\"old\"", 40_000)
        assertEquals(200, changed.code)
        changed.body!!.close()

        val notModified = trusted.get(url("/file"), ETAG, null)
        assertEquals(304, notModified.code)
        notModified.body?.close()
        assertEquals(ETAG, seen.last()["if-none-match"])
        assertNull(seen.last()["range"])
    }

    @Test fun notFoundIsACodeNotAnException() = runBlocking {
        val r = trusted.get(url("/missing"), null, null)
        assertEquals(404, r.code)
        r.body?.close()
        Unit
    }

    @Test fun defaultPolicyAllowsOnlyGitHubHosts() {
        val app = UrlConnectionFetcher(sslSocketFactory = trustsUserCa)
        expectFailure(FetchFailure.HOST_NOT_ALLOWED) { app.get(url("/file"), null, null) }
        expectFailure(FetchFailure.HOST_NOT_ALLOWED) { app.get("http://github.com/x", null, null) }
        expectFailure(FetchFailure.HOST_NOT_ALLOWED) { app.get("https://github.com.evil.example/x", null, null) }
        assertTrue(seen.isEmpty())
    }

    @Test fun closedPortIsOffline() {
        val closed = ServerSocket(0).use { it.localPort }
        val f = UrlConnectionFetcher(urlAllowed = { true }, sslSocketFactory = trustsUserCa, connectTimeoutMs = 2_000)
        expectFailure(FetchFailure.OFFLINE) { f.get("https://localhost:$closed/file", null, null) }
    }
}
