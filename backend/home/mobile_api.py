import json
from decimal import Decimal, InvalidOperation

from django.db.models import Q
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_GET, require_POST

from .algorithm import generate_itinerary
from .constants import DEFAULT_FALLBACK_COORDS, TRAVEL_MOOD_TAGS
from .models import Eatery, Poi
from .mobile_share import build_resume_snapshot, build_resume_url, create_resume_token, qr_png_base64
from .search import get_search_tree
from .trip_planner import _auto_fill_selections, _build_trip_state, normalize_tag_value


def _error(message, status=400, field_errors=None):
    return JsonResponse({
        'success': False,
        'message': message,
        'field_errors': field_errors or {},
    }, status=status)


def _image_url(request, item_type, image_code):
    if not image_code:
        return None
    folder = 'pois' if item_type == 'POI' else 'eateries'
    return request.build_absolute_uri(f'/static/home/images/{folder}/{image_code}.png')


def _default_start_label(language):
    """Human-readable address for the default starting point (Da Lat Market).

    DEFAULT_FALLBACK_LABEL is an internal sentinel and must not reach the UI.
    """
    market = Poi.objects.filter(image_code='P001').first()
    if market:
        label = (market.address_en or market.address) if language == 'en' else market.address
        if label:
            return label
    return 'Da Lat Market, Da Lat' if language == 'en' else 'Chợ Đà Lạt, Đà Lạt'


def _serialize_catalog_item(request, item, item_type):
    if item_type == 'poi':
        price = item.price_per_person
        time_tags = None
        highlight = item.highlight
    else:
        price = item.price_max if item.price_max is not None else item.price_min
        time_tags = item.time_tags
        highlight = None
    return {
        'id': item.id,
        'type': item_type.upper(),
        'name': item.name,
        'name_en': item.name_en,
        'address': item.address,
        'address_en': item.address_en,
        'rating': item.rating,
        'price': str(price or 0),
        'latitude': item.latitude,
        'longitude': item.longitude,
        'tags': item.tags if item_type == 'poi' else time_tags,
        'open_hours': item.open_hours,
        'highlight': highlight,
        'media_url': item.tiktok_link,
        'image_code': item.image_code,
        'image_url': _image_url(request, item_type.upper(), item.image_code),
    }


@require_GET
def mobile_catalog(request):
    item_type = request.GET.get('type', 'poi').lower()
    query = request.GET.get('query', '').strip()
    try:
        limit = min(max(int(request.GET.get('limit', 50)), 1), 100)
    except ValueError:
        limit = 50

    if item_type == 'poi':
        queryset = Poi.objects.all().order_by('-rating', 'name')
    elif item_type == 'eatery':
        queryset = Eatery.objects.all().order_by('-rating', 'name')
    else:
        return _error("type must be 'poi' or 'eatery'", field_errors={'type': 'Unsupported catalog type.'})

    if query:
        queryset = queryset.filter(
            Q(name__icontains=query) | Q(name_en__icontains=query) |
            Q(address__icontains=query) | Q(address_en__icontains=query)
        )

    items = [_serialize_catalog_item(request, item, item_type) for item in queryset[:limit]]
    return JsonResponse({'success': True, 'data': {'items': items, 'count': len(items)}})


@require_GET
def mobile_search_suggestions(request):
    item_type = request.GET.get('type', 'poi').lower()
    query = request.GET.get('q', '').strip()
    try:
        limit = min(max(int(request.GET.get('limit', 20)), 1), 25)
    except ValueError:
        limit = 20

    if item_type not in ('poi', 'eatery'):
        return _error("type must be 'poi' or 'eatery'", field_errors={'type': 'Unsupported catalog type.'})
    if not query:
        return JsonResponse({'success': True, 'data': {'items': [], 'count': 0}})

    suggestion_payloads = get_search_tree().suggest(
        query,
        limit=limit,
        item_type=item_type.upper(),
    )
    model = Poi if item_type == 'poi' else Eatery
    ordered_ids = [payload['id'] for payload in suggestion_payloads]
    items_by_id = model.objects.in_bulk(ordered_ids)
    items = [
        _serialize_catalog_item(request, items_by_id[item_id], item_type)
        for item_id in ordered_ids
        if item_id in items_by_id
    ]
    return JsonResponse({'success': True, 'data': {'items': items, 'count': len(items)}})


