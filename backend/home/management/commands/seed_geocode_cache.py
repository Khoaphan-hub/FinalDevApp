"""
Management command to seed the geocoded location cache with known correct locations
This should be run after database migrations to populate common locations
"""
from django.core.management.base import BaseCommand
from home.models import GeocodedLocation


class Command(BaseCommand):
    help = 'Seeds the geocoded location cache with known correct locations for Da Lat'

    def handle(self, *args, **options):
        # List of known locations with correct coordinates
        known_locations = [
            {
                'place_name': 'Chợ Đà Lạt',
                'raw_address': 'Nguyễn Thị Minh Khai, Phường 1, Thành phố Đà Lạt, Lâm Đồng, Vietnam',
                'normalized_address': 'nguyễn thị minh khai, phường 1, thành phố đà lạt, lâm đồng, vietnam',
                'street': 'nguyễn thị minh khai',
                'ward': '1',
                'house_number': '',
                'latitude': 11.943031693125658,
                'longitude': 108.43696493717796,
            },
            {
                'place_name': 'Du Parc Hotel',
                'raw_address': '15 Tran Phu, Ward 3, Da Lat',
                'normalized_address': '15 tran phu, ward 3, da lat',
                'street': 'tran phu',
                'ward': '3',
                'house_number': '15',
                'latitude': 11.942702,
                'longitude': 108.435712,
            },
            {
                'place_name': 'Hồ Xuân Hương',
                'raw_address': 'Hồ Xuân Hương, Phường 1, Thành phố Đà Lạt, Tỉnh Lâm Đồng, Việt Nam',
                'normalized_address': 'hồ xuân hương, phường 1, thành phố đà lạt, tỉnh lâm đồng, việt nam',
                'street': '',
                'ward': '1',
                'house_number': '',
                'latitude': 11.94053,
                'longitude': 108.4429,
            },
        ]

        created_count = 0
        updated_count = 0

        for loc_data in known_locations:
            place_name = loc_data['place_name']
            
            # Check if entry already exists (by place_name)
            existing = GeocodedLocation.objects.filter(place_name=place_name).first()
            
            if existing:
                # Update existing entry
                existing.raw_address = loc_data['raw_address']
                existing.normalized_address = loc_data['normalized_address']
                existing.street = loc_data['street']
                existing.ward = loc_data['ward']
                existing.house_number = loc_data['house_number']
                existing.latitude = loc_data['latitude']
                existing.longitude = loc_data['longitude']
                existing.save()
                updated_count += 1
                self.stdout.write(
                    self.style.WARNING(f'Updated: {place_name} -> ({loc_data["latitude"]}, {loc_data["longitude"]})')
                )
            else:
                # Create new entry
                GeocodedLocation.objects.create(**loc_data)
                created_count += 1
                self.stdout.write(
                    self.style.SUCCESS(f'Created: {place_name} -> ({loc_data["latitude"]}, {loc_data["longitude"]})')
                )

        self.stdout.write(
            self.style.SUCCESS(
                f'\n✓ Seeding complete: {created_count} created, {updated_count} updated'
            )
        )
