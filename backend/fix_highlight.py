"""
Fix POI highlight icon to be inside data-vi/data-en
"""
import re

filepath = 'home/Templates/trip_selection_combined.html'

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern: Old structure with separate highlight-icon span
old_pattern = r'<div class="item-highlight">\s*<span class="highlight-icon">💡</span>\s*\n?\s*<span data-vi="([^"]+)" data-en="([^"]+)">([^<]+)</span>\s*</div>'

# New structure: icon inside data attributes
new_pattern = r'<div class="item-highlight">\n                                        <span data-vi="💡 \1" data-en="💡 \2">💡 \3</span>\n                                    </div>'

content = re.sub(old_pattern, new_pattern, content)

# Write back
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("✓ Fixed POI highlight icon positioning")
