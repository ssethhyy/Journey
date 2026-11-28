# Changelog

All notable changes to Journey are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- **"Time to leave" alert while you track a service.** From where you are and how long the walk to the stop takes
  (at an ordinary pace, with a minute to spare), Journey tells you when to set off. It alerts once per service, and
  not when you're already at the stop. Turn it off in Settings → *Live tracking*.
- **How fast to go, on the live notification.** While you're on your way to board, the notification starts with a
  pace to aim for (*Leisurely walk*, *Walk*, *Brisk walk*, *Jog*, *Run*, or *You may miss it*), from how far you are
  and the time left. The progress tracker shows a walking or running figure to match. Turn it off in Settings →
  *Live tracking*.
- **Stops and lines open on the map.** Tapping a stop or line (from search, favourites, a stop's lines or a line's
  stops) now opens the Map with a floating panel showing what its page showed: departures, filters, service updates,
  facilities and lines for a stop, or directions and stops for a line. A line is also drawn along its route on the map.
  A stop with no known position still opens on its own page.
- **Map panels turn into their pages.** Pull the Nearby, stop, line or journey panel up and it becomes the page as
  you drag. It widens to the screen's edges, its corners straighten, its colour and padding become the page's, and
  its header folds away. When it lands, the real page is already in place, so only the toolbar changes: it gains the
  page's Back button. Pull down at the top of the page, or press Back, and it shrinks back into the panel on the map.
  Nearby now has its own page listing every stop nearby. Panels also move faster and land more crisply. Pushing up anywhere on a panel,
  not just its header, grows it; the list scrolls once it has become the page.
