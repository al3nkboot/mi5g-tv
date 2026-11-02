package tv.mi5g.adb.pairing

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/** BoringSSL-compatible Ed25519 SPAKE2 used by adb pairing. */
internal class Spake2(private val alice: Boolean, password: ByteArray, random: SecureRandom = SecureRandom()) {
    private val p = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val l = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))
    private val d = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(p)).mod(p)
    private data class Pt(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)
    private fun pt(x: BigInteger, y: BigInteger) = Pt(x.mod(p), y.mod(p), BigInteger.ONE, x.multiply(y).mod(p))
    private val identity = pt(BigInteger.ZERO, BigInteger.ONE)
    private fun add(a: Pt, b: Pt): Pt {
        val aa = a.y.subtract(a.x).multiply(b.y.subtract(b.x)).mod(p)
        val bb = a.y.add(a.x).multiply(b.y.add(b.x)).mod(p)
        val cc = BigInteger.TWO.multiply(d).multiply(a.t).multiply(b.t).mod(p)
        val dd = BigInteger.TWO.multiply(a.z).multiply(b.z).mod(p)
        val e = bb.subtract(aa).mod(p); val f = dd.subtract(cc).mod(p)
        val g = dd.add(cc).mod(p); val h = bb.add(aa).mod(p)
        return Pt(e.multiply(f).mod(p), g.multiply(h).mod(p), f.multiply(g).mod(p), e.multiply(h).mod(p))
    }
    private fun mul(a: Pt, n: BigInteger): Pt {
        var k = n; var q = identity; var b = a
        while (k.signum() > 0) { if (k.testBit(0)) q = add(q, b); b = add(b, b); k = k.shiftRight(1) }
        return q
    }
    private fun le(bytes: ByteArray) = BigInteger(1, bytes.reversedArray())
    private fun enc(a: Pt): ByteArray {
        val zi = a.z.modInverse(p); val x = a.x.multiply(zi).mod(p); val y = a.y.multiply(zi).mod(p)
        val out = little(y, 32); if (x.testBit(0)) out[31] = (out[31].toInt() or 0x80).toByte(); return out
    }
    private fun dec(bytes: ByteArray): Pt? {
        if (bytes.size != 32) return null
        val copy = bytes.copyOf(); val sign = (copy[31].toInt() ushr 7) != 0; copy[31] = (copy[31].toInt() and 127).toByte()
        val y = le(copy); if (y >= p) return null
        val y2 = y.multiply(y).mod(p); val u = y2.subtract(BigInteger.ONE).mod(p); val v = d.multiply(y2).add(BigInteger.ONE).mod(p)
        val xx = u.multiply(v.modInverse(p)).mod(p)
        var x = xx.modPow(p.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), p)
        if (x.multiply(x).subtract(xx).mod(p) != BigInteger.ZERO) x = x.multiply(BigInteger.TWO.modPow(p.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), p)).mod(p)
        if (x.multiply(x).mod(p) != xx) return null
        if (x.testBit(0) != sign) x = p.subtract(x)
        return pt(x, y)
    }
    private val m = dec(hex("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"))!!
    private val n = dec(hex("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"))!!
    private val base = pt(BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202"), BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960"))
    private val private: BigInteger
    private val passwordHash = MessageDigest.getInstance("SHA-512").digest(password)
    private val passwordScalar: BigInteger
    val message: ByteArray
    init {
        val seed = ByteArray(64).also(random::nextBytes)
        private = le(seed).mod(l).multiply(BigInteger.valueOf(8))
        var s = le(passwordHash).mod(l)
        if (s.testBit(0)) s = s.add(l)
        if (s.testBit(1)) s = s.add(l.shiftLeft(1))
        if (s.testBit(2)) s = s.add(l.shiftLeft(2))
        passwordScalar = s
        message = enc(add(mul(base, private), mul(if (alice) m else n, s)))
    }
    fun process(peerMessage: ByteArray): ByteArray? {
        val peer = dec(peerMessage) ?: return null
        val mask = mul(if (alice) n else m, passwordScalar)
        val unmasked = add(peer, Pt(p.subtract(mask.x).mod(p), mask.y, mask.z, p.subtract(mask.t).mod(p)))
        val shared = enc(mul(unmasked, private))
        val aliceName = "adb pair client\u0000".toByteArray()
        val bobName = "adb pair server\u0000".toByteArray()
        val fields = if (alice) listOf(aliceName, bobName, message, peerMessage) else listOf(aliceName, bobName, peerMessage, message)
        val md = MessageDigest.getInstance("SHA-512")
        (fields + listOf(shared, passwordHash)).forEach { md.update(little(it.size.toLong(), 8)); md.update(it) }
        return md.digest()
    }
    private fun little(v: Long, size: Int): ByteArray = ByteArray(size) { (v ushr (it * 8)).toByte() }
    private fun little(v: BigInteger, size: Int): ByteArray {
        val src = v.toByteArray().reversedArray(); return ByteArray(size).also { src.copyInto(it, 0, 0, minOf(src.size, size)) }
    }
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
