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
.\.venv\Scripts\python.exe manage.py migrate
.\.venv\Scripts\python.exe manage.py createcachetable
.\.venv\Scripts\python.exe manage.py load_data
.\.venv\Scripts\python.exe manage.py check
.\.venv\Scripts\python.exe manage.py runserver 0.0.0.0:8000 --noreload
```

`db.sqlite3` is not in version control, so the three database commands are required on a
fresh clone. Skipping them leaves the catalog empty and every request from the app fails
with `no such table: home_poi`. `load_data` imports 79 attractions and 163 eateries from the
CSV files, including their English translations.

The Android emulator always uses `http://10.0.2.2:8000/`. A physical phone uses the LAN IPv4 address configured in each developer's **own** `local.properties`; there is no shared developer IP to edit in Java.

### Tester setup — own phone over Wi-Fi

1. Switch to `integration/merge-all` and pull that branch, not `main`. This integration build is awaiting testing.
2. Prepare and start Django on the tester's computer using the steps above. A fresh database does not contain another member's accounts; register a test user through the app once the backend is ready.
3. Run `ipconfig` on the computer running Django and find the **IPv4 Address of its Wi-Fi adapter** (not the phone's IP or the default gateway).
4. Open `local.properties` next to `settings.gradle.kts`. Keep the existing `sdk.dir` line and add or update this entry, replacing the example with that computer's IPv4 address:

   ```properties
   journify.devServerIp=192.168.1.25
   ```

5. Keep the phone and backend computer on the same Wi-Fi. Connect USB with USB debugging enabled for installation, select the phone in Android Studio, sync Gradle and press Run to rebuild/install. API requests use Wi-Fi; `adb reverse` is not required.

`local.properties` is already ignored by Git: do not force-add it or replace someone else's SDK path. Each member's IP stays local when pulling/pushing shared code. The value is read **at build time**, so after changing IP, rebuild/reinstall the app; changing this file does not reconfigure an APK already installed on a phone. Do not include `http://`, `:8000`, or a trailing slash in the property. The backend port remains `8000`.

Without the property, the build falls back to the emulator host `10.0.2.2`. Emulator testing works with no IP setup; physical-phone testers must set the property. If the computer changes Wi-Fi/IP, update only this local entry and rebuild. If connection still fails, check that Django is running on `0.0.0.0:8000` and that the network/firewall allows the phone to reach that computer.

The LAN address is only a development convenience. A distributed APK must use a deployed HTTPS backend rather than a private `192.168.x.x` address.

PDF QR links use the incoming backend address by default. For a submission/deployed backend, set `JOURNIFY_PUBLIC_BASE_URL` to its public HTTPS origin before starting Django, for example `https://journify.example.com`. A QR containing `10.0.2.2` works only inside the Android emulator and cannot be opened by another phone.

The PDF route graphic is a compact coordinate-based overview designed to remain available without a paid static-map service. For turn-by-turn road geometry, open the itinerary's map screen in Journify. Place photos, current weather and newly generated resume QR links require network access during export; missing optional data is represented by a clear fallback instead of failing the whole PDF.

Weather is loaded directly from Open-Meteo for Đà Lạt and needs no API key. If the device is offline, the card shows a clear retry action.

The warnings about optional `sentence-transformers` and `google-generativeai` can be ignored for the mobile itinerary flow. They only disable the web chatbot, which replies that AI features are unavailable.

`DEBUG` defaults to on for local development. Set `DJANGO_DEBUG=False` when deploying. Django
then stops serving `/static/` itself, so the place photos the app loads need `manage.py
collectstatic` plus a real static file server (or a host that serves `STATIC_ROOT`) in front.
Set `DJANGO_ALLOWED_HOSTS` to the deployed hostname as well; the permissive `*` default exists
only for emulator and LAN testing.

## Build Android

Open this folder in Android Studio and run the `app` configuration, or run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"
.\gradlew.bat assembleDebug
```

`JAVA_HOME` must point at the JBR inside your own Android Studio installation; the path above
is the Windows default. A command-line build also needs `local.properties` in the repository
root, which is not in version control. Android Studio writes it on first open; to create it by
hand, add one line with the colon escaped:

```
sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Project memory

Read `JOURNIFY_MIGRATION.md` before continuing development. It records the original web flow, architecture decisions, lecturer requirements, completed milestones and current next actions.
