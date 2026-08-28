import pprint
import re
from decimal import Decimal, InvalidOperation
from itertools import zip_longest
from typing import Dict, List, Tuple

from django.shortcuts import redirect, render

from .algorithm import generate_itinerary, haversine_distance
from .constants import DEFAULT_FALLBACK_COORDS, DEFAULT_FALLBACK_LABEL, TRAVEL_MOOD_TAGS
from .models import Eatery, Poi
from .sort import get_sorted_eateries, get_sorted_pois
from .utils import normalize_time_tags, normalize_user_slot, slot_vi_label

CURRENCY_SANITIZE_PATTERN = re.compile(r'[^0-9.]')


def clean_currency_string(raw_value):
    """Remove grouping characters while preserving a single decimal point."""
    if raw_value is None:
        return ''

    candidate = str(raw_value)
    if not candidate:
        return ''

    cleaned = CURRENCY_SANITIZE_PATTERN.sub('', candidate)
    if cleaned.count('.') <= 1:
        return cleaned

    first, remainder = cleaned.split('.', 1)
    remainder = remainder.replace('.', '')
    return f"{first}.{remainder}" if remainder else first


def normalize_tag_value(raw_value):
    if not raw_value:
        return ''
    return str(raw_value).strip().lower()


def extract_poi_tags(raw_tags):
    tokens = []
    if not raw_tags:
        return tokens
    normalized = str(raw_tags).replace('/', ',')
    for token in normalized.split(','):
        cleaned = token.strip()
        if cleaned:
            tokens.append(cleaned)
    return tokens


def _safe_decimal(value, default="0"):
    try:
        return Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError):
        return Decimal(str(default))


def _build_trip_state(trip_setup):
    if not trip_setup:
        return None

    try:
        trip_days = int(trip_setup.get('days'))
        trip_max_pois_per_day = int(trip_setup.get('max_pois_per_day'))
    except (TypeError, ValueError):
        return None

    budget_value = str(trip_setup.get('budget') or '').strip()
    if not budget_value:
        return None

    try:
        budget_amount = Decimal(budget_value)
    except InvalidOperation:
        return None

    if budget_amount < 0:
        return None

    start_location = trip_setup.get('start_location') or {
        'lat': DEFAULT_FALLBACK_COORDS[0],
        'lon': DEFAULT_FALLBACK_COORDS[1],
        'address_label': DEFAULT_FALLBACK_LABEL,
        'fallback_used': True,
    }

    start_lat = start_location.get('lat', DEFAULT_FALLBACK_COORDS[0])
    start_lon = start_location.get('lon', DEFAULT_FALLBACK_COORDS[1])

    return {
        'trip_setup': trip_setup,
        'trip_days': trip_days,
        'trip_max_pois_per_day': trip_max_pois_per_day,
        'budget_value': budget_value,
        'budget_amount': budget_amount,
        'start_location': start_location,
        'start_lat': start_lat,
        'start_lon': start_lon,
        'start_address_label': start_location.get('address_label', DEFAULT_FALLBACK_LABEL),
        'start_fallback_used': bool(start_location.get('fallback_used', False)),
        # Prefer explicit user address, but fall back to place_name if provided.
        'user_address_input': (trip_setup.get('user_address') or '').strip(),
        'accommodation_input': ((trip_setup.get('user_address') or '').strip() or (trip_setup.get('place_name') or '').strip()),
    }


def _sanitize_int_list(raw_values):
    sanitized = []
    seen = set()
    for value in raw_values or []:
        str_value = str(value).strip()
        if not str_value or not str_value.lstrip('-').isdigit():
            continue
        num = int(str_value)
        if num in seen:
            continue
        seen.add(num)
        sanitized.append(num)
    return sanitized


def _find_matching_poi(name, address):
    name_clean = (name or '').strip()
    address_clean = (address or '').strip()
    if not name_clean:
        return None

    exact = Poi.objects.filter(name__iexact=name_clean).first()
    if exact:
        return exact

    if address_clean:
        combined = Poi.objects.filter(name__icontains=name_clean, address__icontains=address_clean).first()
        if combined:
            return combined

    partial = Poi.objects.filter(name__icontains=name_clean).first()
    if partial:
        return partial

    if address_clean:
        return Poi.objects.filter(address__icontains=address_clean).first()

    return None


def _find_matching_eatery(name, address):
    name_clean = (name or '').strip()
    address_clean = (address or '').strip()
    if not name_clean:
        return None

    exact = Eatery.objects.filter(name__iexact=name_clean).first()
    if exact:
        return exact

    if address_clean:
        combined = Eatery.objects.filter(name__icontains=name_clean, address__icontains=address_clean).first()
        if combined:
            return combined

    partial = Eatery.objects.filter(name__icontains=name_clean).first()
    if partial:
        return partial

    if address_clean:
        return Eatery.objects.filter(address__icontains=address_clean).first()

    return None


def _hydrate_custom_pois(raw_rows: List[Dict]) -> Tuple[List[Dict], Decimal]:
    extra_pois = []
    extra_budget = Decimal('0')

    for index, row in enumerate(raw_rows or [], start=1):
        try:
            latitude_val = float(row.get('latitude'))
            longitude_val = float(row.get('longitude'))
            price_decimal = Decimal(str(row.get('price')))
        except (TypeError, ValueError, InvalidOperation):
            continue

        extra_pois.append({
            'id': -index,
            'name': row.get('name'),
            'address': row.get('address'),
            'tags': row.get('tags', ''),
            'rating': None,
            'latitude': latitude_val,
            'longitude': longitude_val,
            'price_per_person': float(price_decimal),
        })
        extra_budget += price_decimal

    return extra_pois, extra_budget


def _get_selected_mood_info(trip_state):
    trip_setup = trip_state.get('trip_setup') or {}
    mood = trip_setup.get('mood')
    labels = TRAVEL_MOOD_TAGS.get(mood, [])
    values = []
    for tag in labels:
        normalized = normalize_tag_value(tag)
        if normalized:
            values.append(normalized)
    return mood, labels, values


