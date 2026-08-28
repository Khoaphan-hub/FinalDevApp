# In home/management/commands/load_data.py
import csv
import re
from decimal import Decimal, InvalidOperation
from django.core.management.base import BaseCommand
from home.models import Poi, Eatery
import home.signals # Import signals module to access the flag

TIME_SLOTS = {
    'morning': (5 * 60, 12 * 60),
    'afternoon': (12 * 60, 18 * 60),
    'evening': (18 * 60, 24 * 60 + 300),
}
POI_PRICE_COLUMNS = (
    'Giá vé / Giá nước',
    'Giá vé',
    'Giá tiền',
)
PRICE_NUMBER_RE = re.compile(r'\d[\d\.]*')
FREE_TOKENS = {'miễn phí', 'free'}
UNAVAILABLE_TOKENS = {'n/a', 'na', 'đang cập nhật', 'chưa có', 'liên hệ'}

def parse_time_tags(open_hours: str) -> str:
    if not open_hours:
        return ''
    text = open_hours.lower()
    if 'cả ngày' in text or '24' in text:
        return 'morning,afternoon,evening'

    ranges = []
    for part in re.split(r'[;,]', open_hours):
        times = re.findall(r'(\d{1,2}):(\d{2})', part)
        if len(times) >= 2:
            sh, sm = map(int, times[0])
            eh, em = map(int, times[1])
            start = sh * 60 + sm
            end = eh * 60 + em
            if end < start:
                end += 24 * 60
            ranges.append((start, end))

    slots = []
    for name, (slot_start, slot_end) in TIME_SLOTS.items():
        for start, end in ranges:
            if max(start, slot_start) < min(end, slot_end):
                slots.append(name)
                break
    return ','.join(slots)

