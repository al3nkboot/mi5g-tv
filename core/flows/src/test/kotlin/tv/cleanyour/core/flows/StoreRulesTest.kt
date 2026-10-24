package tv.cleanyour.core.flows

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.engine.JournalRecord
import tv.cleanyour.core.engine.RunStatus
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.GroupDefault
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageEntry
import tv.cleanyour.core.model.PackageGroup
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.planner.PlanKind
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult
import tv.cleanyour.core.registry.IndexState
import tv.cleanyour.core.trust.VerifiedIndex
import tv.cleanyour.testing.fixtures.FakeArtifactFetcher
import tv.cleanyour.testing.fixtures.FakeProfileSource
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.testing.fixtures.sha256Of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// STORES-PLAN 1: the machines under the Play build's rules (no update check, no add-ons) offer
// neither, and run neither when asked; the full build's rules are the defaults and change nothing.
@OptIn(ExperimentalCoroutinesApi::class)
class StoreRulesTest {

    @Test fun theRulesOfTheTwoBuilds() {
        assertEquals(StoreRules(updateCheck = true, addOns = true, updatesOff = true), StoreRules.FULL)
        assertEquals(StoreRules(updateCheck = false, addOns = false, updatesOff = false), StoreRules.PLAY)
        assertEquals(StoreRules.FULL, StoreRules())
    }

    @Test fun thePlayBuildHasNoUpdateCheck() = runTest(timeout = 30.seconds) {
        val h = Harness()
        var asked = 0
        val deps = h.manageDeps(store = StoreRules.PLAY, updates = UpdateChecker { asked++; "9.9.9" })
        val m = AppSettingsMachine(deps, backgroundScope)
        settle()
        assertFalse(m.state.value.updateCheck)
        assertFalse(m.state.value.addOns)
        m.dispatch(AppSettingsIntent.CheckUpdates); settle()
        assertEquals(UpdateCheck.Idle, m.state.value.update)
        assertEquals(0, asked)

        val full = AppSettingsMachine(h.manageDeps(), backgroundScope)
        settle()
        assertTrue(full.state.value.updateCheck && full.state.value.addOns)
        full.dispatch(AppSettingsIntent.CheckUpdates); settle()
        assertEquals(UpdateCheck.Available("0.2.0"), full.state.value.update)
    }

