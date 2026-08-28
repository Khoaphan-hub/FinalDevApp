from django import template
from decimal import Decimal, InvalidOperation

register = template.Library()

@register.filter(name='vnd_format')
def vnd_format(value):
    """
    Format a number as Vietnamese Dong (VND) with thousands separator.
    Example: 100000 -> 100.000VND
    """
    if value is None or value == '':
        return ''
    
    try:
        # Convert to float first to handle Decimal, int, or string
        num = float(value)
        
        # Round to nearest integer (VND typically doesn't use decimals)
        num = int(round(num))
        
        # Format with dot as thousands separator
        formatted = f"{num:,}".replace(',', '.')
        
        return f"{formatted}VND"
    except (ValueError, TypeError, InvalidOperation):
        return value

@register.filter(name='vnd_format_range')
def vnd_format_range(min_val, max_val):
    """
    Format a price range in VND.
    Example: (50000, 100000) -> 50.000VND - 100.000VND
    """
    if min_val is None and max_val is None:
        return '-'
    
    min_formatted = vnd_format(min_val) if min_val is not None else '-'
    max_formatted = vnd_format(max_val) if max_val is not None else ''
    
    if max_val is not None and min_val is not None:
        return f"{min_formatted} - {max_formatted}"
    elif min_val is not None:
        return min_formatted
    else:
        return max_formatted


@register.filter(name='dict_get')
def dict_get(mapping, key):
    """Safely fetch a key from a mapping inside templates."""
    if mapping is None:
        return None
    try:
        return mapping.get(key)
    except AttributeError:
        return None


@register.filter(name='tag_to_class')
def tag_to_class(tag):
    """
    Convert a tag name to a CSS class name.
    Example: 'Pure nature' -> 'pure-nature'
             '50% nature' -> '50-nature'
    """
    if not tag:
        return 'default'
    return tag.lower().replace(' ', '-').replace('%', '')


TAG_TRANSLATIONS = {
    'Pure nature': 'Thiên nhiên',
    '50% nature': '50% thiên nhiên',
    'Healing': 'Chữa lành',
    'Spiritual': 'Tâm linh',
    'Sporty': 'Thể thao',
    'Cafe': 'Cà phê',
    'Eating': 'Ăn uống',
    '50% human': '50% con người',
    'History': 'Lịch sử',
    'Manmade': 'Nhân tạo',
    'Bizarre': 'Kỳ lạ',
    'General': 'Chung',
}

@register.filter(name='translate_tag_vi')
def translate_tag_vi(tag_en):
    if not tag_en:
        return tag_en
    
    # Handle comma-separated tags
    tags = [t.strip() for t in tag_en.split(',')]
    translated_tags = [TAG_TRANSLATIONS.get(t, t) for t in tags]
    return ', '.join(translated_tags)
