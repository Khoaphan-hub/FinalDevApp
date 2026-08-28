# Project Structure Guide

## Running the Project

1. Open PowerShell and move into the project folder:
   
	```powershell
	cd "C:/Users/phana/OneDrive/Desktop/APCS/APCS 2025/Computational Thinking/Project/firstsite"
	```
   
2. Activate the virtual environment with all required packages:
   
	```powershell
	.\Khoa\Scripts\Activate.ps1
	```
   
3. First-time setup requires database migrations and data seeding:
   
	```powershell
	python manage.py migrate
	python manage.py load_data
	```
   
4. Launch the local server:
   
	```powershell
	python manage.py runserver
	```
   
5. Visit http://127.0.0.1:8000/ in your browser.

## Project Layout Overview

```
firstsite/
├─ manage.py
├─ requirements.txt
├─ dalat_pois.csv, dalat_eateries.csv (seed data)
├─ db.sqlite3 (development database)
├─ firstsite/                # Django project configuration
│  ├─ settings.py            # Installed apps, middleware, templates/static config
│  ├─ urls.py                # Root URL routes mapped to home.views
│  ├─ asgi.py / wsgi.py      # ASGI/WSGI application entry points
│  └─ __init__.py
└─ home/                     # Main application logic
	 ├─ admin.py               # Model admin registration (extend as needed)
	 ├─ algorithm.py           # Itinerary generation engine
	 ├─ ai_service.py          # FAQ/chat answer lookup
	 ├─ constants.py           # Shared constants
	 ├─ forms.py (if added)    # Django forms (not currently used)
	 ├─ geocode.py             # Geocoding helpers
	 ├─ management/commands/   # Custom CLI commands
	 │  └─ load_data.py        # Loads CSVs, refreshes caches
	 ├─ migrations/            # Database schema history
	 ├─ models.py              # Poi, Eatery, Profile models
	 ├─ prefix_tree.py         # Trie utilities for search suggestions
	 ├─ search.py              # Additional search helpers
	 ├─ static/home/js/        # Front-end behaviour (trip_planner.js, chatbot.js)
	 ├─ Templates/             # HTML pages rendered by views
	 │  ├─ welcome.html        # Landing page
	 │  ├─ trip_setup.html     # Step 1 & 2: trip constraints and starting point
	 │  ├─ trip_selection_combined.html # Step 3 & 4: combined POI and eatery selection
	 │  ├─ itinerary.html      # Itinerary detail view
	 │  ├─ profile.html        # User profile page
	 │  └─ base.html           # Shared layout
	 ├─ templatetags/          # Custom template filters (currency formatting)
	 ├─ tests.py               # Django unit tests
	 ├─ urls.py (if added)     # App-specific routes (currently using project urls)
	 └─ views.py               # Request handlers and API endpoints
```

## Request and View Flow

### Root Routing (`firstsite/urls.py`)
- `/` → `welcome_view`: landing page with navigation to login/register/trip generator.
- `/generate/` → `trip_setup_view`: Step 1 & 2 form (days, POI limit, budget, starting point, mood).
- `/trip-selection/` → `trip_selection_combined`: Step 3 & 4 combined interface for selecting POIs and eateries.
- `/trip-selection/process/` → `process_trip_selection`: handles form submission and generates itinerary.
- `/plan/itinerary/` → `trip_itinerary_view`: displays the generated trip plan with map.
- `/api/search-suggestions/`: prefix tree-based search endpoint for POIs and eateries.
- `/api/geocode-and-sort/`: sorts locations by distance from a coordinate.
- `/api/toggle-poi-selection/`: AJAX endpoint for toggling selections.
- `/logout/`: authentication logout.
- `/chat/`, `/api/chat/`: chatbot interface powered by `ai_service`.
- `/profile/`: user profile management page.

