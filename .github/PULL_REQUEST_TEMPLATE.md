## What does this change?

<!-- A clear summary of the change and why it's needed. Link the issue: "Closes #123". -->

## How was it tested?

<!-- Devices and Android versions, what you tried, and the tests you added or updated. -->

## Screenshots or recording (UI changes)

<!-- Before and after, in light AND dark. Use fake data: no real addresses, favourites or keys. -->

## Checklist

**Scope and quality**
- [ ] One focused change, based on an up-to-date `main`, with no unrelated formatting or refactoring
- [ ] Follows the [guidelines](../CONTRIBUTING.md#code-and-design-guidelines) (theme colours, `Sym.*` icons, logic outside composables)
- [ ] No leftover debug code or commented-out blocks

**Tests and builds**
- [ ] `./gradlew :app:testDebugUnitTest` passes, with tests added or updated
- [ ] `./gradlew :app:assembleDebug` succeeds (and `assembleRelease` if I touched resources, the manifest or R8 rules)

**UI changes**
- [ ] Checked light, dark and dynamic colour, a small screen and a large font size
- [ ] Icons and touch targets have accessibility labels and are at least 48dp

**Safety**
- [ ] **No API keys, tokens, personal data or addresses** in the code, tests, screenshots or logs
- [ ] No new permissions or network destinations (or they're explained above)
- [ ] Any third-party code, art or data is MIT-compatible and credited in `THIRD_PARTY_NOTICES.md`

**Documentation**
- [ ] README or docs updated if behaviour changed
- [ ] Added a line under **Unreleased** in `CHANGELOG.md` for anything users will notice

<!-- If AI tools helped write this, that's fine. You're responsible for it, so please say so here. -->
