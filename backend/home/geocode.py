"""
Geocoding utilities using Google Gemini AI with caching
"""
try:
    import google.generativeai as genai
except ImportError:
    genai = None
import json
import re
import sqlite3
import os
from functools import lru_cache
from rapidfuzz import fuzz
from django.conf import settings
from django.utils import timezone

# Configure Gemini AI
GEMINI_API_KEY_1 = getattr(settings, 'GEMINI_API_KEY_1', '')
if GEMINI_API_KEY_1 and genai is not None:
    genai.configure(api_key=GEMINI_API_KEY_1)


def normalize_for_comparison(text):
    """Normalize text for better comparison (remove Vietnamese variations and word order)."""
    if not text:
        return ''
    
    # Replace common Vietnamese variations
    replacements = {
        'phường': 'phuong',
        'quận': 'quan',
        'ward': 'phuong',
        'district': 'quan',
        'đường': 'duong',
        'street': 'duong',
        'phố': 'pho',
        'hotel': '',
        'khách sạn': '',
        'khach san': '',
    }
    
    text = text.lower()
    for old, new in replacements.items():
        text = text.replace(old, new)
    
    # Remove common words and sort remaining words for order-agnostic comparison
    # Keep punctuation like commas to preserve address structure
    return text


def find_cached_location(address, similarity_threshold=0.9):
    """
    Search for a cached location that matches the address using fuzzy matching.
    Only checks POI database (static landmarks/attractions), NOT accommodations or geocoded cache.
    Returns (latitude, longitude) if found with sufficient similarity, None otherwise.
    """
    from .models import Poi
    from rapidfuzz import process
    
    if not address:
        return None
    
    # Only check POI database (static landmarks/attractions)
    poi_candidates = [(poi.name.lower(), poi) for poi in Poi.objects.all()]
    if poi_candidates:
        poi_result = process.extractOne(
            address.lower(),
            [name for name, _ in poi_candidates],
            scorer=fuzz.ratio,
            score_cutoff=similarity_threshold * 100
        )
        if poi_result:
            matched_name, score, index = poi_result
            poi = poi_candidates[index][1]
            print(f"[POI DB] Fuzzy match found for '{address}' -> {poi.name} (ID: {poi.image_code}, similarity: {score:.2f}%)")
            return (poi.latitude, poi.longitude)
    
    print(f"[CACHE] No match found for '{address}'")
    return None


def geocode_address_via_gemini(address):
    """Call Gemini API to geocode an address. Internal function."""
    if not GEMINI_API_KEY_1:
        print("[GEMINI] API key not configured. Using fallback coordinates.")
        return None
    
    # Append "Da Lat, Vietnam" if not already in the address
    search_address = address
    if address and 'da lat' not in address.lower() and 'dalat' not in address.lower():
        search_address = f"{address}, Da Lat, Vietnam"
        print(f"[GEMINI] Expanded address: '{address}' -> '{search_address}'")
    
    try:
        model = genai.GenerativeModel('gemini-2.5-flash')
        
        prompt = f"""
        Find the geographic coordinates for the following location in Da Lat, Vietnam:
        "{search_address}"
        
        IMPORTANT: Make sure you find the EXACT location that matches this name in Da Lat, Vietnam.
        Do not substitute with a different place. Use the latest administrative boundaries in Vietnam.

        Return ONLY a JSON object with this exact format:
        {{
            "latitude": <latitude_value>,
            "longitude": <longitude_value>,
            "formatted_address": "<full street address with ward/district>",
            "place_name": "<name of the place/building>",
            "confidence": "<high/medium/low>"
        }}
        
        Example for "Hotel Du Parc":
        {{
            "latitude": 11.942702,
            "longitude": 108.435712,
            "formatted_address": "15 Tran Phu, Ward Xuan Huong, Da Lat, Lam Dong, Vietnam",
            "place_name": "Du Parc Hotel",
            "confidence": "high"
        }}
        
        Do not include any additional text or explanation, only the JSON object.
        """
        
        response = model.generate_content(prompt)
        response_text = response.text.strip()
        
        # Extract JSON from the response (in case there's markdown formatting)
        json_match = re.search(r'\{[^{}]*\}', response_text, re.DOTALL)
        if json_match:
            json_str = json_match.group(0)
            result = json.loads(json_str)
        else:
            # Try parsing the entire response as JSON
            result = json.loads(response_text)
        
        lat = result.get('latitude')
        lon = result.get('longitude')
        formatted_address = result.get('formatted_address', '')
        place_name = result.get('place_name', '')
        
        if lat is None or lon is None:
            print(f"[GEMINI] Failed to get coordinates for '{address}'")
            return None
        
        print(f"[GEMINI] Geocoded '{address}' -> lat={lat}, lon={lon}")
        if formatted_address:
            print(f"[GEMINI] Formatted address: '{formatted_address}'")
        if place_name:
            print(f"[GEMINI] Place name: '{place_name}'")
        
        # Return coordinates, formatted address, and place name
        return float(lat), float(lon), formatted_address, place_name
        
    except Exception as e:
        print(f"[GEMINI] Error geocoding '{address}': {str(e)}")
        return None


