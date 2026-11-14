<div align="center">

# Journey

**A Material 3 Expressive replacement for the PTV app.**<br>
Live departures, journey planning, a live map, myki balance and trip tracking for Melbourne and Victoria.

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?logo=jetpackcompose&logoColor=white)
![Status](https://img.shields.io/badge/status-active-brightgreen)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-ff69b4.svg)](CONTRIBUTING.md)

</div>

> [!IMPORTANT]
> Journey is an **unofficial, independent** project. It is not affiliated with or endorsed by Public Transport
> Victoria, Transport Victoria, the Victorian Government, Mapbox or Transitous. "myki" and the PTV pictograms
> belong to their owners. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

<p align="center">
  <img src="docs/screenshots/for-you.png" width="15%" alt="For You tab" />
  <img src="docs/screenshots/stop-departures.png" width="15%" alt="Stop departures" />
  <img src="docs/screenshots/plan-results.png" width="15%" alt="Journey planner results" />
  <img src="docs/screenshots/journey-detail.png" width="15%" alt="Journey detail" />
  <img src="docs/screenshots/myki-card.png" width="15%" alt="myki card" />
  <img src="docs/screenshots/profile.png" width="15%" alt="Profile" />
</p>
<p align="center"><sub>Rendered by the test suite with fake data, so no real addresses or accounts appear.</sub></p>

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Getting started](#getting-started)
- [API keys (bring your own)](#api-keys-bring-your-own)
- [Building and testing](#building-and-testing)
- [Releases and updates](#releases-and-updates)
- [Project structure](#project-structure)
- [How it works](#how-it-works)
- [Privacy](#privacy)
- [Known limitations](#known-limitations)
- [Contributing](#contributing)
- [Credits and licence](#credits-and-licence)

## Features

### 🏠 For You
- **Arrange it your way:** For You is made of widgets. Tap the pencil to drag them into any order (they snap into place), remove them, add others (*Network status*, *Shortcuts*) and open each one's own settings. Every widget has a small title with an arrow to fold it away, or turn titles off altogether.
- **Weather and a greeting:** a big temperature with an icon for the conditions, "Good morning" with your name and today's date, folding into one line after a few seconds. Weather comes from OpenWeatherMap (built in), WeatherAPI.com or Open-Meteo.
- Your favourite stops with **live departures**, first on the page and grouped by mode (trains, trams, buses). Choose how many departures, the order, and whether stops with nothing coming show.
- **Favourite just the lines you use:** keep every departure from a stop, or pick lines and directions such as *Frankston to City*.
- **Disruptions that matter:** folded to one quiet line, and only when there's a major disruption (closures, replacement buses, changed timetables) at your stops or on your lines.
- Saved places (Home, Work and your own) one tap away from planning a trip.
- **Commutes:** the trips you make most (learned from your planning, or added by you) with when to leave, a backup if you miss it, and one-tap tracking or planning.
- **Near you:** the closest stops and their next departures.
- Mode filters, pull to refresh and auto refresh.
- A **Profile** with stats from the trips you log, and an avatar that morphs between Material 3 Expressive shapes.

### 🧭 Plan
- Door-to-door journeys from your current location (or any place) to anywhere, depart now or at a time you choose.
- **Live updates:** journeys re-plan in the background so delays show as they happen.
- Journey detail with a route map coloured by mode (train blue, tram green, bus orange, walking white), a dot at each change, walking legs, platforms and delays. Tap a stop to open its page.
- **Explore a journey on the map:** tap the journey's map for a full-size view on the Map tab with a panel of legs. Tap a leg to zoom to it and see where to get on and off, how many stops and how long.
- **Track a journey or a single service:** a live notification (Android 16 live updates style) and an in-app banner.
- **Full-screen alert to get off:** while a journey is tracked, an alarm-style screen names your stop when it's time to get off (from your location and the service's arrival). It never rings, and only vibrates when your phone isn't on silent.
- **Search stays in Victoria** (or Australia, if you choose), so a search never lands overseas.

### 🗺️ Map
- Full-screen edge-to-edge Mapbox map of stops for every mode, a stop preview and a floating *Nearby* panel that keeps you centred in the part of the map you can see.
- Search stops, routes and lines, and *Plan a trip here* from any stop.
- **Stop roulette** (from MelbourneTrainGame): roll a random station within a radius, with an optional travel challenge.

### 🚆 Live vehicle tracking
- Open any service to see a **map with its route, stops and the vehicle moving in real time**.
- A live status card ("Between Alphington and Fairfield") with the **scheduled and predicted time**, and a stop list shortened around you and the vehicle, with the vehicle's icon on the line.
- **Crowding** (optional, with a free Transport Victoria Open Data key): how busy the service is, how full each carriage is where reported, and alerts when a tracked service gets busy. Air conditioning notes come from the fleet data and service alerts.

### 💳 myki
- Keep your mykis in a wallet-style stack, with a **default myki**. Hold a card to your phone to read its number over **NFC** (experimental, you can always type it).
- **Log trips** from Plan or from a myki's page, or deduct a custom amount. Fares come from a built-in fare engine: the 2-hour fare, daily and weekend caps, Zone 2, the Free Tram Zone, concession, child and seniors fares.
- **Pricing that adapts:** discounts such as half-price or free travel are rules with date ranges you can switch on, edit or add, and prices are editable when they change.
- **Low-balance notifications** when a card drops below an amount you choose.

### 🧩 More
- **Home screen widget** (Jetpack Glance): favourites with live times in expressive Google Sans Flex type, a refresh button and a last-updated time.
- **[Essentials](https://github.com/sameerasw/essentials) island provider:** live tracking, your next journey steps and a disruptions alert (coloured by severity) on the Dynamic Island-style overlay.
- **Updates from inside the app:** Journey checks GitHub when you open it (at most every few hours), notifies you about a new version, shows what's new and installs it without a browser. Pre-releases are optional.
- **Design:** Material 3 Expressive with fully rounded Google Sans Flex type, dynamic colour, a floating navigation bar that follows you onto every page (back button, page name and an action), progressive blur behind the status bar and the bar, Material Symbols Rounded, optional pitch-black dark theme and modern haptics. Prefer the original floating title pill? Switch in *Settings → Appearance → Navigation bar*.

## Requirements

| | |
|---|---|
| **Device** | Android 8.0+ (API 26). Android 13+ for the blur effect, Android 16 for live-update notifications, an NFC phone to scan mykis. |
| **Android Studio** | A recent stable release that supports Android Gradle Plugin 9.4. |
| **JDK** | 25. Gradle downloads a matching toolchain for you through the Foojay resolver. |
| **Android SDK** | Platform **37.1** (compile SDK 37, minor API level 1) and build tools. Android Studio's SDK Manager installs them. |
| **Keys** | A free PTV Timetable API key and a Mapbox public token (plus an optional Transport Victoria Open Data key for crowding). See [below](#api-keys-bring-your-own). |

## Getting started

```bash
# 1. Clone
git clone https://github.com/ssethhyy/Journey.git
cd Journey

# 2. Add your keys (see the next section). .env is git-ignored.
cp .env.template .env
#    ...then edit .env

# 3. Build and install on a connected device or emulator
./gradlew :app:installDebug          # Windows: gradlew.bat :app:installDebug
```

Or open the folder in Android Studio, let Gradle sync, and press **Run**.

> [!TIP]
> The app builds and the whole test suite runs **without any keys**. Without them you simply get "missing
> credentials" messages instead of live data, and the map shows a placeholder.

## API keys (bring your own)

This repository contains **no keys**. Journey needs two free credentials, and you supply your own:

| Credential | Used for | Where to get it | Cost |
|---|---|---|---|
| `PTV_DEV_ID` and `PTV_API_KEY` | Live departures, stops, routes, disruptions, fare zones, vehicle positions | Request a key from Public Transport Victoria on the [PTV Timetable API page](https://www.ptv.vic.gov.au/footer/data-and-reporting/datasets/ptv-timetable-api/). They email you a **developer ID** and an **API key**. | Free |
| `VIC_OPENDATA_KEY` | *Optional.* Crowding: how busy a service and its carriages are, plus crowding alerts | Create a free account and key at [opendata.transport.vic.gov.au](https://opendata.transport.vic.gov.au) | Free |
| `MAPBOX_ACCESS_TOKEN` | The map tab, trip map and journey map | Create a **public** token (starts with `pk.`) at [account.mapbox.com](https://account.mapbox.com/access-tokens/) | Free tier |
| `WEATHER_API_KEY` | *Optional.* The weather on For You, from OpenWeatherMap (the default provider). Without it, people pick Open-Meteo (no key) or paste their own key in the Greeting widget's settings | Create a free account at [openweathermap.org](https://home.openweathermap.org/users/sign_up) and copy a key from *API keys* (new keys can take an hour or two to start working) | Free |

Journey's journey planner uses [Transitous](https://transitous.org), which needs **no key**.

### Option 1: `.env` (recommended for development)

Copy [`.env.template`](.env.template) to `.env` in the project root and fill it in:

```properties
PTV_DEV_ID=1234567
PTV_API_KEY=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
MAPBOX_ACCESS_TOKEN=pk.xxxxxxxx
```

Gradle reads this file at build time and bakes the values into the app **you build on your machine**. The file is
listed in [`.gitignore`](.gitignore), so it is never committed. An environment variable with the same name (for
example in CI) works too.

### Option 2: inside the app

Open **Profile → Settings → API access** and paste your developer ID, API key and Mapbox token. They are stored
privately on the device and override the build-time values. This is handy when you install an APK built by
someone else, or for trying different keys without rebuilding.

> [!WARNING]
> **Keep your keys out of Git.**
> - Never paste real keys into source files, issues, pull requests, screenshots or logs.
> - Use a **public** Mapbox token (`pk.`), never a secret one (`sk.`).
> - Keys built into an APK can be extracted by anyone who has that APK, so **do not publish or share APKs built with
>   your own keys**. Share the source and let people use their own.
> - If a key is ever committed or leaked, **revoke it and create a new one** straight away. Removing it from a later
>   commit does not remove it from history. See [SECURITY.md](SECURITY.md).
> - Turn on GitHub's *secret scanning* and *push protection* for your fork (Settings → Code security).

## Building and testing

```bash
./gradlew :app:assembleDebug            # debug APK  -> app/build/outputs/apk/debug/
./gradlew :app:installDebug             # build and install
./gradlew :app:testDebugUnitTest        # all unit and UI tests (no keys or network needed)
./gradlew :app:assembleRelease          # optimised release APK (see "Signing release builds" below)
```

- Tests use [Robolectric](https://robolectric.org) with native graphics and a local mock server, so they are
  deterministic and need no device. Screenshot tests write PNGs to `app/build/screenshots/`.
- Debug builds install as **`com.ssethhyy.journey.dev`**, next to a release build, so testing never replaces (or
  wipes) the copy you use every day.
- **Signing release builds:** set `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
  `RELEASE_KEY_PASSWORD` in `.env` to sign with your own keystore (create one with `keytool`, see `.env.template`).
  Without them release builds are signed with the debug key. Keep the keystore outside the project and back it up:
  without it you can't publish updates that install over earlier versions.
- Release builds keep their icons through [`app/src/main/res/raw/keep.xml`](app/src/main/res/raw/keep.xml). Do not
  remove it: Material Symbols are loaded by name, which the resource shrinker can't see.

<details>
<summary><b>Troubleshooting</b></summary>

| Problem | Fix |
|---|---|
| `SDK location not found` | Open the project in Android Studio, or set `sdk.dir` in `local.properties`, or export `ANDROID_HOME`. |
| `Failed to find target with hash string 'android-37.1'` | Install *Android 37.1* in SDK Manager → SDK Platforms (tick *Show Package Details*). |
| Gradle can't find JDK 25 | Let the Foojay toolchain resolver download it, or install a JDK 25 and point `JAVA_HOME` at it. |
| The map tab says *Map needs a Mapbox token* | Add a public `pk.` token (see above) and rebuild, or paste it in Settings → API access. |
| Departures show a credentials or *403* error | Check the developer ID and API key. They are a pair, and the key must be copied exactly. |
| Dates and times look an hour off | Journey shows Melbourne time. Check your device's time zone setting if you're elsewhere. |
</details>

## Releases and updates

Releases are published on [GitHub Releases](https://github.com/ssethhyy/Journey/releases) with the APK attached and
notes from [`docs/releases/`](docs/releases). The app checks the same page when it opens (at most every four hours,
never in the background), notifies you once about each new version, and installs it from inside the app through
Android's own install prompt. Android asks once to allow Journey to install apps. Turn checks off, or include
pre-releases, in *Settings → Updates*.

To publish a release that existing installs pick up:

1. Raise `versionCode` and set `versionName` in `app/build.gradle.kts` (for example `1.2.1-beta.1` for a beta).
2. Move the changelog's *Unreleased* entries into a new version section and add the notes to `docs/releases/`.
3. Build with your release key in `.env` (`./gradlew :app:assembleRelease`). Updates only install over a build
   signed with the same key.
4. Create a GitHub release tagged `vX.Y.Z` (tick *pre-release* for betas) and attach
   `app/build/outputs/apk/release/app-release.apk`.

## Project structure

```text
Journey/
├── app/                           The Android app (single module)
│   └── src/main/java/com/ssethhyy/journey/
│       ├── data/
│       │   ├── api/               PTV Timetable API client (HMAC-signed), DTOs and mappers
│       │   ├── commute/           Commutes: learned and saved trips
│       │   ├── crowding/          GTFS Realtime crowding feed, carriage matching and alerts
│       │   ├── fare/              Fare engine, fare table and discount rules
│       │   ├── game/              Stop roulette (MelbourneTrainGame)
│       │   ├── location/          Location provider
│       │   ├── model/             Domain models (stops, departures, patterns, trip progress)
│       │   ├── myki/              Cards, trip ledger, NFC reader, low-balance alerts, profile stats
│       │   ├── planner/           Journey planner (Transitous / MOTIS) client and models
│       │   ├── repository/        Settings, favourites, places, PTV repository and stop matching
│       │   └── update/            GitHub release checks and in-app installs
│       ├── island/                Essentials island provider (payloads, icons, expanded views)
│       ├── tracking/              Live tracking, journey steps, stop alerts and crowding alerts
│       ├── ui/
│       │   ├── components/        Floating bars, timeline, symbols, progressive blur
│       │   ├── navigation/        Navigation 3 keys and the navigator
│       │   ├── screens/           For You, Plan, Map, stop, trip, myki, profile, settings...
│       │   └── theme/             Material 3 Expressive theme, typography, mode colours
│       ├── widget/                Glance home-screen widget
│       └── utils/                 Formatters, haptics, geo maths
├── third_party/essentials-island/ Vendored Essentials Island SDK (MIT, unmodified)
├── art/                           Source art: pictograms, launcher icon, icon and card guides
├── docs/                          Architecture notes, release notes (docs/releases) and screenshots
├── .github/                       Issue and PR templates, CI, Dependabot
├── .env.template                  Template for your own keys (copy to .env)
└── gradle/libs.versions.toml      Dependency versions
```

## How it works

Journey is a single-activity Jetpack Compose app. A small hand-written dependency container (`AppContainer`)
wires repositories to screens, screens read state from `ViewModel`s exposed as `StateFlow`s, and everything is
stored on the device with DataStore. Departures, stops and vehicle positions come from the **PTV Timetable API**,
journeys from **Transitous**, and maps from **Mapbox**.

The [architecture guide](docs/ARCHITECTURE.md) covers the layers, the fare engine, live tracking, the island
provider and the testing approach in more detail.

## Privacy

- **No account, no analytics, no ads, no trackers.**
- Your favourites, places, mykis, trips and settings stay **on your device**.
- The app talks only to PTV (departures), Transitous (journeys), Mapbox (map tiles), Transport Victoria Open Data
  (crowding, only if you add a key), the weather provider you choose (OpenWeatherMap by default, while the weather is
  shown) and GitHub (checking for updates when the app opens, if turned on). Your location is used on the device for
  *nearby*, planning, the weather and stop alerts, and sent to those services only as the coordinates of a request.
- Journey reads a myki's **card number** over NFC. A myki's balance and trips are locked on the card, so balances
  are entered by you and updated by the trips you log.

## Known limitations

- **NFC card scanning is experimental.** It reads only the card number and hasn't been verified against every card, so typing the number always works.
- **myki balances can't be read from the card** (they're encrypted and the keys aren't public). Balances are
  entered by hand and adjusted as you log trips, so they can drift from the real balance.
- **Fares are calculated by Journey**, from prices entered by hand. Check them in *Settings → Fares and discounts*
  after each 1 January. The weekly cap and regional **V/Line** fares aren't modelled (V/Line asks for an amount).
  Public holidays are a hand-kept list for 2026 and 2027.
- A trip between two Zone 2 stops that passes through Zone 1 is charged as Zone 2 unless you change the zone.
- **Crowding** depends on what Victoria's feed reports. Many services have no crowding data, and live air
  conditioning faults aren't published at all.
- **Stop alerts** work for tracked journeys, not single services (Journey doesn't know where you get off).
- Journey is a client for third-party services, so it depends on their availability and terms.

## Contributing

Contributions of all kinds are welcome: bug reports, fixes, features, fare updates, translations of the docs, design
feedback and tests. Please read **[CONTRIBUTING.md](CONTRIBUTING.md)** first. It covers the workflow, code and design
guidelines, and what a good pull request looks like. A quick version:

1. **Fork** the repo and create a branch (`feat/my-change` or `fix/my-bug`).
2. Add **your own keys** to `.env` and make sure `./gradlew :app:testDebugUnitTest` passes.
3. Make a focused change with tests, following the [guidelines](CONTRIBUTING.md#code-and-design-guidelines).
4. Open a **pull request** using the template, with screenshots for any UI change.

Found a security problem or leaked a key by accident? See [SECURITY.md](SECURITY.md). Everyone taking part is
expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Credits and licence

Journey is released under the **[MIT License](LICENSE)**.

Built with and thanks to:
- [Essentials](https://github.com/sameerasw/essentials) and [android-common](https://github.com/sameerasw/android-common) by sameerasw, for the island SDK and the design-language reference the UI follows.
- [Material Symbols](https://fonts.google.com/icons) and Material 3 Expressive by Google.
- [Transitous](https://transitous.org) and [MOTIS](https://github.com/motis-project/motis) for open journey planning.
- Public Transport Victoria and Transport Victoria for open data, and [Mapbox](https://www.mapbox.com) with
  [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors for maps.
- [Metrodroid](https://github.com/metrodroid/metrodroid) for documenting how the myki card number is stored.

The full list of third-party code, data and brand notices is in **[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)**.
