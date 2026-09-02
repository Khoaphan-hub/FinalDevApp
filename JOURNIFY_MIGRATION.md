# Journify Web-to-Android Migration Log

**Canonical Android project since the separation milestone:** this `JournifyAndroid` folder. The parent `MobileDevFinalProj` is the previous mixed project and should no longer receive Journify feature work.

> This file is the persistent project memory. Read it before doing more work so the old web project does not need to be scanned again. Update it whenever a milestone is completed or an architectural decision changes.

## Objective

Convert the existing **Journify** Da Lat journey-planning MVP from a Django website into a native Android application while reusing the Python/Django backend and its route-planning algorithms.

The Android client should not reimplement K-Means, TSP, geocoding, budget selection, or distance-matrix logic. Those remain server responsibilities.

## Lecturer requirements — grading source of truth

The final-project notice supplied by the group is binding for scope and delivery.

### Product requirements that affect implementation

- Native-installable Android application, minimum Android API 24.
- At least 3–4 meaningfully connected screens.
- Persistent local data is mandatory.
- Must integrate an external REST API or a device capability.
- Must demonstrate lifecycle handling, asynchronous work, and runtime permissions where applicable.
- Responsive UI plus explicit loading, empty, error, and offline states.
- Reliable install/run with no critical crash, freeze, or data loss.

Journify will satisfy these through:

- screens: Explore/Home, Trip Setup, Place Selection, Itinerary Detail, Saved Trips, Profile;
- local persistence: Room cache for saved/generated itineraries and last trip draft;
- external integration: Django REST API for catalog and Python itinerary generation;
- optional device integration after the core flow: current location for the starting point;
- asynchronous repository calls and observable UI state;
- demo/offline repository fallback so the presentation is not blocked by network failure.

### Required submission package

Final archive name: ascending student IDs joined with underscores. Required structure:

```text
<student_ids>/
├── README.md
├── src/                 # no build, .gradle, node_modules, .idea
├── apk/app-release.apk  # mandatory, API 24+
├── report/report.pdf    # 10–30 pages
└── video/demo-link.txt  # public 5–10 minute demo link
```

README must include members, IDs, title, demo link, build instructions, and test credentials if login is required. The report must cover users/topic, architecture, technologies, setup, work division, and self-assessment. All members must speak in the demo video.

Deadline stated in the notice: **23:59 on 5 September 2026**. One group representative submits through the supplied Google Form and retains the confirmation email.

### Grading priorities

- Functional completeness: 30%
- Technical quality/source: 25%
- UI/UX: 20%
- Originality/complexity: 15%
- Report/presentation/collaboration: 10%

Implementation priority therefore remains: stable end-to-end generation flow first, clean architecture and persistence second, polished states/UI third, optional community features last.

## Source locations

- Android project (working project): `MobileDevFinalProj/JournifyAndroid`
- Reused Django backend (now copied into the working project): `JournifyAndroid/backend`
- Original presentation: `C:\Users\phana\OneDrive\Desktop\CT POC.pdf`

## Product understood from the presentation and source

Journify creates a personalized multi-day Da Lat itinerary. A user provides:

- number of days;
- maximum attractions per day;
- total budget;
- mood/travel style;
- accommodation or starting address;
- optionally chosen attractions and eateries.

The backend then:

1. matches mood tags and automatically fills missing places;
2. checks budget and schedule capacity;
3. groups attractions geographically across days with K-Means;
4. orders stops with TSP/route optimization;
5. inserts breakfast/lunch/dinner choices;
6. calculates distances from a precomputed Da Lat route database;
7. returns Vietnamese and English daily schedules.

Main user flow for the Android MVP:

`Welcome/Login -> Home -> Trip setup -> Optional place selection -> Generate -> Daily itinerary -> Save/share`

## Backend audit — milestone 1 completed

### Technology and reusable assets

- Django 5.2.7 + Django REST Framework
- SQLite application database: `db.sqlite3`
- Route/distance database: `dalat_distances.db`
- Named distance matrix: `dalat_distance_matrix_named.csv`
- Core planner: `home/algorithm.py::generate_itinerary(...)`
- Automatic mood/budget filling: `home/trip_planner.py::_auto_fill_selections(...)`
- Route geometry API: `home/route_service.py` and `/api/get-dalat-route/`
- POI and eatery data already exist in Django models and CSV files.

### Core planner inputs

`generate_itinerary` accepts:

- `num_days`
- `daily_poi_limit`
- selected POI queryset
- selected eatery queryset
- optional extra POIs/eateries
- optional meal-slot overrides
- accommodation address
- whether to use Da Lat centre as fallback

