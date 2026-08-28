"""
Fix Time_Tags_EN in dalat_eateries_en.csv
"""
import csv

TIME_TAGS = {
    'Tất cả': 'All day',
    'Trưa/Tối': 'Lunch/Dinner',
    'Sáng/Trưa': 'Breakfast/Lunch',
    'Sáng': 'Breakfast',
    'Trưa': 'Lunch',
    'Tối': 'Dinner',
    'Sáng/Tối': 'Breakfast/Dinner',
    'Sáng/Trưa/Tối': 'All day'  # Added missing mapping
}

# Read original eateries
eateries_vi = {}
with open('dalat_eateries.csv', 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        if row.get('ID'):
            eateries_vi[row['ID']] = row.get('Sáng/Trưa/Tối', '').strip()

# Fix English eateries
with open('dalat_eateries_en.csv', 'r', encoding='utf-8') as f:
    rows = list(csv.DictReader(f))

fixed_count = 0
for row in rows:
    eatery_id = row['ID']
    time_tag_vi = eateries_vi.get(eatery_id, '')
    
    # Apply translation
    if time_tag_vi in TIME_TAGS:
        row['Time_Tags_EN'] = TIME_TAGS[time_tag_vi]
        fixed_count += 1
    else:
        # Keep original if no mapping found
        print(f"  ⚠ No mapping for '{time_tag_vi}' (ID: {eatery_id})")

# Write back
with open('dalat_eateries_en.csv', 'w', encoding='utf-8', newline='') as f:
    writer = csv.DictWriter(f, fieldnames=['ID', 'Name_EN', 'Address_EN', 'Time_Tags_EN'])
    writer.writeheader()
    writer.writerows(rows)

print(f"\n✓ Fixed {fixed_count}/{len(rows)} time tags")
