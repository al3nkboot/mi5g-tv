## What and why

_What does this change, and why? Link the issue it fixes._

## How it was tested

_Commands run (`./gradlew check`, `verifyRoborazziDebug`, ...), and on which TV or emulator if any._

## Checklist

- [ ] `./gradlew check` passes, and `verifyRoborazziDebug` if a screen changed (changed goldens are committed and I looked at each one)
- [ ] Tests cover the change (a bug fix starts with a failing test)
- [ ] Every change to a TV stays reversible, goes through the plan the user sees ("Show commands") and respects the protected list and the watchdog
- [ ] No new network host, permission, dependency or pin, or it is called out below with the reason
- [ ] No keys, passwords, tokens, or a real TV's serial, MAC or IP address in code, fixtures, logs or screenshots
- [ ] No em dashes or double hyphens in text, comments or commit messages

## Notes for the reviewer

_Interface, module rule or pin changes; anything risky._
