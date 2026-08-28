"""
Continue Eatery Translation from E082 onwards
"""
import csv
import time
from deep_translator import GoogleTranslator

# Initialize translator
translator = GoogleTranslator(source='vi', target='en')

# Manual translations for time tags
TIME_TAGS = {
    'Tất cả': 'All day',
    'Trưa/Tối': 'Lunch/Dinner',
    'Sáng/Trưa': 'Breakfast/Lunch',
    'Sáng': 'Breakfast',
    'Trưa': 'Lunch',
    'Tối': 'Dinner',
    'Sáng/Tối': 'Breakfast/Dinner'
}

# Address term replacements
ADDRESS_TERMS = {
    'Đường': 'Street',
    'Phường': 'Ward',
    'Quận': 'District',
    'Thành phố': 'City',
    'Tỉnh': 'Province',
    'Thị trấn': 'Town',
    'Xã': 'Commune',
    'Huyện': 'County'
}

def translate_text(text, is_address=False):
    """Translate text with retry logic and rate limiting"""
    if not text or text.strip() == '':
        return ''
    
    # Apply address term replacements if it's an address
    if is_address:
        for vi, en in ADDRESS_TERMS.items():
            text = text.replace(vi, en)
        return text
    
    try:
        # Rate limiting: wait between requests
        time.sleep(0.5)
        
        # Translate using Google Translate
        translated = translator.translate(text)
        return translated.strip() if translated else text
        
    except Exception as e:
        print(f"  ⚠ Translation failed: {e}")
        return text

def continue_eateries_translation():
    """Continue translating Eateries from E082"""
    input_file = 'dalat_eateries.csv'
    output_file = 'dalat_eateries_en.csv'
    
    print("\n" + "="*70)
    print("CONTINUING EATERY TRANSLATION FROM E082")
    print("="*70)
    
    with open(input_file, 'r', encoding='utf-8') as infile:
        reader = csv.DictReader(infile)
        
        fieldnames = ['ID', 'Name_EN', 'Address_EN', 'Time_Tags_EN']
        rows = []
        count = 0
        
        for row in reader:
            if not row.get('ID') or not row['ID'].strip():
                continue
            
            count += 1
            eatery_id = row['ID']
            
            # Skip already translated (E001-E081)
            eatery_num = int(eatery_id.replace('E', ''))
            if eatery_num < 82:
                continue
            
            name_vi = row.get('Tên quán', '')
            address_vi = row.get('Địa chỉ', '')
            time_tags_vi = row.get('Sáng/Trưa/Tối', '')
            
            # Translate restaurant name
            name_en = translate_text(name_vi, is_address=False)
            
            # Translate address
            address_en = translate_text(address_vi, is_address=True)
            
            # Translate time tags using manual dictionary
            time_tags_en = TIME_TAGS.get(time_tags_vi, time_tags_vi)
            
            english_row = {
                'ID': eatery_id,
                'Name_EN': name_en,
                'Address_EN': address_en,
                'Time_Tags_EN': time_tags_en
            }
            rows.append(english_row)
            
            print(f"  [{count}/166] {eatery_id}: {name_vi} → {name_en}")
    
    # Append to existing CSV or create new with header
    try:
        with open(output_file, 'r', encoding='utf-8') as check_file:
            existing_data = list(csv.DictReader(check_file))
    except FileNotFoundError:
        existing_data = []
    
    # Write all data (existing + new)
    all_rows = existing_data + rows
    with open(output_file, 'w', encoding='utf-8', newline='') as outfile:
        writer = csv.DictWriter(outfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(all_rows)
    
    print("\n" + "="*70)
    print(f"✓ SUCCESS: Added {len(rows)} eateries to {output_file}")
    print(f"✓ TOTAL: {len(all_rows)} eateries in file")
    print("="*70)

if __name__ == '__main__':
    continue_eateries_translation()