def _poi_matches_tag_set(poi_obj, tag_set):
    if not tag_set:
        return False
    poi_tags = {
        normalize_tag_value(token)
        for token in extract_poi_tags(getattr(poi_obj, 'tags', ''))
    }
    return bool(poi_tags & tag_set)


def _partition_pois_by_mood(unselected_pois, tag_set):
    if not tag_set:
        return [], list(unselected_pois)

    mood_matches = []
    remainder = []
    for poi in unselected_pois:
        if _poi_matches_tag_set(poi, tag_set):
            mood_matches.append(poi)
        else:
            remainder.append(poi)
    return mood_matches, remainder


def _auto_fill_selections(
    trip_state,
    selected_poi_ids=None,
    extra_pois=None,
    selected_eatery_ids=None,
    extra_eateries=None,
    preferred_poi_tag_values=None,
    preferred_poi_tag_labels=None,
):
    """Auto-fill POI and eatery selections until trip constraints are satisfied."""

    selected_poi_ids = list(selected_poi_ids or [])
    extra_pois = list(extra_pois or [])
    selected_eatery_ids = list(selected_eatery_ids or [])
    extra_eateries = list(extra_eateries or [])
    preferred_poi_tag_values = list(preferred_poi_tag_values or [])
    preferred_poi_tag_labels = list(preferred_poi_tag_labels or [])

    preferred_poi_tags = set(preferred_poi_tag_values)

    autofill_lat = trip_state['start_lat'] if trip_state['start_lat'] is not None else DEFAULT_FALLBACK_COORDS[0]
    autofill_lon = trip_state['start_lon'] if trip_state['start_lon'] is not None else DEFAULT_FALLBACK_COORDS[1]

    required_poi_count = trip_state['trip_days'] * trip_state['trip_max_pois_per_day']
    required_eatery_count = trip_state['trip_days'] * 3

    auto_fill_messages = []

    existing_poi_ids = set(selected_poi_ids)
    auto_filled_poi_ids = set()
    preferred_tag_candidate_ids = set()
    tag_based_fill_count = 0
    total_pois_selected = len(selected_poi_ids) + len(extra_pois)

    if total_pois_selected < required_poi_count:
        sorted_pois = get_sorted_pois(autofill_lat, autofill_lon)

        if preferred_poi_tags:
            tag_matched_entries = []
            fallback_entries = []
            for entry in sorted_pois:
                entry_tags = {
                    normalize_tag_value(token)
                    for token in extract_poi_tags(entry.get('tags'))
                }
                if entry_tags & preferred_poi_tags:
                    tag_matched_entries.append(entry)
                    if entry.get('id') is not None:
                        preferred_tag_candidate_ids.add(entry.get('id'))
                else:
                    fallback_entries.append(entry)
            candidate_entries = tag_matched_entries + fallback_entries
        else:
            candidate_entries = sorted_pois

        for entry in candidate_entries:
            poi_id = entry.get('id')
            if poi_id is None or poi_id in existing_poi_ids:
                continue
            if entry.get('latitude') is None or entry.get('longitude') is None:
                continue
            selected_poi_ids.append(poi_id)
            existing_poi_ids.add(poi_id)
            auto_filled_poi_ids.add(poi_id)
            if poi_id in preferred_tag_candidate_ids:
                tag_based_fill_count += 1
            total_pois_selected += 1
            if total_pois_selected >= required_poi_count:
                break

    if total_pois_selected < required_poi_count:
        if auto_filled_poi_ids:
            auto_fill_messages.append(
                f"Added {len(auto_filled_poi_ids)} POIs automatically, but only {total_pois_selected} of the {required_poi_count} desired POI slots could be filled with the current data."
            )
        else:
            auto_fill_messages.append(
                f"Only {total_pois_selected} POIs were available out of the {required_poi_count} requested POI slots."
            )
    elif auto_filled_poi_ids:
        auto_fill_messages.append(
            f"Added {len(auto_filled_poi_ids)} POIs automatically to reach the target of {required_poi_count} POI slots."
        )

    if preferred_poi_tags:
        label_text = ", ".join(preferred_poi_tag_labels) or "your selected tags"
        if tag_based_fill_count:
            auto_fill_messages.append(
                f"Applied your POI mood tags ({label_text}) to auto-fill {tag_based_fill_count} spot{'s' if tag_based_fill_count > 1 else ''}."
            )
        elif auto_filled_poi_ids:
            auto_fill_messages.append(
                f"No nearby POIs matched your mood tags ({label_text}), so the closest alternatives were added instead."
            )
        elif total_pois_selected < required_poi_count:
            auto_fill_messages.append(
                f"No additional POIs matched your mood tags ({label_text})."
            )

    auto_filled_eatery_ids = set()
    total_eateries_selected = len(selected_eatery_ids) + len(extra_eateries)

    if total_eateries_selected < required_eatery_count:
        sorted_eateries = get_sorted_eateries(autofill_lat, autofill_lon)
        for entry in sorted_eateries:
            eatery_id = entry.get('id')
            if eatery_id in selected_eatery_ids:
                continue
            if entry.get('latitude') is None or entry.get('longitude') is None:
                continue
            selected_eatery_ids.append(eatery_id)
            auto_filled_eatery_ids.add(eatery_id)
            total_eateries_selected += 1
            if total_eateries_selected >= required_eatery_count:
                break

    if total_eateries_selected < required_eatery_count:
        if auto_filled_eatery_ids:
            auto_fill_messages.append(
                f"Added {len(auto_filled_eatery_ids)} eateries automatically, but only {total_eateries_selected} of the {required_eatery_count} meal slots could be filled with the current data."
            )
        else:
            auto_fill_messages.append(
                f"Only {total_eateries_selected} eateries were available out of the {required_eatery_count} needed meal slots."
            )
    elif auto_filled_eatery_ids:
        auto_fill_messages.append(
            f"Added {len(auto_filled_eatery_ids)} eateries automatically to cover {required_eatery_count} meal slots."
        )

    return {
        'selected_poi_ids': selected_poi_ids,
        'selected_eatery_ids': selected_eatery_ids,
        'total_pois_selected': total_pois_selected,
        'total_eateries_selected': total_eateries_selected,
        'required_poi_count': required_poi_count,
        'required_eatery_count': required_eatery_count,
        'auto_fill_notice': " ".join(auto_fill_messages) if auto_fill_messages else None,
        'auto_fill_messages': auto_fill_messages,
        'auto_filled_poi_ids': auto_filled_poi_ids,
        'auto_filled_eatery_ids': auto_filled_eatery_ids,
        'tag_based_fill_count': tag_based_fill_count,
    }


