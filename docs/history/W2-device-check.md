# W2 device check: one Allow, no second prompt (F1, F2)

The part of W2's definition of done that needs the user's Mi TV 4A and phone. Everything else is
proven on the fake adbd. Nobody but the user runs this, with the user present (BRIEF rule 7).
Every step is read-only: the only shell lines sent are `getprop` and, in part B, the app's probe.

Tick each line. Write the results (fingerprints, mode, tvId) into the I1 notes, never a serial.

## Before you start

- [ ] The TV is on, on the same Wi-Fi as the laptop and the phone, and its IP is known (TV
      Settings > Network). Call it `TV_IP` below.
- [ ] TV Settings > Device Preferences > About: tap Build seven times; Developer options > USB
      debugging ON and Network debugging (ADB over network) ON, so port 5555 is open.
- [ ] Nothing else is connected to the TV over adb (`adb disconnect` on the laptop).

## Part A: transport from the laptop (wire and session against the real adbd)

Runs `:adb:session`'s `RealTvCheck` on the JVM with a software key kept at
`~/.cleanyourtv-w2-check/adbkey.p8` (owner-only). It proves the handshake, the 20-byte AUTH
signature, shell v2 and the D37 identity against the real adbd. Run it from a clone of the branch
on the laptop (JDK 21), not on forge (forge is not on the TV's network).

1. [ ] First connect:

   ```bash
   ./gradlew :adb:session:test --tests '*RealTvCheck*' --rerun -Pcleanyourtv.realTv=TV_IP
   ```

   The output prints `W2-CHECK fingerprint XX:XX:...`. Within a few seconds the TV shows "Allow USB
   debugging?" with the same fingerprint.
   - [ ] The fingerprint on the TV equals the printed one, all 16 pairs.
   - [ ] Tick "Always allow from this computer" and choose OK within 120 s.
   - [ ] Output: `W2-CHECK connected: tvId <16 hex> model MiTV-... sdk 28 adbSecure true`, the
         features line contains `shell_v2`, `exec ... exit 0`, `first connect prompts: 1`,
         `second connect prompts: 0`, `W2-CHECK PASS`. The second connect is a new TCP
         connection with the expected TvId, so it also proves D37 on the real set.
2. [ ] Reconnect with the same key; this run must not raise the dialog at all:

   ```bash
   ./gradlew :adb:session:test --tests '*RealTvCheck*' --rerun -Pcleanyourtv.realTv=TV_IP -Pcleanyourtv.realTvExpectNoPrompt=1
   ```

   - [ ] No dialog on the TV; output `first connect prompts: 0`, `second connect prompts: 0`,
         `W2-CHECK PASS`.
3. [ ] Pin the D37 formula on the real set: on the laptop run
   `printf '%s%s' "$(adb -s TV_IP:5555 shell getprop ro.serialno | tr -d '\r')" "$(adb -s TV_IP:5555 shell getprop ro.product.device | tr -d '\r')" | shasum -a 256 | cut -c1-16`
   (after `adb connect TV_IP:5555`, which may raise one more dialog for the laptop's own adb key).
   - [ ] It prints the same 16 hex as the `tvId` in step 1. This is the value W6's `restore.sh`
         checks; a mismatch blocks I1.
4. [ ] Decline path: run step 1 with a fresh key file
   (`-Pcleanyourtv.realTvKey=/tmp/w2-deny.p8`) and choose Cancel on the TV.
   - [ ] The test fails with `OpenResult.Failed(error=Denied)` (or `PendingTooLong` if the TV only
         closes the dialog); nothing else is sent. Delete `/tmp/w2-deny.p8`.

Failure meanings: `Refused` or `Unreachable`, port 5555 is closed (network debugging off, wrong IP);
`NoShellV2`, the set lacks `shell_v2` (not expected on Android 9); `Protocol(...)` with
"TLS", the set wants wireless-debugging pairing (0.2); a prompt in step 2, the TV did not store the
key (Always allow not ticked) or the signature is wrong (it would then also fail step 1's PASS).

## Part B: the phone's Keystore key (F2, and F1 end to end)

Needs the internal debug APK with W8's screens (the I1 build) on the user's phone. The signer is
`KeystoreAdbSigner`; it provisions StrongBox, then TEE, then the wrapped software key, and uses a
path only after its self-test passes.

1. [ ] Fresh install of the APK; open it. Welcome lists the Mi TV within 8 s, or type `TV_IP`.
2. [ ] Connect. The phone shows a fingerprint; the TV shows "Allow USB debugging?" with the same
      fingerprint (all 16 pairs). Tick "Always allow", choose OK.
3. [ ] The app reaches "What we found" (probe only, nothing written).
4. [ ] Disconnect and connect again from the app: no dialog on the TV.
5. [ ] Kill the app from Recents, reopen, connect: no dialog on the TV.
6. [ ] App settings > About shows the key mode. Record it: `STRONGBOX`, `TEE` or
      `WRAPPED_SOFTWARE`, plus any provisioning notes (why a hardware path was skipped). Any of the
      three passes; `WRAPPED_SOFTWARE` goes into the I1 notes with its reasons.
7. [ ] `adb backup -f cyt.ab tv.cleanyour.app` from the laptop over USB to the phone yields an
      archive with no app data (allowBackup false).
8. [ ] App settings > Reset my key, then connect: the TV asks again, with a new fingerprint. Allow it.

## Afterwards

- [ ] Optional: TV Developer options > Revoke USB debugging authorizations removes the laptop's
      test key (and every other key, so the phone will be asked again once).
- [ ] Delete `~/.cleanyourtv-w2-check/` on the laptop when the I1 session is over.
- [ ] Network debugging stays as the user wants it; the app's own `debug_off` is an I2 step.
