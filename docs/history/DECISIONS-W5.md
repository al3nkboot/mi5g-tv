# W5 decisions: trust, module index, downloads, launcher pin

Stream W5, 2026-10-19. Built against product spec section 5.11 and 5.13 as frozen by gate R0. No
frozen signature changed; no RFC was needed.

## What lives where

| Piece | Module | Notes |
| :- | :- | :- |
| `Ed25519Minisign` (`Minisign`) | `:core:trust` | minisign verify with Bouncy Castle's lightweight Ed25519 and BLAKE2b; adapted from the spike's S8 code by the same author; bounded inputs |
| `KeysFile`, `IndexDocument` | `:core:trust` | strict schema 1 readers for `keys.json` and `index.json` (formats in the KDoc-style comments of each file) |
| `MinisignIndexVerifier` (`IndexVerifier`) | `:core:trust` | the chain of trust and the fixed failure order (below) |
| `TrustRoots`, `PinnedLauncher`, `BundledLauncherPin` (`LauncherPin`) | `:core:trust` | compiled anchors: index root key, bundled keys sequence, launcher pin |
| `DownloadHosts` | `:core:trust` | the D41 host allowlist, used by the index reader, the artifact fetcher and the HTTP client |
| `IndexRegistry` (`Registry`) | `:core:registry` | the index state machine |
| `ResumableArtifactFetcher` (`ArtifactFetcher`) | `:core:registry` | sha256 and size while streaming, Range plus If-Range resume, optional signer check |
| `UrlConnectionFetcher` (`HttpFetcher`) | `:core:registry` | see D-W5-1 |
| `DiskBlobStore` (`BlobStore`, `PartialStore`) | `:core:registry` | content-addressed cache, LRU by mtime, 300 MB cap; see D-W5-1 |
| `FileBlobStore`, `BundledAssets` | `:android:platform` | `DiskBlobStore` over `cacheDir/artifacts`, `filesDir/bundled` and `assets/bundled/` |
| `PackageManagerApkInspector` (`ApkInspector`), `SignerDigests` | `:android:platform` | `GET_SIGNING_CERTIFICATES`, Android 9 `GET_SIGNATURES` fallback |
| Launcher APK, seed index, keys | `android/platform/src/main/assets/bundled/` | `cleanyourtv-launcher.apk`, `index.json(.minisig)`, `keys.json(.minisig)` |
| `scripts/build-launcher-on-forge.sh`, `scripts/sign-seed-index.py` | `scripts/` | launcher build and signing; seed re-dating and signing |

## Decisions

- D-W5-1 `UrlConnectionFetcher` and the blob store logic (`DiskBlobStore`) are pure `java.net` and
  `java.io`, so they live in `:core:registry` and are tested on the JVM (a real HTTPS server, a real
  directory). `:android:platform` holds only what needs Android: `FileBlobStore` (directories and
  assets) and `PackageManagerApkInspector`. CONTRIBUTING lists all three under `:android:platform`;
  the ownership is unchanged (W5), only the module is lower.
- D-W5-2 Verifier order, one F9 copy line each: keys file (root signature, shape, not older than the
  bundled keys sequence) `BAD_KEYS_FILE`; index signature key revoked `REVOKED_KEY`, not listed (the
  root included: it signs `keys.json` only) `UNKNOWN_KEY`, not verifying (including an edited trusted
  comment) `BAD_SIGNATURE`; index shape `MALFORMED`; sequence lower than the last accepted
  `ROLLBACK` (equal is accepted, a refetch of the same index); now at or past `expires` `EXPIRED`.
- D-W5-3 `nowMs = MinisignIndexVerifier.VERIFY_IGNORING_EXPIRY` checks signatures, keys and shape
  but not expiry. The registry uses it to re-check stored and bundled copies and then decides
  staleness itself, so an expired copy is still shown ("may be out of date") rather than lost.
- D-W5-4 Staleness is the index's own expiry: `Ready.stale` is true at or after `expires`.
  `IndexState.installableIndex(nowMs)` returns the index only for `Ready`, not stale and before its
  expiry at `nowMs` (the stale flag is fixed when the state is published, so the expiry is checked
  again at the call; `IndexRegistry.installableIndex()` does this with the registry's clock); a stale list, or
  the last list behind a `Failed` state, is shown (`shownIndex()`) but not installed from. This is
  SECURITY S2's "disables privileged ops until refreshed".
- D-W5-5 Replays: the index sequence and the keys sequence are high-water marks stored with the
  cached copy (one atomic document, `registry/index-cache.json`). A fetched `keys.json` older than
  the accepted one is `BAD_KEYS_FILE` even when root signed, so an old keys file that still lists a
  since-revoked key as active cannot bring that key back. The keys floor for a fresh install is
  `TrustRoots.BUNDLED_KEYS_SEQUENCE`.
- D-W5-6 Revocation: `index.json` `revoked` lists sha256 digests. A module whose signer digest is
  listed reads as `Tier.REVOKED`; a listed APK digest rules out that version only.
  `VerifiedIndex.installable(module)` gives the newest version that is neither, and nothing for a
  `REVOKED` or `QUARANTINED` module. `VerifiedIndex.revocations` stays the raw list, so W8 can check
  installed packages against it.
- D-W5-7 `post_install` is parsed for shape and dropped (`ModuleVersion.postInstall` is always
  empty in 0.1, R0 "deliberately left open").
- D-W5-8 Index text fields reject control characters and Unicode direction overrides and isolates
  (a look-alike guard for names on the phone); every URL that is fetched must pass `DownloadHosts`
  (https, exact host, port 443, no user info); `source` must be https. Limits: index 1 MiB, keys
  64 KiB, signature 4 KiB, 500 modules, 20 versions, 64 permissions, APK 200 MiB.
- D-W5-9 Fetcher semantics: an existing blob with the right digest is returned without a request;
  a partial without an ETag is never resumed (a resume could splice two different files); `200`
  for a range request (server ignored Range, or If-Range did not match) restarts from byte 0; `416`
  drops the partial and restarts; a cut stream keeps the partial and resumes up to three times per
  call and again on the next call. With an inspector, a signer that is not the index's (or an APK
  the inspector cannot read) is `SIGNER_MISMATCH` and the blob is deleted.
