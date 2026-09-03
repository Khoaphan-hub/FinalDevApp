import numpy as np
import pandas as pd
from math import radians, sin, cos, sqrt, atan2, ceil
from sklearn.cluster import KMeans
import logging
import os
import sqlite3
import threading
from itertools import permutations

# The step-by-step trace below used to go to stdout via print(). That had two problems: it
# printed Vietnamese place names, which raised UnicodeEncodeError whenever process stdout was
# not UTF-8 (the wsgi/gunicorn case), and it could not be silenced in production. Logging is
# encoding-safe and its level is configured in settings.LOGGING.
logger = logging.getLogger(__name__)

MEAL_SLOT_LABELS = {
    'morning': 'Sáng',
    'afternoon': 'Trưa',
    'evening': 'Tối',
}

# Global variables to cache the distance database connection and location IDs
_DISTANCE_DB_PATH = None
_LOCATION_ID_SET = None
# Maps a model primary key to the id used inside dalat_distances.db, e.g. ('POI', 80) -> 'P001'.
_MATRIX_ID_BY_PK = None
# One read-only connection per thread. The dev server is multi-threaded and a SQLite
# connection cannot be shared across threads, but reopening a 19 MB file on every distance
# lookup was costing more than the queries themselves.
_DISTANCE_CONN = threading.local()

# --- 1. CORE FUNCTIONS ---

def get_distance_db_path():
    """
    Get the path to the distance database.
    Returns the database path.
    """
    global _DISTANCE_DB_PATH
    
    if _DISTANCE_DB_PATH is None:
        # Get the directory where this algorithm.py file is located
        current_dir = os.path.dirname(os.path.abspath(__file__))
        # Go up one level to firstsite directory
        parent_dir = os.path.dirname(current_dir)
        _DISTANCE_DB_PATH = os.path.join(parent_dir, 'dalat_distances.db')
    
    return _DISTANCE_DB_PATH

def get_distance_connection():
    """
    Returns this thread's read-only connection to the distance database, opening it once.

    The file is a pre-computed, never-modified road-distance matrix, so a long-lived
    read-only connection is safe and avoids paying the open/close cost per lookup.
    """
    conn = getattr(_DISTANCE_CONN, 'conn', None)
    if conn is None:
        # mode=ro tells SQLite the file is read-only, so it skips journal and lock setup.
        conn = sqlite3.connect('file:%s?mode=ro' % get_distance_db_path(), uri=True)
        _DISTANCE_CONN.conn = conn
    return conn


def get_all_location_ids():
    """
    Get all unique location IDs from the distance database (cached).
    Returns a set of location IDs.
    """
    global _LOCATION_ID_SET
    
    if _LOCATION_ID_SET is not None:
        return _LOCATION_ID_SET
    
    try:
        db_path = get_distance_db_path()
        logger.debug(f"Loading location IDs from: {db_path}")
        
        cursor = get_distance_connection().cursor()

        # Get all unique origin_ids (which should include all locations)
        cursor.execute("SELECT DISTINCT origin_id FROM distances")
        location_ids = set(row[0] for row in cursor.fetchall())
        
        _LOCATION_ID_SET = location_ids
        logger.debug(f"Location IDs loaded successfully. Count: {len(location_ids)} locations")
        return location_ids
    except Exception as e:
        logger.error(f"Error loading location IDs: {e}")
        return set()

def get_distance_from_db(origin_id: str, dest_id: str) -> float:
    """
    Get distance between two locations from the database.
    Returns distance in km, or None if not found.
    """
    try:
        cursor = get_distance_connection().cursor()

        cursor.execute(
            "SELECT distance_km FROM distances WHERE origin_id = ? AND dest_id = ?",
            (origin_id, dest_id)
        )

        result = cursor.fetchone()

        if result:
            return float(result[0])
        return None
    except Exception as e:
        logger.error(f"Error fetching distance from DB: {e}")
        return None

def get_matrix_id_map():
    """
    Builds, once per process, the map from a model primary key to its distance-matrix id.

    These are two different numbering schemes. dalat_distances.db was generated from the
    original CSV ordering and uses P001-P079 / E001-E163, while the Django primary keys are
    whatever the database assigned on import (currently POIs 80-158, eateries 164-326).
    image_code is the column that carries the original CSV code, so it is the correct join
    key between the two. Only 242 rows, so a single pass is cheap.
    """
    global _MATRIX_ID_BY_PK

    if _MATRIX_ID_BY_PK is not None:
        return _MATRIX_ID_BY_PK

    from .models import Poi, Eatery
    mapping = {}
    for pk, code in Poi.objects.values_list('id', 'image_code'):
        if code:
            mapping[('POI', pk)] = code
    for pk, code in Eatery.objects.values_list('id', 'image_code'):
        if code:
            mapping[('EATERY', pk)] = code

    _MATRIX_ID_BY_PK = mapping
    return mapping


def get_location_matrix_id(location):
    """
    Convert a location dict with 'id' and 'type' to matrix ID format.
    Returns None if not found in matrix.
    """
    loc_id = location.get('id')
    loc_type = location.get('type', 'POI')

    if loc_id is None or loc_id <= 0:
        return None

    # Look the code up instead of formatting the primary key. Formatting produced ids such as
    # P080/E164 that match no row in the matrix, so every lookup missed and the routing fell
    # back to straight-line distance for every leg.
    key = ('EATERY' if loc_type == 'EATERY' else 'POI', loc_id)
    return get_matrix_id_map().get(key)