class Command(BaseCommand):
    help = 'Loads POI and Eatery data from CSV files'

    def parse_poi_price(self, raw_value):
        if not raw_value:
            return None

        lowered = raw_value.strip().lower()
        if not lowered:
            return None

        if any(token in lowered for token in FREE_TOKENS):
            return Decimal('0')

        if any(token in lowered for token in UNAVAILABLE_TOKENS):
            return None

        normalized = (
            lowered.replace('vnđ', '')
            .replace('vnd', '')
            .replace('đ', '')
            .replace('₫', '')
            .replace(',', '')
            .replace(' ', '')
            .replace('k', '000')
        )

        numbers = []
        for match in PRICE_NUMBER_RE.findall(normalized):
            digits = match.replace('.', '')
            if not digits:
                continue
            try:
                numbers.append(Decimal(digits))
            except InvalidOperation:
                continue

        if not numbers:
            return None

        return max(numbers)

    def handle(self, *args, **options):
        # Set flag to skip signals during bulk import
        home.signals._skip_signals = True
        
        try:
            # Clear existing data (optional, but helpful for re-running)
            Poi.objects.all().delete()
            Eatery.objects.all().delete()
            
            self.stdout.write("Loading POI data...")
            
            # Load highlights from separate file
            highlights_dict = {}
            with open('dalat_pois_highlights.csv', mode='r', encoding='utf-8') as file:
                reader = csv.DictReader(file)
                for row in reader:
                    highlights_dict[row['ID']] = row.get('Highlights', '')
            
            # --- IMPORTANT ---
            # Put your CSV files in the ROOT folder, next to manage.py
            with open('dalat_pois.csv', mode='r', encoding='utf-8') as file:
                reader = csv.DictReader(file)
                for row in reader:
                    # --- NEW TAG LOGIC ---
                    # 1. Get both *unique* tags from your CSV
                    tag_from_col_1 = row.get('Class1') # <-- Reads from 'tag1' column
                    tag_from_col_2 = row.get('Class2') # <-- Reads from 'tag2' column

                    all_tags_list = []
                    
                    # 2. Check to make sure the tag isn't 'None' or empty
                    if tag_from_col_1 and tag_from_col_1 != 'None':
                        all_tags_list.append(tag_from_col_1.strip())
                    if tag_from_col_2 and tag_from_col_2 != 'None':
                        all_tags_list.append(tag_from_col_2.strip())
                    
                    # 3. Join them with a comma
                    final_tags_string = ", ".join(all_tags_list)
                    # --- END TAG LOGIC ---

                    price_value = None
                    for column in POI_PRICE_COLUMNS:
                        column_value = row.get(column)
                        if column_value:
                            price_value = self.parse_poi_price(column_value)
                            if price_value is not None:
                                break

                    # Get highlight from the highlights dictionary
                    poi_id = (row.get('ID') or '').strip()
                    highlight_text = highlights_dict.get(poi_id, '')

                    Poi.objects.create(
                        name=row['Tên địa điểm'],
                        address=row['Địa chỉ'],
                        open_hours=row.get('Giờ mở cửa'),
                        tiktok_link=row.get('Link TikTok') if row.get('Link TikTok') else None,
                        rating=float(row['Đánh giá']) if row.get('Đánh giá') else None,
                        price_per_person=price_value,
                        highlight=highlight_text,
                        latitude=float((row['Lat']).replace(',', '.')),
                        longitude=float((row['Lon']).replace(',', '.')),
                        tags=final_tags_string,
                        image_code=poi_id or None,
                    )
            
            self.stdout.write("Loading Eatery data...")
            with open('dalat_eateries.csv', mode='r', encoding='utf-8') as file:
                reader = csv.DictReader(file)
                for row in reader:
                    # --- ADD THIS PARSING LOGIC ---
                    price_str = row.get('Giá tiền') # Or your exact column name
                    
                    price_min_val = None
                    price_max_val = None
                    
                    if price_str:
                        # 1. Remove '.' (e.g., "100.000" -> "100000")
                        cleaned_str = price_str.replace('.', '')
                        
                        # 2. Split by '-'
                        parts = cleaned_str.split('-')
                        try:
                            if len(parts) == 2:
                                # It's a range: "100000 - 200000"
                                price_min_val = int(parts[0].strip())
                                price_max_val = int(parts[1].strip())
                            elif len(parts) == 1:
                                # It's a single value: "50000"
                                price_min_val = int(parts[0].strip())
                                price_max_val = price_min_val # Set both to the same
                        
                        except ValueError:
                            # This catches errors if the field has text like "Free"
                            self.stdout.write(f"Warning: Could not parse price '{price_str}'")
                            pass # price_min_val and price_max_val will remain None
                    
                    # --- END OF PARSING LOGIC ---

                    Eatery.objects.create(
                        name=row['Tên quán'],
                        address=row['Địa chỉ'],
                        open_hours=row.get('Giờ mở cửa'),
                        time_tags=parse_time_tags(row.get('Giờ mở cửa')), # Adjust CSV column name if needed
                        latitude=float((row['Lat']).replace(',', '.')),
                        longitude=float((row['Lon']).replace(',', '.')),
                        rating=float((row['Đánh giá']).replace(',', '.')) if row.get('Đánh giá') else None,
                        tiktok_link=row.get('Media') if row.get('Media') else None,
                        price_min=price_min_val,
                        price_max=price_max_val,
                    image_code=(row.get('ID') or '').strip() or None,
                )
        
            # ==========================================
            # LOAD ENGLISH TRANSLATIONS
            # ==========================================
            self.stdout.write("\n" + "="*70)
            self.stdout.write("Loading English translations...")
            self.stdout.write("="*70)
            
            # Load POIs English data
            pois_en_path = 'dalat_pois_en.csv'
            try:
                with open(pois_en_path, 'r', encoding='utf-8') as file:
                    reader = csv.DictReader(file)
                    updated_count = 0
                    for row in reader:
                        try:
                            poi_id = row.get('ID')
                            if poi_id:
                                poi = Poi.objects.filter(image_code=poi_id).first()
                                if poi:
                                    poi.name_en = row.get('Name_EN', '')
                                    poi.address_en = row.get('Address_EN', '')
                                    poi.highlight_en = row.get('Highlight_EN', '')
                                    poi.tags_en = row.get('Tags_EN', '')
                                    poi.save()
                                    updated_count += 1
                        except Exception as e:
                            self.stdout.write(f"  ✗ Error updating POI {row.get('ID')}: {e}")
                    
                    self.stdout.write(self.style.SUCCESS(f"\n✓ Updated {updated_count} POIs with English data"))
            
            except FileNotFoundError:
                self.stdout.write(self.style.WARNING(
                    f"\n⚠ Warning: {pois_en_path} not found. English POI data will be empty."
                ))
            
            # Load Eateries English data
            eateries_en_path = 'dalat_eateries_en.csv'
            try:
                with open(eateries_en_path, 'r', encoding='utf-8') as file:
                    reader = csv.DictReader(file)
                    updated_count = 0
                    for row in reader:
                        try:
                            eatery_id = row.get('ID')
                            if eatery_id:
                                eatery = Eatery.objects.filter(image_code=eatery_id).first()
                                if eatery:
                                    eatery.name_en = row.get('Name_EN', '')
                                    eatery.address_en = row.get('Address_EN', '')
                                    eatery.time_tags_en = row.get('Time_Tags_EN', '')
                                    eatery.save()
                                    updated_count += 1
                        except Exception as e:
                            self.stdout.write(f"  ✗ Error updating Eatery {row.get('ID')}: {e}")
                    
                    self.stdout.write(self.style.SUCCESS(f"\n✓ Updated {updated_count} eateries with English data"))
            
            except FileNotFoundError:
                self.stdout.write(self.style.WARNING(
                    f"\n⚠ Warning: {eateries_en_path} not found. English eatery data will be empty."
                ))
            
            self.stdout.write("\n" + "="*70)
            # ==========================================
            # END ENGLISH TRANSLATIONS
            # ==========================================
        
            self.stdout.write(self.style.SUCCESS('Successfully loaded all data!'))
        
        finally:
            # Re-enable signals and trigger one reload
            home.signals._skip_signals = False
            
            # Manually trigger AI reload once after all data is loaded
            self.stdout.write("Triggering AI data reload...")
            home.signals.trigger_ai_data_reload()