### Core planner output

The function returns `(itinerary_vi, itinerary_en, error)`.

Each itinerary is a dictionary keyed by day number. Each day contains ordered stops with:

- `type`: `ACCOMMODATION`, `POI`, or `EATERY`
- `id`
- `name`
- `address`
- `lat`, `lon`
- `travel_to_next_km`
- `slot` for eateries (`morning`, `afternoon`, `evening`)

The accommodation is inserted at both the start and end of a day when coordinates are available.

### Existing useful routes

- `/api/geocode-and-sort/`
- `/api/search-suggestions/`
- `/api/get-location-details/`
- `/api/get-dalat-route/`
- `/api/login/`
- `/api/register/`
- public/shared itinerary DRF endpoints

### Important backend gap

There is currently **no stateless JSON endpoint that generates an itinerary**. The web flow stores form data and generated results in a Django browser session, then redirects to HTML pages. Login/register also use Django session cookies and CSRF rather than mobile-friendly tokens.

Therefore Android cannot cleanly reuse the planner until a small API adapter is added. The algorithms themselves do not need rewriting.

## Proposed Android API contract

### `POST /api/mobile/itineraries/generate/`

Request:

```json
{
  "language": "vi",
  "days": 3,
  "daily_poi_limit": 3,
  "budget": 3000000,
  "moods": ["Relaxed", "Foodie"],
  "start_address": "Chợ Đà Lạt",
  "use_default_center": true,
  "selected_poi_ids": [],
  "selected_eatery_ids": [],
  "auto_fill": true
}
```

Response:

```json
{
  "success": true,
  "data": {
    "days": [
      {
        "day": 1,
        "stops": [
          {
            "type": "POI",
            "id": 1,
            "name": "...",
            "address": "...",
            "latitude": 11.94,
            "longitude": 108.45,
            "travel_to_next_km": 2.4,
            "meal_slot": null
          }
        ]
      }
    ],
    "budget": {
      "total": 3000000,
      "estimated": 1200000,
      "remaining": 1800000
    },
    "notices": []
  }
}
```

Errors should use HTTP 400 with `{ "success": false, "message": "...", "field_errors": {} }`.

### Other mobile endpoints needed

- `GET /api/mobile/catalog/?type=poi|eatery&query=&moods=`
- `POST /api/mobile/auth/login/`
- `POST /api/mobile/auth/register/`
- `GET/POST /api/mobile/itineraries/` for saved plans
- existing route-geometry endpoint can be wrapped or reused later for the map screen.

For the five-day MVP, generation and catalog are higher priority than full token authentication and social/community features.

## Android architecture decision

Use a clean standalone Java/XML Android project. The current Java namespace remains `com.example.finalproject` for build stability and can be renamed after the backend connection is stable.

Layers:

- `domain/model`: `TripRequest`, `Itinerary`, `ItineraryDay`, `ItineraryStop`, `Mood`
- `domain/repository`: planner/catalog repository interfaces
- `application/usecase`: generate itinerary and load catalog use cases
- `infrastructure/remote`: HTTP/JSON implementation of repository interfaces
- `infrastructure/local`: Room persistence for drafts and saved itineraries
- `infrastructure/demo`: deterministic demo repository so UI remains demonstrable when the Django server is unavailable
- `presentation`: native Android Activities/Fragments/ViewModels and XML layouts

Avoid new networking libraries initially. Java `HttpURLConnection` and `org.json` are sufficient and avoid dependency-download risk. Network work must stay behind repository interfaces.

## UI direction

- Brand: warm, optimistic Da Lat travel experience
- Primary color: pine green
- Accent: sunrise/coral
- Background: soft cream
- Rounded cards, large touch targets, minimal text per step
- Vietnamese-first copy with English-compatible data model
- Planner setup should fit one scrollable native screen for MVP speed
- Results should use day chips/tabs and a vertical itinerary timeline

## Milestones

- [x] **M1 — Understand presentation, web flow, backend, algorithms, models and limitations.**
- [x] **M2 — Define Android domain/API boundaries and create the native Journify shell/theme/navigation.**
- [x] **M3 — Build trip setup form and validation.**
- [x] **M4 — Build itinerary result timeline with demo data.**
- [x] **M5 — Add Django mobile generation/catalog API adapter and connect Android.**
- [x] **M6 — Add Room persistence plus loading/empty/error/offline states.**
- [x] **M6.5 — Add partial place selection, rich place details/review links, and itinerary replacement.**
- [x] **M6.6 — Restore Trie-powered live search in the Android place-selection flow.**
- [x] **M6.7 — Add native PDF export with resumable QR and Đà Lạt weather.**
- [x] **M6.8 — Add persistent Vietnamese/English UI and Trie search to the Home catalog.**
- [x] **M6.9 — Support direct Android Studio testing on emulator and physical phone.**
- [x] **M6.10 — Upgrade PDF export into a rich visual itinerary guide.**
- [ ] **M7 — Build/test APK and prepare README/report/demo/submission structure.**