def _get_cheapest_eateries(required_count):
    """Return the cheapest available eateries (with geo data) and their total price."""

    if required_count <= 0:
        return [], Decimal('0')

    candidate_entries = []
    for entry in Eatery.objects.values('id', 'price_min', 'price_max', 'latitude', 'longitude'):
        eatery_id = entry.get('id')
        if not eatery_id:
            continue
        if entry.get('latitude') is None or entry.get('longitude') is None:
            continue

        price_choice = entry.get('price_max') if entry.get('price_max') is not None else entry.get('price_min')
        try:
            price_decimal = Decimal(str(price_choice)) if price_choice is not None else Decimal('0')
        except (InvalidOperation, TypeError):
            price_decimal = Decimal('0')

        candidate_entries.append({
            'id': eatery_id,
            'price': price_decimal,
        })

    candidate_entries.sort(key=lambda item: (item['price'], item['id']))

    cheapest_selection = candidate_entries[:required_count]
    total_cost = sum(item['price'] for item in cheapest_selection)
    return cheapest_selection, total_cost


def _get_cheapest_pois(required_count):
    """Return the cheapest POIs and their total per-person price."""

    if required_count <= 0:
        return [], Decimal('0')

    candidate_entries = []
    for entry in Poi.objects.values('id', 'price_per_person'):
        poi_id = entry.get('id')
        if not poi_id:
            continue

        price_value = entry.get('price_per_person')
        try:
            price_decimal = Decimal(str(price_value)) if price_value is not None else Decimal('0')
        except (InvalidOperation, TypeError):
            price_decimal = Decimal('0')

        candidate_entries.append({
            'id': poi_id,
            'price': price_decimal,
        })

    candidate_entries.sort(key=lambda item: (item['price'], item['id']))

    cheapest_selection = candidate_entries[:required_count]
    total_cost = sum(item['price'] for item in cheapest_selection)
    return cheapest_selection, total_cost


# ============================================================================
# DEPRECATED VIEWS - Old separate POI/Eatery selection flows
# These are kept for backwards compatibility but the app now uses
# trip_selection_combined view in views.py for the combined interface.
# Templates trip_select_pois.html and trip_select_eateries.html removed.
# ============================================================================

