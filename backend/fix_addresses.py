"""
Fix Vietnamese text in English CSV addresses
Replaces "địa chỉ cũ" with "old address"
"""
import csv

def fix_addresses():
    # Fix POIs addresses
    pois_file = 'dalat_pois_en.csv'
    try:
        with open(pois_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            rows = list(reader)
        
        fixed_count = 0
        for row in rows:
            if 'Address_EN' in row:
                original = row['Address_EN']
                # Replace Vietnamese address terms
                row['Address_EN'] = row['Address_EN'].replace('địa chỉ cũ', 'old address')
                row['Address_EN'] = row['Address_EN'].replace('Địa chỉ cũ', 'Old address')
                row['Address_EN'] = row['Address_EN'].replace('( Địa', '( Old')
                row['Address_EN'] = row['Address_EN'].replace('( địa', '( old')
                
                if original != row['Address_EN']:
                    fixed_count += 1
        
        # Write back
        with open(pois_file, 'w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)
        
        print(f"✓ Fixed {fixed_count}/{len(rows)} POI addresses")
    
    except FileNotFoundError:
        print(f"⚠ Warning: {pois_file} not found")
    
    # Fix Eateries addresses (if needed)
    eateries_file = 'dalat_eateries_en.csv'
    try:
        with open(eateries_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            rows = list(reader)
        
        fixed_count = 0
        for row in rows:
            if 'Address_EN' in row:
                original = row['Address_EN']
                row['Address_EN'] = row['Address_EN'].replace('địa chỉ cũ', 'old address')
                row['Address_EN'] = row['Address_EN'].replace('Địa chỉ cũ', 'Old address')
                
                if original != row['Address_EN']:
                    fixed_count += 1
        
        # Write back
        with open(eateries_file, 'w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)
        
        print(f"✓ Fixed {fixed_count}/{len(rows)} Eatery addresses")
    
    except FileNotFoundError:
        print(f"⚠ Warning: {eateries_file} not found")

if __name__ == '__main__':
    print("="*70)
    print("FIXING VIETNAMESE TEXT IN ENGLISH ADDRESSES")
    print("="*70)
    fix_addresses()
    print("\n✓ Complete! Now run: python manage.py load_data")
