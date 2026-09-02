# Journify Android

Journify is a native Android journey planner for Đà Lạt. The Android client reuses the original Django/Python planning engine for mood-based place selection, budget checks, geographic clustering and route ordering.

## Main flow

1. Enter trip length, places per day, budget, mood and starting point.
2. Search the catalog with accent-insensitive Trie suggestions, optionally choose attractions and eateries, and let Django fill any missing slots.
3. Generate an optimized multi-day itinerary.
4. View place details, TikTok/Google Maps review links and the route map.
5. Replace individual stops, then save the edited trip locally with Room.
6. Reopen or delete saved trips, share text, or export a polished visual PDF containing place photos, per-day route diagrams and weather, ratings, prices, opening hours, TikTok/Google Maps links, total distance, and a 30-day QR resume link.
7. See current Đà Lạt weather and a three-day forecast on the home screen; PDF export requests enough forecast days for the itinerary, up to seven days.

The Home catalog and the place-selection step both search live as the user types. Django's Trie indexes Vietnamese and English place names, so queries such as `xuan` and `flower` work without pressing a search button.

Use the `EN`/`VI` action in the top bar to switch languages. The choice is stored by Android and restored after the app restarts. In English mode, the app requests an English itinerary from Django and prefers the English name/address fields from the catalog; it does not rely on automatic on-device translation.

## Run the Django backend

From `backend` on Windows PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-mobile.txt
.\.venv\Scripts\python.exe manage.py check
.\.venv\Scripts\python.exe manage.py runserver 0.0.0.0:8000 --noreload
```

The development build selects its backend automatically: the Android emulator uses `http://10.0.2.2:8000/`, while a physical phone uses the current development computer address `http://192.168.1.10:8000/`. For direct phone testing, connect the phone with USB debugging, keep the phone and computer on the same Wi-Fi, run Django on `0.0.0.0:8000`, select the phone in Android Studio, and press Run. If the computer receives a different LAN address, update `PHYSICAL_PHONE_BASE_URL` in `RemotePlannerRepository`.

The LAN address is only a development convenience. A distributed APK must use a deployed HTTPS backend rather than a private `192.168.x.x` address.

PDF QR links use the incoming backend address by default. For a submission/deployed backend, set `JOURNIFY_PUBLIC_BASE_URL` to its public HTTPS origin before starting Django, for example `https://journify.example.com`. A QR containing `10.0.2.2` works only inside the Android emulator and cannot be opened by another phone.

The PDF route graphic is a compact coordinate-based overview designed to remain available without a paid static-map service. For turn-by-turn road geometry, open the itinerary's map screen in Journify. Place photos, current weather and newly generated resume QR links require network access during export; missing optional data is represented by a clear fallback instead of failing the whole PDF.

Weather is loaded directly from Open-Meteo for Đà Lạt and needs no API key. If the device is offline, the card shows a clear retry action.

The warnings about optional `sentence-transformers` and `google-generativeai` can be ignored for the mobile itinerary flow.

## Build Android

Open this folder in Android Studio and run the `app` configuration, or run:

```powershell
$env:JAVA_HOME='D:\AndroiStudio\jbr'
$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"
.\gradlew.bat assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Project memory

Read `JOURNIFY_MIGRATION.md` before continuing development. It records the original web flow, architecture decisions, lecturer requirements, completed milestones and current next actions.