### `trip_selection_combined` (`home/views.py::trip_selection_combined`)
- Displays the combined Step 3 & 4 interface in `trip_selection_combined.html`.
- On GET: 
	- Retrieves trip setup data (days, budget, mood, starting point) from session.
	- Filters POIs by mood tags if a mood was selected in Step 2.
	- Sorts all POIs and eateries by distance from the starting point using haversine formula.
	- Separates POIs into mood-matched and remaining lists for display.
	- Passes sorted, distance-annotated lists to the template.

### `process_trip_selection` (`home/views.py::process_trip_selection`)
- Handles form submission from `trip_selection_combined.html`.
- Processes selected POI and eatery IDs, custom entries.
- Validates budget constraints and POI limits.
- Generates itinerary using the algorithm module.
- Stores results in session and redirects to itinerary view.

### Support Functions in `home/views.py`
- `search_suggestions_view`: returns JSON suggestions using prefix tree for real-time search.
- `toggle_poi_selection_view`: toggles POI selection via AJAX.
- `geocode_and_sort_view`: sorts locations by distance from a provided coordinate.
- Authentication views (`logout_view`) and chatbot class-based views.

## Front-End Behaviour
- Template `trip_selection_combined.html` features:
	- Real-time prefix tree search with thumbnails for POIs and eateries.
	- Mood-based grouping ("Matches Your Mood" and "More Options" sections).
	- Distance display for each location from the starting point.
	- Custom POI/eatery input forms with dynamic row addition.
	- Budget tracking sidebar showing remaining budget.
	- Interactive Leaflet map with markers for all locations.
	- Tab-based navigation between Destinations and Eateries.
- `chatbot.js` powers the chat widget associated with `/chat/`.

## Itinerary Algorithm (`home/algorithm.py`)
1. Converts selected POIs/eateries (plus custom additions) into pandas DataFrames, ensuring latitude/longitude data is present.
2. Checks overall capacity (`num_days * daily_poi_limit`), returning an error if too many POIs are selected.
3. Processes eateries, normalizing their time tags into Vietnamese slots (`Sang`, `Trua`, `Toi`).
4. Clusters POIs geographically with KMeans; `redistribute_pois` shifts POIs between clusters until each day respects the POI limit.
5. For each day, orders POIs greedily by proximity, inserts meals near the path when preferred eateries match the appropriate slot, and tracks travel distances between stops using `haversine_distance`.
6. Returns a dictionary keyed by day number, containing ordered stops (each stop includes `type`, `name`, `address`, `slot`, and `travel_from_prev_km`). Views and templates consume this structure to render tables.

## Data Layer and Utilities
- `home/models.py`
	- `Poi`: core fields for tourist destinations, including `price_per_person` (Decimal) and coordinates.
	- `Eatery`: includes optional `time_tags`, min/max price integers, and TikTok links.
	- `Profile`: extends Django `User` via OneToOne relation, created automatically with a signal.
- `home/management/commands/load_data.py`
	- `python manage.py load_data` wipes existing POIs/eateries, parses the CSVs, stores prices as decimals/integers, and refreshes search/price caches.
- `home/prefix_tree.py`
	- Normalizes Vietnamese text to ASCII, builds a trie, and returns suggestion payloads while deduplicating by (type, id).
- `home/ai_service.py` and related chat views handle question/answer lookups for the chatbot route.
- `home/templatetags/currency_filters.py` adds `vnd_format` for currency rendering in templates.
- `home/tests.py` holds Django tests; expand it when adding new logic.

## Where to Modify
- **UI changes**: edit templates (`home/Templates`) and associated CSS/JS.
- **Budget or selection logic**: adjust `home/views.py` (especially inside the `home` view).
- **Itinerary rules**: update `home/algorithm.py` and ensure the result structure still matches template expectations.
- **Data model tweaks**: edit `home/models.py`, run migrations, update loaders/templates accordingly.
- **Search/autocomplete behaviour**: tweak `_serialize_*` helpers, trie utilities, and front-end JS handling.

Use this document as a quick reference for navigating the codebase and planning targeted updates.
