"""
Auto-translation script for Da Lat POIs and Eateries
Uses Deep Translator (Google Translate) for automatic translation
"""

import csv
import os
import time
from deep_translator import GoogleTranslator

# Initialize translator
translator = GoogleTranslator(source='vi', target='en')

# High-quality manual translations for location names
LOCATION_NAME_TRANSLATIONS = {
    "Chợ Đà Lạt": "Da Lat Market",
    "Dinh tỉnh trưởng": "Provincial Governor's Palace",
    "Quảng trường Lâm Viên": "Lam Vien Square",
    "Hồ Xuân Hương Đà Lạt": "Xuan Huong Lake Da Lat",
    "Chùa Linh Sơn Đà Lạt": "Linh Son Pagoda Da Lat",
    "Nhà Thờ Don Bosco Đà Lạt": "Don Bosco Church Da Lat",
    "Thác Datanla Đà Lạt": "Datanla Waterfall Da Lat",
    "Đồi Robin Đà Lạt": "Robin Hill Da Lat",
    "Cổng trời Bali Đà Lạt Greenhills": "Bali Heaven's Gate Da Lat Greenhills",
    "Thiền Viện Trúc Lâm": "Truc Lam Zen Monastery",
    "Nhà Thờ Con Gà Đà Lạt": "Chicken Church Da Lat",
    "Ga Đà Lạt": "Da Lat Railway Station",
    "Langfarm Center": "Langfarm Center",
    "Crazy House": "Crazy House",
    "Fresh Garden Đà Lạt": "Fresh Garden Da Lat",
}

# Common address terms
ADDRESS_TERMS = {
    "Phường": "Ward",
    "Xã": "Commune", 
    "Huyện": "District",
    "Thành phố": "City",
    "Tỉnh": "Province",
    "Đường": "St.",
    "Số": "No.",
    "Đà Lạt": "Da Lat",
    "Lâm Đồng": "Lam Dong",
    "Việt Nam": "Vietnam",
}

# Time tags
TIME_TAGS = {
    "Sáng": "morning",
    "Trưa": "afternoon",
    "Tối": "evening",
    "Sáng/Trưa/Tối": "morning,afternoon,evening",
    "Sáng/Trưa": "morning,afternoon",
    "Trưa/Tối": "afternoon,evening",
}

def translate_text(text, is_address=False, max_retries=3):
    """
    Translate Vietnamese text to English using Deep Translator (Google Translate)
    
    Args:
        text: Vietnamese text to translate
        is_address: Whether this is an address (apply special handling)
        max_retries: Number of retry attempts on failure
    
    Returns:
        Translated English text
    """
    if not text or text.strip() == "" or text == "N/A":
        return text
    
    # Apply manual replacements for addresses
    if is_address:
        result = text
        for vi, en in ADDRESS_TERMS.items():
            result = result.replace(vi, en)
        return result
    
    # Use Deep Translator with retry logic
    for attempt in range(max_retries):
        try:
            time.sleep(0.3)  # Rate limiting
            translated = translator.translate(text)
            return translated
        except Exception as e:
            print(f"  ⚠ Translation attempt {attempt + 1} failed: {e}")
            if attempt < max_retries - 1:
                time.sleep(1)  # Wait before retry
            else:
                print(f"  ✗ Failed to translate: {text[:50]}...")
                return text  # Return original on final failure
    
    return text

def create_pois_english_csv():
    """Create English version of POIs CSV using Google Translate API"""
    input_file = 'dalat_pois.csv'
    output_file = 'dalat_pois_en.csv'
    
    print("\n" + "="*70)
    print("TRANSLATING POIs (81 locations)")
    print("="*70)
    
    with open(input_file, 'r', encoding='utf-8') as infile:
        reader = csv.DictReader(infile)
        
        fieldnames = ['ID', 'Name_EN', 'Address_EN', 'Highlight_EN', 'Tags_EN']
        rows = []
        count = 0
        
        for row in reader:
            # Skip rows without ID
            if not row.get('ID') or not row['ID'].strip():
                continue
            
            count += 1
            poi_id = row['ID']
            name_vi = row.get('Tên địa điểm', '')
            address_vi = row.get('Địa chỉ', '')
            highlight_vi = row.get('Highlights', '')
            tags = row.get('Class1', '')
            
            # Check manual translation first for names
            name_en = LOCATION_NAME_TRANSLATIONS.get(name_vi)
            if not name_en:
                name_en = translate_text(name_vi, is_address=False)
            
            # Translate address (apply manual replacements)
            address_en = translate_text(address_vi, is_address=True)
            
            # Translate highlight description
            highlight_en = translate_text(highlight_vi, is_address=False)
            
            english_row = {
                'ID': poi_id,
                'Name_EN': name_en,
                'Address_EN': address_en,
                'Highlight_EN': highlight_en,
                'Tags_EN': tags  # Keep tags in English (already in English)
            }
            rows.append(english_row)
            
            print(f"  [{count}/81] {poi_id}: {name_vi} → {name_en}")
    
    # Write to CSV
    with open(output_file, 'w', encoding='utf-8', newline='') as outfile:
        writer = csv.DictWriter(outfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    
    print("\n" + "="*70)
    print(f"✓ SUCCESS: Created {output_file} with {len(rows)} POIs")
    print("="*70)

def create_eateries_english_csv():
    """Create English version of Eateries CSV using Google Translate API"""
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
            
            print(f"  [{count}/166] {eatery_id}: {name_vi[:40]} → {name_en[:40]}")
    
    # Write to CSV
    with open(output_file, 'w', encoding='utf-8', newline='') as outfile:
        writer = csv.DictWriter(outfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    
    print("\n" + "="*70)
    print(f"✓ SUCCESS: Created {output_file} with {len(rows)} eateries")
    print("="*70)

if __name__ == '__main__':
    print("\n" + "=" * 70)
    print(" " * 15 + "DA LAT BILINGUAL DATASET GENERATOR")
    print(" " * 20 + "Using Google Translate API")
    print("=" * 70)
    
    start_time = time.time()
    
    try:
        # Translate POIs (81 locations)
        create_pois_english_csv()
        
        # Translate Eateries (166 restaurants)
        create_eateries_english_csv()
        
        elapsed_time = time.time() - start_time
        
        print("\n" + "=" * 70)
        print("✅ TRANSLATION COMPLETE!")
        print("=" * 70)
        print(f"⏱ Time elapsed: {elapsed_time:.1f} seconds")
        print(f"📁 Created files:")
        print(f"   - dalat_pois_en.csv (81 POIs)")
        print(f"   - dalat_eateries_en.csv (166 eateries)")
        print("\n📋 NEXT STEPS:")
        print("   1. Review generated CSV files")
        print("   2. Update load_data.py (see LOAD_DATA_UPDATE_EXAMPLE.py)")
        print("   3. Run: python manage.py load_data")
        print("   4. Update views.py and templates")
        print("   5. Test language switching 🇻🇳 ⟷ 🇬🇧")
        print("=" * 70 + "\n")
        
    except FileNotFoundError as e:
        print(f"\n❌ ERROR: CSV file not found - {e}")
        print("Make sure dalat_pois.csv and dalat_eateries.csv are in the current directory")
    except Exception as e:
        print(f"\n❌ ERROR: {e}")
        import traceback
        traceback.print_exc()
