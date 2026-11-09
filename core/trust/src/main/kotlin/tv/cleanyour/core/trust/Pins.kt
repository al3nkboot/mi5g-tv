package tv.cleanyour.core.trust

import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.store.BlobStore

// Trust anchors compiled into the app. Changing one is a reviewed code change, never a data file.
object TrustRoots {
    // Root of the module index chain: signs keys.json only (KeysFile). Release key K2R, generated
    // 2026-10-20 for 0.1.0; it replaced the development keys of K2, which never signed a public
    // build. The private half stays offline with the maintainer (docs/history/DECISIONS-W5.md, K2R).
    const val INDEX_ROOT_PUBLIC_KEY = "RWTBm+H9rjMuaUomcwuf9/CwnU9d1uhPoxDKO1hAMS9ILHGBQlhx7ZlY"   // key id 692E33AEFDE19BC1

    // The sequence of the keys.json bundled with this build: an older keys file is refused.
    const val BUNDLED_KEYS_SEQUENCE = 1L

    val indexRoot: MinisignKey get() = Ed25519Minisign.parsePublicKey(INDEX_ROOT_PUBLIC_KEY)

    fun indexVerifier(): MinisignIndexVerifier = MinisignIndexVerifier(indexRoot, keysFloor = BUNDLED_KEYS_SEQUENCE)
}

// The launcher APK bundled in the phone app (D13, Q1): upstream github.com/Gathin23/cleanyourtv at
// tag v2.5.0 (MIT), commit 9071ec8, plus the Mi5G 2.0 changes in third_party/cleanyourtv/launcher
// (tile menu, Control Center, button mapper, media pill, clock; Mi5G 2.2 channel-key and settings-clear toggles). `assembleRelease`, re-signed with the
// Mi5G release key (the original launcher key is not available), so SIGNER_SHA256 changed in 2.0. The verifyLauncherPin task (part of
// :core:trust check and of :android:platform preBuild) fails the build when the asset differs.
object PinnedLauncher {
    const val ASSET_NAME = "cleanyourtv-launcher.apk"
    const val UPSTREAM_COMMIT = "9071ec8d2f41826778f6b4c7f5f42dadc22f5b22"
    const val PACKAGE = "com.gathin.tvhome"
    const val VERSION_CODE = 10L
    const val VERSION_NAME = "2.8-mi5g"
    const val SIZE = 704889L
    const val SHA256 = "bbdd994c749514ac76e5af8afaaf1e8fe6352bbadf103ac2e0815cee9672eef4"
    // SHA-256 of the signing certificate (DER): the Mi5G release key, v2 and v3.
    const val SIGNER_SHA256 = "c1f317965f154fe5e79fe7483abd5dd5dec7df57d87f3a645a5fd12f39a5480c"
    // The 1.x signer ("O=cleanyourtv, CN=Clean Your TV launcher") of the launcher and the curtain.
    const val LEGACY_SIGNER_SHA256 = "576418154bec6505bfc709ccaa6ae85b6e09d98eb89f4a25147ca1b4b0505132"
}

// The TV curtain bundled in the phone app (RFC-CURTAIN-1): module :tv:curtain of this repo, built
// twice and compared, then signed with the launcher release key by scripts/build-curtain-on-forge.sh; re-signed
// unchanged with the Mi5G release key in 2.0 so both TV apps keep one signer.
// One signer to pin: SIGNER_SHA256 is the launcher's. The verifyCurtainPin task (part of :core:trust
// check and of :android:platform preBuild) fails the build when the asset differs.
object PinnedCurtain {
    const val ASSET_NAME = "cleanyourtv-curtain.apk"
    const val PACKAGE = "tv.cleanyour.curtain"
    const val ACTIVITY = ".CurtainActivity"
    const val RECEIVER = ".CueReceiver"
    const val VERSION_CODE = 1L
    const val VERSION_NAME = "0.1"
    const val SIZE = 70_246L
    const val SHA256 = "ea3c2559ac561c9549f5da18ba6f4eba724579a13064b00fa36e543f6389cf63"
    const val SIGNER_SHA256 = PinnedLauncher.SIGNER_SHA256
}

// LauncherPin over the BlobStore's bundled asset. bundled() refuses an asset whose bytes are not the
// pinned ones, so a swapped asset can never be offered as the launcher.
class BundledLauncherPin(private val blobs: BlobStore) : LauncherPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedLauncher.ASSET_NAME)
        check(blob.sha256.hex == PinnedLauncher.SHA256 && blob.size == PinnedLauncher.SIZE) {
            "bundled launcher does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedLauncher.PACKAGE),
            versionCode = PinnedLauncher.VERSION_CODE,
            versionName = PinnedLauncher.VERSION_NAME,
            sha256 = Sha256(PinnedLauncher.SHA256),
            signer = Sha256(PinnedLauncher.SIGNER_SHA256),
            blob = blob,
        )
    }
}

// CurtainPin over the BlobStore's bundled asset, refusing an asset whose bytes are not the pinned ones.
class BundledCurtainPin(private val blobs: BlobStore) : CurtainPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedCurtain.ASSET_NAME)
        check(blob.sha256.hex == PinnedCurtain.SHA256 && blob.size == PinnedCurtain.SIZE) {
            "bundled curtain does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedCurtain.PACKAGE),
            versionCode = PinnedCurtain.VERSION_CODE,
            versionName = PinnedCurtain.VERSION_NAME,
            sha256 = Sha256(PinnedCurtain.SHA256),
            signer = Sha256(PinnedCurtain.SIGNER_SHA256),
            blob = blob,
        )
    }
}
