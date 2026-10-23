package com.jsalinas.miboxos.testutil

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Hue Bridge falso: HTTPS con una cadena como la real (CA "O=Philips Hue, CN=root-bridge" → CN = id del bridge)
 * y la API v2 con una casa de ejemplo: Living (2 luces), Dormitorio (1 luz), zona Planta baja.
 */
class FakeHueBridge(
    val bridgeId: String = "001788fffe4a1b2c",
    issuerOrg: String = "Philips Hue",
) {
    val server = MockWebServer()
    val requests: MutableList<RecordedRequest> = CopyOnWriteArrayList()

    /** Cuántos intentos de vinculación responden "botón no apretado" antes de aceptar. */
    @Volatile var pairRejections = 0
    val appKey = "test-app-key"

    init {
        val caKeys = keyPair()
        val caName = X500Name("C=NL, O=$issuerOrg, CN=root-bridge")
        val ca = cert(caName, caName, caKeys, caKeys, isCa = true)
        val leafKeys = keyPair()
        val leaf = cert(caName, X500Name("C=NL, O=$issuerOrg, CN=$bridgeId"), leafKeys, caKeys, isCa = false)

        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("bridge", leafKeys.private, CharArray(0), arrayOf(leaf, ca))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
        val ssl = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        server.useHttps(ssl.socketFactory, false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return respond(request)
            }
        }
        server.start()
    }

    val host: String get() = server.hostName
    val port: Int get() = server.port

    fun discoveryJson() = """[{"id":"$bridgeId","internalipaddress":"$host","port":$port}]"""

    fun puts(): List<RecordedRequest> = requests.filter { it.method == "PUT" }

    fun shutdown() = server.shutdown()

    private fun respond(r: RecordedRequest): MockResponse {
        val path = r.path.orEmpty()
        if (r.method == "POST" && path == "/api") {
            return if (pairRejections-- > 0) {
                json("""[{"error":{"type":101,"address":"","description":"link button not pressed"}}]""")
            } else {
                json("""[{"success":{"username":"$appKey","clientkey":"ABCDEF"}}]""")
            }
        }
        if (r.getHeader("hue-application-key") != appKey) {
            return MockResponse().setResponseCode(403).setBody("""{"errors":[{"description":"unauthorized user"}]}""")
        }
        if (r.method == "PUT") return json("""{"data":[{"rid":"x","rtype":"light"}],"errors":[]}""")
        val type = path.removePrefix("/clip/v2/resource/")
        return json("""{"errors":[],"data":${RESOURCES[type] ?: "[]"}}""")
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    companion object {
        private val RESOURCES = mapOf(
            "device" to """[
                {"id":"dev-1","metadata":{"name":"Lámpara pie"},"services":[{"rid":"light-1","rtype":"light"},{"rid":"zb-1","rtype":"zigbee_connectivity"}]},
                {"id":"dev-2","metadata":{"name":"Techo living"},"services":[{"rid":"light-2","rtype":"light"}]},
                {"id":"dev-3","metadata":{"name":"Velador"},"services":[{"rid":"light-3","rtype":"light"}]}
            ]""",
            "room" to """[
                {"id":"room-1","metadata":{"name":"Living"},"children":[{"rid":"dev-1","rtype":"device"},{"rid":"dev-2","rtype":"device"}],"services":[{"rid":"gl-living","rtype":"grouped_light"}]},
                {"id":"room-2","metadata":{"name":"Dormitorio"},"children":[{"rid":"dev-3","rtype":"device"}],"services":[{"rid":"gl-dorm","rtype":"grouped_light"}]}
            ]""",
            "zone" to """[
                {"id":"zone-1","metadata":{"name":"Planta baja"},"children":[{"rid":"light-1","rtype":"light"},{"rid":"light-2","rtype":"light"}],"services":[{"rid":"gl-pb","rtype":"grouped_light"}]}
            ]""",
            "bridge_home" to """[{"id":"home-1","children":[],"services":[{"rid":"gl-all","rtype":"grouped_light"}]}]""",
            "light" to """[
                {"id":"light-1","metadata":{"name":"Lámpara pie"}},
                {"id":"light-2","metadata":{"name":"Techo living"}},
                {"id":"light-3","metadata":{"name":"Velador"}}
            ]""",
            "scene" to """[
                {"id":"scene-1","metadata":{"name":"Relajarse"},"group":{"rid":"room-1","rtype":"room"}},
                {"id":"scene-2","metadata":{"name":"Relajarse"},"group":{"rid":"room-2","rtype":"room"}},
                {"id":"scene-3","metadata":{"name":"Cine"},"group":{"rid":"room-1","rtype":"room"}}
            ]""",
        )

        private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

        private fun cert(issuer: X500Name, subject: X500Name, subjectKeys: KeyPair, issuerKeys: KeyPair, isCa: Boolean): X509Certificate {
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                issuer, BigInteger.valueOf(now), Date(now - 86_400_000), Date(now + 86_400_000L * 365),
                subject, subjectKeys.public,
            ).addExtension(Extension.basicConstraints, true, BasicConstraints(isCa))
            val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerKeys.private)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }
    }
}
