"""
Automatic template updater for bilingual support
This script adds data-vi and data-en attributes to POI/Eatery fields
"""

import re
import os

def update_poi_fields(content):
    """Add bilingual attributes to POI fields"""
    
    # POI name
    content = re.sub(
        r'(<div class="item-name">)({{ poi\.name }})(</div>)',
        r'\1\2\3'.replace(r'\2', r'{{ poi.name }}</div>').replace(
            '<div class="item-name">{{ poi.name }}</div>',
            '<div class="item-name" data-vi="{{ poi.name }}" data-en="{{ poi.name_en|default:poi.name }}">{{ poi.name }}</div>'
        ),
        content
    )
    
    # POI category/tags
    content = re.sub(
        r'(<div class="item-category">)({{ poi\.tags\|default:"General" }})(</div>)',
        r'<div class="item-category" data-vi="{{ poi.tags|default:\'General\' }}" data-en="{{ poi.tags_en|default:poi.tags|default:\'General\' }}">{{ poi.tags|default:"General" }}</div>',
        content
    )
    
    # POI address
    content = re.sub(
        r'(📍 {{ poi\.address }})',
        r'" data-vi="📍 {{ poi.address }}" data-en="📍 {{ poi.address_en|default:poi.address }}">📍 {{ poi.address }}',
        content
    )
    
    # POI highlight - wrap in span
    content = re.sub(
        r'(\s+)({{ poi\.highlight\|capfirst }})',
        r'\1<span data-vi="{{ poi.highlight|capfirst }}" data-en="{{ poi.highlight_en|default:poi.highlight|capfirst }}">{{ poi.highlight|capfirst }}</span>',
        content
    )
    
    return content

def update_eatery_fields(content):
    """Add bilingual attributes to Eatery fields"""
    
    # Eatery name
    content = re.sub(
        r'<div class="item-name">{{ eatery\.name }}</div>',
        r'<div class="item-name" data-vi="{{ eatery.name }}" data-en="{{ eatery.name_en|default:eatery.name }}">{{ eatery.name }}</div>',
        content
    )
    
    # Eatery address
    content = re.sub(
        r'📍 {{ eatery\.address }}',
        r'📍 {{ eatery.address }}" data-vi="📍 {{ eatery.address }}" data-en="📍 {{ eatery.address_en|default:eatery.address }}',
        content
    )
    
    # Time tags
    content = re.sub(
        r'{{ eatery\.time_tags }}',
        r'<span data-vi="{{ eatery.time_tags }}" data-en="{{ eatery.time_tags_en|default:eatery.time_tags }}">{{ eatery.time_tags }}</span>',
        content
    )
    
    return content

def update_template_file(filepath):
    """Update a single template file"""
    print(f"\nUpdating: {filepath}")
    
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        original_length = len(content)
        
        # Apply POI updates
        content = update_poi_fields(content)
        
        # Apply Eatery updates
        content = update_eatery_fields(content)
        
        # Backup original
        backup_path = filepath + '.backup'
        with open(backup_path, 'w', encoding='utf-8') as f:
            f.write(content)
        
        # Write updated content
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        
        changes = len(content) - original_length
        print(f"  ✓ Updated ({changes:+d} characters)")
        print(f"  ✓ Backup saved to: {backup_path}")
        
        return True
        
    except Exception as e:
        print(f"  ✗ Error: {e}")
        return False

if __name__ == '__main__':
    print("=" * 70)
    print(" " * 20 + "TEMPLATE BILINGUAL UPDATER")
    print("=" * 70)
    
    templates_dir = r"D:\Hcmus\Y2T1- Computational Thinking\CS252-Dalat\firstsite\home\Templates"
    
    files_to_update = [
        os.path.join(templates_dir, "trip_selection_combined.html"),
        os.path.join(templates_dir, "itinerary.html"),
    ]
    
    print("\nNOTE: This script will:")
    print("1. Add data-vi and data-en attributes to POI/Eatery fields")
    print("2. Create .backup files before making changes")
    print("3. Update templates in-place")
    
    input("\nPress Enter to continue or Ctrl+C to cancel...")
    
    success_count = 0
    for filepath in files_to_update:
        if os.path.exists(filepath):
            if update_template_file(filepath):
                success_count += 1
        else:
            print(f"\n✗ File not found: {filepath}")
    
    print("\n" + "=" * 70)
    print(f"✓ Updated {success_count}/{len(files_to_update)} templates")
    print("=" * 70)
    print("\nMANUAL REVIEW NEEDED:")
    print("  - Check .backup files to compare changes")
    print("  - Test language switching in browser")
    print("  - If issues occur, restore from .backup files")
