<!--
Not submitted yet, on purpose. F-Droid's Inclusion Policy asks that a new app "should have been
actively developed for at least 3 months", unless it is proven to have some users. This app's
first commit is 2026-10-19, so the merge request waits until 2027-01-19, or until there is public
evidence of users (release download counts, stars, issues opened by other people), whichever comes
first. Reviewers close new-app requests that come earlier.

The boxes below are ticked only where they are true today. Before submitting, untick anything
that is no longer true and tick the rest as it becomes true: the 3-month mark or user evidence
(inclusion criteria), the guides read, the merge request branch, the v0.3.1 tag and its full commit
hash in the metadata, only the newest build entry kept, and the pipeline.
-->

# fdroiddata merge request: New app: Clean Your TV

The text below goes into the "New App" merge request on
[fdroiddata](https://gitlab.com/fdroid/fdroiddata), together with `metadata/tv.cleanyour.app.yml`
(the draft is [tv.cleanyour.app.yml](tv.cleanyour.app.yml) next to this file). It follows the
current template (`.gitlab/merge_request_templates/Default.md`) section for section, with every
checklist line kept as it is; only the template's HTML comments and the `Closes rfp#` line (there
is no RFP issue) are left out.

Before submitting:

1. Tag the release, publish its signed APK on GitHub, and check that the `fdroid-check` workflow
   passed on that tag with "match" (`gh workflow run fdroid.yml --ref vX.Y.Z` after the Release
   is published).
2. In the metadata, keep only the newest build entry, replace its `commit: vX.Y.Z` with the full
   commit hash of the tag (`fdroid rewritemeta` also drops the placeholder comment above it), and
   check that CurrentVersion and CurrentVersionCode match it.
3. Run `fdroid readmeta`, `fdroid rewritemeta tv.cleanyour.app` and `fdroid lint tv.cleanyour.app`
   in the fdroiddata fork.
4. Commit only `metadata/tv.cleanyour.app.yml` on a new, unprotected branch of the fork, with the
   message `New app: Clean Your TV`, then paste the title and description below.

Title:

```
New app: Clean Your TV
```

Description (paste from the next line down to `/label`):