## Current state / next action

Milestone 2 is complete. The launcher opens Journify directly. The native shell contains Explore/Home, Trips and Profile destinations, a new warm Da Lat visual theme, and a redesigned home screen. Pure Java domain models (`Mood`, `TripRequest`, `Itinerary`, `ItineraryDay`, `ItineraryStop`), `PlannerRepository`, and `GenerateItineraryUseCase` are in place. The standalone project contains no An Tâm application classes.

Verification: `assembleDebug` completed successfully on 28 August 2026. The local terminal requires `JAVA_HOME=D:\AndroiStudio\jbr` and `GRADLE_USER_HOME=C:\Users\phana\.gradle`.

Milestones 3 and 4 are complete. `PlannerActivity` collects days, POIs/day, budget, multiple moods, and either Da Lat centre or a custom start address. `GenerateItineraryUseCase` validates the request. The UI exposes loading and inline error states. `DemoPlannerRepository` asynchronously creates deterministic offline data, and `ItineraryActivity` displays budget totals, day selection chips, and ordered stop cards. This demo adapter is intentionally marked offline and will be replaced behind the same repository interface when Django is connected.

Verification: `assembleDebug` and `testDebugUnitTest` both completed successfully after M3–M4. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

M5 and M6 are now complete. The copied Django backend exposes stateless mobile catalog and generation endpoints. Android uses the remote repository first and a deterministic offline demo repository if the server cannot be reached. Saved itineraries persist in Room and are available under the Trips tab. The map screen renders real stop coordinates with Leaflet/OpenStreetMap and requests a road route from OSRM.

### Selection, review links and editing milestone — 29 August 2026

- Trip setup now continues to a native place-selection screen instead of immediately generating.
- Users may choose any subset of the required POIs and eateries; selected IDs are sent to Django and `_auto_fill_selections` fills the remaining capacity according to trip length and mood.
- Users can switch between POI/eatery lists without losing selections, or skip selection and let Journify choose everything.
- The catalog API now returns rating, price, coordinates, opening hours, tags/meal slots, POI highlight, image URL, and the original CSV media link.
- Place details display all available information and label the external action as TikTok review, Google Maps review, or a generic review link based on the actual URL.
- Every generated POI/eatery has `Chi tiết` and `Thay đổi` actions. Replacing a stop updates its data, estimated trip cost, adjacent straight-line distance estimates, and the coordinates used by the map screen.
- Android UI was installed and exercised on `emulator-5554`: partial POI/eatery selection generated a 3-day itinerary, the selected POI remained in the optimized schedule, review details rendered correctly, and replacing Bánh căn Lệ with Bánh bèo Thái Phiên changed the estimate from 1,237,000₫ to 1,157,000₫.
- Django `manage.py check`, live catalog/generation HTTP tests, `assembleDebug`, and `testDebugUnitTest` completed successfully. The current APK remains at `app/build/outputs/apk/debug/app-debug.apk`.

Next: M7 hardening and submission work. Add a small set of domain tests, document backend/device setup, produce a release APK, and prepare report/video folders. Cloud deployment or configurable LAN server address is still required if the APK must work on a physical phone without the development computer.

### Saved-trip completion milestone — 29 August 2026

- Saving now waits for Room to confirm insertion, clears the planner/result stack, returns to `MainActivity`, and automatically opens the `Chuyến đi` tab.
- The saved-trips fragment is recreated/refreshed on arrival, so the new card appears immediately without manually navigating back or changing tabs.
- Serializable itinerary models now use stable `serialVersionUID` values. The Room reader skips incompatible payloads created by older development builds instead of failing the entire list.
- Saved-trip cards now include `Xoá` with a Material confirmation dialog and refresh after deletion.
- Itinerary detail now exposes Android's native share sheet with a readable text summary of budget, days and stops.
- Emulator verification completed: a new three-day trip was generated, saved, and shown immediately in the selected Trips tab; the delete confirmation rendered; and the native share sheet displayed the generated itinerary text.
- `testDebugUnitTest` and `assembleDebug` remain successful.

