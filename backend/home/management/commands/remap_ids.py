"""
Management command to remap Eatery and POI IDs to match the distance matrix CSV.
"""
from django.core.management.base import BaseCommand
from django.db import connection, transaction
from django.conf import settings
import os


class Command(BaseCommand):
    help = 'Remap Eatery and POI IDs to match the distance matrix CSV (1-163 for Eateries, 1-79 for POIs)'

    def handle(self, *args, **options):
        self.stdout.write(self.style.WARNING('Starting ID remapping...'))
        
        # Get database path
        db_path = settings.DATABASES['default']['NAME']
        self.stdout.write(f'Database: {db_path}')
        
        # Use raw SQL via sqlite3 to avoid Django's debug formatting issues
        import sqlite3
        
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        try:
            # Step 1: Get current data
            cursor.execute('SELECT * FROM home_eatery ORDER BY id')
            eateries = cursor.fetchall()
            eatery_columns = [desc[0] for desc in cursor.description]
            
            cursor.execute('SELECT * FROM home_poi ORDER BY id')
            pois = cursor.fetchall()
            poi_columns = [desc[0] for desc in cursor.description]
            
            self.stdout.write(f'Found {len(eateries)} eateries')
            self.stdout.write(f'Found {len(pois)} POIs')
            
            # Step 2: Clear tables
            cursor.execute('DELETE FROM home_eatery')
            cursor.execute('DELETE FROM home_poi')
            
            # Step 3: Re-insert with new IDs
            # Get column indices
            eatery_id_idx = eatery_columns.index('id')
            poi_id_idx = poi_columns.index('id')
            
            for new_id, eatery in enumerate(eateries, start=1):
                # Convert tuple to list, update ID, convert back
                eatery_list = list(eatery)
                eatery_list[eatery_id_idx] = new_id
                placeholders = ','.join(['?'] * len(eatery_list))
                cursor.execute(
                    f'INSERT INTO home_eatery VALUES ({placeholders})',
                    eatery_list
                )
            
            for new_id, poi in enumerate(pois, start=1):
                # Convert tuple to list, update ID, convert back
                poi_list = list(poi)
                poi_list[poi_id_idx] = new_id
                placeholders = ','.join(['?'] * len(poi_list))
                cursor.execute(
                    f'INSERT INTO home_poi VALUES ({placeholders})',
                    poi_list
                )
            
            # Step 4: Reset sequences
            cursor.execute("UPDATE sqlite_sequence SET seq = 163 WHERE name = 'home_eatery'")
            cursor.execute("UPDATE sqlite_sequence SET seq = 79 WHERE name = 'home_poi'")
            
            # Commit changes
            conn.commit()
            
            # Verify
            cursor.execute('SELECT MIN(id), MAX(id) FROM home_eatery')
            eatery_range = cursor.fetchone()
            cursor.execute('SELECT MIN(id), MAX(id) FROM home_poi')
            poi_range = cursor.fetchone()
            
            self.stdout.write(self.style.SUCCESS(f'\n✓ Eateries remapped: {eatery_range[0]}-{eatery_range[1]}'))
            self.stdout.write(self.style.SUCCESS(f'✓ POIs remapped: {poi_range[0]}-{poi_range[1]}'))
            self.stdout.write(self.style.SUCCESS('\nID remapping completed successfully!'))
            
        except Exception as e:
            conn.rollback()
            self.stdout.write(self.style.ERROR(f'Error: {e}'))
            raise
        finally:
            conn.close()