    @Test fun thePlayBuildDoesNotOpenCardsAndModules() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = h.knownTv()
        val play = ManageMachine(h.manageDeps(store = StoreRules.PLAY), backgroundScope, null)
        settle()
        assertFalse(play.state.value.addOns)
        play.effects.test {
            play.dispatch(ManageIntent.Open(ManageSection.MODULES)); settle()
            assertTrue(drain().isEmpty())
        }
        // Every other section still opens.
        play.effects.test {
            play.dispatch(ManageIntent.Open(ManageSection.SWITCHED_OFF)); settle()
            assertEquals(ManageEffect.Navigate("switchedoff/${tv.id.value}"), drain().single())
        }
        val full = ManageMachine(h.manageDeps(), backgroundScope, null)
        settle()
        assertTrue(full.state.value.addOns)
        full.effects.test {
            full.dispatch(ManageIntent.Open(ManageSection.MODULES)); settle()
            assertEquals(ManageEffect.Navigate("modules/${tv.id.value}"), drain().single())
        }
    }

    @Test fun thePlayBuildNeverRefreshesTheListOrDownloadsAnAddOn() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val apk = "retro apk bytes".encodeToByteArray()
        val signer = sha256Of("signer".encodeToByteArray())
        val ref = ArtifactRef("https://github.com/x/retro.apk", apk.size.toLong(), sha256Of(apk), signer)
        val version = ModuleVersion(1, "0.1.0", "2026-10-03", ref, emptyList(), "", 21, emptyList(), emptyList())
        val retro = Module("retro", "Retro", "NES", "A card", PackageName("io.github.example.retro"), "card", Tier.COMMUNITY,
            "example", "https://github.com/example/retro", "MIT", null, null, listOf(version))
        h.registry.onRefresh = { IndexState.Ready(VerifiedIndex(1, Long.MAX_VALUE, listOf(retro), emptyList()), stale = false, fetchedAtMs = 0) }
        val tv = h.knownTv()
        val fetcher = FakeArtifactFetcher(h.blobs, mapOf(ref.url to apk))
        val m = ModulesMachine(h.manageDeps(fetcher, store = StoreRules.PLAY), backgroundScope, tv.id)
        settle()
        m.dispatch(ModulesIntent.Refresh); settle()
        m.dispatch(ModulesIntent.Add("retro")); settle()
        m.dispatch(ModulesIntent.Update("retro")); settle()
        assertEquals(0, h.registry.refreshes)
        assertTrue(fetcher.fetched.isEmpty())
        assertTrue(h.planner.requests.none { it is PlanRequest.AddModule })
        assertEquals(null, m.state.value.install)
    }

    // RF-STORES-030 M2: at the Custom level, Switched off lists the profile's ask groups with Off and
    // Kept pills. The Play build keeps the TV's updaters on, so an updater (by name, or in the
    // "Software updates" group) gets no pill there, and putting back one an earlier full build had
    // switched off offers no Undo. The full build offers both, as before.
    @Test fun thePlayBuildOffersNoUpdatesOffInSwitchedOff() = runTest(timeout = 30.seconds) {
        val ota = PackageName("com.mitv.ota")          // an updater by its name
        val helper = PackageName("com.mitv.helper")    // an updater only by its group
        val voice = PackageName("com.google.android.katniss")
        val on = PackageState(EnabledState.ENABLED, true, 1, null, false, false, null, false)
        val off = on.copy(enabled = EnabledState.DISABLED_USER)
        val profile = miProfile.copy(groups = listOf(
            PackageGroup("updates", "Software updates", GroupDefault.ASK, listOf(PackageEntry(ota, null), PackageEntry(helper, null))),
            PackageGroup("voice", "Voice assistant", GroupDefault.ASK, listOf(PackageEntry(voice, null))),
        ))

        suspend fun customTv(h: Harness) = h.knownTv().copy(level = Level.CUSTOM).also { t ->
            h.tvs.upsert(t)
            h.snapshots.save(Samples.snapshot(packages = Samples.miTvPackages(emptySet()) + mapOf(ota to off, helper to on, voice to on), takenAtMs = Samples.NOW_MS + 1_000))
            // An earlier full build switched ota off.
            val plan = h.plan(listOf(Op.Disable(ota)), id = "01FAKERUN0000000000000009")
            h.ledger.record(plan)
            h.journal.append(JournalRecord.PlanStart(plan.id, t.id, Samples.zeroHash, 1, plan.hash, "0.2.0", 0))
            h.journal.append(JournalRecord.Begin(plan.id, 0, Op.Disable(ota), listOf("fake"), null, 10))
            h.journal.append(JournalRecord.Done(plan.id, 0, 0, true, 5))
            h.journal.append(JournalRecord.End(plan.id, RunStatus.COMPLETED))
            val previous = h.planner.respond
            h.planner.respond = { r, snap ->
                if (r is PlanRequest.PutBack) PlanResult.Ok(h.plan(listOf(Op.Enable(r.pkg)), kind = PlanKind.PUT_BACK, id = "01FAKEPUTB00000000000000001"))
                else previous(r, snap)
            }
        }

        for (store in listOf(StoreRules.PLAY, StoreRules.FULL)) {
            val h = Harness()
            val tv = customTv(h)
            val m = SwitchedOffMachine(h.manageDeps(store = store, profiles = FakeProfileSource(listOf(profile))), backgroundScope, tv.id)
            settle()
            val s = m.state.value
            // The switched-off updater is listed with Put back in both builds.
            assertEquals(listOf(ota), s.groups.flatMap { g -> g.rows.map { it.pkg } }, store.toString())
            val pills = s.askGroups.flatMap { g -> g.rows.map { it.pkg } }.toSet()
            if (store == StoreRules.PLAY) {
                assertEquals(setOf(voice), pills)
                assertTrue(s.askGroups.none { it.id == "updates" })
            } else {
                assertEquals(setOf(ota, helper, voice), pills)
            }

            m.effects.test {
                m.dispatch(SwitchedOffIntent.PutBack(ota)); settle()
                val snack = assertIs<ManageEffect.Snackbar>(drain().single())
                assertEquals(SnackKind.PUT_BACK, snack.kind)
                assertEquals(store.updatesOff, snack.undo, store.toString())
            }
            // The snackbar's Undo and an Off pill for an updater change nothing in the Play build.
            val before = h.planner.requests.size
            m.dispatch(SwitchedOffIntent.Undo(ota)); settle()
            m.dispatch(SwitchedOffIntent.SetOff(helper, true)); settle()
            val asked = h.planner.requests.drop(before).filterIsInstance<PlanRequest.SwitchOff>()
            if (store == StoreRules.PLAY) assertTrue(asked.isEmpty(), asked.toString())
            else assertEquals(listOf(setOf(ota), setOf(helper)), asked.map { it.pkgs })
            // Any other ask row still switches off.
            m.dispatch(SwitchedOffIntent.SetOff(voice, true)); settle()
            assertTrue(h.planner.requests.any { it == PlanRequest.SwitchOff(setOf(voice)) }, store.toString())
        }
    }
}