def trip_poi_selection_view(request):
    """DEPRECATED: Old Step 3 POI selection. Use trip_selection_combined instead."""
    trip_state = _build_trip_state(request.session.get('trip_setup'))
    if not trip_state:
        return redirect('home')

    user = request.user if request.user.is_authenticated else None

    selected_mood, preferred_poi_tag_labels, preferred_poi_tag_values = _get_selected_mood_info(trip_state)
    preferred_poi_tag_set = set(preferred_poi_tag_values)

    all_pois = list(Poi.objects.all())
    for poi in all_pois:
        price = poi.price_per_person if poi.price_per_person is not None else Decimal('0')
        poi.budget_price = price

    selected_poi_ids = request.session.get('selected_poi_ids') or []
    planner_state = request.session.get('planner_step3') or {}
    if planner_state.get('selected_poi_ids'):
        selected_poi_ids = planner_state['selected_poi_ids']

    selected_poi_ids = _sanitize_int_list(selected_poi_ids)

    custom_state_rows = planner_state.get('custom_pois') or []
    custom_poi_rows = custom_state_rows if custom_state_rows else [{'name': '', 'address': '', 'price': '', 'latitude': '', 'longitude': ''}]
    price_override_input = planner_state.get('poi_price_overrides') or {}

    start_lat = trip_state['start_lat']
    start_lon = trip_state['start_lon']

    selected_pois = [poi for poi in all_pois if poi.id in selected_poi_ids]
    unselected_pois = [poi for poi in all_pois if poi.id not in selected_poi_ids]

    if start_lat is not None and start_lon is not None:
        def poi_distance(poi_obj):
            if poi_obj.latitude is None or poi_obj.longitude is None:
                return float('inf')
            return haversine_distance(start_lat, start_lon, poi_obj.latitude, poi_obj.longitude)

        unselected_pois.sort(key=poi_distance)

    mood_matched_pois, remaining_pois = _partition_pois_by_mood(unselected_pois, preferred_poi_tag_set)
    ordered_pois = selected_pois + mood_matched_pois + remaining_pois

    custom_input_errors: List[str] = []

    stored_poi_cost = _safe_decimal(planner_state.get('poi_total_cost') or 0)

    context = {
        'user': user,
        'pois': ordered_pois,
        'selected_pois_list': selected_pois,
        'mood_matched_pois': mood_matched_pois,
        'remaining_pois': remaining_pois,
        'trip_days': trip_state['trip_days'],
        'trip_max_pois_per_day': trip_state['trip_max_pois_per_day'],
        'budget_number': trip_state['budget_amount'],
        'budget_remaining': trip_state['budget_amount'] - stored_poi_cost,
        'total_selected_cost': stored_poi_cost,
        'start_address_label': trip_state['start_address_label'],
        'start_fallback_used': trip_state['start_fallback_used'],
        'start_location': trip_state['start_location'],
        'trip_setup': trip_state['trip_setup'],
        'selected_poi_ids': selected_poi_ids,
        'selected_mood': selected_mood,
        'preferred_poi_tag_labels': preferred_poi_tag_labels,
        'preferred_poi_tag_values': preferred_poi_tag_values,
        'custom_poi_rows': custom_poi_rows,
        'custom_input_errors': custom_input_errors,
        'has_saved_poi_state': bool(planner_state),
    }

    if request.method == 'POST':
        form_selected_poi_ids = _sanitize_int_list(request.POST.getlist('selected_pois'))
        selected_poi_ids = form_selected_poi_ids
        request.session['selected_poi_ids'] = selected_poi_ids

        matched_poi_price_overrides: Dict[int, Decimal] = {
            int(pid): Decimal(str(value))
            for pid, value in price_override_input.items()
            if str(pid).lstrip('-').isdigit()
        }

        submitted_rows = []
        valid_custom_rows = []

        poi_fields = list(zip_longest(
            request.POST.getlist('poi_name'),
            request.POST.getlist('poi_address'),
            request.POST.getlist('poi_price'),
            request.POST.getlist('poi_lat'),
            request.POST.getlist('poi_lon'),
            fillvalue=''
        ))

        for index, (name, address, price, lat, lon) in enumerate(poi_fields, start=1):
            name_value = (name or '').strip()
            address_value = (address or '').strip()
            price_value = (price or '').strip()
            lat_value = (lat or '').strip()
            lon_value = (lon or '').strip()

            row_snapshot = {
                'name': name_value,
                'address': address_value,
                'price': price_value,
                'latitude': lat_value,
                'longitude': lon_value,
            }

            if not any(row_snapshot.values()):
                continue

            submitted_rows.append(row_snapshot)

            if not name_value:
                custom_input_errors.append(f"POI row {index}: please provide a name.")
                continue

            price_value_clean = clean_currency_string(price_value)
            if not price_value_clean:
                custom_input_errors.append(f"POI row {index}: please provide a price.")
                continue

            try:
                price_decimal = Decimal(price_value_clean)
            except InvalidOperation:
                custom_input_errors.append(f"POI row {index}: price must be a valid number.")
                continue

            if price_decimal < 0:
                custom_input_errors.append(f"POI row {index}: price cannot be negative.")
                continue

            matched_poi = _find_matching_poi(name_value, address_value)
            if matched_poi:
                if matched_poi.id not in selected_poi_ids:
                    selected_poi_ids.append(matched_poi.id)
                matched_poi_price_overrides[matched_poi.id] = price_decimal
                continue

            try:
                latitude_val = float(lat_value)
                longitude_val = float(lon_value)
            except ValueError:
                custom_input_errors.append(f"POI row {index}: latitude and longitude must be numbers.")
                continue

            valid_custom_rows.append({
                'name': name_value,
                'address': address_value,
                'price': str(price_decimal),
                'latitude': latitude_val,
                'longitude': longitude_val,
            })

        if submitted_rows:
            custom_poi_rows = submitted_rows
        else:
            custom_poi_rows = valid_custom_rows if valid_custom_rows else [{'name': '', 'address': '', 'price': '', 'latitude': '', 'longitude': ''}]

        updated_selected = [poi for poi in all_pois if poi.id in selected_poi_ids]
        updated_unselected = [poi for poi in all_pois if poi.id not in selected_poi_ids]

        if start_lat is not None and start_lon is not None:
            updated_unselected.sort(key=poi_distance)

        updated_mood_matches, updated_remaining = _partition_pois_by_mood(updated_unselected, preferred_poi_tag_set)

        context.update({
            'selected_poi_ids': selected_poi_ids,
            'selected_pois_list': updated_selected,
            'mood_matched_pois': updated_mood_matches,
            'remaining_pois': updated_remaining,
            'custom_poi_rows': custom_poi_rows,
        })

        if custom_input_errors:
            context['custom_input_errors'] = custom_input_errors
            return render(request, 'trip_select_pois.html', context)

        selected_poi_ids = _sanitize_int_list(selected_poi_ids)
        request.session['selected_poi_ids'] = selected_poi_ids
        context['selected_poi_ids'] = selected_poi_ids

        poi_total_cost = Decimal('0')
        if selected_poi_ids:
            tracked_pois = Poi.objects.filter(id__in=selected_poi_ids)
            for poi in tracked_pois:
                override_price = matched_poi_price_overrides.get(poi.id)
                if override_price is not None:
                    poi_total_cost += override_price
                elif poi.price_per_person is not None:
                    poi_total_cost += Decimal(poi.price_per_person)

        for custom_row in valid_custom_rows:
            poi_total_cost += _safe_decimal(custom_row.get('price') or 0)

        request.session['planner_step3'] = {
            'selected_poi_ids': selected_poi_ids,
            'preferred_poi_tags': preferred_poi_tag_values,
            'custom_pois': valid_custom_rows,
            'poi_price_overrides': {
                poi_id: str(value)
                for poi_id, value in matched_poi_price_overrides.items()
            },
            'poi_total_cost': str(poi_total_cost),
        }
        request.session.modified = True

        context['total_selected_cost'] = poi_total_cost
        context['budget_remaining'] = trip_state['budget_amount'] - poi_total_cost
        return redirect('plan-select-eateries')

    return render(request, 'trip_select_pois.html', context)