- D-W5-10 HTTP client: redirects by hand (at most 5), every hop checked against the allowlist before
  connecting; `Accept-Encoding: identity`; a fixed `User-Agent: Mozilla/5.0` (no phone model, no app
  version; the default Android agent names the model). Network errors are `OFFLINE`. A TLS failure
  (as with a proxy's user CA) is `HTTP_ERROR` with no code, because `FetchFailure` has no TLS case;
  the copy for it can say the download could not be checked. Adding `FetchFailure.TLS` would be an
  RFC; not needed for 0.1.
- D-W5-11 `BlobRef.path` from `FileBlobStore` is always an absolute file path: bundled assets are
  copied once into `filesDir/bundled/<sha256>`, so the session (W2) can stream any blob from a file.
- D-W5-12 `PackageManagerApkInspector` refuses (null) an APK with more than one signer or none;
  `ApkInfo.method` says whether `GET_SIGNING_CERTIFICATES` or the Android 9 `GET_SIGNATURES`
  fallback produced the digest. With a v3 rotation, the current signer is used; the legacy API
  would report the oldest certificate, which is why the fallback is named in `method`.

## Keys and custody

- K1 Launcher release key (Q1). RSA 4096, self-signed, `O=cleanyourtv, CN=Clean Your TV launcher`,
  valid to 2056-10-11, PKCS12, generated 2026-10-19 on the maintainer's machine with OpenSSL, random 33-byte password.
  Signer certificate sha256 `576418154bec6505bfc709ccaa6ae85b6e09d98eb89f4a25147ca1b4b0505132`.
  Keystore and password only in the maintainer's offline key store (directory 700,
  files 600); never in the repo. Signing happens in a throwaway container on the build host: the keystore
  and password travel over ssh stdin into the container's `/dev/shm` and are removed after signing,
  so they never touch the build host's disk. The user should keep an offline copy (password manager plus a
  backup); losing the key means the next launcher build needs another guarded reinstall (D36).
- K2 Module index keys, DEVELOPMENT ONLY (retired by K2R). minisign 0.12 keys generated 2026-10-19, each
  encrypted with its own random password: root `EE792DBF95AA99A9` (signs `keys.json`; its public key
  is compiled in `TrustRoots.INDEX_ROOT_PUBLIC_KEY`) and index key `B63EB88CF5C83556` (signs
  `index.json`; listed active in `keys.json` sequence 1). Secret keys and passwords only in the
  maintainer's key store. These keys must be rotated before any public
  release: a new root generated offline by the owner, a new index key held where the published index
  is built, a new `keys.json`, a new compiled root and an app update (a root change always needs one).
- K2R Release module index keys (2026-10-20, before 0.1.0 went public). A new minisign root
  `692E33AEFDE19BC1` (compiled in `TrustRoots.INDEX_ROOT_PUBLIC_KEY`) and a new index key
  `0190B42669E69FB0` (active in `keys.json` sequence 1, signed by the new root), each encrypted with
  its own random password and kept offline with the maintainer, never in CI. The K2 development keys
  never signed anything a public build trusts. The seed was re-signed at sequence 2 with a 90 day
  expiry (K3 as amended).
- K3 The seed index expires 14 days (90 days since K2R, `--days` to change) after it is signed (S2). A build more than 14 days after the
  last signing ships a stale seed (shown, not installable until a refresh succeeds). Re-date and
  re-sign with `scripts/sign-seed-index.py index` before an internal build; it raises the sequence.