### Trie place-search milestone — 29 August 2026

- The Android place-selection screen now includes live, debounced search while users choose POIs and eateries before itinerary generation.
- Django reuses the original accent-insensitive `PrefixTree`: full place names and individual name tokens are indexed, so an unaccented prefix such as `xuan` finds `Hồ Xuân Hương Đà Lạt`.
- A stateless mobile endpoint at `/api/mobile/search-suggestions/` filters suggestions by POI/eatery type and returns the same rich place payload used by selection cards.
- Search results cannot overwrite a newer query when the user types or switches tabs quickly. Selected place IDs remain checked when filtering, clearing search, or changing between POI and eatery tabs.
- Empty and connection-error search states are rendered inside the selection screen, while clearing the query restores the cached full catalog without another request.
- Verification completed with Django system checks, two PrefixTree tests, live HTTP queries, Android unit tests/build, and emulator interaction. On `emulator-5554`, `xuan` returned Hồ Xuân Hương; selecting it, switching to eateries, and returning to POIs preserved the `1/9` count and checked state.

### PDF, QR resume and weather milestone — 29 August 2026

- The result screen now exports an A4 PDF natively on Android, preserving Vietnamese text and presenting budget, every day/stop, distance and meal-slot information.
- Before export, Android asks Django for a unique 30-day resume token. The PDF embeds the returned QR; scanning it loads the same itinerary snapshot through the existing web resume flow.
- Django exposes `POST /api/mobile/itineraries/share/`, reuses `ItineraryResumeToken`, and supports `JOURNIFY_PUBLIC_BASE_URL` so deployed QR links point at a real HTTPS backend.
- The app shares PDFs safely through Android `FileProvider`; files stay in app cache rather than requesting broad storage permission.
- Home now displays live Open-Meteo conditions for Đà Lạt (temperature, feels-like, humidity, wind) and a three-day min/max/rain forecast, with loading, failure and retry states.
- Verification: Django check and four tests passed; the live share endpoint returned a PNG QR and a valid resume redirect; Open-Meteo returned current data and three forecast days; Android unit tests and debug build passed. On `emulator-5554`, the weather card loaded, the final PDF button created a 161,316-byte three-page A4 PDF, and Android opened a one-file PDF share sheet.
- A rendered PDF copy was visually inspected with Poppler. The emulator-only QR correctly contains the backend resume URL; for a phone demo, deploy Django and set `JOURNIFY_PUBLIC_BASE_URL` first.

Next: deploy the backend, replace the debug base URL for a physical-phone build, then finish M7 release/report/demo packaging.

### Bilingual UI and Home catalog Trie milestone — 31 August 2026

- The `Xem địa điểm`/`Explore places` catalog now performs the same debounced, accent-insensitive Trie search as the place-selection step. Results update while typing, clearing the query restores the cached catalog, and request versioning prevents an older response from replacing a newer query or tab.
- Django's Trie now indexes both `name` and `name_en`, including individual tokens. Vietnamese `xuan` finds Hồ Xuân Hương and English `flower` finds the matching flower attractions without pressing Search.
- A persistent `EN`/`VI` action is available in the main top bar. Android AppCompat stores the chosen locale and restores it across activity/app restarts.
- Home, weather, catalog, planner, selection, details, itinerary, saved trips, replacement, map, PDF export, validation messages and offline demo data have Vietnamese and English resources.
- English mode sends `language=en` to Django and prefers `name_en`/`address_en` in API responses. This keeps the translated itinerary and location data aligned with the original web backend rather than translating text on the device.
- Verification completed with five Django tests and system checks, Android unit tests and debug build, and emulator interaction. On `emulator-5554`, switching to English survived a force-stop/relaunch; live `xuan` and `flower` searches returned the expected Vietnamese and English results.

Next: M7 release/report/demo packaging and deployment of Django to a public HTTPS host for use by physical phones.

### Language switch visual refinement — 31 August 2026

- Replaced the small single-language toolbar action with a persistent segmented `VN / EN` control.
- The current language is shown with a solid Journify-green segment while the alternative remains visible, making both the active mode and the switching action immediately understandable.
- Added a language/globe icon, a rounded outlined container, larger touch targets, and screen-reader descriptions for both choices.
- Verified both English and Vietnamese states on `emulator-5554`; the control fits beside the Journify title without overlap and switching still recreates the UI with the selected locale.

### Physical-phone development milestone — 31 August 2026

