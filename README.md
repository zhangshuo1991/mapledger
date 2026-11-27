# 地图记账 MapLedger (Android, Kotlin + Jetpack Compose)

Two-step map ledger: pick a place on the map → type the amount. Category is filled in automatically
from the place's POI type, and your manual changes are remembered per place.

## Features (v1)
- First-launch privacy consent (高德合规 updatePrivacyShow/Agree only after 同意).
- Full-screen Amap map centred on your location (GCJ-02); denied → falls back to 上海人民广场.
- Draggable, flingable bottom sheet of nearby places sorted by distance; drag the map under the centre pin
  to search somewhere else, or "就记在图钉这里" for a custom spot (named by reverse geocode).
- Amount sheet: custom keypad with press springs + haptics, shake on invalid input, morphing 完成 key.
  Category auto-filled from the Amap typecode; your override is remembered for that place.
- Category-coloured ¥ pins on the map (bigger with more visits), rolling month total.
- Ledger (month total, category bar, grouped by day, swipe to delete with 撤销) and per-place detail
  (visits, total, average, last visit, 再记一笔, 在地图上看).
- Light / dark mode (night map style), Room storage.

## Build
```bash
source /workspace/tools/env.sh          # JDK 21 + Android SDK (re-create with /workspace/tools/setup.sh)
cd /workspace/mapledger
./gradlew assembleDebug                 # signed with ~/.android/debug.keystore
./gradlew assembleRelease               # signed with /workspace/mapledger-keys/release.jks (alias mapledger)
```
Secrets are read at build time from `/workspace/mapledger-keys/` (override with `MAPLEDGER_KEYS=/path`):
`amap.key` → manifest meta-data `com.amap.api.v2.apikey` (via manifestPlaceholder), `release.pass` → release
store/key password. Nothing secret is committed.

Alternatives to the files (handy for CI):
```bash
./gradlew assembleDebug -PAMAP_KEY=<your amap key>         # or env AMAP_KEY=...
./gradlew assembleRelease -PRELEASE_STORE_PASSWORD=<pass>  # store and key password
```
Signing: debug uses `~/.android/debug.keystore` (alias androiddebugkey); release uses
`$MAPLEDGER_KEYS/release.jks`, alias `mapledger`. Release is minified with R8 (`app/proguard-rules.pro`
keeps the Amap SDK).

**Amap keys are bound to package + signing SHA1.** Package is `com.mapledger.app`; only APKs signed with the
debug keystore or the release keystore above will authenticate.

## Architecture
```
data/    Room: Expense, PlaceOverride (user's category per place), PlaceSummary query, LedgerDb
place/   Place, GeoPoint, PlaceSearch (interface)
         AmapPlaceSearch     – PoiSearch around point (800 m, distance-sorted, cached per ~100 m cell),
                               reverse geocode for custom pins, POI-id lookup for taps on map labels
         AmapCategoryMapper  – Amap typecode → ledger category (05 餐饮, 06 购物, 08 娱乐 incl. 0806 影院 / 0803 KTV…)
map/     MapProvider / MapController / MapEvents / LocationProvider (interfaces)
         amap/AmapMapProvider   – TextureMapView in Compose, category-colored pins, my-location dot, night map
         amap/AmapLocationProvider – Amap location SDK (GCJ-02); falls back to platform GPS converted to GCJ-02
         amap/AmapPrivacy       – 合规: updatePrivacyShow/Agree for Maps, Location, Search, gated by consent screen
ui/      Compose UI: PrivacyScreen, MapChrome (center pin + physical draggable sheet), AmountSheet (custom keypad,
         spring animations, haptics), LedgerScreen, PlaceDetailScreen
```
To swap the backend (e.g. Tencent, MapLibre/OSM outside China) implement `MapProvider`, `PlaceSearch`,
`LocationProvider` and change the three constructors in `AppRoot`/`MainViewModel`.

## Coordinates (GCJ-02)
Everything in v1 stays in Amap's GCJ-02 datum (map, location SDK, POIs) so there is no offset inside China.
If you ever mix in WGS-84 data (raw GPS, OSM, Google), convert with `CoordinateConverter` first — otherwise
points shift ~300–600 m in mainland China. Outside mainland China Amap data is sparse.

## Notes
- Amap only ships ARM native libs. The APK packages **arm64-v8a only** (compressed, release ≈ 25 MB);
  add `-PwithArmv7` to also include armeabi-v7a for old 32-bit phones. On an x86_64 emulator the arm64
  libs run through the image's ARM translation (Android 11+ google_apis images) — slow but functional.
- Testing notes and the emulator test log are in `TESTING.md`.
- Fallback city when location is denied: 上海人民广场.

## Gradle wrapper jar

`gradle/wrapper/gradle-wrapper.jar` is binary and is not included in the GitHub copy of this repo. To regenerate it (Gradle 8.11.1, as pinned in `gradle-wrapper.properties`), run once with a locally installed Gradle:

```bash
gradle wrapper --gradle-version 8.11.1
```

After that, `./gradlew assembleDebug` works as usual.
