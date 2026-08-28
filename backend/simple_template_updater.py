"""
Simple template updater for bilingual POI/Eatery names
Updates only the essential fields: name, address, time_tags
"""
import re

def update_template(filepath):
    """Update template file with bilingual attributes"""
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # POI name - simple pattern
    content = re.sub(
        r'<div class="item-name">{{ poi\.name }}</div>',
        '<div class="item-name" data-vi="{{ poi.name }}" data-en="{{ poi.name_en|default:poi.name }}">{{ poi.name }}</div>',
        content
    )
    
    # POI address (simple pattern)
    content = re.sub(
        r'📍 {{ poi\.address }}',
        '<span data-vi="📍 {{ poi.address }}" data-en="📍 {{ poi.address_en|default:poi.address }}">📍 {{ poi.address }}</span>',
        content
    )
    
    # POI highlight
    content = re.sub(
        r'{{ poi\.highlight\|capfirst }}',
        '<span data-vi="{{ poi.highlight|capfirst }}" data-en="{{ poi.highlight_en|default:poi.highlight|capfirst }}">{{ poi.highlight|capfirst }}</span>',
        content
    )
    
    # Eatery name
    content = re.sub(
        r'<div class="item-name">{{ eatery\.name }}</div>',
        '<div class="item-name" data-vi="{{ eatery.name }}" data-en="{{ eatery.name_en|default:eatery.name }}">{{ eatery.name }}</div>',
        content
    )
    
    # Eatery address
    content = re.sub(
        r'📍 {{ eatery\.address }}',
        '<span data-vi="📍 {{ eatery.address }}" data-en="📍 {{ eatery.address_en|default:eatery.address }}">📍 {{ eatery.address }}</span>',
        content
    )
    
    # Eatery time tags
    content = re.sub(
        r'{{ eatery\.time_tags }}',
        '<span data-vi="{{ eatery.time_tags }}" data-en="{{ eatery.time_tags_en|default:eatery.time_tags }}">{{ eatery.time_tags }}</span>',
        content
    )
    
    # Check if changes were made
    if content != original_content:
        # Create backup
        with open(filepath + '.backup', 'w', encoding='utf-8') as f:
            f.write(original_content)
        print(f"  ✓ Created backup: {filepath}.backup")
        
        # Write updated content
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"  ✓ Updated: {filepath}")
        return True
    else:
        print(f"  - No changes needed: {filepath}")
        return False

if __name__ == '__main__':
    files_to_update = [
        'home/Templates/trip_selection_combined.html',
        'home/Templates/itinerary.html'
    ]
    
    print("="*70)
    print("UPDATING TEMPLATES FOR BILINGUAL SUPPORT")
    print("="*70)
    
    updated_count = 0
    for filepath in files_to_update:
        print(f"\nProcessing: {filepath}")
        if update_template(filepath):
            updated_count += 1
    
    print("\n" + "="*70)
    print(f"✓ Updated {updated_count}/{len(files_to_update)} files")
    print("="*70)
