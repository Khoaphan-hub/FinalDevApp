# Integration test report — 2 September 2026

Historical report for the 2 September test build at `fad080b`, not the current merged version. Later team fixes, QR implementation and user acceptance are recorded in `JOURNIFY_MIGRATION.md` and `QR_IMPORT_TEST_REPORT.md`. Untested rows here remain untested for that original run; some listed bugs have since been addressed.

## Environment and safety

- Branch: `integration/merge-all`, pulled with fast-forward only; base commit `fad080b`.
- Device: Android Studio emulator `emulator-5554`, Pixel 10 Pro XL / 16 KB x86_64 image.
- Backend: local Django on port 8000; emulator uses `http://10.0.2.2:8000/`.
- `main` and all member branches unchanged. No push performed during this testing phase.
- App installed with `adb install -r`; no uninstall/clear-data. Before-upgrade databases and preferences backed up to ignored `tmp/emulator-before-upgrade.tar`.
- Local-only synthetic account: `qa_emulator_0902`. No real account credentials changed. Any QA reports/published trips must be clearly marked and cleaned up by exact identifiers.

## Completed automated checks

| Check | Result |
| --- | --- |
| Git pull | Already up to date at `fad080b` |
| Django system check | Pass |
| Django existing tests | 8/8 pass, isolated temporary test DB |
| Android initial compile | Failed: wrong callback import, missing integration strings |
| Android compile after approved fixes | Pass |
| Android unit tests | 25/25 pass |
| Android lint | 0 errors, 88 warnings; warnings still need review |
| APK install/update | Pass; login opens on emulator |

Approved fixes so far: correct the weather callback import; add missing Vietnamese/English auth, location, offline, weather, mood and saved-title resources; complete English translations for the merged profile/community UI.

## Runtime checks

| Flow | Result / evidence |
| --- | --- |
| Empty login fields | Pass: inline validation |
| Registration password mismatch | Pass: inline validation |
| Register new account | Pass: HTTP 200 and home opens |
| Invalid login password | Pass: HTTP 401 and readable error |
| Valid login | Pass: home opens |
| Weather at home | Pass: current temperature and 3-day forecast displayed |
| Profile load | Loads correct synthetic username; BUG: null phone renders as literal `null` |
| Profile save/reload | Pass: synthetic email and phone survive tab navigation/reload |
| Saved data upgrade | 5 pre-existing rows remain in backup; not readable by current serialized models (see below) |
| Catalog/search/detail/reviews/report | Pending |
| Plan/partial selection/generation/edit/save | 3 days/3 POIs per day/3M VND; selected Xuan Huong Lake via Trie `xuan`, autofill preserves it. Rename and save pass; Save returns to Trips. Replacement persists in model/saved trip, but day-chip switch renders stale pre-replacement data (BUG). |
| Text sharing/community publishing/open/save | Text share sheet contains all 3 days and title/cost; no external send. Publish fails HTTP 400 at `/api/shared-itineraries/submit/`. Community open/save pending. |
| Map/PDF/QR | Driving map renders (day 1: 16.9 km/28 min). PDF 8 pages/18 POIs+eateries, photos, route diagrams, per-place details and 3 daily forecasts. QR decoded from page PNG using zxing-cpp; resume returns HTTP 200 with original itinerary. Maps URLs overflow right edge; PDF has no explicit link annotations, clickability depends on viewer auto-detection. |
| Vietnamese/English switch | VI switch changes labels and keeps saved list; toolbar incorrectly reverts to Journify while Trips selected (minor). |
| Rotation / background return | Itinerary portrait→landscape→portrait preserves renamed title, replacement, cost and chosen day 1. Original emulator rotation settings restored. Background/cold launch pending. |
| Offline warm cache / cold launch | Pending |
| Location permission / outside Da Lat | Pending |

## Findings under investigation

### Old saved payload compatibility

The before-upgrade `journify.db` has schema version 1 and 5 `saved_trips` rows. Their Java serialized class IDs predate the explicit `serialVersionUID=1` used by current models. `RoomSavedTripRepository.loadAll()` silently skips incompatible payloads, making old trips look absent. The current model files are unchanged relative to `main`, so do not attribute the original format incompatibility solely to this merge. No old rows have been deleted. Recovery requires a deliberate legacy-payload migration; preserve the backup.

### Community contract mismatch (source inspection, runtime pending)

The Android publisher sends a JSON itinerary, while the existing submission endpoint reads web session state. The list detail expects an `itinerary` envelope although backend returns the payload directly, and passes a String where `ItineraryActivity` expects a serialized Itinerary object. Reproduce through the app before marking runtime failures.

## Artifacts

- Unit test XML: `app/build/test-results/testDebugUnitTest/`.
- Lint report: `app/build/reports/lint-results-debug.html`.
- Screenshots and local QA helpers: ignored `tmp/` (not to be committed; may contain local test data).

Continue from `JOURNIFY_MIGRATION.md` and this report; no need to rescan the old web repository.
