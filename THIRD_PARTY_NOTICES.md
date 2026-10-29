# Third-party notices

Journey is MIT licensed ([LICENSE](LICENSE)), but it builds on other people's work and talks to
other people's services. This file lists what that includes and under which terms.

## Code and documents included in this repository

| What | Where | Licence |
|---|---|---|
| **Essentials Island SDK** (`island-sdk`, `island-sdk-compose`), copied unmodified from [sameerasw/essentials](https://github.com/sameerasw/essentials) at commit `1b26918` | `third_party/essentials-island/` | MIT, © 2025 Sameera Sandakelum. See `third_party/essentials-island/LICENSE` |
| **Material Symbols Rounded** icons, downloaded from [google/material-design-icons](https://github.com/google/material-design-icons) | `app/src/main/res/drawable/rounded_*.xml` | Apache License 2.0, © Google |

A few source files carry a `sameerasw.com` MIT header because they follow patterns from the
[android-common](https://github.com/sameerasw/android-common) design-language documents (MIT, © 2026 sameerasw.com),
which Journey uses as its UI reference but does not include.

## Libraries (pulled in by Gradle, not copied here)

| Library | Licence |
|---|---|
| Jetpack Compose, Material 3 (including Expressive), AndroidX Navigation 3, Lifecycle, DataStore, WorkManager, Glance, Core, Splash Screen, Profile Installer | Apache 2.0, © Google / AndroidX |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache 2.0, © JetBrains |
| OkHttp, MockWebServer | Apache 2.0, © Square |
| Gson | Apache 2.0, © Google |
| Google Play services Location | Android SDK / Play services terms |
| Mapbox Maps SDK for Android | [Mapbox Terms of Service](https://www.mapbox.com/legal/tos) (a Mapbox account and public token are required) |
| Robolectric, AndroidX Test, JUnit | MIT / Apache 2.0 / EPL 1.0 |

Exact versions are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Services and data

Journey is a client for these services. It does not host or redistribute their data, and you must follow
each service's own terms when you run it with your own keys.

- **PTV Timetable API v3** from Public Transport Victoria / Transport Victoria. Departures, stops, routes,
  disruptions, fare zones and vehicle positions. You need your own key, and its terms apply to you. The data is
  published by the State of Victoria (Department of Transport and Planning) under
  [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/), so credit them if you
  redistribute it. Journey credits Transport Victoria on its journey screen.
- **Transport Victoria Open Data** GTFS Realtime vehicle positions (crowding), © State of Victoria (Department of Transport and Planning), licensed
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Used only with your own free key.
- **[Transitous](https://transitous.org)** (MOTIS), a community-run journey planner built on open transit
  feeds and OpenStreetMap. No key is needed. Please keep request volume modest.
- **Mapbox** map tiles. Map data © Mapbox, © OpenStreetMap contributors.
- **Fare information** (prices, caps, discounts, public holidays) is entered by hand from
  [Transport Victoria's published fares](https://transport.vic.gov.au) and may be out of date. See
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#fares) for how to update it.

## Brand names and artwork (not covered by the MIT licence)

- **"myki"** and the myki logo and card design belong to the State of Victoria / Public Transport Victoria.
  The card artwork in `app/src/main/res/drawable-nodpi/myki_card_art*.png` was **drawn by the project author** as an
  original piece inspired by the official card, not copied from it. It shows your own cards in the app. It still
  evokes a trademarked design, so it is not offered under the MIT licence, and if a rights holder objects it will be
  changed or removed.
- **PTV transport pictograms** (train, tram, bus, V/Line, night bus) shown beside departures belong to their
  respective owners. Their vector sources are in `art/pictograms/`.
- **"Essentials"** and the Essentials island belong to their authors; Journey is an independent provider for
  that app and is not affiliated with it.

Journey is an **unofficial, independent project**. It is not affiliated with, endorsed by or supported by
Public Transport Victoria, Transport Victoria, the Victorian Government, Mapbox or Transitous. If you are a
rights holder and want an asset removed or credited differently, please open an issue.