@csrf_exempt
@require_POST
def mobile_create_itinerary_share(request):
    try:
        payload = json.loads(request.body.decode('utf-8'))
        snapshot = build_resume_snapshot(payload)
        resume = create_resume_token(snapshot)
        share_url = build_resume_url(request, resume.token)
        qr_base64 = qr_png_base64(share_url)
    except (json.JSONDecodeError, UnicodeDecodeError):
        return _error('Request body must be valid JSON.')
    except ValueError as error:
        return _error(str(error))
    except ImportError:
        return _error('QR generator is unavailable on this server.', status=503)

    return JsonResponse({
        'success': True,
        'data': {
            'share_url': share_url,
            'qr_base64': qr_base64,
            'expires_at': resume.expires_at.isoformat(),
        },
    })


@csrf_exempt
@require_POST
def mobile_generate_itinerary(request):
    try:
        payload = json.loads(request.body.decode('utf-8'))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return _error('Request body must be valid JSON.')

    field_errors = {}
    try:
        days = int(payload.get('days'))
        if days < 1 or days > 7:
            field_errors['days'] = 'Days must be between 1 and 7.'
    except (TypeError, ValueError):
        days = 0
        field_errors['days'] = 'A valid number of days is required.'

    try:
        daily_limit = int(payload.get('daily_poi_limit'))
        if daily_limit < 1 or daily_limit > 6:
            field_errors['daily_poi_limit'] = 'POIs per day must be between 1 and 6.'
    except (TypeError, ValueError):
        daily_limit = 0
        field_errors['daily_poi_limit'] = 'A valid daily POI limit is required.'

    try:
        budget = Decimal(str(payload.get('budget')))
        if budget <= 0:
            field_errors['budget'] = 'Budget must be greater than zero.'
    except (InvalidOperation, TypeError):
        budget = Decimal('0')
        field_errors['budget'] = 'A valid budget is required.'

    moods = payload.get('moods') or []
    if isinstance(moods, str):
        moods = [moods]
    moods = [mood for mood in moods if mood in TRAVEL_MOOD_TAGS]
    if not moods:
        field_errors['moods'] = 'Select at least one supported mood.'
    if field_errors:
        return _error('Please check the trip information.', field_errors=field_errors)

    language = payload.get('language', 'vi')
    use_default = bool(payload.get('use_default_center', True))
    start_address = str(payload.get('start_address') or '').strip()
    default_start_label = _default_start_label(language)
    trip_setup = {
        'days': days,
        'max_pois_per_day': daily_limit,
        'budget': str(budget),
        'mood': moods[0],
        'user_address': '' if use_default else start_address,
        'place_name': default_start_label if use_default else start_address,
        'start_location': {
            'lat': DEFAULT_FALLBACK_COORDS[0],
            'lon': DEFAULT_FALLBACK_COORDS[1],
            'address_label': default_start_label if use_default else start_address,
            'fallback_used': use_default,
        },
    }
    trip_state = _build_trip_state(trip_setup)

    tag_labels = []
    for mood in moods:
        tag_labels.extend(TRAVEL_MOOD_TAGS[mood])
    tag_values = list(dict.fromkeys(normalize_tag_value(tag) for tag in tag_labels))

    selected_poi_ids = payload.get('selected_poi_ids') or []
    selected_eatery_ids = payload.get('selected_eatery_ids') or []
    fill = _auto_fill_selections(
        trip_state=trip_state,
        selected_poi_ids=selected_poi_ids,
        selected_eatery_ids=selected_eatery_ids,
        preferred_poi_tag_values=tag_values,
        preferred_poi_tag_labels=tag_labels,
    )
    if fill['total_pois_selected'] < fill['required_poi_count']:
        return _error('Not enough attractions are available for this trip length.')
    if fill['total_eateries_selected'] < fill['required_eatery_count']:
        return _error('Not enough eateries are available for all meal slots.')

    pois = Poi.objects.filter(id__in=fill['selected_poi_ids'])
    eateries = Eatery.objects.filter(id__in=fill['selected_eatery_ids'])
    poi_cost = sum((poi.price_per_person or Decimal('0')) for poi in pois)
    eatery_cost = sum(Decimal(eatery.price_max if eatery.price_max is not None else (eatery.price_min or 0)) for eatery in eateries)
    estimated_cost = poi_cost + eatery_cost
    if estimated_cost > budget:
        return _error(
            f'The selected itinerary costs about {estimated_cost:,.0f} VND, above the provided budget.',
            field_errors={'budget': 'Increase the budget or reduce days/places per day.'},
        )

    itinerary_vi, itinerary_en, algorithm_error = generate_itinerary(
        num_days=days,
        daily_poi_limit=daily_limit,
        selected_pois_qs=pois,
        selected_eateries_qs=eateries,
        accommodation_address=trip_state.get('accommodation_input'),
        use_default_center=use_default,
    )
    if algorithm_error:
        return _error(algorithm_error)

    chosen = itinerary_en if language == 'en' else itinerary_vi
    normalized_days = []
    for day_number, stops in chosen.items():
        normalized_stops = []
        for stop in stops:
            item_type = stop.get('type', 'POI')
            item = None
            if item_type == 'POI' and stop.get('id'):
                item = Poi.objects.filter(id=stop['id']).first()
            elif item_type == 'EATERY' and stop.get('id'):
                item = Eatery.objects.filter(id=stop['id']).first()
            image_code = item.image_code if item else None
            item_price = Decimal('0')
            if item_type == 'POI' and item:
                item_price = item.price_per_person or Decimal('0')
            elif item_type == 'EATERY' and item:
                item_price = Decimal(item.price_max if item.price_max is not None else (item.price_min or 0))
            normalized_stops.append({
                'type': item_type,
                'id': stop.get('id', 0),
                'name': stop.get('name'),
                'address': stop.get('address'),
                'latitude': stop.get('lat'),
                'longitude': stop.get('lon'),
                'travel_to_next_km': stop.get('travel_to_next_km', 0),
                'meal_slot': stop.get('slot'),
                'rating': item.rating if item else None,
                'price': str(item_price),
                'open_hours': item.open_hours if item else None,
                'tags': (item.tags if item_type == 'POI' else item.time_tags) if item else None,
                'highlight': item.highlight if item_type == 'POI' and item else None,
                'media_url': item.tiktok_link if item else None,
                'image_code': image_code,
                'image_url': _image_url(request, item_type, image_code),
            })
        normalized_days.append({'day': int(day_number), 'stops': normalized_stops})

    return JsonResponse({
        'success': True,
        'data': {
            'title': 'Đà Lạt theo cách của bạn' if language != 'en' else 'Da Lat, your way',
            'days': normalized_days,
            'budget': {
                'total': str(budget),
                'estimated': str(estimated_cost),
                'remaining': str(budget - estimated_cost),
            },
            'notices': fill['auto_fill_messages'],
        },
    })

