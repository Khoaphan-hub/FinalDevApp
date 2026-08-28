"""
Utility functions for data processing and validation
"""
import os
from typing import Optional
from django.conf import settings
from django.templatetags.static import static
from .constants import VALID_EATERY_SLOTS, SLOT_LABELS_EN_TO_VI

# Shared fallbacks keep templates clean if a photo has not been curated yet.
_DEFAULT_IMAGE_MAP = {
    'poi': 'home/images/dalat.jpg',
    'eatery': 'home/images/dalat_2.jpg',
}


def _static_asset_exists(relative_path: str) -> bool:
    """Check if a static asset exists inside home/static/ before linking it."""
    absolute_path = os.path.join(settings.BASE_DIR, 'home', 'static', relative_path.replace('/', os.sep))
    return os.path.exists(absolute_path)


def normalize_user_slot(raw_slot):
    if not raw_slot:
        return None
    lowered = raw_slot.strip().lower()
    return lowered if lowered in VALID_EATERY_SLOTS else None


def slot_vi_label(slot_key):
    return SLOT_LABELS_EN_TO_VI.get(slot_key)


def normalize_time_tags(raw_value):
    """Normalize time tags from various formats to standardized Vietnamese labels."""
    tokens = []
    for token in (raw_value or '').replace('/', ',').split(','):
        t = token.strip()
        if not t:
            continue
        lowered = t.lower()
        if lowered in {'sáng', 'sang', 'morning'}:
            tokens.append('Sáng')
        elif lowered in {'trưa', 'trua', 'afternoon', 'lunch'}:
            tokens.append('Trưa')
        elif lowered in {'tối', 'toi', 'evening', 'dinner', 'night'}:
            tokens.append('Tối')
        else:
            tokens.append(t)
    # Remove duplicates while preserving order
    seen = set()
    ordered = []
    for token in tokens:
        if token not in seen:
            ordered.append(token)
            seen.add(token)
    return '/'.join(ordered)


def get_item_image_url(item_type: str, identifier: Optional[object]) -> str:
    """Return the static URL for curated POI/Eatery images, with graceful fallback."""
    if identifier in (None, ''):
        return static(_DEFAULT_IMAGE_MAP[item_type])

    directory = 'home/images/pois' if item_type == 'poi' else 'home/images/eateries'
    prefix = 'P' if item_type == 'poi' else 'E'

    raw_code = str(identifier).strip()
    if not raw_code:
        return static(_DEFAULT_IMAGE_MAP[item_type])

    if raw_code.isdigit():
        base_name = f"{prefix}{int(raw_code):03d}"
    else:
        base_name = raw_code.upper()

    filename = f"{base_name}.png"
    relative_path = f"{directory}/{filename}"

    if _static_asset_exists(relative_path):
        return static(relative_path)

    return static(_DEFAULT_IMAGE_MAP[item_type])