def trip_eatery_selection_view(request):
    """Process trip selection from combined interface. Handles form submission and generates itinerary.
    
    Note: Despite the name, this now handles both POI and eatery data from trip_selection_combined.html.
    Originally was Step 4 of old separate flow, now serves as the processing endpoint for the combined interface.
    """
    trip_state = _build_trip_state(request.session.get('trip_setup'))
    if not trip_state:
        return redirect('home')

    planner_state = request.session.get('planner_step3') or {}
    
    # Handle POI data from combined page POST submission before checking session
    if request.method == 'POST':
        submitted_poi_ids = _sanitize_int_list(request.POST.getlist('selected_pois'))
        if submitted_poi_ids:
            # Update session with POI data from the combined page
            planner_state['selected_poi_ids'] = submitted_poi_ids
            planner_state['preferred_poi_tags'] = planner_state.get('preferred_poi_tags', [])
            planner_state['custom_pois'] = planner_state.get('custom_pois', [])
            planner_state['poi_price_overrides'] = planner_state.get('poi_price_overrides', {})
            request.session['planner_step3'] = planner_state
            request.session.modified = True
    
    if not planner_state.get('selected_poi_ids'):
        return redirect('trip_selection_combined')

    selected_mood, mood_tag_labels, mood_tag_values = _get_selected_mood_info(trip_state)

    all_pois = list(Poi.objects.all())

    selected_poi_ids = _sanitize_int_list(planner_state.get('selected_poi_ids'))
    selected_poi_tag_values = planner_state.get('preferred_poi_tags') or mood_tag_values

    if planner_state.get('preferred_poi_tags'):
        normalized_label_lookup = {
            normalize_tag_value(label): label
            for label in mood_tag_labels
        }
        selected_poi_tag_labels = [
            normalized_label_lookup.get(value, value.title())
            for value in selected_poi_tag_values
        ]
    else:
        selected_poi_tag_labels = mood_tag_labels

    extra_pois, extra_poi_budget_total = _hydrate_custom_pois(planner_state.get('custom_pois'))

    matched_poi_price_overrides: Dict[int, Decimal] = {}
    for key, value in (planner_state.get('poi_price_overrides') or {}).items():
        if not str(key).lstrip('-').isdigit():
            continue
        try:
            matched_poi_price_overrides[int(key)] = Decimal(str(value))
        except (InvalidOperation, TypeError):
            continue

    all_eateries = list(Eatery.objects.all())
    for eatery in all_eateries:
        if eatery.price_max is not None:
            price = Decimal(eatery.price_max)
        elif eatery.price_min is not None:
            price = Decimal(eatery.price_min)
        else:
            price = Decimal('0')
        eatery.budget_price = price

    start_lat = trip_state['start_lat']
    start_lon = trip_state['start_lon']

    def tagged(eatery_obj, slot):
        return eatery_obj.time_tags and slot in {t.strip() for t in eatery_obj.time_tags.split(',')}

    morning_eateries = [e for e in all_eateries if tagged(e, 'morning')]
    afternoon_eateries = [e for e in all_eateries if tagged(e, 'afternoon')]
    evening_eateries = [e for e in all_eateries if tagged(e, 'evening')]

    if start_lat is not None and start_lon is not None:
        def eatery_distance(eatery_obj):
            if eatery_obj.latitude is None or eatery_obj.longitude is None:
                return float('inf')
            return haversine_distance(start_lat, start_lon, eatery_obj.latitude, eatery_obj.longitude)

        morning_eateries.sort(key=eatery_distance)
        afternoon_eateries.sort(key=eatery_distance)
        evening_eateries.sort(key=eatery_distance)

    user = request.user if request.user.is_authenticated else None

    selected_eatery_slot_map: Dict[int, str] = {}
    preselected_eatery_ids = request.session.pop('shared_selected_eatery_ids', None)
    preselected_eatery_ids = _sanitize_int_list(preselected_eatery_ids) if preselected_eatery_ids else []

    shared_slot_map = request.session.pop('shared_eatery_slot_map', None)
    if isinstance(shared_slot_map, dict):
        for raw_id, raw_slot in shared_slot_map.items():
            try:
                numeric_id = int(raw_id)
            except (TypeError, ValueError):
                continue
            if numeric_id <= 0:
                continue
            normalized_slot = normalize_user_slot(raw_slot)
            if not normalized_slot:
                continue
            selected_eatery_slot_map[numeric_id] = normalized_slot
            if numeric_id not in preselected_eatery_ids:
                preselected_eatery_ids.append(numeric_id)

    custom_input_errors: List[str] = []
    custom_eatery_rows = [{'name': '', 'address': '', 'time_tags': '', 'price': '', 'lat': '', 'lon': ''}]

    poi_committed_cost = _safe_decimal(planner_state.get('poi_total_cost') or 0)

    context = {
        'user': user,
        'morning_eateries': morning_eateries,
        'afternoon_eateries': afternoon_eateries,
        'evening_eateries': evening_eateries,
        'trip_days': trip_state['trip_days'],
        'trip_max_pois_per_day': trip_state['trip_max_pois_per_day'],
        'budget_number': trip_state['budget_amount'],
        'budget_remaining': trip_state['budget_amount'] - poi_committed_cost,
        'total_selected_cost': poi_committed_cost,
        'start_address_label': trip_state['start_address_label'],
        'start_fallback_used': trip_state['start_fallback_used'],
        'selected_poi_count': len(selected_poi_ids) + len(extra_pois),
        'preferred_poi_tag_labels': selected_poi_tag_labels,
        'selected_mood': selected_mood,
        'auto_fill_notice': None,
        'selected_eatery_slot_map': selected_eatery_slot_map,
        'custom_input_errors': custom_input_errors,
        'custom_eatery_rows': custom_eatery_rows,
        'error_message': None,
        'poi_committed_cost': poi_committed_cost,
    }

    if request.method == 'POST':
        preferred_poi_tag_labels = selected_poi_tag_labels
        selected_eatery_slot_map = {}
        
        # Handle eatery data from combined page format (selected_eateries_morning, etc.)
        for slot_name in ['morning', 'afternoon', 'evening']:
            slot_eateries = _sanitize_int_list(request.POST.getlist(f'selected_eateries_{slot_name}'))
            for eatery_id in slot_eateries:
                selected_eatery_slot_map[eatery_id] = slot_name
        
        # Also handle original format (selected_eateries_with_slot)
        for entry in request.POST.getlist('selected_eateries_with_slot'):
            if '|' not in entry:
                continue
            raw_id, raw_slot = entry.split('|', 1)
            if not raw_id or not raw_id.lstrip('-').isdigit():
                continue
            normalized_slot = normalize_user_slot(raw_slot)
            if not normalized_slot:
                continue
            selected_eatery_slot_map[int(raw_id)] = normalized_slot

        context['selected_eatery_slot_map'] = selected_eatery_slot_map

        selected_eatery_ids = _sanitize_int_list(request.POST.getlist('selected_eateries'))
        # Add eateries from slot-specific fields if not already included
        for eatery_id in selected_eatery_slot_map.keys():
            if eatery_id not in selected_eatery_ids:
                selected_eatery_ids.append(eatery_id)

        custom_eatery_rows = []
        extra_eateries = []
        extra_eatery_budget_total = Decimal('0')
        matched_eatery_price_overrides: Dict[int, Decimal] = {}

        eatery_fields = list(zip_longest(
            request.POST.getlist('eatery_name'),
            request.POST.getlist('eatery_address'),
            request.POST.getlist('eatery_time_tags'),
            request.POST.getlist('eatery_price'),
            request.POST.getlist('eatery_lat'),
            request.POST.getlist('eatery_lon'),
            fillvalue=''
        ))

        for index, (name, address, time_tags, price, lat, lon) in enumerate(eatery_fields, start=1):
            name_value = (name or '').strip()
            address_value = (address or '').strip()
            time_tag_value = (time_tags or '').strip()
            price_value = (price or '').strip()
            lat_value = (lat or '').strip()
            lon_value = (lon or '').strip()

            row_snapshot = {
                'name': name_value,
                'address': address_value,
                'time_tags': time_tag_value,
                'price': price_value,
                'lat': lat_value,
                'lon': lon_value,
            }

            if not any(row_snapshot.values()):
                continue

            custom_eatery_rows.append(row_snapshot)

            if not name_value:
                custom_input_errors.append(f"Eatery row {index}: please provide a name.")
                continue

            price_value_clean = clean_currency_string(price_value)
            if not price_value_clean:
                custom_input_errors.append(f"Eatery row {index}: please provide a price.")
                continue

            try:
                price_decimal = Decimal(price_value_clean)
            except InvalidOperation:
                custom_input_errors.append(f"Eatery row {index}: price must be a valid number.")
                continue

            if price_decimal < 0:
                custom_input_errors.append(f"Eatery row {index}: price cannot be negative.")
                continue

            matched_eatery = _find_matching_eatery(name_value, address_value)
            if matched_eatery:
                if matched_eatery.id not in selected_eatery_ids:
                    selected_eatery_ids.append(matched_eatery.id)
                matched_eatery_price_overrides[matched_eatery.id] = price_decimal
                continue

            normalized_tags = normalize_time_tags(time_tag_value)
            if not normalized_tags:
                custom_input_errors.append(f"Eatery row {index}: please provide at least one time tag.")
                continue

            try:
                latitude_val = float(lat_value)
                longitude_val = float(lon_value)
            except ValueError:
                custom_input_errors.append(f"Eatery row {index}: latitude and longitude must be numbers.")
                continue

            extra_eateries.append({
                'id': -(len(extra_eateries) + 1),
                'name': name_value,
                'address': address_value,
                'time_tags': normalized_tags,
                'latitude': latitude_val,
                'longitude': longitude_val,
                'price': float(price_decimal),
            })
            extra_eatery_budget_total += price_decimal

        if not custom_eatery_rows:
            custom_eatery_rows = [{'name': '', 'address': '', 'time_tags': '', 'price': '', 'lat': '', 'lon': ''}]

        context['custom_eatery_rows'] = custom_eatery_rows

        if custom_input_errors:
            request.session['form_errors'] = custom_input_errors
            request.session.modified = True
            return redirect('trip_selection_combined')

        budget_amount = trip_state['budget_amount']

        auto_fill_result = _auto_fill_selections(
            trip_state=trip_state,
            selected_poi_ids=selected_poi_ids,
            extra_pois=extra_pois,
            selected_eatery_ids=selected_eatery_ids,
            extra_eateries=extra_eateries,
            preferred_poi_tag_values=selected_poi_tag_values,
            preferred_poi_tag_labels=selected_poi_tag_labels,
        )

        selected_poi_ids = auto_fill_result['selected_poi_ids']
        selected_eatery_ids = auto_fill_result['selected_eatery_ids']
        total_pois_selected = auto_fill_result['total_pois_selected']
        total_eateries_selected = auto_fill_result['total_eateries_selected']
        required_poi_count = auto_fill_result['required_poi_count']
        required_eatery_count = auto_fill_result['required_eatery_count']
        auto_fill_messages = auto_fill_result.get('auto_fill_messages', [])
        auto_filled_poi_ids = auto_fill_result.get('auto_filled_poi_ids', set())
        auto_filled_eatery_ids = auto_fill_result.get('auto_filled_eatery_ids', set())
        tag_based_fill_count = auto_fill_result.get('tag_based_fill_count', 0)

        if total_eateries_selected < required_eatery_count:
            shortage = required_eatery_count - total_eateries_selected
            request.session['form_errors'] = [
                f"Only {total_eateries_selected} eateries fit your current budget, but {required_eatery_count} meal slots are required (short by {shortage}). Please add more eateries or increase your budget before generating the itinerary."
            ]
            request.session.modified = True
            return redirect('trip_selection_combined')

        if auto_fill_result['auto_fill_notice']:
            context['auto_fill_notice'] = auto_fill_result['auto_fill_notice']

        selected_pois_qs = Poi.objects.filter(id__in=selected_poi_ids)
        selected_eateries_qs = Eatery.objects.filter(id__in=selected_eatery_ids)

        total_selected_cost = extra_poi_budget_total + extra_eatery_budget_total

        for poi in selected_pois_qs:
            override_price = matched_poi_price_overrides.get(poi.id)
            if override_price is not None:
                total_selected_cost += override_price
            elif poi.price_per_person is not None:
                total_selected_cost += poi.price_per_person

        for eatery in selected_eateries_qs:
            override_price = matched_eatery_price_overrides.get(eatery.id)
            if override_price is not None:
                total_selected_cost += override_price
            else:
                price_choice = eatery.price_max if eatery.price_max is not None else eatery.price_min
                if price_choice is not None:
                    total_selected_cost += Decimal(price_choice)

        context['total_selected_cost'] = total_selected_cost
        context['budget_remaining'] = budget_amount - total_selected_cost

        if total_selected_cost > budget_amount:
            budget_warning = f"⚠️ Budget exceeded! Selected items cost ₫{total_selected_cost:,.0f} but your budget is ₫{budget_amount:,.0f}. Please remove some items or increase your budget."
            request.session['form_errors'] = [budget_warning]
            request.session.modified = True
            return redirect('trip_selection_combined')

        max_total_eateries = trip_state['trip_days'] * 3
        total_eateries_selected = len(selected_eatery_ids) + len(extra_eateries)
        if total_eateries_selected > max_total_eateries:
            error_message = f"⚠️ Too many eateries! You selected {total_eateries_selected} eateries, but your limit is {max_total_eateries} ({trip_state['trip_days']} days × 3 meals)."
            request.session['form_errors'] = [error_message]
            request.session.modified = True
            return redirect('trip_selection_combined')

        slot_overrides_vi = {
            eatery_id: slot_vi_label(slot_key)
            for eatery_id, slot_key in selected_eatery_slot_map.items()
            if slot_vi_label(slot_key)
        }

        itinerary_results_vi, itinerary_results_en, error_msg = generate_itinerary(
            num_days=trip_state['trip_days'],
            daily_poi_limit=trip_state['trip_max_pois_per_day'],
            selected_pois_qs=selected_pois_qs,
            selected_eateries_qs=selected_eateries_qs,
            extra_pois=extra_pois,
            extra_eateries=extra_eateries,
            user_slot_overrides=slot_overrides_vi,
            accommodation_address=trip_state.get('accommodation_input'),
            use_default_center=trip_state.get('start_fallback_used', False),
        )

        if itinerary_results_vi:
            for day_num, stops in list(itinerary_results_vi.items()):
                for item in stops:
                    try:
                        if item.get('type') == 'POI' and item.get('id') and item['id'] > 0:
                            poi_data = Poi.objects.filter(id=item['id']).values('address', 'image_code').first()
                            if poi_data:
                                item['address'] = poi_data['address']
                                item['image_code'] = poi_data['image_code']
                        elif item.get('type') == 'EATERY' and item.get('id') and item['id'] > 0:
                            eatery_data = Eatery.objects.filter(id=item['id']).values('address', 'image_code').first()
                            if eatery_data:
                                item['address'] = eatery_data['address']
                                item['image_code'] = eatery_data['image_code']
                        else:
                            item['address'] = item.get('address')
                    except Exception:
                        item['address'] = item.get('address')
            print("\n" + "=" * 50)
            print("FINAL ITINERARY (from trip_planner.py):")
            pprint.pprint(itinerary_results_vi)
            print("=" * 50 + "\n")

        if error_msg:
            context['error_message'] = error_msg
        else:
            auto_fill_snapshot = None
            if auto_fill_messages or auto_filled_poi_ids or auto_filled_eatery_ids:
                auto_fill_snapshot = {
                    'messages': auto_fill_messages,
                    'poi_ids': sorted(auto_filled_poi_ids),
                    'eatery_ids': sorted(auto_filled_eatery_ids),
                    'tag_matched_fill_count': tag_based_fill_count,
                }

            planner_payload = {
                'results': itinerary_results_vi,
                'results_en': itinerary_results_en,
                'total_selected_cost': str(total_selected_cost),
                'budget_remaining': str(context['budget_remaining']),
                'selected_counts': {
                    'pois': len(selected_poi_ids) + len(extra_pois),
                    'eateries': len(selected_eatery_ids) + len(extra_eateries),
                }
            }

            if auto_fill_snapshot:
                planner_payload['auto_fill_snapshot'] = auto_fill_snapshot

            request.session['planner_itinerary'] = planner_payload
            request.session.modified = True
            return redirect('plan-itinerary')

    # If we reach here (GET request), redirect to combined selection page
    return redirect('trip_selection_combined')