- The Android network configuration now distinguishes an emulator from a real phone. Emulators continue to use `10.0.2.2:8000`, while the current physical-device development address is `192.168.1.10:8000`.
- This keeps both test paths available without editing the base URL every time the developer changes the selected Android Studio device.
- USB debugging was authorized on a Samsung Galaxy S23 (`SM-S911B`), Android Studio/ADB installed and opened Journify directly, and the phone joined the same `192.168.1.0/24` Wi-Fi network as the backend computer.
- Real-device verification succeeded: the catalog loaded Django data and remote images, and the selection screen's live Trie search returned matching hill locations for `đồi`.
- The physical LAN address remains development-only and may change after reconnecting Wi-Fi. Release builds still require a deployed HTTPS backend.

### Rich visual PDF milestone — 31 August 2026

- Replaced the former text-heavy PDF with a polished A4 travel guide using the Journify cream, pine-green and coral visual system.
- The cover now summarizes total/estimated/remaining budget, total distance, number of days and places, and shows a three-image destination mosaic.
- Every itinerary day includes an export-time Đà Lạt forecast, day distance, and a coordinate-based route diagram with ordered start/place markers. The diagram is a lightweight visual overview; the in-app map remains the source for detailed road routing.
- Each place is presented in a structured card with its image, name/address, type, rating, individual price, distance to the next stop, opening hours, meal slot, TikTok or Google review URL when available, and a Google Maps coordinate link.
- The final page keeps the 30-day resume QR and itinerary totals. Missing images, ratings, prices, hours or reviews fall back to explicit placeholders instead of breaking export.
- Image downloads are concurrent, bounded and downsampled before embedding. The verified three-day sample decreased from about 10 MB to 5.3 MB without visible loss at A4 viewing size.
- The forecast request now follows the itinerary length (up to seven days). A weather failure does not block PDF creation; that day instead receives an unavailable message.
- Verification completed on the connected Galaxy S23: Android unit tests and debug build passed, the app exported an eight-page A4 PDF with 18 place cards, and every rendered page was visually inspected with Poppler for clipping, overlap, missing images and QR layout.

Next: M7 release/report/demo packaging and deployment of Django to a public HTTPS host so QR resume links work outside the development Wi-Fi.

### Reliable TikTok and Google Maps links milestone — 1 September 2026

- Fixed PDF TikTok links by removing tracking parameters without rebuilding the URI path. The original `@username/video/...` path is now preserved instead of being changed to `%40username/video/...`.
- Place details and PDF export now share one external-link builder, preventing the two screens from producing different review or map URLs.
- Google Maps links now use the canonical Vietnamese business name and address supplied separately by Django, while the visible place name/address can remain localized in English.
- Django catalog, Trie suggestion and generated-itinerary payloads expose `map_name` and `map_address`. Android carries these values through catalog selection, itinerary generation, stop replacement, saved trips, detail actions and PDF export.
- New itineraries use `https://www.google.com/maps/search/?api=1&query=...`; coordinates remain the fallback when canonical text is unavailable, including itineraries saved by an older development build.
- Added unit coverage for preserving TikTok `@`, removing TikTok tracking queries, leaving non-TikTok review links untouched, name/address Maps search and coordinate fallback.
- Verification completed with Django system checks and five backend tests, Android unit tests and debug build. On the connected Galaxy S23, the revised APK opened a TikTok review directly and, while Journify was in English mode, opened the exact Google Maps business profile using the original Vietnamese business identity.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Django is currently running on `0.0.0.0:8000` for LAN testing.

### Separation milestone

A clean standalone Android Studio project was created at `MobileDevFinalProj/JournifyAndroid`. It contains only Journify application/domain/infrastructure/presentation classes and the required resources/build wrapper. Old An Tâm source was deliberately excluded.

Independent verification: running `assembleDebug` from the `JournifyAndroid` folder completed successfully on 28 August 2026. Its APK is `JournifyAndroid/app/build/outputs/apk/debug/app-debug.apk`.

### Old-project cleanup milestone

The parent `MobileDevFinalProj` content was cleaned on 28 August 2026. All old An Tâm source, build/configuration folders, IDE metadata, documents, and the old Git repository were deleted. The container now holds only `JournifyAndroid`. A new Git repository with an initial Journify baseline commit exists inside this project.

## Constraints and cautions

- Do not edit unrelated `.idea` changes; they existed before migration work.
- Do not delete the old source until Journify compiles; unused old classes can remain temporarily.
- Backend source is now inside `JournifyAndroid/backend` and is part of the canonical project.
- Never place a laptop localhost URL directly into a release build. Android emulator uses `10.0.2.2`; physical devices need the computer LAN IP or a deployed HTTPS server.
