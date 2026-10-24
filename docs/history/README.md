# Engineering history

How 0.1 was built, kept as it was written. The app was built in parallel workstreams (W1 to W9 and
later fix streams) against frozen interfaces, with written reviews at each gate. These files record
the decisions, the interface changes and the review findings; they are not instructions for
contributors (see [CONTRIBUTING.md](../../CONTRIBUTING.md) for that).

The documents mention a product spec, an architecture and a security design by section number
(PRODUCT-SPEC 5.2, ARCHITECTURE 6.8, SECURITY 2.4 and so on). Those design documents were written
before the code and are not part of this repository; the code, its comments and the tests are the
reference now. "forge" is the remote Docker build host the `scripts/*-on-forge.sh` helpers drive.

| File | What it holds |
| :- | :- |
| [CONTRIBUTING-agents.md](CONTRIBUTING-agents.md) | The workstream ownership table and the RFC rule used while the streams ran in parallel |
| [DECISIONS-W1.md](DECISIONS-W1.md) | Foundation: toolchain pins, module graph rules, fakes, the APK size gate |
| [DECISIONS-W2.md](DECISIONS-W2.md) | ADB transport and the keystore backed signer |
| [DECISIONS-W3.md](DECISIONS-W3.md) | Ops, planner, executor, journal and the TV side watchdog |
| [DECISIONS-W4.md](DECISIONS-W4.md) | Probe, TV profiles and the launcher config codec |
| [DECISIONS-W5.md](DECISIONS-W5.md) | Trust: pins, the signed module index, downloads, keys and custody (K1, K2, K2R) |
| [DECISIONS-W6.md](DECISIONS-W6.md) | Restore points and the restore files |
| [DECISIONS-W8.md](DECISIONS-W8.md) | Flows, screens and the Android glue |
| [DECISIONS-W9.md](DECISIONS-W9.md) | Release pipeline, CI, end to end tests on emulators, benchmarks |
| [RFCS.md](RFCS.md) | Interface changes from the later streams (dock, curtain, cards, watchdog, profiles) |
| [rfcs/](rfcs/) | Interface changes proposed by W2, W3, W4 and W8 |
| [reviews/](reviews/) | Review reports: R1 to R5 per stream, RF-* for the later fix streams |
| [runbook-0.1.md](runbook-0.1.md) | The real device runbook for 0.1 and the release checklist |
| [W2-device-check.md](W2-device-check.md) | The one Allow, no second prompt device check |

Code comments refer to these files by name (for example "DECISIONS-W5.md, K2"); they all live here.