def haversine_distance(lat1, lon1, lat2, lon2):
    """Calculates the distance between two points on the Earth's surface."""
    R = 6371  # Earth radius in kilometers
    lat1_rad, lon1_rad = radians(lat1), radians(lon1)
    lat2_rad, lon2_rad = radians(lat2), radians(lon2)

    dlon = lon2_rad - lon1_rad
    dlat = lat2_rad - lat1_rad

    a = sin(dlat / 2)**2 + cos(lat1_rad) * cos(lat2_rad) * sin(dlon / 2)**2
    c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c

def redistribute_pois(df, daily_poi_limit, num_days, kmeans_centers):
    """
    Checks clusters against the daily limit and moves surplus POIs (farthest from
    cluster center) to the smallest cluster until capacity is met.
    """
    
    # Track POI count per cluster
    cluster_counts = df.groupby('cluster').size().to_dict()
    
    # Identify overloaded and underloaded clusters
    overloaded_clusters = [k for k, v in cluster_counts.items() if v > daily_poi_limit]
    underloaded_clusters = [k for k, v in cluster_counts.items() if v < daily_poi_limit]

    logger.debug(f"Initial Cluster Counts: {cluster_counts}")

    while overloaded_clusters:
        # 1. Identify the largest cluster (source)
        source_cluster_id = max(overloaded_clusters, key=cluster_counts.get)
        
        # 2. Identify the smallest cluster (destination)
        if not underloaded_clusters:
            dest_cluster_id = min(cluster_counts, key=cluster_counts.get)
        else:
            dest_cluster_id = min(underloaded_clusters, key=cluster_counts.get)

        if cluster_counts[dest_cluster_id] >= daily_poi_limit:
            if dest_cluster_id in underloaded_clusters:
                underloaded_clusters.remove(dest_cluster_id)
            continue
        
        # 3. Find the POI in the source cluster farthest from its cluster center
        source_df = df[df['cluster'] == source_cluster_id].copy()
        
        center_lat, center_lon = kmeans_centers[source_cluster_id]
        
        source_df['distance_to_center'] = source_df.apply(
            lambda row: haversine_distance(row['lat'], row['lon'], center_lat, center_lon), 
            axis=1
        )
        
        poi_to_move = source_df.loc[source_df['distance_to_center'].idxmax()]
        
        # 4. Move the POI
        original_index = poi_to_move.name
        df.loc[original_index, 'cluster'] = dest_cluster_id
        
        # 5. Update counts and lists
        cluster_counts[source_cluster_id] -= 1
        cluster_counts[dest_cluster_id] += 1
        
        logger.debug(f"-> Moved {poi_to_move['name']} (from Day {source_cluster_id+1} to Day {dest_cluster_id+1})")

        if cluster_counts[source_cluster_id] <= daily_poi_limit:
            overloaded_clusters.remove(source_cluster_id)

    logger.debug(f"Final Balanced Counts: {cluster_counts}")
    return df

def build_distance_matrix_optimized(locations):
    """
    Efficiently build distance matrix for a list of locations using database lookups.
    
    Args:
        locations: List of location dicts with 'id', 'type', 'lat', 'lon'
    
    Returns:
        numpy array distance matrix
    """
    n = len(locations)
    dist_matrix = np.zeros((n, n))
    
    if n <= 1:
        return dist_matrix
    
    # Pre-compute all matrix IDs to avoid repeated conversions
    matrix_ids = [get_location_matrix_id(loc) for loc in locations]
    
    # Get all valid location IDs from database
    valid_location_ids = get_all_location_ids()
    
    # Check if we can use the database for all locations
    all_in_db = all(mid is not None and mid in valid_location_ids for mid in matrix_ids)
    
    if all_in_db:
        # OPTIMIZED: Batch lookup using SQL
        try:
            cursor = get_distance_connection().cursor()
            
            # Build query to fetch all distances we need in one go
            pairs = []
            indices_map = {}
            for i in range(n):
                for j in range(i + 1, n):
                    pairs.append((matrix_ids[i], matrix_ids[j]))
                    indices_map[(matrix_ids[i], matrix_ids[j])] = (i, j)
            
            # Batch query
            if pairs:
                placeholders = ','.join(['(?,?)'] * len(pairs))
                flat_params = [item for pair in pairs for item in pair]
                
                query = f"""
                    SELECT origin_id, dest_id, distance_km 
                    FROM distances 
                    WHERE (origin_id, dest_id) IN ({placeholders})
                """
                
                cursor.execute(query, flat_params)
                results = cursor.fetchall()
                
                # Populate matrix with results
                for origin_id, dest_id, distance in results:
                    i, j = indices_map[(origin_id, dest_id)]
                    dist_matrix[i][j] = float(distance)
                    dist_matrix[j][i] = float(distance)
            
            return dist_matrix
        except Exception as e:
            logger.warning(f"Warning: Batch database lookup failed ({e}), falling back to individual lookups")
    
    # Fallback: Build matrix with individual lookups or haversine
    for i in range(n):
        for j in range(i + 1, n):  # Only compute upper triangle, matrix is symmetric
            dist = None
            
            # Try database lookup
            if matrix_ids[i] and matrix_ids[j]:
                dist = get_distance_from_db(matrix_ids[i], matrix_ids[j])
            
            # Fallback to haversine
            if dist is None:
                loc_i, loc_j = locations[i], locations[j]
                lat_i, lon_i = loc_i.get('lat'), loc_i.get('lon')
                lat_j, lon_j = loc_j.get('lat'), loc_j.get('lon')
                
                if lat_i and lon_i and lat_j and lon_j:
                    dist = haversine_distance(lat_i, lon_i, lat_j, lon_j)
                else:
                    dist = 999.0  # Large penalty for missing coordinates
            
            # Symmetric matrix: set both [i,j] and [j,i]
            dist_matrix[i][j] = dist
            dist_matrix[j][i] = dist
    
    return dist_matrix

