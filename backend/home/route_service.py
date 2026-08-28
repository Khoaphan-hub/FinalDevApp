"""
Route service for retrieving pre-computed routes from dalat_routes.db
"""
import sqlite3
import os
import pandas as pd
from pathlib import Path
from typing import Optional, Dict, Tuple
import polyline


# Get the path to the database files
BASE_DIR = Path(__file__).resolve().parent.parent
DB_PATH = BASE_DIR / 'dalat_routes.db'
DISTANCE_DB_PATH = BASE_DIR / 'dalat_distances.db'

# Travel speed constant (km/h)
TRAVEL_SPEED_KMH = 36


def get_distance_km(origin_id: str, dest_id: str) -> Optional[float]:
    """
    Get distance in kilometers between two locations from the database.
    
    Args:
        origin_id: Origin location ID (e.g., 'E001', 'P001')
        dest_id: Destination location ID (e.g., 'E002', 'P002')
    
    Returns:
        Distance in kilometers, or None if not found
    """
    if not os.path.exists(DISTANCE_DB_PATH):
        print(f"Distance database not found at {DISTANCE_DB_PATH}")
        return None
    
    try:
        conn = sqlite3.connect(str(DISTANCE_DB_PATH))
        cursor = conn.cursor()
        
        cursor.execute(
            "SELECT distance_km FROM distances WHERE origin_id = ? AND dest_id = ?",
            (origin_id, dest_id)
        )
        
        result = cursor.fetchone()
        conn.close()
        
        if result:
            return float(result[0])
        return None
    except Exception as e:
        print(f"Error fetching distance: {e}")
        return None


def calculate_eta_minutes(distance_km: float) -> int:
    """
    Calculate estimated time of arrival in minutes based on distance.
    Assumes travel speed of 36 km/h.
    
    Args:
        distance_km: Distance in kilometers
    
    Returns:
        ETA in minutes (rounded)
    """
    if distance_km <= 0:
        return 0
    
    hours = distance_km / TRAVEL_SPEED_KMH
    minutes = hours * 60
    return round(minutes)


def get_route_polyline(origin_id: str, dest_id: str) -> Optional[str]:
    """
    Get the encoded polyline for a route from the database.
    
    Args:
        origin_id: Origin location ID (e.g., 'E001', 'P001')
        dest_id: Destination location ID (e.g., 'E002', 'P002')
    
    Returns:
        Encoded polyline string, or None if not found
    """
    if not os.path.exists(DB_PATH):
        print(f"Database not found at {DB_PATH}")
        return None
    
    try:
        conn = sqlite3.connect(str(DB_PATH))
        cursor = conn.cursor()
        
        cursor.execute(
            "SELECT polyline FROM routes WHERE origin_id = ? AND dest_id = ?",
            (origin_id, dest_id)
        )
        
        result = cursor.fetchone()
        conn.close()
        
        if result:
            return result[0]
        return None
    except Exception as e:
        print(f"Error fetching route polyline: {e}")
        return None


def decode_polyline_to_coords(encoded_polyline: str) -> list:
    """
    Decode a polyline string to a list of [lat, lon] coordinates.
    
    Args:
        encoded_polyline: Encoded polyline string
    
    Returns:
        List of [lat, lon] coordinate pairs
    """
    try:
        coords = polyline.decode(encoded_polyline)
        # polyline.decode returns [(lat, lon), ...], we need [[lat, lon], ...]
        return [[lat, lon] for lat, lon in coords]
    except Exception as e:
        print(f"Error decoding polyline: {e}")
        return []


def get_route_data(origin_id: str, dest_id: str) -> Optional[Dict]:
    """
    Get complete route data including polyline, distance, and ETA.
    
    Args:
        origin_id: Origin location ID (e.g., 'E001', 'P001')
        dest_id: Destination location ID (e.g., 'E002', 'P002')
    
    Returns:
        Dictionary with route data:
        {
            'polyline': 'encoded_polyline_string',
            'coordinates': [[lat, lon], ...],
            'distance': distance_in_meters (float),
            'duration': duration_in_seconds (int),
            'distance_km': distance_in_km (float),
            'duration_min': duration_in_minutes (int)
        }
        Returns None if route not found
    """
    # Get polyline from database
    encoded_polyline = get_route_polyline(origin_id, dest_id)
    if not encoded_polyline:
        return None
    
    # Get distance from matrix
    distance_km = get_distance_km(origin_id, dest_id)
    if distance_km is None:
        return None
    
    # Calculate ETA
    duration_min = calculate_eta_minutes(distance_km)
    
    # Decode polyline to coordinates
    coordinates = decode_polyline_to_coords(encoded_polyline)
    
    return {
        'polyline': encoded_polyline,
        'coordinates': coordinates,
        'distance': distance_km * 1000,  # Convert to meters for consistency with OSRM
        'duration': duration_min * 60,   # Convert to seconds for consistency with OSRM
        'distance_km': distance_km,
        'duration_min': duration_min
    }


def get_location_id_from_code(location_code: str) -> Optional[str]:
    """
    Extract the location ID from various code formats.
    Handles codes like 'E001', 'P001', or full names.
    
    Args:
        location_code: Location code or name
    
    Returns:
        Location ID string (e.g., 'E001', 'P001'), or None if invalid
    """
    if not location_code:
        return None
    
    # If it already looks like a valid ID (E001, P001, A001, etc.)
    if location_code.startswith(('E', 'P', 'A')) and len(location_code) >= 4:
        return location_code[:4]  # Return first 4 characters (e.g., 'E001', 'P001', 'A032')
    
    return None


def is_dalat_location(location_code: str) -> bool:
    """
    Check if a location code represents a Dalat location in our database.
    
    Args:
        location_code: Location code to check
    
    Returns:
        True if it's a valid Dalat location code, False otherwise
    """
    location_id = get_location_id_from_code(location_code)
    if not location_id:
        return False
    
    # Check if this ID exists in our distance database
    if not os.path.exists(DISTANCE_DB_PATH):
        return False
    
    try:
        conn = sqlite3.connect(str(DISTANCE_DB_PATH))
        cursor = conn.cursor()
        
        cursor.execute(
            "SELECT 1 FROM distances WHERE origin_id = ? LIMIT 1",
            (location_id,)
        )
        
        result = cursor.fetchone()
        conn.close()
        
        return result is not None
    except Exception as e:
        print(f"Error checking location: {e}")
        return False
