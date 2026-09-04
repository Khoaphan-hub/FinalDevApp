"""Create resumable itinerary links and QR images for the native Android app."""

from __future__ import annotations

import base64
from copy import deepcopy
from datetime import timedelta
from io import BytesIO
import secrets
from urllib.parse import urljoin

from django.conf import settings
from django.urls import reverse
from django.utils import timezone

from .models import ItineraryResumeToken


QR_LINK_TTL_DAYS = 30


def build_resume_snapshot(itinerary: dict) -> dict:
    days = itinerary.get('days')
    if not isinstance(days, list) or not days:
        raise ValueError('Itinerary must contain at least one day.')

    results = {}
    selected_poi_ids = []
    selected_eatery_ids = []
    for day in days:
        try:
            day_number = int(day.get('day_number', day.get('day')))
        except (AttributeError, TypeError, ValueError):
            raise ValueError('Each itinerary day needs a valid day number.')
        stops = []
        for stop in day.get('stops') or []:
            item_type = str(stop.get('type') or 'POI').upper()
            item_id = int(stop.get('id') or 0)
            normalized = {
                'type': item_type,
                'id': item_id,
                'name': str(stop.get('name') or ''),
                'address': str(stop.get('address') or ''),
                'lat': float(stop.get('latitude') or 0),
                'lon': float(stop.get('longitude') or 0),
                'travel_to_next_km': float(stop.get('travel_to_next_km') or 0),
                'slot': stop.get('meal_slot'),
            }
            for field in ('price', 'rating', 'open_hours', 'tags', 'highlight', 'map_name', 'map_address'):
                if stop.get(field) is not None:
                    normalized[field] = stop[field]
            stops.append(normalized)
            if item_id > 0 and item_type == 'POI':
                selected_poi_ids.append(item_id)
            elif item_id > 0 and item_type == 'EATERY':
                selected_eatery_ids.append(item_id)
        results[str(day_number)] = stops

    total_budget = int(itinerary.get('total_budget_vnd', itinerary.get('total_budget')) or 0)
    estimated_cost = int(itinerary.get('estimated_cost_vnd', itinerary.get('estimated_cost')) or 0)
    planner_itinerary = {
        'results': deepcopy(results),
        'results_en': deepcopy(results),
        'total_selected_cost': estimated_cost,
        'budget_remaining': total_budget - estimated_cost,
        'selected_counts': {
            'pois': len(set(selected_poi_ids)),
            'eateries': len(set(selected_eatery_ids)),
        },
    }
    trip_setup = {
        'days': len(results),
        'budget': str(total_budget),
        'mood': 'journify',
        'place_name': 'Chợ Đà Lạt',
        'start_location': {
            'lat': 11.942964,
            'lon': 108.436867,
            'address_label': 'Chợ Đà Lạt',
            'fallback_used': True,
        },
    }
    return {
        'title': str(itinerary.get('title') or 'Đà Lạt theo cách của bạn'),
        'trip_setup': trip_setup,
        'planner_itinerary': planner_itinerary,
        'itinerary_results': deepcopy(planner_itinerary),
        'selected_poi_ids': list(dict.fromkeys(selected_poi_ids)),
        'shared_selected_eatery_ids': list(dict.fromkeys(selected_eatery_ids)),
    }


def create_resume_token(snapshot: dict) -> ItineraryResumeToken:
    token = None
    for _ in range(10):
        candidate = secrets.token_urlsafe(24)
        if not ItineraryResumeToken.objects.filter(token=candidate).exists():
            token = candidate
            break
    if token is None:
        raise RuntimeError('Unable to allocate resume token.')
    now = timezone.now()
    return ItineraryResumeToken.objects.create(
        token=token,
        payload=snapshot,
        expires_at=now + timedelta(days=QR_LINK_TTL_DAYS),
        last_accessed=now,
    )


def build_resume_url(request, token: str) -> str:
    path = reverse('resume-itinerary', args=[token])
    public_base = str(getattr(settings, 'JOURNIFY_PUBLIC_BASE_URL', '') or '').strip()
    if public_base:
        return urljoin(public_base.rstrip('/') + '/', path.lstrip('/'))
    return request.build_absolute_uri(path)


def qr_png_base64(value: str) -> str:
    import qrcode

    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_H,
        box_size=8,
        border=4,
    )
    qr.add_data(value)
    qr.make(fit=True)
    image = qr.make_image(fill_color='#173f35', back_color='#ffffff')
    output = BytesIO()
    image.save(output, format='PNG')
    return base64.b64encode(output.getvalue()).decode('ascii')
