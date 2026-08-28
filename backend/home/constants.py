"""
Constants used across the home app
"""

SEARCH_SUGGESTION_LIMIT = 10
DEFAULT_FALLBACK_COORDS = (11.9404, 108.4583)  # Da Lat Market
DEFAULT_FALLBACK_LABEL = "Da Lat Market (fallback)"
VALID_EATERY_SLOTS = {'morning', 'afternoon', 'evening'}
SLOT_LABELS_EN_TO_VI = {
    'morning': 'Sáng',
    'afternoon': 'Trưa',
    'evening': 'Tối',
}

TRAVEL_MOOD_TAGS = {
    'Relaxed': ['Pure nature', '50% nature', 'Healing', 'Spiritual'],
    'Active': ['Sporty', '50% nature'],
    'Romantic': ['Cafe', 'Eating', '50% human'],
    'Foodie': ['Eating', 'Cafe'],
    'Culture': ['History', 'Manmade', '50% human'],
    'Social': ['Cafe', 'Eating', '50% human'],
    'Shopping': ['Manmade', '50% human'],
    'Healing': ['Healing', 'Pure nature', 'Spiritual'],
    'Bizarre': ['Bizarre'],
}
