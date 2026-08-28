from django import template

register = template.Library()

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
