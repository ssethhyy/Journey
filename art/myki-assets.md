# myki artwork for Journey

## 1. FAB icon (`ic_myki_card.xml`)

Start from the exact symbol Journey uses now:

- Google Fonts: https://fonts.google.com/icons?selected=Material+Symbols+Rounded:credit_card:FILL@0;wght@400;GRAD@0;opsz@24
- Settings: **Rounded**, Fill **0**, Weight **400**, Grade **0**, Optical size **24**, export as **Android** (or SVG)
- Raw XML: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/credit_card/materialsymbolsrounded/credit_card_24px.xml

Your version should be:

- A vector drawable at 24 dp × 24 dp, viewport 960 × 960 (the same as the Google file), one colour (`@android:color/white`, tinted by the app)
- Stroke weight matched to weight 400 (the card outline is 80 units thick in the 960 viewport)
- The myki mark kept inside the card's inner area (about x 160–800, y 280–680 in Google's export)
- Saved as `app/src/main/res/drawable/ic_myki_card.xml`. Journey picks it up automatically, and `credit_card` is the fallback.

If you draw it as an SVG, Android Studio's *Vector Asset* import converts it. Don't use a PNG.

## 2. Card face (`myki_card_art`)

- **Format:** PNG or lossless WebP. A photo-like gradient is fine. Use SVG only if it's pure flat shapes, since complex SVGs convert poorly to Android vectors.
- **Size:** **1712 × 1080 px**, which is the ISO ID-1 card ratio (85.6 × 54 mm, 1.585 : 1). This is sharp on the largest phones.
- **Corners:** square, full-bleed. Journey rounds the corners itself (20–24 dp), so don't bake in rounding or transparency.
- **Safe areas:**
  - Keep the top-left ~40 % × 18 % free of important detail. The stacked wallet shows the card's name and balance there.
  - Keep the bottom-right ~30 % × 15 % free. Non-full-fare cards show a "Concession" etc. badge there.
  - In the stack, only the top **26 %** of each back card is visible, so put the wordmark in that band.
- **Where to save:** `app/src/main/res/drawable-nodpi/myki_card_art.png` (the folder holds your art now; only .png/.webp/.xml files may go in it, no README or .gitkeep, or the build fails)
- **Per fare type (optional):** add `myki_card_art_concession.png`, `myki_card_art_child.png` or `myki_card_art_seniors.png`. Journey uses the matching file and falls back to `myki_card_art`. File names must be lowercase letters, digits and underscores (no hyphens). Fare-specific art is expected to carry its own label, so Journey skips its own fare-type badge for it.