def find_accommodation_in_db(place_name, similarity_threshold=0.7):
    """
    Search for an accommodation in dalat_accommodations.db using fuzzy matching with rapidfuzz.
    Returns (id, latitude, longitude) if found, None otherwise.
    Uses 70% similarity threshold to handle partial names (e.g., "MerPerle" matches "MerPerle Dalat").
    """
    from rapidfuzz import process
    
    if not place_name or ',' in place_name:
        return None
    
    try:
        db_path = os.path.join(settings.BASE_DIR, 'dalat_accommodations.db')
        if not os.path.exists(db_path):
            print(f"[ACCOMMODATION DB] Database not found at {db_path}")
            return None
        
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        cursor.execute("SELECT id, name_en, name_vn, lat, lon FROM accommodations")
        accommodations = cursor.fetchall()
        conn.close()
        
        if not accommodations:
            return None
        
        # Normalize input for comparison
        normalized_input = normalize_for_comparison(place_name).lower()
        
        # Build a list of choices with their metadata
        choices = []
        for acc_id, name_en, name_vn, lat, lon in accommodations:
            for name in [name_en, name_vn]:
                if name:
                    normalized_name = normalize_for_comparison(name).lower()
                    choices.append((normalized_name, (acc_id, lat, lon, name)))
        
        # Use rapidfuzz's extractOne for efficient fuzzy matching
        result = process.extractOne(
            normalized_input,
            [choice[0] for choice in choices],
            scorer=fuzz.ratio,
            score_cutoff=similarity_threshold * 100
        )
        
        if result:
            matched_text, score, index = result
            acc_id, lat, lon, original_name = choices[index][1]
            print(f"[ACCOMMODATION DB] Fuzzy match found for '{place_name}' -> '{original_name}' (ID: {acc_id}, similarity: {score:.2f}%)")
            return (acc_id, lat, lon)
        
        print(f"[ACCOMMODATION DB] No match found for '{place_name}' (threshold: {similarity_threshold * 100:.0f}%)")
        return None
        
    except Exception as e:
        print(f"[ACCOMMODATION DB] Error searching database: {e}")
        return None


@lru_cache(maxsize=256)
def geocode_address(address, is_place_name=True, return_id=False):
    """
    Resolve a free-form address to (lat, lon) or (lat, lon, id) if return_id=True.
    
    Search order for place names (is_place_name=True):
    1. Check accommodations database with fuzzy matching
    2. Call Gemini API if not found
    
    Search order for addresses (is_place_name=False):
    1. Check cache (POI database and geocoded locations)
    2. Call Gemini API if not found
    
    Args:
        address: The address or place name to geocode
        is_place_name: True if this is a place name (from "Place Name" field),
                      False if it's a full address
        return_id: If True, return (lat, lon, id) for accommodations, (lat, lon, None) otherwise
    
    Returns:
        (lat, lon) if return_id=False
        (lat, lon, id) if return_id=True (id is accommodation ID or None)
    """
    if not address:
        return None
    
    # If it's a place name, search accommodations database first, then use Gemini
    if is_place_name and ',' not in address:
        accommodation_result = find_accommodation_in_db(address)
        if accommodation_result:
            # Result is (acc_id, lat, lon)
            acc_id, lat, lon = accommodation_result
            if return_id:
                return (lat, lon, acc_id)
            return (lat, lon)
        
        # Not in accommodations DB, use Gemini directly
        result = geocode_address_via_gemini(address)
        if result:
            # Result is now (lat, lon, formatted_address, place_name)
            lat, lon, formatted_address, place_name = result
            if return_id:
                return (lat, lon, None)
            return (lat, lon)
        
        if return_id:
            return None
        return None
    
    # For addresses (not place names), check cache first
    cached_coords = find_cached_location(address)
    if cached_coords:
        if return_id:
            return (cached_coords[0], cached_coords[1], None)
        return cached_coords
    
    # Not in cache, geocode via Gemini
    result = geocode_address_via_gemini(address)
    
    if result:
        # Result is now (lat, lon, formatted_address, place_name)
        lat, lon, formatted_address, place_name = result
        if return_id:
            return (lat, lon, None)
        return (lat, lon)
    
    if return_id:
        return None
    return None
