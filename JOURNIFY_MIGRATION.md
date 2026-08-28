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