- K4 Retro facts in the seed were read from the published release, not copied: release v0.1.0
  asset `retro-release.apk`, 3,663,466 bytes, sha256
  `a3bcf445e1062d9e905b1d21b347236a6831e9ace6bfa1e76f9d154acf2d9db8` (downloaded and hashed, and
  GitHub's asset digest agrees), signer `0202281bb726dbc12b82622dee50a278656128a82204db3cf055f8f5c006e405`
  (`apksigner verify` printing certificates, one signer, v2), package `io.github.udhaykumarbala.retro`,
  versionCode 1, versionName 0.1.0, minSdk 26, ABIs arm64-v8a and armeabi-v7a, permissions INTERNET,
  READ_EXTERNAL_STORAGE and its own DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (`aapt2 dump badging`).

## The bundled launcher

Upstream `github.com/Gathin23/cleanyourtv` at `1bbf0c939fa7d1c0ed91c5fec37ddb57e749a3fd` (main,
module cards merged), `launcher/` built with its own wrapper (Gradle 8.9, AGP 8.5.2) by
`./gradlew assembleRelease` in `eclipse-temurin:17-jdk` on forge, under the shared build lock; the
R8-minified release APK re-signed with K1 (v2 and v3, no v1, no v4). Result: `com.gathin.tvhome`
versionCode 2, versionName 2.0, 270,885 bytes, sha256
`26461a1fd802e6f2f3ec2200171dd9bac2cfafe069cc07754464d903216a6897`. `PinnedLauncher` holds these
values; `:core:trust:verifyLauncherPin` (a JavaExec over the test classpath with apksig) checks
size, sha256, a valid v2 signature and the single signer digest, runs with `check`, and runs
before every `:android:platform` build (`preBuild` depends on it). `BundledLauncherPin.bundled()`
refuses an asset whose bytes are not the pinned ones at run time.

The submodule moved from `7205769` to `1bbf0c9` with it, so W1's fixture count of upstream examples
went from 26 to 30 (FixturesLoadTest) and W4's profile conversion sees four more examples.

Launcher 2.4 (2026-10-20, branch launcher240, LAUNCHER-240-PLAN): upstream tag v2.4.0, commit
`69c93b66a75c70a9e386adff327b6a51c12addc5`. `scripts/build-launcher-on-forge.sh` now builds twice in
fresh copies and fresh containers, re-signs both with K1 and requires the two signed APKs to be byte
for byte identical (upstream's raw release APKs differ only in AGP's own signing block). Result:
`com.gathin.tvhome` versionCode 5, versionName 2.4, 386,581 bytes, sha256
`0e46460e9fb69ace2e19c89cd87f01907b8b98f0949e6de410a9dbfe2a407936`, signer unchanged (K1). A TV with
our 2.0 is updated in place (same signer, `pm install -r`), by a Setup run or by "Update the home
screen" in Manage (D4). The submodule's examples went from 30 to 93; the 63 without curated metadata
are listed in `core/spec/upstream-feedback/unconverted.txt` and skipped (D2).

## Wiring notes for W8 (AppGraph)

```kotlin
val blobs = FileBlobStore(context)
val inspector = PackageManagerApkInspector(context)
val launcherPin = BundledLauncherPin(blobs)
val http = UrlConnectionFetcher()
val registry = IndexRegistry(http, documentStore, clock, BundledAssets.seedIndex(context), sources = null, TrustRoots.indexVerifier())
val artifacts = ResumableArtifactFetcher(http, blobs, blobs, inspector)
// at start: registry.restore(); on "Add a card": registry.refresh(); install only from registry.installableIndex()
```

- Never install from `registry.module(id)`: it also answers from a stale or failed list. Install
  only from `registry.installableIndex()` and `VerifiedIndex.installable(module)`, and compare the
  downloaded APK's package and versionCode (`ApkInfo`) with the index entry (SECURITY S1).
- `sources = null` until a published copy exists; then `IndexSources.under("https://<allowed host>/...")`.
- The network security config must allow exactly the `DownloadHosts.ALL` hosts with system trust
  anchors only and cleartext off (SECURITY 4.4).

## Tests

- `:core:trust`: `MinisignTest` (spike vectors made outside Kotlin, edits, swaps, bounds),
  `IndexVerifierTest` (the fixture matrix: good, bad signature, wrong key, revoked key, rotation,
  expired, rollback, replay, tampered keys, keys signed by another key, keys older than the floor,
  root signing the index, malformed, failure order, revocation by signer and by APK, quarantine),
  `IndexDocumentTest` (hostile documents, host rules), `SeedIndexTest` (the shipped files, signed by
  the real minisign tool, verify against the compiled root), `LauncherPinTest`.
- `:core:registry`: `IndexRegistryTest` (state machine with FakeHttp and real signatures),
  `ArtifactFetcherTest` (FakeHttp: Range, If-Range, 200 instead of 206, hash and size mismatch,
  416, hosts, offline, resume across calls, signer), `DiskBlobStoreTest`, `UrlConnectionFetcherTest`.
- `:android:platform`: `SignerDigestsTest`.

MITM proof: mitmproxy is not installed on the maintainer's machine or the build host, so `UrlConnectionFetcherTest` shows the
same property on the JVM: a local HTTPS server whose certificate comes from a freshly made user CA
serves a client that trusts that CA (control), and the fetcher as shipped (platform trust only)
fails the handshake with no request reaching the server. The on-device mitmproxy check stays in the
release checklist (7.3 item 6) for the user's phone.
