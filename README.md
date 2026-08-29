# Journify Android

Journify is a native Android journey planner for Đà Lạt. The Android client reuses the original Django/Python planning engine for mood-based place selection, budget checks, geographic clustering and route ordering.

## Main flow

1. Enter trip length, places per day, budget, mood and starting point.
2. Search the catalog with accent-insensitive Trie suggestions, optionally choose attractions and eateries, and let Django fill any missing slots.
3. Generate an optimized multi-day itinerary.
4. View place details, TikTok/Google Maps review links and the route map.
5. Replace individual stops, then save the edited trip locally with Room.
6. Reopen or delete saved trips, share text, or export a polished PDF containing a 30-day QR resume link.
7. See current Đà Lạt weather and a three-day forecast on the home screen.

## Run the Django backend

From `backend` on Windows PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-mobile.txt
.\.venv\Scripts\python.exe manage.py check
.\.venv\Scripts\python.exe manage.py runserver 0.0.0.0:8000 --noreload
```

The debug Android build uses `http://10.0.2.2:8000/`, which maps the Android emulator to this computer. A physical phone requires the computer's LAN address or a deployed HTTPS backend; update `RemotePlannerRepository.DEFAULT_BASE_URL` before that build.

PDF QR links use the incoming backend address by default. For a submission/deployed backend, set `JOURNIFY_PUBLIC_BASE_URL` to its public HTTPS origin before starting Django, for example `https://journify.example.com`. A QR containing `10.0.2.2` works only inside the Android emulator and cannot be opened by another phone.

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
