# Module card test fixtures (internal, not published)

Tiny plain Java Android TV apps that declare a module card, one per scenario, built from
`project/` (one product flavor each). Only the sources, this table and the digests are kept in
this repo: the APKs are build outputs and the throwaway keys stay outside git (ARCHITECTURE 6.8),
so CI rebuilds them when a stream needs them (W5, W9). Release builds, not
shrunk, signed with the throwaway keys in `../keys` (key A: `keyA.jks`, key B: `keyB.jks`, both
with password `fixture`). Signer digests and APK hashes are in `digests.txt` next to the APKs:

- key A signer SHA-256: `dacc2ee9eab15834d58c633e955d268e88bf11f8df57593177cc8f3029d6e39d` (prompt shows `dacc 2ee9 eab1`)
- key B signer SHA-256: `ec8f5bf71162d27a7cd869eaeb648728b2be40daa446726ea23dfbc98bbf9c96` (prompt shows `ec8f 5bf7 1162`)

Install with `pm install -r <apk>` over ADB (or `tools/b64push.py` + `pm install`), press Home,
and read rejections with `logcat -s TvHomeCards`.

| APK | Package | App label | Card | Key | Expected launcher behaviour |
| :- | :- | :- | :- | :- | :- |
| `spoof.apk` | `io.example.spoof` | Retro | label `Retro`, caption, accent | A | With Retro (`io.github.udhaykumarbala.retro`, app label "Retro") installed: no prompt. The spoof card is rejected (`label 'Retro' is the name of another installed app`). If Retro's card was already Shown or Hidden by the owner it stays exactly as it was (a decided card is never displaced); if Retro's card was still pending, both pending cards clash and neither is asked about until one is uninstalled. The spoof shows as an ordinary tile with its own banner. Without Retro installed: a normal prompt for `io.example.spoof`. |
| `spoofvariant.apk` | `io.example.spoof` (versionCode 2) | Retro | label `Retro Games` | A | `pm install -r` over `spoof.apk` works (same key). Press Home: still no prompt, log line `app label 'Retro' is the name of another installed app` (the app label collides even though the card label no longer does). |
| `spoofvariant2.apk` | `io.example.spoof` (versionCode 3) | Spoof | label `Retro Games` | A | `pm install -r` over the previous one. Press Home: the prompt appears for `io.example.spoof` with its own package and digest, saying "The card is named 'Retro Games'. The app is 'Spoof'." Keep hidden: the app keeps its ordinary tile, no prompt on the next Home press. |
| `cardtesta.apk` | `io.example.cardtest` | Card Test | label `Card Test`, caption `Signer change test`, accent `#3D8BE8` | A | Prompt, Keep hidden or Show card as chosen; the record is pinned to key A. |
| `cardtestb.apk` | `io.example.cardtest` | Card Test | same card | B | `pm install -r` over the key A build must fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (Android's own check). After `pm uninstall io.example.cardtest` and installing this build: the prompt says "Card Test was updated with a different signing key. Show its card again?", Keep hidden focused, the card not shown even if the record said show. Show card: new record with the key B digest. Reinstalling the key A build (uninstall first) prompts again. |
| `deep.apk` | `io.example.deep` | Deep XML | `<card>` with children nested four deep | A | No prompt, no crash, one log line `XML nested deeper than 2`; the app shows as an ordinary tile. |
| `biglabel.apk` | `io.example.biglabel` | Big Label | label of 40 characters | A | No prompt, no crash, one log line `label is longer than 24 characters`; ordinary tile. (A 40 KB attribute cannot be built: aapt2 replaces it with STRING_TOO_LARGE, which the launcher rejects on the character rule instead; the 40 KB case is covered by the JVM unit tests only.) |
| `reservedverified.apk` | `io.example.verified` | Verified Demo | label `Verified Retro` | A | No prompt, log line `label 'Verified Retro' is reserved`; ordinary tile. |
| `reservedsettings.apk` | `io.example.settingsname` | Settings Demo | label `Settings` | A | No prompt, log line `label 'Settings' is reserved`; ordinary tile. |
| `reservedhdmi.apk` | `io.example.hdminame` | HDMI Demo | label `HDMI 2` | A | No prompt, log line `label 'HDMI 2' is reserved`; ordinary tile. |
| `bidi.apk` | `io.example.bidi` | `Bidi<U+202E> Label<newline>Two lines` | label `Bidi Test`, caption | A | Prompt with the title "New card from Bidi Label Two lines" as plain text on one line (bidi override and newline removed), "The card is named 'Bidi Test'. The app is 'Bidi Label Two lines'.", the whole package on the identity line (up to two lines). |
| `plaincollide.apk` | `io.example.plaincollide` | Card Test | none (no card filter; the meta-data is inert) | A | With cardtest's card Shown: press Home, the card is still shown and still in the Module cards list; this app is an ordinary tile named "Card Test". With cardtest still pending: cardtest's prompt is suppressed (`label 'Card Test' is the name of another installed app`) until this app is uninstalled. |
| `cardclash.apk` | `io.example.cardclash` | Clash Demo | label `Card Test`, caption | A | With cardtest's card decided (Shown or Hidden): no prompt for this app, one log line (`label 'Card Test' is the name of another installed app`, since cardtest's app label is also "Card Test"; a clash between two cards whose app labels differ logs `another card is also named`), cardtest unchanged. With cardtest pending too: both are dropped, nobody is asked; uninstall one and the other is asked. |
| `many1.apk` to `many9.apk` | `io.example.many1` to `many9` | Many 1 to Many 9 | labels `Many 1` to `Many 9`, captions, accents | A | Nine cards: nine prompts chained after one Home press (Back on each records Keep hidden). Set all nine to Shown in the Module cards list: the list scrolls inside the panel, Down from the last row stays put, Back closes it and the D-pad continues from the Settings pill; toggling a row never moves the glass or scrolls the grid. Eight cards render (Many 1 to Many 8, by label); Many 9 is listed as Shown but keeps its ordinary tile (the limit is 8). |

All fixtures declare the card on the same activity as their `LEANBACK_LAUNCHER` filter, exported
and enabled, so the only reason each one is rejected is the one named above.

Hold OK on Settings while any fixture is installed and accepted (cardtest, spoofvariant2, bidi, many): the
Module cards list shows its label, package, full digest and state. The rejected fixtures never
appear in the list.

`spoof-xmltree.txt` is `aapt2 dump xmltree` of the compiled `res/xml/module_card.xml` in
`spoof.apk`: it shows how the resource compiler stores `spec` (integer) and `accent` (colour),
the forms the launcher's parser accepts.