- **Services open on the map.** Tapping a departure anywhere (a stop's board, a stop on the map, a favourite)
  now opens the Map with the service drawn and its live position shown, and a panel with how it's running,
  crowding, its stopping pattern and service updates. Pull the panel up for the service's page, and down to return.
  Back goes to the stop you tapped it on, or the tab you came from.
- **Planned journeys open on the map.** Choosing a journey on Plan (or a commute on For You) opens it on the Map,
  with a panel that starts with when to leave (*Leave in 12 min*), the leave and arrive times, how long it takes and
  the first service to catch, then its steps, then the stop-by-stop detail. Tap a step to highlight it on the map
  and zoom to it. Pull the panel up for the journey's page, laid out the same way, and down to return. Back goes
  back to Plan (or For You). The journey's times update live on the map too, and you can track it from the panel.
- **The map no longer reloads when you come back to it.** The home screen (For You, Plan and the Map) now stays alive
  under any page you open, paused while it's covered. Coming back from a stop, a line, search or settings is instant:
  the map is exactly as you left it and doesn't rebuild its style or tiles. While a page is on top, the home screen
  fetches nothing.
- **Stops and lines are kept between launches.** A line's details, shape, directions and stops are saved for a week,
  and nearby stops and stop details for a day, in the app's cache. Opening the app doesn't fetch them from PTV again.
  Departures, disruptions, stopping patterns and search are always fetched fresh.
- **The map keeps what it's showing in view.** Whatever height a panel is (short of full page), the stop, the line,
  your location, or just the spot you were looking at moves to the middle of the part of the map left uncovered.
- **A new For You, built from widgets you arrange yourself.** Greeting, Places, Favourites, Disruptions, Commutes and
  Near you are each a widget. Tap the pencil at the top of For You to edit the page:
  - **Move widgets by dragging.** Hold a widget, or grab its handle, and it lifts and follows your finger. A filled
    rounded shape shows where it will land, the other widgets move aside, and it snaps into place when you let go.
  - **Each widget has its own settings** (the cog), and a remove button. Removed widgets, plus two new ones (*Network
    status* and *Shortcuts* to Plan, the Map, Disruptions, Search and Stop roulette), wait under *Add a widget*.
    *Reset to the default layout* puts everything back.
  - Settings → *Customise For You* opens edit mode too.
- **Weather on For You.** The Greeting widget now shows the temperature and an icon for the conditions, in the style
  of the new header design: a big thin temperature with the weather icon just beside it, centred on the figures, a bold greeting and today's
  date. It stays pinned at the top of the page as its top bar: scrolling down shrinks it into a slim bar right under
  your finger, with the page fading softly beneath it, and pulling down at the top grows it back. Let go part-way and
  it settles open or closed, whichever is closer or the way you flicked. Every part of it (temperature, icon,
  greeting, date and the bar's time and date) moves together from one value, so nothing lags behind. It also shrinks
  on its own after a few seconds, and tapping it toggles it. The slim bar stays at the top even with widget titles
  turned off. Choose what
  the collapsed bar shows: the weather on the left (the default), the time in the middle and the date on the right.
  In the Greeting settings you can also turn the weather or collapsing off, choose how long before it collapses, how
  big the temperature is (small to huge), Celsius or Fahrenheit, show the conditions, feels-like and today's high and
  low, and pick the provider:
  - **OpenWeatherMap** (the default). Builds published on GitHub include a key, so it works straight away.
  - **WeatherAPI.com**, with your own free key.
  - **Open-Meteo**, which needs no key.
  The weather uses your last known location (or Melbourne) and is cached for 15 minutes.
- **Titles on every widget**, small and simple, with an arrow to collapse the widget. Turn all titles on or off in
  edit mode.
- Everything on For You lines up: the greeting, titles, chips, places and cards share the same left and right edges,
  and edit mode opens from an *Edit* pill at the top right.

- **One favourite per line and direction.** When you pick more than one line or direction while adding a favourite,
  *Separate favourites* (on by default) saves each as its own card, so *Frankston to City* and *Frankston to Frankston*
  can sit apart and be reordered. Turn it off to keep them on one card. The star still removes every favourite of
  that stop, and undo puts back exactly the one you removed.

### Changed
- **Live vehicle positions come from Transport Victoria's GTFS Realtime feed** when an Open Data key is set, instead
  of the PTV API alone. The feed updates often and says when each position was recorded, so the map's estimates and
  smooth movement work from a known time. Feed positions older than five minutes are ignored, and PTV's position is
  kept when it's newer.
- **The map's control pills read left to right.** *Nearby*, *Roll a stop* and *My location* sit on the left with no
  gaps, and they never scroll sideways: if they don't fit they close up, then wrap onto a new row above.
- **The Nearby panel has no collapse arrow.** Swipe it up or down instead. Its handle sits in line with the title, so
  the panel starts with its title and no empty space above it.
- **Saved places all work the same way** (thanks @AstonBolwell, #15). There's no separate Home and Work any more:
  every place has a name and an icon, and Home, Work and School are icons any place can use, so you can save two
  homes or none. *Set home* and *Set work* are still suggested until you've saved your first place.
- **Addresses show the suburb**, not the council (*Northcote*, not *Darebin*), in place search and saved places
  (thanks @AstonBolwell, #15).
- **Favourites come first**, straight under your places. Their settings cover departures per stop (moved from
  Settings), grouping by mode, the mode filters, hiding stops with nothing coming, the Add button, and the order of
  your favourites (drag the handles).
- **Disruptions are quieter and only show what matters.** The Disruptions widget is collapsed by default to a single line
  in the page's own colours (for example *1 major disruption · Hurstbridge*), and only appears when there is a major
  disruption (a closure, suspension, buses replacing trains or a changed timetable) at one of your favourite stops or
  on one of your favourite lines. Its settings choose major only, major and delays, or everything; your stops and lines
  or the whole network; how many to list; and whether it hides when there are none.
- **The greeting design** follows the new header: "Good morning, Seth" on one line (longer greetings step down in size)
  and "Today is the 14th of November 2026". Choose another date style, or turn the greeting or date off, in its
  settings. There's no prompt asking for your name any more: until one is saved, tap the greeting to add it (or set it
  in the Greeting settings).
- Your name, departures per stop and the Near you options moved from Settings into the widgets' own settings.
  Commutes can hide suggested commutes and the backup trip and limit how many show; Places can hide the Add tile.
- **Service updates on stop and line pages are quieter too.** Instead of a long list of every notice, a stop or line
  page shows one collapsed line (for example *2 major disruptions · Buses replace trains…*) only when there's a closure,
  suspension, buses replacing trains or a changed timetable (station notices such as car park or lift closures don't
  count, here or on For You). Tap it to see every alert, the important ones first.
  *Settings → Service updates on stops and lines* chooses what counts (major only, major and delays, or everything)
  and whether the card starts collapsed.
- **The map shows stops by zoom level.** Zoomed right out you see V/Line stations across the state; closer in, train
  stations appear, then trams, and buses and night buses once you're down to a few streets. Every stop of those modes
  in view is drawn, so there's no more *Zoom in to see stops*.
- **A draggable Nearby panel on the map.** It's shorter, with room for about three stops, and you drag it: down to
  collapse it, up to open it, and it springs to whichever step is closer (or the way you flicked) when you let go.
  Keep dragging up and the panel grows with your finger until it fills the screen, listing every nearby stop with
  live departures; let go and it snaps to full screen or back to open. Back brings it down again. The map leans in
  with a small zoom while the panel moves.
- **Map controls are pills in a row** resting on top of the Nearby panel (or the stop preview): *My location*,
  *Roll a stop* and *Nearby* when the panel is hidden. If they don't all fit, the row scrolls sideways.
- **Search lists stops and lines by transport type**: V/Line first, then trains, trams, buses and night buses. Good
  matches still come before loose ones. Stations and stops now come before lines, since they're what most searches
  are for.
- **Settings for the home screen widget** (Settings → Home screen widget): put your favourites in the widget's own
  order by dragging, switch off any you'd rather leave off it, and choose what each row shows: the distance, the
  suburb or nothing under the stop, the destination, and the live indicator. The widget redraws straight away.
- **A tidier Settings page**, in clear sections (For You, Appearance, Departures, Service updates, Nearby stops, Map,
  Home screen widget, Stop alerts, Crowding, Game, Essentials island, myki, Interaction, Updates, API access, Data and
  About). Everything you've set carries over. Option pickers whose labels didn't fit (the crowding level) use short
  labels with the full one spelt out underneath.
- For developers: every setting is now one typed definition in `Prefs` (`SettingsRepository.kt`), and the Settings
  page is declared in `SettingsCatalog` as Toggle, Choice, Slider, TextInput, Modes, Action, Info and Custom rows that
  `SettingsRenderer` draws. Adding a setting takes two lines; see CONTRIBUTING.
- **Fewer PTV requests for the same live data.** Two screens asking for the same thing at once now share one request,
  pull to refresh straight after a page loads reuses what just arrived, favourites split from one stop share one
  departures request, nearby stops and disruptions are kept for longer (stops don't move; disruptions for five
  minutes), the island shares that disruptions cache, and the map doesn't reload trams and buses for an area it
  already has. Live times still refresh as often as before.
- **Map tiles are cached on the phone** (up to 200 MB, oldest cleared first), so places you've looked at load from the
  phone instead of being downloaded again.
- For developers: an optional `WEATHER_API_KEY` in `.env` (a free OpenWeatherMap key) is built into release builds for
  the default weather provider. Without it people choose Open-Meteo or paste their own key.

### Changed
- **A new myki card**, drawn from [Open Transit](https://github.com/AstonBolwell/Open-Transit-App)'s myki artwork by
  Aston Bolwell: its line pattern across the card, the PTV logo, the myki wordmark and your card number. With colours
  from your wallpaper the whole card takes them on (card, pattern, logos and text); otherwise it's the familiar
  charcoal myki with the green wordmark.
- **Adding a myki starts with a scan**, laid out like the system's NFC sheet but in Material 3 Expressive: "Ready to
  scan" over a large card playing Open Transit's myki NFC animation (recoloured to match), with *Add manually* and
  *Cancel* underneath. A card that's read, or *Add manually*, moves on to its details, with the card face filling in
  as you type.
- **Route lines follow the real track or road on every map.** PTV sends a route's shape as separate pieces in no
  particular order, and stopping patterns often come without one, so trips used to draw straight lines from stop to
  stop. Now a service's line is stitched from the route's shape stop by stop (the piece running past each pair of
  stops, joined where pieces meet), so it bends where the line really bends. Every line (services, line pages and
  planned journeys) is smoothed the same way, and only where PTV has no shape at all is a gentle curve drawn through
  the stops.
- The myki card sits just a step lighter than the page when colours come from your wallpaper, rather than strongly
  tinted.
- **Collapsing For You widgets is smooth and consistent**: Favourites, Commutes and Near you now collapse with the
  same animation as the others, and the widgets below follow the edge instead of catching up afterwards.

### Fixed
- **For You no longer crashes after scrolling for a while.** Letting go of the greeting header part-way could make it
  spring a fraction past fully open or closed, which crashed the page. Saving the For You layout is safer too: a widget
  or favourite can't end up listed twice (which also crashed the page), moving the only widget no longer fails, a drag
  saved while the layout changed underneath can't drop or double a widget, and a change that can't be saved keeps the
  layout as it was instead of closing the app.
- A greeting moved further down For You no longer shows a second *Edit* pill.
- A line's stops are listed in the order the service runs. Some routes (many buses) came back from PTV with the other
  direction's stops mixed in at the top, in alphabetical order.
- The toolbar comes back when you reach the end of a page, so you're never stuck at the bottom of For You with no way
  to switch tabs.
- A journey's map preview fits the whole route clear of the rounded corners and the *Explore* chip, and the chip itself
  now opens the map. Exploring a journey on the Map tab shows just its route, framed above the controls.
- Option pickers with several choices (the temperature size, the stop roulette's *Roll a*, the service updates level)
  no longer cut their labels off.

### Removed
- The satellite map and 3D buildings, which used up far more of the Mapbox free tier. The map is always the flat
  standard map, with night lighting in the dark theme.

## [1.2.1-beta.1] - 2026-11-13

A pre-release of 1.2.1. It installs straight over 1.2.0, keeping your data. From this version on, Journey can update
itself: turn on *Settings → Updates → Include pre-releases* to be offered future betas too.

### Added
- **Updates from inside the app.** Journey checks GitHub for a newer version when you open it (at most every few hours, never in the background, so it costs no battery) and notifies you once when one is available. Tapping the notification, or *Settings → Updates → Check for updates*, opens a card with the new version's notes. *Install update* downloads it straight into the app, with no browser, and opens Android's own *Update Journey?* prompt. Android asks once to let Journey install updates. Turn checking off, or include pre-releases, in *Settings → Updates*.
- **Search stays in Victoria.** Place search on the Plan tab only returns places in Victoria. Each result is checked against the state the planner reports as well as the map area, so towns just over the border are left out, and results are ranked from where you are or from Melbourne. The Map tab won't pan or zoom out beyond Victoria either. For interstate trips, switch *Settings → Map → Search and map area* to *Australia*.

### Fixed
- **Maps outside the Map tab show again.** The journey, trip and route maps had gone blank in 1.2.0, because they drew on a kind of surface the new edge blur can't pass through. They now draw the same way as the Map tab.

### Changed
- For developers: **API keys and release signing now live in a `.env` file** at the project root instead of `local.properties`. Copy the new `.env.template` to `.env` and fill in `PTV_DEV_ID`, `PTV_API_KEY`, `MAPBOX_ACCESS_TOKEN`, `VIC_OPENDATA_KEY` and, optionally, the `RELEASE_*` signing values. `.env` is git-ignored, so nobody who clones the repo gets your keys. Environment variables with the same names also work (handy for CI), and the old `local.properties` names still work as a fallback. `local.properties.example` has been removed.
- For developers: the README, contributing guide, security policy and architecture guide cover the new features, the `.env` setup, signing release builds and how to publish a release the in-app updater picks up.

## [1.2.0] - 2026-11-12

> **Before you update:** from this release Journey is signed with its own release key. Android won't install it over
> 1.1.0 or earlier, so uninstall the old version first. That clears your favourites, places, commutes and myki data on
> the phone; export your favourites first from *Settings → Data* if you want to keep them. Later updates install
> straight over the top.

### Added
- **Full-screen alert to get off.** While a planned journey is tracked, Journey fills the screen when it's time to get off, like an alarm, even over the lock screen. It names the stop in large type and says what to do: *Press the stop button* on trams and buses, or *Get ready, your stop is next* on trains, with the line, arrival time and platform. It fires once per ride, when your phone is near the stop or the service is about to arrive, whichever comes first. It never rings; it vibrates only if vibration is on and your phone isn't on silent. Turn it off, change vibration, allow full-screen alerts (Android 14+ asks) or preview it in *Settings → Stop alerts*.
- **Favourite just the lines you use.** Tapping the star on a stop with more than one line or direction lets you keep *All lines and directions* (as before), or pick only some, such as *Frankston to City*. For You and the widget then show only those departures, with the chosen lines under the stop's name. Change them later from the favourite's menu with *Choose lines*.
- **Explore a journey on the map.** Tap the map on a journey's page to open it full size on the Map tab, where you can move and zoom. A floating panel lists each leg; tap one to zoom to it and see where to get on and off (with platforms), how many stops and how long, with buttons to open those stops' pages. *Whole route* zooms back out, and Back returns to the journey.
- **Stops on a journey's page open their stop page** when you tap where you get on or off.
- **Change dots on journey maps** mark each place you switch between walking, trains, trams and buses.

### Changed
- **Blur behind the status bar and the bottom bar on every page** in the bottom bar style, including Settings and stop pages. The bottom blur fades away while the bar hides as you scroll, and comes back with it.
- **The journey page's map is fixed in place**, so it no longer fights the page's scrolling; tap it (*Explore*) for the full map. Train legs are named by line, as in *Frankston line to Frankston*.
- **Your location stays in the middle of the visible map**, above the Nearby panel and below the filters, and stays centred as the panel opens, expands or closes, until you move the map yourself.
- For developers: release builds are signed with your own key when the `release.*` settings are in `local.properties`, and fall back to the debug key without them.

### Fixed
- Zooming the map out too far to load stops hides the old stop markers instead of leaving a stale cluster on screen.

## [1.1.0] - 2026-11-10

Everything since 1.0.0, including all the changes tried out in the beta builds.

### Added
- **Commutes on For You.** The trips you make most, such as *To Work*, with when to leave, when you arrive and the services to catch. Journey learns them from the journeys you plan (a trip planned on two separate occasions becomes a commute, and it notes the time you usually go), and you can add your own. Each card has **Track** to follow the journey live, **Plan** to open it in the planner, and an **If you miss it** backup with the next trip, which you can track too. Remove your own commutes or hide suggested ones from the card menu.
- **Near you on For You.** The closest stops with each one's next departure, shared with the Map tab's Nearby panel. Tap a stop to open it or *Map* to see them all. If location isn't allowed or is switched off, it says so and offers to fix it. Turn it off, or show 1 to 8 stops (3 by default), in *Settings → Nearby stops*.
- **Crowding.** The trip screen shows how busy a service is (a five-step meter with a plain label) and, for trains that report it, how full each carriage is, naming the fullest cars. Data comes from Victoria's free GTFS Realtime feeds on Transport Victoria Open Data, using a free key added in *Settings → API access* (or `vic.opendata.key` in `local.properties`).
- **Crowding alerts.** While you track a service, Journey notifies you once when it reaches your chosen level (*Standing room only*, *Very crowded* or *Full*) and again only if it gets worse. Set it up in *Settings → Crowding*.
- **Air conditioning notes.** The trip screen says when the fleet data lists a vehicle without air conditioning, and when an active service alert mentions an air-conditioning fault. PTV publishes no live air-conditioning status, so nothing more can be shown.
- **Stop roulette**, from MelbourneTrainGame. Tap the dice on the Map tab to roll a random stop (a train station by default) within your search radius. The roll shuffles through stops, then lands and flies the map there, with an optional travel challenge, *Plan trip* and *Roll again*. Set it up in the new *Settings → Game* section: on or off, mode, radius (1 to 40 km, 10 km by default), skipping stops within walking distance, challenges and the rolling animation.
- **Your next steps on the Essentials island.** While a planned journey is tracked, the expanded island lists the steps still ahead (*Walk 350 m to Westgarth Station*, *Hurstbridge to Flinders Street · Platform 1 · get off at Parliament*, *Arrive at Work*), highlights the current step and shows a time for each.
- **Bottom bar navigation style**, now the default. The floating bar is always on screen. On a page it becomes a back button, the page name and that page's action (for example *Track this service* or *Add to favourites*), and it sits centred on pages without one. The original **Top pill** is still in *Settings → Appearance → Navigation bar*.
- **Plan a trip here** on the map's stop preview and on each stop's page.
- **Repository documentation** for GitHub: README, contributing guide, code of conduct, security policy, licence, third-party notices, issue and pull request templates, CI and Dependabot.

### Changed
- **Expressive Google Sans Flex type everywhere.** All text is fully rounded, and each part of the Material 3 Expressive type scale sets the font's optical size, width, weight and slant to suit its job. Display and headline text is wider, titles slightly wide, body text natural for reading, and labels a touch wide. Emphasized styles (heavier, wider, and leaning slightly at the largest sizes) mark hero moments such as the greeting, *Leave now*, countdowns, balances and page titles.
- **Home-screen widget redesigned.** Each stop shows a coloured mode circle, a bold, stretched stop name over a light "400m away", and a right-aligned time over "towards City", with a live icon. It is drawn with Google Sans Flex's variable axes, and every line stays on one line, narrowing and then trimming instead of running into the next row.
- **The island uses expressive Google Sans Flex type** in its expanded views: a bold, wide countdown and emphasized titles and buttons. (The small text in the island's compact and line views is drawn by Essentials in its own font.)
- **The in-app tracking banner fits the app.** It moved off a bright, inverted colour onto the same tonal surface as the cards and toolbar. It shows the mode pictogram with a live dot, the service or next step, the countdown in its own pill, and a wavy progress line.
- **Modern haptics throughout**, following the android-common haptics utility. Taps use the click primitive where the motor supports it, switches use the system on/off feel, and pull to refresh ticks at the threshold. More screens respond to taps. Tracking adds a feel for starting, two firm taps for "leaving now" and a success pulse on arrival. All of it follows *Settings → Haptic feedback*.
- **Journey maps are coloured by mode.** Each leg is drawn in its PTV colour (train blue, tram green, bus orange, V/Line purple) with walking in white, every leg joins the next, and colours stay true at night.
- **Stronger progressive blur behind the status bar**, reaching a little below the clock and icons with a soft tint of the page colour.
- On the myki card page, *Add money or pass* and the options menu sit in the page when the bottom bar is used.
- The app is about 4 MB smaller (the widget now shares the app's font file).
- For developers: debug builds install as `com.ssethhyy.journey.dev`, next to a release build.

### Fixed
- **Current location on the map.** With the phone's Location switch off, the location button waited about eight seconds and then did nothing. It now offers the system *Turn on location?* dialog (asking for permission first if needed), shows a spinner while it finds you, jumps to your last known position straight away, and tells you if no location was found. Nearby explains when Location is off.
- **Plan buttons switch to the Plan tab.** Saved places (Home, Work and others), commutes, the game's *Plan trip* and similar buttons planned the trip but left you on the same tab.
- Long route names (such as V/Line coach routes) no longer squash the stop name in Nearby.
- Times in a journey's timeline stay on one line.
- CI no longer fails while setting up the Android SDK or with `./gradlew: Permission denied`.

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

[Unreleased]: https://github.com/ssethhyy/Journey/compare/v1.2.1-beta.1...HEAD
[1.2.1-beta.1]: https://github.com/ssethhyy/Journey/compare/v1.2.0...v1.2.1-beta.1
[1.2.0]: https://github.com/ssethhyy/Journey/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/ssethhyy/Journey/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ssethhyy/Journey/releases/tag/v1.0.0