@csrf_exempt
def mobile_profile(request):
    if not request.user.is_authenticated:
        return JsonResponse({"error": "Not authenticated"}, status=401)
    
    from .models import Profile
    profile, _ = Profile.objects.get_or_create(user=request.user)
    
    if request.method == "GET":
        avatar_url = ""
        if profile.avatar:
            avatar_url = request.build_absolute_uri(profile.avatar.url)
        
        return JsonResponse({
            "username": request.user.username,
            "email": request.user.email,
            "phone_number": profile.phone_number,
            "avatar_url": avatar_url
        })
    elif request.method == "POST":
        if request.content_type == "application/json":
            try:
                data = json.loads(request.body)
                request.user.email = data.get("email", request.user.email)
                profile.phone_number = data.get("phone_number", profile.phone_number)
            except json.JSONDecodeError:
                return JsonResponse({"error": "Invalid JSON"}, status=400)
        else:
            request.user.email = request.POST.get("email", request.user.email)
            profile.phone_number = request.POST.get("phone_number", profile.phone_number)
            if 'avatar' in request.FILES:
                profile.avatar = request.FILES['avatar']
        
        request.user.save()
        profile.save()
        return JsonResponse({"success": True})
    
    return JsonResponse({"error": "Method not allowed"}, status=405)
