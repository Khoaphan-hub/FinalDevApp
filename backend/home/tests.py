from django.test import SimpleTestCase, TestCase

from .prefix_tree import PrefixTree, term_variants
from .mobile_share import build_resume_snapshot
from .models import PlaceReport, Poi


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

    def test_suggest_supports_english_name_variants(self):
        english = {'id': 3, 'type': 'POI', 'name': 'Da Lat Flower Park'}
        for variant in term_variants(english['name']):
            self.tree.insert(variant, english)
        self.assertEqual([item['id'] for item in self.tree.suggest('flower')], [3])


class MobileResumeSnapshotTests(SimpleTestCase):
    def test_android_itinerary_is_converted_to_web_resume_shape(self):
        snapshot = build_resume_snapshot({
            'title': 'Đà Lạt cuối tuần',
            'total_budget': 2000000,
            'estimated_cost': 750000,
            'days': [{
                'day': 1,
                'stops': [{
                    'id': 4,
                    'type': 'POI',
                    'name': 'Hồ Xuân Hương',
                    'address': 'Đà Lạt',
                    'latitude': 11.94,
                    'longitude': 108.44,
                    'travel_to_next_km': 1.2,
                    'meal_slot': None,
                }],
            }],
        })
        self.assertEqual(snapshot['planner_itinerary']['results']['1'][0]['name'], 'Hồ Xuân Hương')
        self.assertEqual(snapshot['planner_itinerary']['budget_remaining'], 1250000)
        self.assertEqual(snapshot['selected_poi_ids'], [4])


class MobileShareApiTests(TestCase):
    def test_creates_qr_and_resume_token(self):
        response = self.client.post('/api/mobile/itineraries/share/', data={
            'title': 'Đà Lạt cuối tuần',
            'total_budget_vnd': 2000000,
            'estimated_cost_vnd': 750000,
            'days': [{'day_number': 1, 'stops': [{'id': 4, 'type': 'POI',
                'name': 'Hồ Xuân Hương', 'address': 'Đà Lạt',
                'latitude': 11.94, 'longitude': 108.44}]}],
        }, content_type='application/json')
        self.assertEqual(response.status_code, 200)
        data = response.json()['data']
        self.assertTrue(data['share_url'].startswith('http://testserver/resume/'))
        self.assertGreater(len(data['qr_base64']), 1000)


class MobilePlaceReportApiTests(TestCase):
    def setUp(self):
        self.poi = Poi.objects.create(
            name='Hồ Xuân Hương',
            address='Phường 1, Đà Lạt',
            open_hours='Always open',
            tiktok_link='https://www.tiktok.com/example',
            rating=4.5,
            price_per_person=20000,
            latitude=11.9416,
            longitude=108.4383,
            image_code='poi_001',
        )

    def test_creates_report_without_changing_place_data(self):
        response = self.client.post('/api/mobile/place-reports/', data={
            'target_type': 'POI',
            'target_id': self.poi.pk,
            'category': 'WRONG_HOURS',
            'description': 'Giờ đóng cửa thực tế là 22:00.',
        }, content_type='application/json')

        self.assertEqual(response.status_code, 201)
        report = PlaceReport.objects.get(pk=response.json()['data']['report_id'])
        self.assertEqual(report.status, PlaceReport.Status.NEW)
        self.assertEqual(report.target_name, self.poi.name)
        self.assertEqual(report.current_snapshot['open_hours'], 'Always open')
        self.poi.refresh_from_db()
        self.assertEqual(self.poi.open_hours, 'Always open')

    def test_rejects_missing_description(self):
        response = self.client.post('/api/mobile/place-reports/', data={
            'target_type': 'POI',
            'target_id': self.poi.pk,
            'category': 'OTHER',
            'description': '   ',
        }, content_type='application/json')
        self.assertEqual(response.status_code, 400)
        self.assertIn('description', response.json()['field_errors'])

    def test_returns_not_found_for_unknown_place(self):
        response = self.client.post('/api/mobile/place-reports/', data={
            'target_type': 'EATERY',
            'target_id': 999999,
            'category': 'CLOSED',
            'description': 'Địa điểm đã đóng cửa.',
        }, content_type='application/json')
        self.assertEqual(response.status_code, 404)