def solve_tsp_for_locations(locations, force_order_indices=None, use_exact=True):
    """
    Solve TSP for a list of locations using the distance matrix.
    
    Args:
        locations: List of location dicts with 'id', 'type', 'lat', 'lon'
        force_order_indices: List of (index, slot_type) tuples that must maintain order
                           e.g., [(1, 'morning'), (4, 'afternoon'), (7, 'evening')]
        use_exact: If True, use Branch & Bound for optimal solution (slower but exact)
                   If False, use Nearest Neighbor heuristic (faster but approximate)
    
    Returns:
        Ordered list of locations optimized by TSP with forced ordering constraints
    """
    if len(locations) <= 2:
        return locations
    
    # Build distance matrix efficiently using batch operations
    dist_matrix = build_distance_matrix_optimized(locations)
    
    # If we have forced order constraints (meal slots), handle them
    if force_order_indices and len(force_order_indices) > 1:
        return solve_tsp_with_meal_constraints(locations, dist_matrix, force_order_indices, use_exact)
    
    # Choose algorithm based on problem size and user preference
    if use_exact:
        return solve_tsp_branch_and_bound(locations, dist_matrix)
    else:
        return solve_tsp_nearest_neighbor(locations, dist_matrix)

def solve_tsp_with_meal_constraints(locations, dist_matrix, meal_indices, use_exact=True):
    """
    Solve TSP while respecting meal time constraints.
    
    Args:
        locations: List of location dicts
        dist_matrix: Distance matrix
        meal_indices: List of indices where meals should be placed in order
        use_exact: Use Branch & Bound (True) or Nearest Neighbor (False)
    """
    n = len(locations)
    meal_indices_only = [idx for idx, _ in meal_indices]
    
    # Separate POIs and meals
    poi_indices = [i for i in range(n) if i not in meal_indices_only]
    
    # Create segments between meals
    segments = []
    prev_meal_idx = -1
    
    for meal_idx, meal_type in meal_indices:
        # Get POIs between previous meal and current meal
        segment_pois = [i for i in poi_indices if prev_meal_idx < i < meal_idx]
        segments.append({
            'meal': meal_idx,
            'meal_type': meal_type,
            'pois': segment_pois
        })
        prev_meal_idx = meal_idx
    
    # Get POIs after last meal
    remaining_pois = [i for i in poi_indices if i > prev_meal_idx]
    if remaining_pois:
        segments.append({
            'meal': None,
            'meal_type': None,
            'pois': remaining_pois
        })
    
    # Optimize each segment independently
    result = []
    for segment in segments:
        # Add meal first (if exists)
        if segment['meal'] is not None:
            result.append(locations[segment['meal']])
        
        # Optimize POIs in this segment
        if segment['pois']:
            segment_locations = [locations[i] for i in segment['pois']]
            segment_dist_matrix = dist_matrix[np.ix_(segment['pois'], segment['pois'])]
            
            # Choose algorithm based on preference
            if use_exact:
                optimized_segment = solve_tsp_branch_and_bound(segment_locations, segment_dist_matrix)
            else:
                optimized_segment = solve_tsp_nearest_neighbor(segment_locations, segment_dist_matrix)
            
            result.extend(optimized_segment)
    
    return result

def solve_tsp_nearest_neighbor(locations, dist_matrix):
    """
    Solve TSP using nearest neighbor heuristic.
    Fast but not optimal - typically 25-50% longer than optimal path.
    """
    n = len(locations)
    if n <= 1:
        return locations
    
    # Start from first location
    visited = [False] * n
    path = [0]
    visited[0] = True
    
    current = 0
    for _ in range(n - 1):
        nearest = -1
        min_dist = float('inf')
        
        for j in range(n):
            if not visited[j] and dist_matrix[current][j] < min_dist:
                min_dist = dist_matrix[current][j]
                nearest = j
        
        if nearest != -1:
            path.append(nearest)
            visited[nearest] = True
            current = nearest
    
    # Return locations in optimized order
    return [locations[i] for i in path]

def solve_tsp_branch_and_bound(locations, dist_matrix, max_nodes=10000):
    """
    Solve TSP using Branch and Bound algorithm for optimal solution.
    Guarantees the shortest possible path but slower for large n.
    
    Args:
        locations: List of location dicts
        dist_matrix: Distance matrix between locations
        max_nodes: Maximum nodes to explore (prevents excessive computation)
    
    Returns:
        Ordered list of locations with optimal path
    """
    n = len(locations)
    if n <= 1:
        return locations
    if n == 2:
        return locations
    
    # For very small problems, use brute force
    if n <= 4:
        return _solve_tsp_brute_force(locations, dist_matrix)
    
    # For medium problems (5-12 locations), use full branch and bound
    if n <= 12:
        return _solve_tsp_bnb_full(locations, dist_matrix, max_nodes)
    
    # For larger problems, fall back to nearest neighbor with 2-opt improvement
    nn_solution = solve_tsp_nearest_neighbor(locations, dist_matrix)
    return _improve_with_2opt(nn_solution, dist_matrix)

def _solve_tsp_brute_force(locations, dist_matrix):
    """Brute force for very small TSP (n <= 4)"""
    n = len(locations)
    best_path = None
    best_dist = float('inf')
    
    # Try all permutations starting from location 0
    from itertools import permutations
    for perm in permutations(range(1, n)):
        path = [0] + list(perm)
        dist = sum(dist_matrix[path[i]][path[i+1]] for i in range(n-1))
        
        if dist < best_dist:
            best_dist = dist
            best_path = path
    
    return [locations[i] for i in best_path]