```markdown
## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

Clean Your TV cleans up an Android TV or Google TV from an Android phone, over Wi-Fi. The app finds the TV, shows how to switch on network debugging with the remote, and connects to the TV's ADB over the local network (the TV asks the user to allow the phone). It reads what is installed, shows a plan (what it would switch off and what it keeps), and on "Make it clean" saves a restore point, switches off the unwanted pre-installed apps with `pm disable-user` (never uninstall) and installs a clean home screen on the TV. Every change can be undone from the app, one app at a time or all at once. No root, no Shizuku, no computer.

<img src="https://raw.githubusercontent.com/udhaykumarbala/cleanyourtv-app/c5e08a8962442f3228bf4bbc314efc639a852298/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="250" /> <img src="https://raw.githubusercontent.com/udhaykumarbala/cleanyourtv-app/c5e08a8962442f3228bf4bbc314efc639a852298/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="250" /> <img src="https://raw.githubusercontent.com/udhaykumarbala/cleanyourtv-app/c5e08a8962442f3228bf4bbc314efc639a852298/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="250" />

* Source: https://github.com/udhaykumarbala/cleanyourtv-app (MIT)
* Issues: https://github.com/udhaykumarbala/cleanyourtv-app/issues
* Releases: https://github.com/udhaykumarbala/cleanyourtv-app/releases
* Changelog: https://github.com/udhaykumarbala/cleanyourtv-app/blob/HEAD/CHANGELOG.md
* Website: https://cleanyour.tv/app/android (privacy policy: https://cleanyour.tv/app/android/privacy)

### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.

[Clean Your TV](https://github.com/Gathin23/cleanyourtv) (MIT, by Gathin) cleans up Android TVs, but it needs a computer: you run its scripts or give its prompt to a coding agent. This app does the same job from a phone, for people who will never install ADB. The debloaters already on F-Droid (Canta, for example) work on the device they run on, with Shizuku or root; this app manages a different device, the TV, over the network, and keeps a restore point and undo for every change.

## Notes for reviewers

**Two TV APKs inside the app, rebuilt from source.** The app installs two small apps on the TV over ADB (never on the phone), carried in `android/platform/src/main/assets/bundled/`: the Clean Your TV launcher (`com.gathin.tvhome`, from the upstream git submodule `third_party/cleanyourtv`) and the screen the TV shows while the phone works (`tv.cleanyour.curtain`, our `:tv:curtain` module). The recipe `rm`s both and `scripts/fdroid-build-tv-apks.sh` rebuilds them unsigned from source. The app pins each one by size, SHA-256 and signer, and a TV only accepts an update signed with the same key, so the script puts our committed APK Signing Blocks back with `apksigcopier patch` (the mechanism F-Droid uses for reproducible builds). The blocks in `android/platform/signatures/<apk>/` contain only certificates and signatures; they are the only `scanignore` entries. A signature only verifies on exactly the bytes that were signed, so the build stops unless both rebuilt APKs are byte for byte the pinned ones (`verifyLauncherPin`, `verifyCurtainPin`).

**Reproducible build, our signature.** `Binaries` + `AllowedAPKSigningKeys` (eaf7af38...). Our CI runs this recipe with `fdroid build` in the `fdroidserver:buildserver-trixie` image on every tag and compares it with the published APK: for 0.3.0 the result is a match (https://github.com/udhaykumarbala/cleanyourtv-app/actions/runs/39913649964), and every later tag is checked the same way. Evidence: `docs/history/reviews/FDROID-S2-build-evidence.md`.

**Network.** Local network to the chosen TV (ADB, mDNS discovery). GitHub only on the user's action: "Check for updates" (one request, shows the version, downloads nothing) and the opt-in add-ons, whose index is signed and whose APKs are checked by size, SHA-256 and signer before they are installed on the TV; each add-on's page says, before it is added, that add-ons come from their authors' GitHub releases, outside any app store, and are not built or checked by F-Droid. Declared as `NonFreeNet`. No analytics, no crash reporting, no Play Services.

**Permissions.** INTERNET and ACCESS_NETWORK_STATE (the TV's ADB), CHANGE_WIFI_MULTICAST_STATE (finding the TV), FOREGROUND_SERVICE and FOREGROUND_SERVICE_CONNECTED_DEVICE (while it works on the TV), POST_NOTIFICATIONS (progress), USE_BIOMETRIC (the phone's screen lock confirms the first change to a TV).

**Name and package IDs.** This is a community app for Clean Your TV, not the official one. The name and the `tv.cleanyour.*` IDs (from `cleanyour.tv`, Gathin's domain) are used with Gathin's permission, recorded in our README (https://github.com/udhaykumarbala/cleanyourtv-app/blob/main/README.md): "The Clean Your TV name and the app's package IDs under `tv.cleanyour` (from `cleanyour.tv`) are used with Gathin's permission." Gathin will confirm it in this MR.

**Assets.** Code MIT; libraries Apache-2.0 (AndroidX, Kotlin, kotlinx) and the Bouncy Castle licence (MIT style), listed in `THIRD-PARTY-NOTICES`; Outfit font under OFL-1.1. The "aurora" wallpaper comes from the upstream repository; its author confirmed on 2026-10-23 that he made it and that it is under the repository's MIT licence, and he will confirm that here too.

**How it was made.** The app was developed with AI coding agents under human review: I set the design and the safety rules, and I reviewed and accepted every change and every release. This is also stated in the README.

**Testing.** It needs an Android TV or Google TV on the same Wi-Fi as the phone (or an Android TV emulator whose ADB port the phone can reach). Happy to post a screen recording of a full run.

## Checklist

### Policy

* [ ] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified (and does not oppose the inclusion). If you are not the author, please paste the link of the reply from the author. (I am the author. Gathin, author of the bundled launcher and of the Clean Your TV name, agreed; see above.)
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure. The summary and description must be included and images, icon, and changelog should also be provided for better user experience. The `en-US` locale must be included.

### Docs

* [ ] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [ ] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [ ] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [ ] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [ ] Please make sure your fdroiddata fork is public and your branch is not protected. See <https://docs.gitlab.com/user/project/repository/branches/protected/>.
* [ ] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request (there are none).
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. Please check the Changes tab to make sure there is no other unrelated files added in the MR.
* [ ] Releases are tagged and auto update is enabled unless there is a special reason.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [x] External repos are added as git submodules instead of srclibs. You can update git submodules without opening an MR in this repo and the submodule is covered by our scanner.
* [x] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). We'll use your signature for improved security/reliability, also allowing users to switch between different channels. Do note that if you don't enable reproducible build then the apk will be signed with our key so you can't enable it later. If you can't enable this, please add the reasons here.
* [ ] Setup abi split if the APK is large and the splitted ones can be much smaller. (Not needed: 4.8 MB, no native code of our own.)
* [ ] Only the latest versions should be kept in the metadata before it's merged. If you update the metadata, please replace the old versions with the new ones.
* [x] Don't add any disabled versions in the metadata.
* [ ] The `commit` field should be the full hash. Please don't use tag or branch in commit.

### Pipeline

* [ ] All pipelines should pass.
* [ ] All warnings and errors in the Reports tab should be fixed or explained.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just leave a note in the MR so we know we need to trigger the CI.

/label ~"New App"
```
