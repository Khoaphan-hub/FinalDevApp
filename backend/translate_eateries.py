"""
Translate ONLY Eateries Dataset to English
Robust version with better error handling
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
    'Huyện': 'County',
    'Đà Lạt': 'Da Lat'
}

def translate_text(text, is_address=False, max_retries=3):
    """Translate text with retry logic and rate limiting"""
    if not text or text.strip() == '':
        return ''
    
    # Apply address term replacements if it's an address
    if is_address:
        result = text
        for vi, en in ADDRESS_TERMS.items():
            result = result.replace(vi, en)
        return result
    
    # Try translation with retries
    for attempt in range(max_retries):
        try:
            # Rate limiting: wait between requests
            time.sleep(0.5)
            
            # Translate using Google Translate
            translated = translator.translate(text)
            return translated.strip() if translated else text
            
        except Exception as e:
            if attempt < max_retries - 1:
                print(f"  ⚠ Retry {attempt + 1}/{max_retries}: {e}")
                time.sleep(2)  # Wait longer before retry
            else:
                print(f"  ⚠ Translation failed after {max_retries} attempts: {text}")
                return text  # Return original text if all retries fail

def create_eateries_csv():
    """Create English version of Eateries CSV"""
    input_file = 'dalat_eateries.csv'
    output_file = 'dalat_eateries_en.csv'
    
    print("\n" + "="*70)
    print("TRANSLATING EATERIES (166 restaurants)")
    print("="*70)
    
    with open(input_file, 'r', encoding='utf-8') as infile:
        reader = csv.DictReader(infile)
        
        fieldnames = ['ID', 'Name_EN', 'Address_EN', 'Time_Tags_EN']
        rows = []
        count = 0
        total = 166
        
        for row in reader:
            if not row.get('ID') or not row['ID'].strip():
                continue
            
            count += 1
            eatery_id = row['ID']
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
            
            # Progress indicator
            progress_pct = (count / total) * 100
            print(f"  [{count}/{total}] ({progress_pct:.1f}%) {eatery_id}: {name_vi} → {name_en}")
            
            # Save checkpoint every 20 items
            if count % 20 == 0:
                with open(output_file, 'w', encoding='utf-8', newline='') as checkpoint:
                    writer = csv.DictWriter(checkpoint, fieldnames=fieldnames)
                    writer.writeheader()
                    writer.writerows(rows)
                print(f"  ✓ Checkpoint saved: {count} eateries")
    
    # Final save
    with open(output_file, 'w', encoding='utf-8', newline='') as outfile:
        writer = csv.DictWriter(outfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    
    print("\n" + "="*70)
    print(f"✓ SUCCESS: Created {output_file} with {len(rows)} eateries")
    print("="*70)

if __name__ == '__main__':
    try:
        create_eateries_csv()
    except KeyboardInterrupt:
        print("\n\n⚠ Interrupted by user. Partial data may be saved in checkpoint.")
    except Exception as e:
        print(f"\n\n✗ ERROR: {e}")
