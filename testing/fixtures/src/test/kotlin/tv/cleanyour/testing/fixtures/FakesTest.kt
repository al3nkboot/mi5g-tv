package tv.cleanyour.testing.fixtures

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import tv.cleanyour.adb.wire.AuthToken
import tv.cleanyour.core.discovery.DiscoveryEvent
import tv.cleanyour.core.discovery.FoundTv
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.StartRefusal
import tv.cleanyour.core.engine.StartResult
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.store.GateMethod
import tv.cleanyour.core.store.GateResult
import java.io.ByteArrayInputStream
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakesTest {

    @Test fun fakeClockMovesOnlyWhenTold() {
        val c = FakeClock(wallMs = 1000, monotonicMs = 5)
        c.advance(250)
        assertEquals(1250, c.nowMs()); assertEquals(255, c.monotonicMs())
        c.setWall(0)
        assertEquals(0, c.nowMs()); assertEquals(255, c.monotonicMs())
    }

    @Test fun idGenIsDeterministicAndValid() {
        val g = FakeIdGen()
        assertEquals(26, g.planId().value.length)
        assertEquals("0000000000000002", g.runToken().value)
    }

    @Test fun memoryDocumentStoreIsAllOrNothing() = runBlocking<Unit> {
        val s = MemoryDocumentStore()
        s.write("tvs/a/tv.json", "one".encodeToByteArray())
        s.write("tvs/b/tv.json", "two".encodeToByteArray())
        s.failNextWrite = java.io.IOException("disk full")
        assertFailsWith<java.io.IOException> { s.write("tvs/a/tv.json", "broken".encodeToByteArray()) }
        assertEquals("one", s.read("tvs/a/tv.json") { it.decodeToString() })
        assertEquals(listOf("a", "b"), s.list("tvs"))
        assertFailsWith<IllegalArgumentException> { s.write("../escape", ByteArray(1)) }
        s.delete("tvs/a/tv.json")
        assertNull(s.read("tvs/a/tv.json") { it })
    }

    @Test fun signerSignsWhatAdbdVerifies() = runBlocking<Unit> {
        val signer = FakeAdbSigner()
        val token = AuthToken(ByteArray(20) { it.toByte() })
        val sig = signer.sign(token)
        assertTrue(AdbKeyFormat.verify(signer.keyPair.public as RSAPublicKey, token.bytes, sig))
        assertTrue(signer.publicKeyLine.endsWith(" cleanyourtv"))
        assertEquals(524, java.util.Base64.getDecoder().decode(signer.publicKeyLine.substringBefore(' ')).size)
        assertEquals(16, signer.fingerprint.split(':').size)
        assertFailsWith<IllegalArgumentException> { AuthToken(ByteArray(21)) }
    }

    @Test fun socketPairCarriesBytesBothWays() = runBlocking<Unit> {
        val (a, b) = FakeAdbSocket.pair()
        a.write("CNXN".encodeToByteArray(), 0, 4)
        val buf = ByteArray(8)
        assertEquals(4, b.read(buf, 0, 8))
        assertEquals("CNXN", buf.copyOf(4).decodeToString())
        a.close()
        assertEquals(-1, b.read(buf, 0, 8))
    }

    @Test fun sessionPushPullHoldAndFailures() = runBlocking<Unit> {
        val blobs = MemoryBlobStore()
        val ref = blobs.add("{}".encodeToByteArray())
        val s = FakeTvSession(blobs = blobs::bytes)
        val dest = RemotePath(RemoteDir.OVERLAY, listOf("config.json"))
        assertEquals(ref.sha256, s.push(ref, dest).remoteSha256)
        assertContentEquals("{}".encodeToByteArray(), s.pull(dest, 1024))
        val h = s.hold(); assertEquals(1, s.openHolds); h.close(); h.close(); assertEquals(0, s.openHolds)
        s.failNext = AdbError.Transport.Timeout("push")
        assertEquals(AdbError.Transport.Timeout("push"), assertFailsWith<AdbException> { s.push(ref, dest) }.error)
        s.setState(ConnectionState.Idle)
        assertEquals(AdbError.Transport.Closed, assertFailsWith<AdbException> { s.pull(dest, 10) }.error)
    }

    @Test fun sessionManagerRefusesTheWrongTv() = runBlocking<Unit> {
        val m = FakeSessionManager()
        val s = FakeTvSession()
        m.register("192.0.2.10", s)
        m.allowPromptFor["192.0.2.10"] = "AB:CD"
        var prompted: String? = null
        assertIs<tv.cleanyour.core.session.OpenResult.Ok>(m.open(s.identity.let { tv.cleanyour.core.model.Endpoint.PlainTcp("192.0.2.10") }, s.tvId) { prompted = it })
        assertEquals("AB:CD", prompted)
        val wrong = m.open(tv.cleanyour.core.model.Endpoint.PlainTcp("192.0.2.10"), tv.cleanyour.core.model.TvId("ffffffffffffffff")) {}
        assertIs<AdbError.Policy.WrongTv>((wrong as tv.cleanyour.core.session.OpenResult.Failed).error)
    }

    @Test fun discoveryTracksCollectors() = runBlocking<Unit> {
        val d = FakeDiscovery()
        val tv = FoundTv("192.0.2.10", "Mi TV 4A", "MiTV-AXSO0", emptySet(), true, null, null, 0)
        d.emit(DiscoveryEvent.Found(tv))
        val job = launch { d.discover().take(2).toList() }
        yield()
        assertEquals(1, d.activeCollectors)
        d.emit(DiscoveryEvent.Lost("192.0.2.10"))
        job.join()
        assertEquals(0, d.activeCollectors)
    }

    @Test fun credentialGateMintsPasses() = runBlocking<Unit> {
        val clock = FakeClock()
        val gate = FakeCredentialGate(clock)
        val pass = (gate.confirm(Samples.miTvId(), "Confirm it's you", "Before we change anything on Mi TV 4A") as GateResult.Passed).pass
        assertEquals(clock.nowMs() + 600_000, pass.expiresAtMs)
        gate.mode = FakeCredentialGate.Mode.NO_SCREEN_LOCK
        assertEquals(GateMethod.NONE, (gate.confirm(Samples.miTvId(), "t", "s") as GateResult.NotAvailable).pass.method)
        assertEquals(2, gate.prompts.size)
    }

    @Test fun engineFollowsTheStartContract() = runBlocking<Unit> {
        val clock = FakeClock()
        val renderer = FakeOpRenderer()
        val engine = FakeEngine(clock, renderer)
        val session = FakeTvSession()
        val plan = Samples.plan(listOf(Op.Disable(PackageName("com.miui.tv.analytics"))), renderer = renderer)
        val gate = FakeCredentialGate(clock)

        assertEquals(StartResult.Refused(StartRefusal.HASH_MISMATCH), engine.start(plan, session, Samples.zeroHash, null))
        assertEquals(StartResult.Refused(StartRefusal.GATE_REQUIRED), engine.start(plan, session, plan.hash, null))
        val pass = gate.mint(plan.tvId)
        clock.advance(10 * 60 * 1000L)
        assertEquals(StartResult.Refused(StartRefusal.GATE_EXPIRED), engine.start(plan, session, plan.hash, pass))
        assertEquals(StartResult.Started, engine.start(plan, session, plan.hash, gate.mint(plan.tvId)))
        assertIs<RunState.Finished>(engine.runState(plan.tvId).first())

        val readOnly = Samples.plan(listOf(Op.DebugOff), renderer = renderer)
        assertEquals(StartResult.Started, engine.start(readOnly, session, readOnly.hash, null), "SESSION_END needs no pass (D17)")
    }

    @Test fun snapshotStoreKeepsBaselineAndPrunes() = runBlocking<Unit> {
        val store = MemorySnapshotStore()
        val base = Samples.snapshot(reason = SnapshotReason.BASELINE, takenAtMs = 1)
        store.save(base)
        (2L..30L).forEach { store.save(Samples.snapshot(reason = SnapshotReason.PRE_PLAN, takenAtMs = it)) }
        store.prune(base.tvId, keep = 20)
        assertEquals(base, store.baseline(base.tvId))
        assertEquals(30L, store.latest(base.tvId)?.takenAtMs)
        assertNull(store.load(base.tvId, Samples.snapshot(reason = SnapshotReason.PRE_PLAN, takenAtMs = 5).contentHash))
    }

    @Test fun httpHandlesRangesAndCaching() = runBlocking<Unit> {
        val http = FakeHttp(allowedHosts = setOf("github.com"))
        val body = ByteArray(100) { it.toByte() }
        http.serve("https://github.com/x.apk", body, etag = "\"v1\"")
        assertEquals(206, http.get("https://github.com/x.apk", "\"v1\"", 40).code)
        assertEquals(60L, http.get("https://github.com/x.apk", "\"v1\"", 40).length)
        assertEquals(200, http.get("https://github.com/x.apk", "\"v0\"", 40).code, "If-Range mismatch sends the whole file")
        assertEquals(304, http.get("https://github.com/x.apk", "\"v1\"", null).code)
        assertEquals(404, http.get("https://github.com/missing", null, null).code)
        assertFailsWith<IllegalArgumentException> { http.get("https://evil.example/x", null, null) }
    }

    @Test fun blobStoreAndFetcherVerifyHashes() = runBlocking<Unit> {
        val store = MemoryBlobStore(bundled = mapOf("launcher.apk" to ByteArray(3)))
        val bytes = "apk".encodeToByteArray()
        assertEquals(3L, store.bundled("launcher.apk").size)
        assertFailsWith<java.io.IOException> { store.put(sha256Of("other"), ByteArrayInputStream(bytes), 3) }
        val ref = tv.cleanyour.core.model.ArtifactRef("https://github.com/r.apk", 3, sha256Of(bytes), Samples.zeroHash)
        val fetcher = FakeArtifactFetcher(store, mapOf(ref.url to bytes))
        assertIs<tv.cleanyour.core.registry.FetchResult.Ok>(fetcher.fetch(ref) { _, _ -> })
        assertIs<tv.cleanyour.core.registry.FetchResult.Failed>(fetcher.fetch(ref.copy(size = 4)) { _, _ -> })
    }

    @Test fun journalTracksOpenRunsAndCrashes() = runBlocking<Unit> {
        val j = MemoryJournal()
        val tvId = Samples.miTvId()
        val id = tv.cleanyour.core.model.PlanId("01FAKEPLAN0000000000000009")
        j.append(tv.cleanyour.core.engine.JournalRecord.PlanStart(id, tvId, Samples.zeroHash, 1, Samples.zeroHash, "0.1.0", 0))
        assertEquals(listOf(id), j.openRuns(tvId))
        j.crashAfter = 0
        assertFailsWith<java.io.IOException> { j.append(tv.cleanyour.core.engine.JournalRecord.End(id, tv.cleanyour.core.engine.RunStatus.COMPLETED)) }
        assertEquals(1, j.read(id).size)
    }
}
