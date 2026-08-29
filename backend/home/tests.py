from django.test import SimpleTestCase

from .prefix_tree import PrefixTree, term_variants


class PrefixTreeTests(SimpleTestCase):
    def setUp(self):
        self.tree = PrefixTree()
        poi = {'id': 1, 'type': 'POI', 'name': 'Hồ Xuân Hương'}
        eatery = {'id': 2, 'type': 'EATERY', 'name': 'Bánh căn Nhà Chung'}
        for variant in term_variants(poi['name']):
            self.tree.insert(variant, poi)
        for variant in term_variants(eatery['name']):
            self.tree.insert(variant, eatery)

    def test_suggest_normalizes_accents_and_matches_word_prefix(self):
        results = self.tree.suggest('xuan', limit=10)
        self.assertEqual([item['id'] for item in results], [1])

    def test_suggest_filters_place_type_without_stopping_search(self):
        self.assertEqual(self.tree.suggest('ban', item_type='POI'), [])
        results = self.tree.suggest('ban', item_type='eatery')
        self.assertEqual([item['id'] for item in results], [2])