def _solve_tsp_bnb_full(locations, dist_matrix, max_nodes):
    """
    Full Branch and Bound implementation with lower bound pruning.
    """
    n = len(locations)
    
    # Calculate initial upper bound using nearest neighbor
    nn_path_indices = [i for i, _ in enumerate(solve_tsp_nearest_neighbor(locations, dist_matrix))]
    initial_upper_bound = sum(dist_matrix[nn_path_indices[i]][nn_path_indices[i+1]] 
                              for i in range(n-1))
    
    best_path = nn_path_indices
    best_cost = initial_upper_bound
    nodes_explored = [0]  # Use list to make it mutable in nested function
    
    def calculate_lower_bound(path, remaining):
        """
        Calculate lower bound using minimum spanning tree heuristic.
        Lower bound = current path cost + minimum edges from remaining nodes
        """
        if not remaining:
            return sum(dist_matrix[path[i]][path[i+1]] for i in range(len(path)-1))
        
        # Current path cost
        current_cost = sum(dist_matrix[path[i]][path[i+1]] for i in range(len(path)-1))
        
        # Add minimum outgoing edge from last node in path to remaining nodes
        last_node = path[-1]
        min_out = min(dist_matrix[last_node][j] for j in remaining)
        
        # Add minimum edges among remaining nodes (simplified MST approximation)
        if len(remaining) > 1:
            remaining_list = list(remaining)
            min_edges_sum = 0
            for node in remaining_list:
                # Find minimum edge from this node to any other remaining node or back to start
                candidates = [dist_matrix[node][j] for j in remaining_list if j != node]
                candidates.append(dist_matrix[node][0])  # Edge back to start
                if candidates:
                    min_edges_sum += min(candidates)
            current_cost += min_edges_sum / 2  # Divide by 2 as edges are counted twice
        
        return current_cost + min_out
    
    def branch_and_bound(path, remaining, current_cost):
        nonlocal best_path, best_cost, nodes_explored
        
        nodes_explored[0] += 1
        
        # Limit exploration to prevent excessive computation
        if nodes_explored[0] > max_nodes:
            return
        
        # Base case: all nodes visited
        if not remaining:
            # Complete the tour back to start (if needed for closed tour)
            total_cost = current_cost
            if total_cost < best_cost:
                best_cost = total_cost
                best_path = path[:]
            return
        
        # Calculate lower bound
        lower_bound = calculate_lower_bound(path, remaining)
        
        # Prune if lower bound exceeds best known solution
        if lower_bound >= best_cost:
            return
        
        # Branch: try each remaining node
        remaining_list = sorted(remaining, 
                               key=lambda x: dist_matrix[path[-1]][x])  # Sort by distance for better pruning
        
        for next_node in remaining_list:
            new_cost = current_cost + dist_matrix[path[-1]][next_node]
            
            # Prune if current cost already exceeds best
            if new_cost >= best_cost:
                continue
            
            new_remaining = remaining - {next_node}
            branch_and_bound(path + [next_node], new_remaining, new_cost)
    
    # Start branch and bound from node 0
    initial_remaining = set(range(1, n))
    branch_and_bound([0], initial_remaining, 0)
    
    logger.debug(f"Branch & Bound: Explored {nodes_explored[0]} nodes, Best cost: {best_cost:.2f}km")
    
    return [locations[i] for i in best_path]

def _improve_with_2opt(tour, dist_matrix):
    """
    Improve a tour using 2-opt local search.
    Swaps edges to reduce total distance.
    """
    n = len(tour)
    if n <= 3:
        return tour
    
    # Create index mapping
    indices = list(range(n))
    improved = True
    max_iterations = 100
    iteration = 0
    
    def tour_distance(order):
        return sum(dist_matrix[order[i]][order[(i+1) % n]] for i in range(n))
    
    current_order = indices[:]
    current_dist = tour_distance(current_order)
    
    while improved and iteration < max_iterations:
        improved = False
        iteration += 1
        
        for i in range(1, n - 1):
            for j in range(i + 1, n):
                # Try reversing the segment between i and j
                new_order = current_order[:i] + current_order[i:j+1][::-1] + current_order[j+1:]
                new_dist = tour_distance(new_order)
                
                if new_dist < current_dist:
                    current_order = new_order
                    current_dist = new_dist
                    improved = True
                    break
            if improved:
                break
    
    return [tour[i] for i in current_order]

# --- 2. THE ALGORITHM CORE ---

