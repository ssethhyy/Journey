# Contributing to Journey

Thank you for wanting to help! Journey is built by and for people who catch trains, trams and buses in Victoria,
and every kind of contribution makes it better. This guide explains exactly how to contribute and what a good
pull request looks like.

> Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
> Please **never** put API keys, tokens or personal data in an issue, pull request, screenshot or log.
> See [SECURITY.md](SECURITY.md).

## Contents

- [Ways to contribute](#ways-to-contribute)
- [Before you start](#before-you-start)
- [Set up your environment](#set-up-your-environment)
- [Development workflow](#development-workflow)
- [Code and design guidelines](#code-and-design-guidelines)
- [Testing](#testing)
- [Commit messages](#commit-messages)
- [Pull request guidelines](#pull-request-guidelines)
- [Review and merging](#review-and-merging)
- [Reporting bugs and requesting features](#reporting-bugs-and-requesting-features)
- [Specific kinds of contribution](#specific-kinds-of-contribution)
- [Licensing](#licensing)

## Ways to contribute

You don't have to write Kotlin to help.

| You can | How |
|---|---|
| 🐞 **Report a bug** | Open an issue with the *Bug report* template. Steps to reproduce and your device and Android version help a lot. |
| 💡 **Suggest a feature** | Open an issue with the *Feature request* template. Describe the problem first, then your idea. |
| 💲 **Fix the fares** | Prices, caps, discounts or public holidays out of date? Use the *Fare or pricing update* template, or send a PR (see [Fare updates](#fare-and-pricing-updates)). |
| 🔧 **Fix an issue** | Look for [`good first issue`](../../labels/good%20first%20issue) and [`help wanted`](../../labels/help%20wanted). Comment that you're taking it so nobody duplicates the work. |
| ✨ **Build a feature** | Open an issue (or a discussion) **before** starting anything large, so we can agree on the approach. |
| 🧪 **Add tests** | Untested logic is welcome work. Fare rules, tracking and planner mapping are good places to start. |
| 📚 **Improve the docs** | Fix typos, clarify setup steps, improve the architecture notes. |
| 🎨 **Give design feedback** | Journey follows Material 3 Expressive. Screenshots and annotated suggestions are very welcome. |
| 🔍 **Review pull requests** | Read, try and comment on other people's PRs. |

## Before you start

1. **Search** the [issues](../../issues) and [pull requests](../../pulls) in case it's already reported or underway.
2. For anything bigger than a small fix, **open an issue first** and wait for a thumbs up. It saves you from
   building something we can't merge.
3. Keep each pull request to **one change**. Three small PRs are easier to review than one large one.

## Set up your environment

You need Android Studio, JDK 25 (Gradle fetches it automatically), Android SDK platform 37.1, and **your own API
keys**. The full details are in the [README](README.md#getting-started). In short:

```bash
# 1. Fork the repository on GitHub, then clone YOUR fork
git clone https://github.com/ssethhyy/Journey.git
cd Journey
git remote add upstream https://github.com/ssethhyy/Journey.git

# 2. Add your own keys (this file is git-ignored)
cp local.properties.example local.properties
# edit local.properties: ptv.devId, ptv.apiKey, mapbox.accessToken

# 3. Check the build and tests pass before you change anything
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

### Providing your own keys

Journey ships **without** any credentials, so you need your own:

- **PTV Timetable API** (free): request a developer ID and API key from the
  [PTV Timetable API page](https://www.ptv.vic.gov.au/footer/data-and-reporting/datasets/ptv-timetable-api/).
- **Mapbox** (free tier): create a **public** token (`pk.…`) at
  [account.mapbox.com](https://account.mapbox.com/access-tokens/).

Put them in `local.properties`, or paste them in the app under *Settings → API access*. Details and troubleshooting
are in the [README](README.md#api-keys-bring-your-own).

**You do not need keys to run the tests** or to work on logic, fares, UI layout or docs. Tests use a local fake
server. Only live data and the map need real keys.

**Never commit keys.** `local.properties` is git-ignored. Before every commit, check `git status` and `git diff
--staged` for anything that looks like a key. If you accidentally push one, **revoke it immediately** and tell
us (see [SECURITY.md](SECURITY.md)).

## Development workflow

1. **Sync** your fork with upstream and branch from `main`:
   ```bash
   git fetch upstream && git switch main && git merge --ff-only upstream/main
   git switch -c feat/short-description
   ```
2. **Branch names** use a type and a short description: `feat/trip-share`, `fix/widget-refresh`,
   `docs/api-keys`, `refactor/fare-engine`, `test/planner-mapper`, `chore/bump-compose`.
3. **Commit** small, logical steps ([message format](#commit-messages)).
4. **Run the checks** locally (see [Testing](#testing)).
5. **Push** to your fork and open a **pull request** against `main`. Mark it a **draft** if it isn't ready.

## Code and design guidelines

### Kotlin and Compose

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). The repo's
  [`.editorconfig`](.editorconfig) sets indentation (4 spaces) and line endings (LF). Android Studio's
  *Reformat Code* is enough; please don't reformat unrelated code in the same PR.
- **Match the surrounding code**: naming, comment density and idiom. Public functions and non-obvious logic get a
  short KDoc explaining *why*, not *what*.
- Start each new file with the project's header comment (`Feature Module`, `File`, `Description`), like the existing files.
- Keep **business logic out of composables**. Put it in plain Kotlin objects and classes (like `FareEngine`,
  `TripProgress` and `ProfileStatsCalculator`) so it can be unit tested without Android.
- State flows down, events flow up. Screens read from a `ViewModel` (created with `appViewModel { … }`) that
  exposes `StateFlow`s, and repositories own the data.
- Compose: hoist state, pass lambdas rather than ViewModels to leaf composables, give `LazyColumn` items stable
  `key`s, and don't do work in composition.
- Avoid new dependencies. If one is really needed, say why in the PR and add it to `gradle/libs.versions.toml`.
  Don't depend on an unstable (alpha) library unless there is no alternative.

### Design: Material 3 Expressive

Journey has a consistent design language. New UI should feel like it belongs.

- **Theme, don't hard-code.** Use `MaterialTheme.colorScheme`, `typography` and `shapes`. Fixed colours are only for
  data (mode colours, disruption severity, the myki artwork). Test your change in **light, dark and dynamic colour**.
- **Icons are Material Symbols Rounded, only.** Use `Sym.*` from `ui/components/Symbols.kt`. **Do not** use
  `Icons.*` from the old Material Icons library. To add one:
  1. Download the **Rounded** vector (weight 400, grade 0, size 24) from
     [google/material-design-icons](https://github.com/google/material-design-icons) (`symbols/android/<name>/materialsymbolsrounded/`).
  2. Save it as `app/src/main/res/drawable/rounded_<name>_24px.xml` (or `rounded_<name>_fill_24px.xml` for the filled style) and delete the `android:tint` line.
  3. Add `val Name … = symbol("rounded_<name>")` to `Symbols.kt` **and** the name to `allResourceNames`.
  4. List it in `art/material-symbols.md`. The `SymbolsTest` test fails if a file is missing.
- **Top bar and navigation.** Use `ReusableTopAppBar` (the floating pill). Scrolling content should pass *under* it:
  apply the Scaffold's top padding as the list's `contentPadding` and add `Modifier.floatingBarBlur(...)`. Don't
  use collapsing app bars. Forms in bottom sheets use `StandardBottomSheet(scrollable = true)` so they lift above the keyboard.
- **Motion** is expressive but purposeful: springs for shape and size changes, no animation that gets in the way of
  quick glances at a departure board.
- **Accessibility:** give icons a `contentDescription` (or `null` if purely decorative), keep touch targets at 48dp
  or more, don't rely on colour alone, and check large font sizes.
- The UI follows the design language in [sameerasw/android-common](https://github.com/sameerasw/android-common) (floating toolbar, progressive blur, edge-to-edge, theming). Read it before large UI changes.

### Data and networking

- All PTV calls go through `PtvApi` / `PtvRepository` (requests are HMAC-signed and cached with short TTLs). Don't
  call the API from UI code, and don't add polling faster than the existing intervals. These are free public services.
- Be tolerant of missing fields: PTV data is messy. Add a parsing test for any new field or shape you rely on.

### Don't edit vendored code

`third_party/essentials-island/` is copied **unmodified** from upstream. Changes belong in
[sameerasw/essentials](https://github.com/sameerasw/essentials). To update it, copy the two folders again and
update `third_party/essentials-island/README.md` with the new commit.

## Testing

Every behaviour change needs a test, and bug fixes need a test that fails without the fix.

```bash
./gradlew :app:testDebugUnitTest          # everything: logic, fake-server and Robolectric UI tests
./gradlew :app:testDebugUnitTest --tests "*FareEngineTest"     # one class
./gradlew :app:assembleDebug              # make sure it builds
./gradlew :app:assembleRelease            # when you touch resources, R8 rules or manifest entries
```

| Kind of change | Test with |
|---|---|
| Pure logic (fares, progress, stats, parsing) | Plain JUnit, in `app/src/test/.../data` |
| Screens and flows | Robolectric Compose tests against `FakePtvServer` (see `AppFlowScreenshotTest`) |
| Island payloads | `JourneyIslandModelTest` and `IslandHostSimulatorTest` |

- Screenshot tests write PNGs to `app/build/screenshots/`. **Look at them** before you open a PR.
- Tests must not need real keys, a device or the internet.
- If you change user-visible text, update the tests that look for it.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<optional scope>): <short summary in the imperative, 72 chars max>

<optional body: what changed and why, wrapped at ~72 columns>

<optional footer: Closes #123>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`.
Scopes are usually a feature area: `plan`, `map`, `trip`, `myki`, `fares`, `island`, `widget`, `profile`, `ui`.

```text
fix(fares): charge the zone upgrade difference inside a 2-hour fare

A Zone 2 trip followed by a Zone 1+2 trip within two hours now pays only the
difference between the two fares instead of a full new fare.

Closes #42
```

## Pull request guidelines

A pull request is ready when **all** of this is true. The [PR template](.github/PULL_REQUEST_TEMPLATE.md)
has the same checklist.

**Scope and quality**
- [ ] It does **one thing** and links the issue it fixes (`Closes #123`).
- [ ] It's based on an up-to-date `main` and has no unrelated formatting or refactoring.
- [ ] The code follows the [guidelines above](#code-and-design-guidelines). New files have the header comment.
- [ ] No leftover debug code, commented-out blocks or `TODO`s without an issue.

**Tests and builds**
- [ ] `./gradlew :app:testDebugUnitTest` passes locally, with new or updated tests for the change.
- [ ] `./gradlew :app:assembleDebug` succeeds (and `assembleRelease` if resources or manifest changed).
- [ ] CI is green.

**UI changes**
- [ ] **Screenshots** (or a short screen recording) before and after, in light **and** dark.
- [ ] You've checked large font size and a small screen.
- [ ] It uses `Sym.*` icons and theme colours, with accessibility labels.

**Safety**
- [ ] **No keys, tokens, personal data or addresses** in code, screenshots, logs or test fixtures.
      Screenshot fake data, not your real favourites or home address.
- [ ] No new permissions or network destinations without discussion.
- [ ] Third-party code, art or data you add is compatible with the MIT licence, and credited in
      [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

**Documentation**
- [ ] README, `docs/` or in-app text updated if behaviour changed.
- [ ] A line added under **Unreleased** in [CHANGELOG.md](CHANGELOG.md) for anything a user would notice.

**Writing the description**
- **Title:** a Conventional Commit summary, e.g. `feat(trip): show scheduled and predicted time`.
- **Body:** *what* changed, *why*, how you tested it (devices and Android versions), and screenshots.
- Call out anything you're unsure about or want feedback on.
- **AI-assisted code** is fine, but you are responsible for it: understand it, test it, and say so in the PR.

## Review and merging

- A maintainer will review as soon as they can. Please be patient, and ping politely after about a week.
- Expect feedback. It's about the code, never about you. Reply to or resolve every comment.
- Push follow-up commits to the same branch. Don't open a new PR. Squash/fixup as asked, and rebase on `main`
  if there are conflicts.
- Pull requests are normally **squash-merged** with a Conventional Commit message, so the history stays tidy.
- PRs that go quiet for a few weeks, or fail CI without a response, may be closed. You can always reopen them.

## Reporting bugs and requesting features

Use the [issue templates](../../issues/new/choose). A great bug report includes:

- What you did, what you expected, and what happened (with **fake or redacted** data).
- Device, Android version and Journey version.
- Whether it happens with a fresh install, and relevant logs (`adb logcat`, with keys and addresses removed).

For feature requests, start with the **problem** ("I can't tell when my tram is delayed"), then your idea. Mockups welcome.

**Security issues and leaked keys:** don't open a public issue. See [SECURITY.md](SECURITY.md).

## Specific kinds of contribution

### Fare and pricing updates

Fares live in code as plain data, so they're easy to update:

1. Edit `FareDefaults` in `app/src/main/java/com/ssethhyy/journey/data/fare/FareModels.kt`:
   `table` for prices, `rules` for discounts (half-price, free travel, Early Bird…), `VicHolidays` for public holidays.
2. Add or adjust tests in `FareEngineTest` / `MykiFaresTest`.
3. **Link the official source** (a Transport Victoria or vic.gov.au page) in the PR description. We can't accept
   figures from memory or third-party sites.
4. Note the change under **Unreleased** in the changelog.

Users can also edit prices and add discounts in the app (*Settings → Fares and discounts*), but the defaults should be kept correct.

### Translations and docs

Documentation fixes can go straight to a PR. If you'd like to add app translations, please open an issue first so
the string-extraction approach is agreed.

### Art and assets

Only contribute artwork you created or have the right to share under the MIT licence, and say where it came from.
Brand assets ("myki", PTV pictograms) are not covered by the MIT licence. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Myki card art guidelines are in [`art/myki-assets.md`](art/myki-assets.md).

## Licensing

Journey is released under the [MIT License](LICENSE). By submitting a pull request you confirm that you wrote the
contribution (or have the right to submit it) and that it may be distributed under the same licence.

Thank you for helping make catching public transport a little nicer. 🚆🚋🚌
