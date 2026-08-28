"""
Utilities for sorting POIs and eateries by distance
"""
from .models import Poi, Eatery
from .algorithm import haversine_distance


def sorted_items_by_distance(items, start_lat, start_lon, limit=None):
    """Attach distance (km) to iterable of dicts and return sorted copy."""
    enriched = []
    for entry in items:
        lat = entry.get('latitude')
        lon = entry.get('longitude')
        if lat is None or lon is None:
            distance = float('inf')
        else:
            distance = haversine_distance(start_lat, start_lon, lat, lon)

        enriched.append({
            **entry,
            'distance_km': round(distance, 2) if distance != float('inf') else None,
        })

    enriched.sort(key=lambda item: item['distance_km'] if item['distance_km'] is not None else float('inf'))

    if limit is not None:
        return enriched[:limit]
    return enriched


def get_sorted_pois(start_lat, start_lon, limit=None):
    """Return POIs sorted by distance from the provided coordinates."""
    pois = Poi.objects.values(
        'id', 'name', 'address', 'open_hours', 'rating', 'price_per_person',
        'tags', 'tiktok_link', 'latitude', 'longitude', 'highlight'
    )
    return sorted_items_by_distance(list(pois), start_lat, start_lon, limit=limit)


def get_sorted_eateries(start_lat, start_lon, limit=None):
    """Return eateries sorted by distance from the provided coordinates."""
    eateries = list(Eatery.objects.values(
        'id', 'name', 'address', 'open_hours', 'price_min', 'price_max',
        'time_tags', 'tiktok_link', 'latitude', 'longitude'
    ))

    for entry in eateries:
        price_choice = entry.get('price_max') or entry.get('price_min')
        entry['budget_price'] = price_choice if price_choice is not None else 0

    return sorted_items_by_distance(eateries, start_lat, start_lon, limit=limit)