def generate_itinerary(
    num_days,
    daily_poi_limit,
    selected_pois_qs,
    selected_eateries_qs,
    extra_pois=None,
    extra_eateries=None,
    user_slot_overrides=None,
    accommodation_address=None,
    use_default_center=False,
):
    """
    Generates an optimized itinerary based on activity density and user preferences.
    Accepts Django QuerySets as input.
    
    Args:
        accommodation_address: User's accommodation address to geocode and use as start/end point
        use_default_center: If True, use Dalat Market (P001) as accommodation without geocoding
    """
    logger.debug("--- Starting Itinerary Generation ---")

    # --- Step 1: Convert QuerySets to DataFrames & Check Capacity ---
    
    # Convert POI QuerySet to DataFrame
    # Note: Your model uses 'latitude'/'longitude', but the algorithm uses 'lat'/'lon'.
    # We rename them here.
    extra_pois = extra_pois or []
    extra_eateries = extra_eateries or []
    user_slot_overrides = user_slot_overrides or {}
    
    # Geocode accommodation if provided
    accommodation_coords = None
    accommodation_name = None
    accommodation_name_en = None
    
    # Check if this is the default Dalat Center/Market (P001)
    from .constants import DEFAULT_FALLBACK_COORDS, DEFAULT_FALLBACK_LABEL
    
    should_use_default = (
        use_default_center or 
        (accommodation_address and (
            'dalat center' in accommodation_address.lower() or
            'da lat market' in accommodation_address.lower() or
            DEFAULT_FALLBACK_LABEL.lower() in accommodation_address.lower()
        ))
    )
    
    if should_use_default:
        # Use Dalat Market (P001) directly without geocoding
        from .models import Poi
        try:
            dalat_market = Poi.objects.filter(image_code='P001').first()
            if dalat_market:
                accommodation_coords = (dalat_market.latitude, dalat_market.longitude)
                accommodation_name = dalat_market.name
                accommodation_name_en = dalat_market.name_en or dalat_market.name
                accommodation_id = 'P001'  # Use POI ID for Dalat Market
                logger.debug(f"[ACCOMMODATION] Using Dalat Market (P001) as default: {dalat_market.name}, lat={accommodation_coords[0]:.6f}, lon={accommodation_coords[1]:.6f}")
            else:
                # Fallback to default coordinates if POI not found
                accommodation_coords = DEFAULT_FALLBACK_COORDS
                accommodation_name = "Dalat Center"
                accommodation_id = None
                logger.warning(f"[ACCOMMODATION] Dalat Market (P001) not found in DB, using fallback coordinates")
        except Exception as e:
            logger.error(f"[ACCOMMODATION] Error fetching Dalat Market: {e}, using fallback")
            accommodation_coords = DEFAULT_FALLBACK_COORDS
            accommodation_name = "Dalat Center"
            accommodation_id = None
    elif accommodation_address and accommodation_address.strip():
        # Custom address provided - geocode it
        from .geocode import geocode_address
        logger.debug(f"--- Geocoding Accommodation: '{accommodation_address}' ---")
        result = geocode_address(accommodation_address, return_id=True)
        accommodation_name = accommodation_address
        accommodation_id = None
        if result:
            accommodation_coords = (result[0], result[1])
            accommodation_id = result[2]  # May be None if not from accommodations DB
            logger.debug(f"[ACCOMMODATION] Geocoded to lat={accommodation_coords[0]:.6f}, lon={accommodation_coords[1]:.6f}")
            if accommodation_id:
                logger.debug(f"[ACCOMMODATION] Found in accommodations database with ID: {accommodation_id}")
        else:
            logger.warning(f"[ACCOMMODATION] Warning: Could not geocode accommodation address. Accommodation will not be added to itinerary.")
            accommodation_coords = None
    else:
        logger.debug(f"[ACCOMMODATION] No accommodation address provided - skipping accommodation stops")
        accommodation_id = None

    # A custom address has no translation, so reuse the Vietnamese value there.
    accommodation_name_en = accommodation_name_en or accommodation_name

    poi_list = list(selected_pois_qs.values(
        'id', 'name', 'name_en', 'address', 'address_en', 'tags', 'rating', 'latitude', 'longitude'
    ))
    poi_list.extend(extra_pois)

    # ENFORCEMENT: Check for minimum POI selection
    if not poi_list:
        logger.error("[ALERT] Minimum Selection Error: You must select at least one POI to build a valid itinerary.")
        # Return an error message or code for the view to handle
        return None, None, "You must select at least one POI to build a valid itinerary."

    selected_pois_df = pd.DataFrame(poi_list)
    selected_pois_df.rename(columns={'latitude': 'lat', 'longitude': 'lon'}, inplace=True)
    if selected_pois_df[['lat', 'lon']].isnull().any().any():
        return None, None, "All POIs must include latitude and longitude."

    total_pois_selected = len(selected_pois_df)
    total_capacity = num_days * daily_poi_limit

    logger.debug(f"Total POIs Selected: {total_pois_selected}")
    logger.debug(f"Total Capacity ({num_days} days * {daily_poi_limit} POIs/day): {total_capacity}")

    if total_pois_selected > total_capacity:
        logger.debug("[CAPACITY ALERT] The itinerary is overloaded!")
        error_msg = f"Please remove {total_pois_selected - total_capacity} POIs or increase the duration/daily limit."
        return None, None, error_msg # Halt process and return error

    # Convert Eatery QuerySet to DataFrame
    eatery_list = list(selected_eateries_qs.values(
        'id', 'name', 'name_en', 'address', 'address_en', 'time_tags', 'time_tags_en', 'latitude', 'longitude'
    ))
    eatery_list.extend(extra_eateries)
    
    if eatery_list:
        preferred_eateries_df = pd.DataFrame(eatery_list)
        preferred_eateries_df.rename(columns={'latitude': 'lat', 'longitude': 'lon'}, inplace=True)
        
        # Convert the 'time_tags' string (e.g., "morning,evening") 
        # into the list format the algorithm expects (e.g., ['morning', 'evening'])
        def _normalize_meal_tokens(raw_value):
            if not raw_value:
                return []
            tokens = []
            for token in str(raw_value).replace('/', ',').split(','):
                t = token.strip()
                if not t:
                    continue
                lowered = t.lower()
                if lowered in {'sáng', 'sang', 'morning'}:
                    tokens.append('Sáng')
                elif lowered in {'trưa', 'trua', 'afternoon', 'lunch'}:
                    tokens.append('Trưa')
                elif lowered in {'tối', 'toi', 'evening', 'dinner', 'night'}:
                    tokens.append('Tối')
                else:
                    tokens.append(t)
            # remove duplicates while maintaining order
            seen = set()
            ordered = []
            for token in tokens:
                if token not in seen:
                    ordered.append(token)
                    seen.add(token)
            return ordered

        preferred_eateries_df['mealTime'] = preferred_eateries_df['time_tags'].apply(_normalize_meal_tokens)

        def _forced_slot_lookup(raw_id):
            try:
                numeric_id = int(raw_id)
            except (TypeError, ValueError):
                return None
            return user_slot_overrides.get(numeric_id)

        preferred_eateries_df['forcedSlot'] = preferred_eateries_df['id'].apply(_forced_slot_lookup)

    else:
        # Handle case where no eateries are selected
        preferred_eateries_df = pd.DataFrame(columns=['id', 'name', 'address', 'time_tags', 'lat', 'lon', 'mealTime', 'forcedSlot'])
        logger.debug("No eateries were passed from the view.")
        logger.debug("---------------------------------------\n")
        # --- END OF PRINT STATEMENT ---

    # --- Step 2: Geographic Clustering and Feasibility ---
    logger.debug("--- Step 2: K-Means Clustering for Geographic Grouping ---")
    
    X = selected_pois_df[['lat', 'lon']].values
    n_clusters = num_days
    
    if n_clusters < 1 or n_clusters > len(X):
        n_clusters = len(X)
        logger.debug(f"[INFO] Reduced number of clusters to match number of selected POIs: {n_clusters}")

    try:
        kmeans = KMeans(n_clusters=n_clusters, random_state=42, n_init=10, init='k-means++')
        selected_pois_df['cluster'] = kmeans.fit_predict(X)
        kmeans_centers = kmeans.cluster_centers_
    except ValueError as e:
        logger.error(f"[ERROR] Clustering failed: {e}")
        return None, None, f"Clustering failed: {e}"

    # --- Step 2.5: Capacity Enforcement (NEW STEP) ---
    logger.debug("--- Step 2.5: Enforcing Daily Capacity Limit ---")
    selected_pois_df = redistribute_pois(selected_pois_df, daily_poi_limit, num_days, kmeans_centers)

    daily_itineraries = {i: [] for i in range(num_days)}
    
    for i in range(num_days):
        cluster_data = selected_pois_df[selected_pois_df['cluster'] == i]
        daily_itineraries[i] = cluster_data.to_dict('records')

    # --- Step 3: Sequential Time Slotting & Eatery Placement ---
    logger.debug("--- Step 3: Sequential Ordering & Eatery Placement ---")
    
    final_itinerary_vi = {}
    final_itinerary_en = {}
    unassigned_preferred_eateries = list(preferred_eateries_df.id.values)

    def _pick_best_eatery(slot_label, base_lat, base_lon, forced_only=False, allow_any=False):
        if preferred_eateries_df.empty or not unassigned_preferred_eateries:
            return None

        mask = preferred_eateries_df['id'].isin(unassigned_preferred_eateries)
        if forced_only:
            mask &= preferred_eateries_df['forcedSlot'] == slot_label
        else:
            if allow_any:
                mask &= preferred_eateries_df['forcedSlot'].isna()
            else:
                if slot_label:
                    mask &= preferred_eateries_df['mealTime'].apply(lambda slots: slot_label in slots)
                else:
                    mask &= preferred_eateries_df['forcedSlot'].isna()
                mask &= preferred_eateries_df['forcedSlot'].isna() | (preferred_eateries_df['forcedSlot'] == slot_label)

        candidates = preferred_eateries_df[mask]
        if candidates.empty:
            return None

        best_row = None
        min_dist = float('inf')
        for _, eatery in candidates.iterrows():
            eatery_lat = eatery.get('lat')
            eatery_lon = eatery.get('lon')
            if pd.isna(eatery_lat) or pd.isna(eatery_lon):
                continue
            dist = haversine_distance(base_lat, base_lon, eatery_lat, eatery_lon)
            if dist < min_dist:
                min_dist = dist
                best_row = eatery

        if best_row is None:
            return None

        return best_row.to_dict(), min_dist

    def _select_eatery_for_slot(slot_label, base_lat, base_lon, allow_any=False):
        forced_pick = _pick_best_eatery(slot_label, base_lat, base_lon, forced_only=True)
        if forced_pick:
            return forced_pick
        targeted_pick = _pick_best_eatery(slot_label, base_lat, base_lon, forced_only=False)
        if targeted_pick:
            return targeted_pick
        if allow_any:
            return _pick_best_eatery(None, base_lat, base_lon, forced_only=False, allow_any=True)
        return None
    
    for day_index, pois in daily_itineraries.items():
        day_num = day_index + 1
        
        if not pois:
            final_itinerary_vi[day_num] = []
            final_itinerary_en[day_num] = []
            continue

        # 3.1 Convert POIs to standard format for TSP
        poi_locations = []
        poi_locations_vi = []
        poi_locations_en = []
        for poi in pois:
            # Base location for TSP (language-agnostic)
            poi_locations.append({
                'id': poi['id'],
                'name': poi['name'],
                'address': poi.get('address'),
                'lat': poi['lat'],
                'lon': poi['lon'],
                'type': 'POI'
            })
            # Vietnamese version
            poi_locations_vi.append({
                'id': poi['id'],
                'name': poi['name'],
                'name_en': poi.get('name_en'),
                'address': poi.get('address'),
                'address_en': poi.get('address_en'),
                'lat': poi['lat'],
                'lon': poi['lon'],
                'type': 'POI'
            })
            # English version
            poi_locations_en.append({
                'id': poi['id'],
                'name': poi.get('name_en') or poi['name'],
                'name_vi': poi['name'],
                'address': poi.get('address_en') or poi.get('address'),
                'address_vi': poi.get('address'),
                'lat': poi['lat'],
                'lon': poi['lon'],
                'type': 'POI'
            })

        # 3.2 Select and place eateries for this day
        def _select_best_eatery(slot_key, reference_locations):
            if preferred_eateries_df.empty or not unassigned_preferred_eateries:
                return None
            
            slot_label_vi = MEAL_SLOT_LABELS.get(slot_key)
            if not slot_label_vi:
                return None
            
            # Calculate average reference coordinates
            avg_lat = sum(loc['lat'] for loc in reference_locations) / len(reference_locations)
            avg_lon = sum(loc['lon'] for loc in reference_locations) / len(reference_locations)
            
            # Try forced slot first
            selection = _select_eatery_for_slot(slot_label_vi, avg_lat, avg_lon)
            if not selection:
                selection = _select_eatery_for_slot(slot_label_vi, avg_lat, avg_lon, allow_any=True)
            
            return selection
        
        # Select eateries for all three meal slots
        selected_meals = {}
        selected_meals_vi = {}
        selected_meals_en = {}
        
        for slot_key in ['morning', 'afternoon', 'evening']:
            meal_result = _select_best_eatery(slot_key, poi_locations)
            if meal_result:
                meal_eatery, _ = meal_result
                # Base meal (for TSP)
                selected_meals[slot_key] = {
                    'id': meal_eatery['id'],
                    'name': meal_eatery['name'],
                    'address': meal_eatery.get('address'),
                    'lat': meal_eatery.get('lat'),
                    'lon': meal_eatery.get('lon'),
                    'type': 'EATERY',
                    'slot': slot_key
                }
                # Vietnamese version
                selected_meals_vi[slot_key] = {
                    'id': meal_eatery['id'],
                    'name': meal_eatery['name'],
                    'name_en': meal_eatery.get('name_en'),
                    'address': meal_eatery.get('address'),
                    'address_en': meal_eatery.get('address_en'),
                    'lat': meal_eatery.get('lat'),
                    'lon': meal_eatery.get('lon'),
                    'type': 'EATERY',
                    'slot': slot_key
                }
                # English version
                selected_meals_en[slot_key] = {
                    'id': meal_eatery['id'],
                    'name': meal_eatery.get('name_en') or meal_eatery['name'],
                    'name_vi': meal_eatery['name'],
                    'address': meal_eatery.get('address_en') or meal_eatery.get('address'),
                    'address_vi': meal_eatery.get('address'),
                    'lat': meal_eatery.get('lat'),
                    'lon': meal_eatery.get('lon'),
                    'type': 'EATERY',
                    'slot': slot_key
                }
                unassigned_preferred_eateries.remove(meal_eatery['id'])
        
        # 3.3 Enforce meal order while still using TSP for POIs.
        logger.debug(f"--- Optimizing Day {day_num} with TSP ---")
        optimized_pois = solve_tsp_for_locations(poi_locations, force_order_indices=None) if poi_locations else []
        num_optimized_pois = len(optimized_pois)

        if num_optimized_pois and 'afternoon' in selected_meals:
            first_block_count = max(1, ceil(num_optimized_pois / 2))
        else:
            first_block_count = num_optimized_pois

        first_poi_block = optimized_pois[:first_block_count]
        second_poi_block = optimized_pois[first_block_count:]

        # Build ordered schedule (for distance calculation)
        ordered_day_locations = []

        if 'morning' in selected_meals:
            ordered_day_locations.append(selected_meals['morning'])

        ordered_day_locations.extend(first_poi_block)

        if 'afternoon' in selected_meals:
            ordered_day_locations.append(selected_meals['afternoon'])

        ordered_day_locations.extend(second_poi_block)

        if 'evening' in selected_meals:
            ordered_day_locations.append(selected_meals['evening'])

        optimized_schedule = ordered_day_locations
        
        # Build language-specific ordered schedules using the same TSP order
        # Vietnamese version
        ordered_day_locations_vi = []
        if 'morning' in selected_meals_vi:
            ordered_day_locations_vi.append(selected_meals_vi['morning'])
        
        # Map TSP-optimized POIs to Vietnamese POI data
        for poi in first_poi_block:
            poi_vi = next((p for p in poi_locations_vi if p['id'] == poi['id']), None)
            if poi_vi:
                ordered_day_locations_vi.append(poi_vi)
        
        if 'afternoon' in selected_meals_vi:
            ordered_day_locations_vi.append(selected_meals_vi['afternoon'])
        
        for poi in second_poi_block:
            poi_vi = next((p for p in poi_locations_vi if p['id'] == poi['id']), None)
            if poi_vi:
                ordered_day_locations_vi.append(poi_vi)
        
        if 'evening' in selected_meals_vi:
            ordered_day_locations_vi.append(selected_meals_vi['evening'])
        
        # English version
        ordered_day_locations_en = []
        if 'morning' in selected_meals_en:
            ordered_day_locations_en.append(selected_meals_en['morning'])
        
        for poi in first_poi_block:
            poi_en = next((p for p in poi_locations_en if p['id'] == poi['id']), None)
            if poi_en:
                ordered_day_locations_en.append(poi_en)
        
        if 'afternoon' in selected_meals_en:
            ordered_day_locations_en.append(selected_meals_en['afternoon'])
        
        for poi in second_poi_block:
            poi_en = next((p for p in poi_locations_en if p['id'] == poi['id']), None)
            if poi_en:
                ordered_day_locations_en.append(poi_en)
        
        if 'evening' in selected_meals_en:
            ordered_day_locations_en.append(selected_meals_en['evening'])

        optimized_schedule = ordered_day_locations
        
        # 3.5 Calculate travel distances for the optimized route
        # OPTIMIZED: Build distance matrix once for all route calculations
        route_dist_matrix = build_distance_matrix_optimized(optimized_schedule)
        
        # Build Vietnamese schedule
        day_schedule_vi = []
        
        # Add accommodation at the start if coordinates are available
        if accommodation_coords:
            first_destination_dist = 0.0
            if ordered_day_locations_vi:
                first_dest = ordered_day_locations_vi[0]
                first_destination_dist = haversine_distance(
                    accommodation_coords[0], accommodation_coords[1],
                    first_dest['lat'], first_dest['lon']
                )
            
            accommodation_start_vi = {
                'type': 'ACCOMMODATION',
                'id': 0,
                'name': accommodation_name,
                'address': accommodation_address if accommodation_address else accommodation_name,
                'travel_to_next_km': first_destination_dist,
                'lat': accommodation_coords[0],
                'lon': accommodation_coords[1],
                'matrix_id': accommodation_id,  # Add the accommodation ID for route lookups
                'image_code': accommodation_id  # Frontend uses image_code for data-code attribute
            }
            day_schedule_vi.append(accommodation_start_vi)
        
        for idx, location in enumerate(ordered_day_locations_vi):
            travel_km = 0.0
            
            if idx < len(ordered_day_locations_vi) - 1:
                # Use pre-computed distance matrix to get distance to next destination
                travel_km = route_dist_matrix[idx][idx + 1]
            else:
                # Last destination: calculate distance back to accommodation
                if accommodation_coords:
                    travel_km = haversine_distance(
                        location['lat'], location['lon'],
                        accommodation_coords[0], accommodation_coords[1]
                    )
            
            stop_entry_vi = {
                'type': location['type'],
                'id': location['id'],
                'name': location['name'],
                'address': location.get('address'),
                'travel_to_next_km': travel_km,
                'lat': location['lat'],
                'lon': location['lon']
            }
            
            if location['type'] == 'EATERY':
                stop_entry_vi['slot'] = location['slot']
            
            day_schedule_vi.append(stop_entry_vi)
        
        # Add accommodation at the end
        if accommodation_coords:
            accommodation_end_vi = {
                'type': 'ACCOMMODATION',
                'id': 0,
                'name': accommodation_name,
                'address': accommodation_address if accommodation_address else accommodation_name,
                'matrix_id': accommodation_id,
                'image_code': accommodation_id,
                'travel_to_next_km': 0.0,
                'lat': accommodation_coords[0],
                'lon': accommodation_coords[1],
                'matrix_id': accommodation_id  # Add the accommodation ID for route lookups
            }
            day_schedule_vi.append(accommodation_end_vi)
        
        # Build English schedule
        day_schedule_en = []
        
        if accommodation_coords:
            first_destination_dist = 0.0
            if ordered_day_locations_en:
                first_dest = ordered_day_locations_en[0]
                first_destination_dist = haversine_distance(
                    accommodation_coords[0], accommodation_coords[1],
                    first_dest['lat'], first_dest['lon']
                )
            
            accommodation_start_en = {
                'type': 'ACCOMMODATION',
                'id': 0,
                'name': accommodation_name_en,
                'address': accommodation_address if accommodation_address else accommodation_name,
                'travel_to_next_km': first_destination_dist,
                'lat': accommodation_coords[0],
                'lon': accommodation_coords[1]
            }
            day_schedule_en.append(accommodation_start_en)
        
        for idx, location in enumerate(ordered_day_locations_en):
            travel_km = 0.0
            
            if idx < len(ordered_day_locations_en) - 1:
                travel_km = route_dist_matrix[idx][idx + 1]
            else:
                if accommodation_coords:
                    travel_km = haversine_distance(
                        location['lat'], location['lon'],
                        accommodation_coords[0], accommodation_coords[1]
                    )
            
            stop_entry_en = {
                'type': location['type'],
                'id': location['id'],
                'name': location['name'],
                'address': location.get('address'),
                'travel_to_next_km': travel_km,
                'lat': location['lat'],
                'lon': location['lon']
            }
            
            if location['type'] == 'EATERY':
                stop_entry_en['slot'] = location['slot']
            
            day_schedule_en.append(stop_entry_en)
        
        if accommodation_coords:
            accommodation_end_en = {
                'type': 'ACCOMMODATION',
                'id': 0,
                'name': accommodation_name_en,
                'address': accommodation_address if accommodation_address else accommodation_name,
                'travel_to_next_km': 0.0,
                'lat': accommodation_coords[0],
                'lon': accommodation_coords[1]
            }
            day_schedule_en.append(accommodation_end_en)
        
        final_itinerary_vi[day_num] = day_schedule_vi
        final_itinerary_en[day_num] = day_schedule_en
        logger.debug(f"Day {day_num} optimized: {len(day_schedule_vi)} stops (including accommodation)")
        
        # Print route summary for verification
        if accommodation_coords:
            route_summary = " -> ".join([
                f"{s['name'][:20]}({'ACCOM' if s['type']=='ACCOMMODATION' else s['type']})"
                for s in day_schedule_vi
            ])
            logger.debug(f"[TSP Route Day {day_num}] {route_summary}")

    # Return both itineraries and no error message
    return final_itinerary_vi, final_itinerary_en, None