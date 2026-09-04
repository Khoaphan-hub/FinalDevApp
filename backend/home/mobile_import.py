"""Read a QR snapshot for native preview, without changing the web session or catalog."""
import math
import re
from decimal import Decimal

from django.http import JsonResponse
from django.views.decorators.http import require_GET

from .models import Eatery, ItineraryResumeToken, Poi


def _failure(code, status):
    return JsonResponse({'success': False, 'code': code}, status=status)


def _number(value, default=0):
    result = float(default if value is None else value)
    if not math.isfinite(result):
        raise ValueError('Non-finite value in snapshot')
    return result


def _text(value):
    return '' if value is None else str(value)


def snapshot_for_mobile(request, payload):
    # Shared serialization keeps photos/review links consistent with the catalog.
    from .mobile_api import _serialize_catalog_item

    planner = payload['planner_itinerary']
    results = planner['results']
    if not isinstance(results, dict) or not 1 <= len(results) <= 31:
        raise ValueError('Invalid days')
    entries = []
    day_numbers = set()
    for key, stops in results.items():
        day = int(key)
        if day < 1 or day in day_numbers or not isinstance(stops, list) or not stops:
            raise ValueError('Invalid day')
        day_numbers.add(day)
        for stop in stops:
            if not isinstance(stop, dict) or stop.get('type', 'POI') not in ('POI', 'EATERY', 'ACCOMMODATION'):
                raise ValueError('Invalid stop')
            entries.append(stop)
    if len(entries) > 500:
        raise ValueError('Too many stops')
    catalogs = {
        kind: model.objects.in_bulk({int(s.get('id') or 0) for s in entries if s.get('type', 'POI') == kind})
        for kind, model in [('POI', Poi), ('EATERY', Eatery)]
    }
    days = []
    for key in sorted(results, key=int):
        stops = []
        for source in results[key]:
            kind = source.get('type', 'POI')
            identifier = int(source.get('id') or 0)
            item = catalogs.get(kind, {}).get(identifier)
            stop = _serialize_catalog_item(request, item, kind.lower()) if item else {}
            latitude = _number(source.get('lat', source.get('latitude')))
            longitude = _number(source.get('lon', source.get('longitude')))
            if not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
                raise ValueError('Invalid coordinates')
            stop.update({
                'id': identifier, 'type': kind,
                'name': _text(source.get('name') or stop.get('name')),
                'address': _text(source.get('address') or stop.get('address')),
                'latitude': latitude, 'longitude': longitude,
                'travel_to_next_km': max(0, _number(source.get('travel_to_next_km'))),
                'meal_slot': source.get('slot', source.get('meal_slot')),
            })
            # New PDFs retain the original quoted price/details; old PDFs use catalog fallback.
            for field in ('open_hours', 'tags', 'highlight', 'map_name', 'map_address'):
                if source.get(field) is not None:
                    stop[field] = _text(source[field])
            stop['price'] = str(max(0, _number(source.get('price', stop.get('price')))))
            stop['rating'] = max(0, min(5, _number(source.get('rating', stop.get('rating')))))
            # Only catalog URLs are returned, never a client-controlled image URL in a QR snapshot.
            stop.setdefault('image_url', None)
            stop.setdefault('media_url', None)
            stops.append(stop)
        days.append({'day': int(key), 'stops': stops})
    total = Decimal(str(payload.get('trip_setup', {}).get('budget') or 0))
    estimated = Decimal(str(planner.get('total_selected_cost') or 0))
    if not total.is_finite() or not estimated.is_finite() or total < 0 or estimated < 0:
        raise ValueError('Invalid budget')
    return {'title': _text(payload.get('title') or 'Journify')[:150], 'days': days,
            'budget': {'total': str(total), 'estimated': str(estimated), 'remaining': str(total - estimated)}}


@require_GET
def mobile_import_itinerary(request, token):
    # Possession of the unguessable QR token grants read access, matching existing /resume/ links.
    if not re.fullmatch(r'[A-Za-z0-9_-]{16,48}', token):
        return _failure('INVALID_QR', 400)
    resume = ItineraryResumeToken.objects.filter(token=token).first()
    if resume is None:
        return _failure('NOT_FOUND', 404)
    if resume.is_expired():
        return _failure('EXPIRED', 410)
    try:
        data = snapshot_for_mobile(request, resume.payload)
    except (KeyError, ValueError, TypeError, AttributeError, ArithmeticError):
        return _failure('INVALID_SNAPSHOT', 422)
    response = JsonResponse({'success': True, 'data': data})
    response['Cache-Control'] = 'no-store'
    response['Referrer-Policy'] = 'no-referrer'
    return response
