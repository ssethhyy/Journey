# Changelog

All notable changes to Journey are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- **Near you on For You.** A section lists the closest stops with each one's next departure, using the same data as the Map tab's Nearby panel. Tap a stop to open it, or *Map* to see them all. If location isn't allowed or is switched off, it says so and offers to fix it. Turn it off, or choose how many stops it shows (1 to 8, 3 by default), in *Settings → Nearby stops*.
- **Commutes on For You.** A new section shows the trips you make most, such as *To Work*, with when to leave, when you arrive and the services to catch. Journey learns them from journeys you plan (a trip planned on two separate occasions becomes a commute, and it notes the time you usually go), and you can add your own with a name, a start and a destination. Each card has **Track** to follow that journey live, **Plan** to jump straight into the planner with it filled in, and an **If you miss it** backup showing the next trip, which you can also track. Remove one of your own, or hide a suggested one, from the card menu.
- **Crowding.** The trip screen shows how busy a service is (a five-step meter with a plain label) and, for trains that report it, how full each carriage is with the fullest cars named. Data comes from Victoria's free GTFS Realtime vehicle position feeds on Transport Victoria Open Data; add a free key in *Settings → API access* or `vic.opendata.key` in `local.properties`. Without a key the card explains how to set it up.
- **Crowding alerts.** While you track a service, Journey sends one notification when it reaches your chosen level (*Standing room only*, *Very crowded* or *Full*) and again only if it gets worse. Configure in *Settings → Crowding*.
- **Air conditioning.** The trip screen says when the fleet data lists a vehicle without air conditioning and when an active service alert mentions an air-conditioning fault. PTV publishes no live air-conditioning status, so nothing more can be shown.
- **Bottom bar navigation style**, now the default. The floating navbar is always on screen: on a page it turns into a back button, the page name and an action button for that page (for example *Track this service*, *Add to favourites*, *Settings*), with small icon actions beside the title. Switch back to the original floating **Top pill** in *Settings → Appearance → Navigation bar*.
- Repository documentation for GitHub: README, contributing guide, code of conduct, security policy, licence, third-party notices, issue and pull request templates, CI and Dependabot.

### Changed
- **Expressive Google Sans Flex type everywhere.** All text is now fully rounded, and each part of the Material 3 Expressive type scale sets the font's optical size, width, weight and slant to suit its job: display and headline text is wider and sized for its optical size, titles slightly wide, body text at its natural width for easy reading, and labels a touch wide. The new emphasized styles (heavier, wider, and leaning slightly on the largest sizes) are used for hero moments such as the greeting, *Leave now*, departure countdowns, balances and page titles. The widget now uses the same font file, so the duplicate copy is gone and the app is about 4 MB smaller.
- **Modern haptics throughout**, following the android-common haptics utility. Taps use the click primitive where the phone's motor supports it, and switches and checkboxes use the system on/off feel (Android 14+). Pulling to refresh ticks when you have pulled far enough and confirms when it refreshes. Chips, buttons and rows on the trip, route, fares and myki screens now respond too. Starting to track gives a soft rise into a click, a tracked service that is leaving now gives two firm taps, and arriving at the end of a tracked journey gives a small success pulse. Everything still follows *Settings → Haptic feedback*.
- **Home-screen widget redesigned** to match the design file: a coloured mode circle, a bold stretched stop name over a light "400m away", and a right-aligned time over "towards City", with the live icon beside the time. It is drawn with Google Sans Flex's weight, width and roundness axes, which normal widget text cannot use. Every text is a single line that narrows and then trims with "…" instead of wrapping into the next row. Up to seven stops show, and the slim *Updated* line and refresh button stay at the bottom. Tapping a row still opens that stop.
- In the bottom bar style the progressive blur at the top covers only the status bar (clock, Wi-Fi and battery icons) instead of the whole title pill area.
- On the myki card page the *Add money or pass* button and the options menu move into the page when the bottom bar is used.

### Fixed
- **Current location on the map.** Tapping the location button with the phone's Location switch off used to wait about eight seconds and then do nothing. Journey now shows the system *Turn on location?* dialog (asking for permission first if needed), shows a spinner on the button while it finds you, points the map at your last known position straight away, and tells you if no location could be found. The Nearby panel says when Location is switched off and offers *Turn on*.
- Long route names (such as V/Line coach routes) no longer squash the stop name in Nearby.
- Debug builds install as `com.ssethhyy.journey.dev`, next to a release build, so testing never replaces or wipes the copy you use.
- CI no longer fails while setting up the Android SDK (the runner's preinstalled SDK is used instead of `android-actions/setup-android`).
- CI no longer fails with `./gradlew: Permission denied`: the Gradle wrapper is marked executable in Git and made executable in the workflow.

## [1.0.0] - 2026-10-27

### Added
- **For You** tab with a personal greeting, favourite stops with live departures, saved places (Home, Work, custom) and network status.
- **Plan** tab: door-to-door journeys with live re-planning, journey detail with a route map, and tracking for a journey or a single service.
- **Map** tab: full-screen map of stops for every mode, stop preview and a floating *Nearby* panel, plus search.
- **Live vehicle tracking** on the trip screen: route map with the vehicle moving in real time, scheduled and predicted times, and a shortened stop list with the vehicle on the line.
- **myki wallet:** stacked cards, a default myki, NFC card number scan (experimental), manual balance and top ups.
- **Trip logging** from Plan or a myki's page, with a built-in fare engine (2-hour fare, daily and weekend caps, Zone 2, Free Tram Zone, concession, child and seniors fares, Early Bird).
- **Fares and discounts** screen with editable prices and discount rules, including the 2026 half-price period and support for free-travel announcements.
- **Low-balance notifications** per myki.
- **Profile** with your name, an avatar that morphs between Material 3 Expressive shapes, and stats from your logged trips.
- **Live tracking notification** and in-app banner.
- **Home screen widget** with live favourites, refresh and last-updated time.
- **Essentials island provider** with live tracking and a disruptions alert coloured by severity.
- Material 3 Expressive design: dynamic colour, floating navigation bar and top pill with progressive blur, Material Symbols Rounded icons.

### Fixed
- Release builds now keep the Material Symbols and myki artwork (they were being removed by resource shrinking, which broke icons and the island's alert icon).
- Bottom-sheet forms lift above the keyboard instead of being covered by it.

[Unreleased]: https://github.com/ssethhyy/Journey/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ssethhyy/Journey/releases/tag/v1.0.0
