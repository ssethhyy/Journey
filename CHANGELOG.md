# Changelog

All notable changes to Journey are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Repository documentation for GitHub: README, contributing guide, code of conduct, security policy, licence, third-party notices, issue and pull request templates, CI and Dependabot.

## [2.0] - 2026-10-27

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

[Unreleased]: https://github.com/ssethhyy/Journey/compare/v2.0...HEAD
[2.0]: https://github.com/ssethhyy/Journey/releases/tag/v2.0