def auto_generate_itinerary_view(request):
    """Generate a full itinerary using only auto-fill logic and skip manual selection."""

    trip_state = _build_trip_state(request.session.get('trip_setup'))
    if not trip_state:
        return redirect('home')

    def _fail_generate(messages):
        payload = messages if isinstance(messages, list) else [messages]
        request.session['trip_setup_errors'] = payload
        request.session.modified = True
        return redirect('home')

    selected_mood, preferred_poi_tag_labels, preferred_poi_tag_values = _get_selected_mood_info(trip_state)

    auto_fill_result = _auto_fill_selections(
        trip_state=trip_state,
        selected_poi_ids=[],
        extra_pois=[],
        selected_eatery_ids=[],
        extra_eateries=[],
        preferred_poi_tag_values=preferred_poi_tag_values,
        preferred_poi_tag_labels=preferred_poi_tag_labels,
    )

    selected_poi_ids = auto_fill_result['selected_poi_ids']
    selected_eatery_ids = auto_fill_result['selected_eatery_ids']

    if auto_fill_result['total_eateries_selected'] < auto_fill_result['required_eatery_count']:
        shortage = auto_fill_result['required_eatery_count'] - auto_fill_result['total_eateries_selected']
        return _fail_generate(
            f"Automatic generation found only {auto_fill_result['total_eateries_selected']} eateries for {auto_fill_result['required_eatery_count']} required meal slots (short by {shortage}). Please add more eateries or increase your budget and try again."
        )

    if not selected_poi_ids and not selected_eatery_ids:
        return _fail_generate('Automatic generation could not find any destinations or eateries. Please adjust your setup and try again.')

    selected_pois_qs = Poi.objects.filter(id__in=selected_poi_ids)
    budget_amount = trip_state['budget_amount']

    poi_cost = Decimal('0')
    for poi in selected_pois_qs:
        if poi.price_per_person is not None:
            poi_cost += Decimal(poi.price_per_person)

    remaining_budget_for_eateries = budget_amount - poi_cost

    cheapest_pois = None
    cheapest_poi_cost = None
    cheapest_eateries = None
    cheapest_eateries_cost = None

    if remaining_budget_for_eateries <= Decimal('0'):
        cheapest_pois, cheapest_poi_cost = _get_cheapest_pois(auto_fill_result['required_poi_count'])
        cheapest_eateries, cheapest_eateries_cost = _get_cheapest_eateries(auto_fill_result['required_eatery_count'])

        if len(cheapest_pois) < auto_fill_result['required_poi_count'] or len(cheapest_eateries) < auto_fill_result['required_eatery_count']:
            return _fail_generate('Cannot generate itinerary: not enough POIs or eateries available for all required slots. Please adjust your selections or budget.')

        minimum_total_cost = cheapest_poi_cost + cheapest_eateries_cost
        return _fail_generate(
            (
                f"The lowest-cost itinerary we can provide costs ₫{minimum_total_cost:,.0f}, "
                f"which exceeds your budget of ₫{budget_amount:,.0f}. Increase your budget or shorten the trip to continue."
            )
        )

    if cheapest_eateries is None or cheapest_eateries_cost is None:
        cheapest_eateries, cheapest_eateries_cost = _get_cheapest_eateries(auto_fill_result['required_eatery_count'])

    if len(cheapest_eateries) < auto_fill_result['required_eatery_count']:
        return _fail_generate('Cannot generate itinerary: not enough POIs or eateries available for all required slots. Please adjust your selections or budget.')

    if cheapest_eateries_cost > remaining_budget_for_eateries:
        return _fail_generate(
            (
                f"Cannot generate itinerary: even the most affordable set of {auto_fill_result['required_eatery_count']} eateries costs ₫{cheapest_eateries_cost:,.0f}, "
                f"which exceeds your remaining meal budget of ₫{remaining_budget_for_eateries:,.0f}. Please increase your budget or reduce your trip constraints."
            )
        )

    selected_eateries_qs = Eatery.objects.filter(id__in=selected_eatery_ids)

    eatery_cost = Decimal('0')
    for eatery in selected_eateries_qs:
        price_choice = eatery.price_max if eatery.price_max is not None else eatery.price_min
        if price_choice is not None:
            eatery_cost += Decimal(price_choice)

    total_selected_cost = poi_cost + eatery_cost
    budget_adjust_notice = None

    if total_selected_cost > budget_amount:
        selected_eatery_ids = [item['id'] for item in cheapest_eateries]
        selected_eateries_qs = Eatery.objects.filter(id__in=selected_eatery_ids)
        eatery_cost = cheapest_eateries_cost
        total_selected_cost = poi_cost + eatery_cost
        budget_adjust_notice = (
            f"Adjusted eatery selections to the most affordable {auto_fill_result['required_eatery_count']} options to stay within your ₫{budget_amount:,.0f} budget."
        )

    slot_overrides_vi = {}
    itinerary_results_vi, itinerary_results_en, error_msg = generate_itinerary(
        num_days=trip_state['trip_days'],
        daily_poi_limit=trip_state['trip_max_pois_per_day'],
        selected_pois_qs=selected_pois_qs,
        selected_eateries_qs=selected_eateries_qs,
        extra_pois=[],
        extra_eateries=[],
        user_slot_overrides=slot_overrides_vi,
        accommodation_address=trip_state.get('accommodation_input'),
        use_default_center=trip_state.get('start_fallback_used', False),
    )

    if error_msg:
        return _fail_generate(error_msg)

    if itinerary_results_vi:
        for day_num, stops in list(itinerary_results_vi.items()):
            for item in stops:
                try:
                    if item.get('type') == 'POI' and item.get('id') and item['id'] > 0:
                        poi_data = Poi.objects.filter(id=item['id']).values('address', 'image_code').first()
                        if poi_data:
                            item['address'] = poi_data['address']
                            item['image_code'] = poi_data['image_code']
                    elif item.get('type') == 'EATERY' and item.get('id') and item['id'] > 0:
                        eatery_data = Eatery.objects.filter(id=item['id']).values('address', 'image_code').first()
                        if eatery_data:
                            item['address'] = eatery_data['address']
                            item['image_code'] = eatery_data['image_code']
                    else:
                        item['address'] = item.get('address')
                except Exception:
                    item['address'] = item.get('address')

    budget_remaining = trip_state['budget_amount'] - total_selected_cost

    notice_chunks = []
    if auto_fill_result['auto_fill_notice']:
        notice_chunks.append(auto_fill_result['auto_fill_notice'])
    if budget_adjust_notice:
        notice_chunks.append(budget_adjust_notice)
    combined_notice = " ".join(notice_chunks) if notice_chunks else None

    request.session['planner_itinerary'] = {
        'results': itinerary_results_vi,
        'results_en': itinerary_results_en,
        'total_selected_cost': str(total_selected_cost),
        'budget_remaining': str(budget_remaining),
        'selected_counts': {
            'pois': len(selected_poi_ids),
            'eateries': len(selected_eatery_ids),
        },
        'auto_fill_notice': combined_notice,
    }
    request.session.modified = True

    return redirect('plan-itinerary')
