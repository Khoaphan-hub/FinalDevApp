"""
Search utilities for POIs and eateries
"""
from functools import lru_cache
from .models import Poi, Eatery
from .prefix_tree import PrefixTree, term_variants
from .utils import get_item_image_url


def _serialize_poi_for_search(poi):
    price = float(poi.price_per_person) if poi.price_per_person is not None else None
    return {
        'id': poi.id,
        'type': 'POI',
        'name': poi.name,
        'address': poi.address,
        'price': price,
        'tags': poi.tags,
        'image_url': get_item_image_url('poi', poi.image_code or poi.id),
    }


def _serialize_eatery_for_search(eatery):
    if eatery.price_max is not None:
        price = float(eatery.price_max)
    elif eatery.price_min is not None:
        price = float(eatery.price_min)
    else:
        price = None

    return {
        'id': eatery.id,
        'type': 'EATERY',
        'name': eatery.name,
        'address': eatery.address,
        'price': price,
        'time_tags': eatery.time_tags,
        'image_url': get_item_image_url('eatery', eatery.image_code or eatery.id),
    }


@lru_cache(maxsize=1)
def get_search_tree():
    tree = PrefixTree()

    for poi in Poi.objects.all():
        payload = _serialize_poi_for_search(poi)
        for name in (poi.name, poi.name_en):
            for variant in term_variants(name):
                tree.insert(variant, payload)

    for eatery in Eatery.objects.all():
        payload = _serialize_eatery_for_search(eatery)
        for name in (eatery.name, eatery.name_en):
            for variant in term_variants(name):
                tree.insert(variant, payload)

    return tree


def refresh_search_tree():
    get_search_tree.cache_clear()
    return get_search_tree()
