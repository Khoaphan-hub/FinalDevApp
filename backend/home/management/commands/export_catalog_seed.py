"""
Writes the whole catalog into the Android assets folder as a seed file.

The app caches the catalog in Room, but a freshly installed APK has an empty cache. If the
backend happens to be unreachable at that moment there is nothing to fall back on, so Explore
shows an error and trip generation drops to nine hardcoded sample places. Shipping the catalog
inside the APK removes that hole: the first launch has real data whether or not the server
answers, and the first successful download simply overwrites it.

The catalog endpoint cannot be used for this because it caps at 100 items with no pagination,
while there are 242 places. Reading the models directly through the same serializer the API
uses keeps the field names identical to what the client already parses.

    python manage.py export_catalog_seed
"""

import json
import os

from django.conf import settings
from django.core.management.base import BaseCommand

from home.mobile_api import _serialize_catalog_item
from home.models import Poi, Eatery

# Relative to the repository root, which is the parent of the backend directory.
ASSET_RELATIVE_PATH = os.path.join('app', 'src', 'main', 'assets', 'catalog_seed.json')


class _NoRequest:
    """
    _serialize_catalog_item only calls request.build_absolute_uri to build image URLs.

    Seeded rows deliberately carry no image URL: any address baked in here would point at
    whichever machine ran this command, and trying to load it offline only wastes a request
    before the placeholder drawable appears anyway. Real URLs arrive with the first online
    catalog refresh.
    """

    def build_absolute_uri(self, location=None):
        return None


class Command(BaseCommand):
    help = 'Exports the full catalog to app/src/main/assets/catalog_seed.json'

    def handle(self, *args, **options):
        request = _NoRequest()

        items = []
        for poi in Poi.objects.all().order_by('-rating', 'name'):
            items.append(_serialize_catalog_item(request, poi, 'poi'))
        for eatery in Eatery.objects.all().order_by('-rating', 'name'):
            items.append(_serialize_catalog_item(request, eatery, 'eatery'))

        # Both language variants stay in one file. The client picks name/name_en per locale
        # exactly as it already does for a live response, so no second file is needed.
        payload = {
            'version': 1,
            'count': len(items),
            'items': items,
        }

        repository_root = os.path.dirname(settings.BASE_DIR)
        destination = os.path.join(repository_root, ASSET_RELATIVE_PATH)
        os.makedirs(os.path.dirname(destination), exist_ok=True)

        with open(destination, 'w', encoding='utf-8') as handle:
            json.dump(payload, handle, ensure_ascii=False, separators=(',', ':'))

        size_kb = os.path.getsize(destination) / 1024
        self.stdout.write('Wrote %d places (%d POI, %d eatery) to %s (%.0f KB)' % (
            len(items),
            sum(1 for item in items if item['type'] == 'POI'),
            sum(1 for item in items if item['type'] == 'EATERY'),
            destination,
            size_kb,
        ))
