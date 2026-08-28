"""
Add bilingual support for POI tags in trip_selection_combined.html
"""
import re

filepath = 'home/Templates/trip_selection_combined.html'

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Update POI tags/category
content = re.sub(
    r'<div class="item-category">{{ poi\.tags\|default:"General" }}</div>',
    '<div class="item-category" data-vi="{{ poi.tags|default:\'General\' }}" data-en="{{ poi.tags_en|default:poi.tags|default:\'General\' }}">{{ poi.tags|default:"General" }}</div>',
    content
)

# Write back
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ Updated poi.tags fields")
