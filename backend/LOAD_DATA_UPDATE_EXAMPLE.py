# CODE UPDATE EXAMPLE: load_data.py with English support

# Add this code at the END of the handle() method, right before the SUCCESS message:

            # ==========================================
            # LOAD ENGLISH TRANSLATIONS
            # ==========================================
            self.stdout.write("\n" + "="*60)
            self.stdout.write("Loading English translations...")
            self.stdout.write("="*60)
            
            # Load POIs English data
            pois_en_path = 'dalat_pois_en.csv'
            try:
                with open(pois_en_path, 'r', encoding='utf-8') as file:
                    reader = csv.DictReader(file)
                    updated_count = 0
                    for row in reader:
                        try:
                            # Find POI by Vietnamese name or ID
                            poi_id = row.get('ID')
                            if poi_id:
                                poi = Poi.objects.filter(image_code=poi_id).first()
                                if poi:
                                    poi.name_en = row.get('Name_EN', '')
                                    poi.address_en = row.get('Address_EN', '')
                                    poi.highlight_en = row.get('Highlight_EN', '')
                                    poi.tags_en = row.get('Tags_EN', '')
                                    poi.save()
                                    updated_count += 1
                                    self.stdout.write(f"  ✓ {poi.name} → {poi.name_en}")
                        except Exception as e:
                            self.stdout.write(f"  ✗ Error updating POI {row.get('ID')}: {e}")
                    
                    self.stdout.write(self.style.SUCCESS(f"\n✓ Updated {updated_count} POIs with English data"))
            
            except FileNotFoundError:
                self.stdout.write(self.style.WARNING(
                    f"\n⚠ Warning: {pois_en_path} not found. Skipping English POI data."
                ))
            
            # Load Eateries English data
            eateries_en_path = 'dalat_eateries_en.csv'
            try:
                with open(eateries_en_path, 'r', encoding='utf-8') as file:
                    reader = csv.DictReader(file)
                    updated_count = 0
                    for row in reader:
                        try:
                            eatery_id = row.get('ID')
                            if eatery_id:
                                eatery = Eatery.objects.filter(image_code=eatery_id).first()
                                if eatery:
                                    eatery.name_en = row.get('Name_EN', '')
                                    eatery.address_en = row.get('Address_EN', '')
                                    eatery.time_tags_en = row.get('Time_Tags_EN', '')
                                    eatery.save()
                                    updated_count += 1
                                    self.stdout.write(f"  ✓ {eatery.name} → {eatery.name_en}")
                        except Exception as e:
                            self.stdout.write(f"  ✗ Error updating Eatery {row.get('ID')}: {e}")
                    
                    self.stdout.write(self.style.SUCCESS(f"\n✓ Updated {updated_count} eateries with English data"))
            
            except FileNotFoundError:
                self.stdout.write(self.style.WARNING(
                    f"\n⚠ Warning: {eateries_en_path} not found. Skipping English eatery data."
                ))
            
            self.stdout.write("\n" + "="*60)
            # ==========================================
            # END ENGLISH TRANSLATIONS
            # ==========================================
        
            self.stdout.write(self.style.SUCCESS('Successfully loaded all data!'))


# ==================================================
# FULL UPDATED handle() METHOD
# ==================================================

def handle(self, *args, **options):
    # Set flag to skip signals during bulk import
    home.signals._skip_signals = True
    
    try:
        # Clear existing data (optional, but helpful for re-running)
        Poi.objects.all().delete()
        Eatery.objects.all().delete()
        
        self.stdout.write("Loading POI data...")
        
        # Load Vietnamese POI data
        with open('dalat_pois.csv', mode='r', encoding='utf-8') as file:
            reader = csv.DictReader(file)
            for row in reader:
                tag_from_col_1 = row.get('Class1')
                tag_from_col_2 = row.get('Class2')

                all_tags_list = []
                
                if tag_from_col_1 and tag_from_col_1 != 'None':
                    all_tags_list.append(tag_from_col_1.strip())
                if tag_from_col_2 and tag_from_col_2 != 'None':
                    all_tags_list.append(tag_from_col_2.strip())
                
                final_tags_string = ", ".join(all_tags_list)

                price_value = None
                for column in POI_PRICE_COLUMNS:
                    column_value = row.get(column)
                    if column_value:
                        price_value = self.parse_poi_price(column_value)
                        if price_value is not None:
                            break

                Poi.objects.create(
                    name=row['Tên địa điểm'],
                    address=row['Địa chỉ'],
                    open_hours=row.get('Giờ mở cửa'),
                    tiktok_link=row.get('Link TikTok') if row.get('Link TikTok') else None,
                    rating=float(row['Đánh giá']) if row.get('Đánh giá') else None,
                    price_per_person=price_value,
                    highlight=row.get('Highlights') or '',
                    latitude=float((row['Lat']).replace(',', '.')),
                    longitude=float((row['Lon']).replace(',', '.')),
                    tags=final_tags_string,
                    image_code=(row.get('ID') or '').strip() or None,
                )
        
        self.stdout.write("Loading Eatery data...")
        
        # Load Vietnamese Eatery data
        with open('dalat_eateries.csv', mode='r', encoding='utf-8') as file:
            reader = csv.DictReader(file)
            for row in reader:
                price_str = row.get('Giá tiền')
                
                price_min_val = None
                price_max_val = None
                
                if price_str:
                    cleaned_str = price_str.replace('.', '')
                    parts = cleaned_str.split('-')
                    try:
                        if len(parts) == 2:
                            price_min_val = int(parts[0].strip())
                            price_max_val = int(parts[1].strip())
                        elif len(parts) == 1:
                            price_min_val = int(parts[0].strip())
                            price_max_val = price_min_val
                    except ValueError:
                        self.stdout.write(f"Warning: Could not parse price '{price_str}'")
                        pass

                Eatery.objects.create(
                    name=row['Tên quán'],
                    address=row['Địa chỉ'],
                    open_hours=row.get('Giờ mở cửa'),
                    time_tags=parse_time_tags(row.get('Giờ mở cửa')),
                    latitude=float((row['Lat']).replace(',', '.')),
                    longitude=float((row['Lon']).replace(',', '.')),
                    rating=float((row['Đánh giá']).replace(',', '.')) if row.get('Đánh giá') else None,
                    tiktok_link=row.get('Media') if row.get('Media') else None,
                    price_min=price_min_val,
                    price_max=price_max_val,
                    image_code=(row.get('ID') or '').strip() or None,
                )
    
        # ==========================================
        # LOAD ENGLISH TRANSLATIONS
        # ==========================================
        self.stdout.write("\n" + "="*60)
        self.stdout.write("Loading English translations...")
        self.stdout.write("="*60)
        
        # Load POIs English data
        pois_en_path = 'dalat_pois_en.csv'
        try:
            with open(pois_en_path, 'r', encoding='utf-8') as file:
                reader = csv.DictReader(file)
                updated_count = 0
                for row in reader:
                    try:
                        poi_id = row.get('ID')
                        if poi_id:
                            poi = Poi.objects.filter(image_code=poi_id).first()
                            if poi:
                                poi.name_en = row.get('Name_EN', '')
                                poi.address_en = row.get('Address_EN', '')
                                poi.highlight_en = row.get('Highlight_EN', '')
                                poi.tags_en = row.get('Tags_EN', '')
                                poi.save()
                                updated_count += 1
                                self.stdout.write(f"  ✓ {poi.name} → {poi.name_en}")
                    except Exception as e:
                        self.stdout.write(f"  ✗ Error updating POI {row.get('ID')}: {e}")
                
                self.stdout.write(self.style.SUCCESS(f"\n✓ Updated {updated_count} POIs with English data"))
        
        except FileNotFoundError:
            self.stdout.write(self.style.WARNING(
                f"\n⚠ Warning: {pois_en_path} not found. Skipping English POI data."
            ))
        
        # Load Eateries English data
        eateries_en_path = 'dalat_eateries_en.csv'
        try:
            with open(eateries_en_path, 'r', encoding='utf-8') as file:
                reader = csv.DictReader(file)
                updated_count = 0
                for row in reader:
                    try:
                        eatery_id = row.get('ID')
                        if eatery_id:
                            eatery = Eatery.objects.filter(image_code=eatery_id).first()
                            if eatery:
                                eatery.name_en = row.get('Name_EN', '')
                                eatery.address_en = row.get('Address_EN', '')
                                eatery.time_tags_en = row.get('Time_Tags_EN', '')
                                eatery.save()
                                updated_count += 1
                                self.stdout.write(f"  ✓ {eatery.name} → {eatery.name_en}")
                    except Exception as e:
                        self.stdout.write(f"  ✗ Error updating Eatery {row.get('ID')}: {e}")
                
                self.stdout.write(self.style.SUCCESS(f"\n✓ Updated {updated_count} eateries with English data"))
        
        except FileNotFoundError:
            self.stdout.write(self.style.WARNING(
                f"\n⚠ Warning: {eateries_en_path} not found. Skipping English eatery data."
            ))
        
        self.stdout.write("\n" + "="*60)
        # ==========================================
        # END ENGLISH TRANSLATIONS
        # ==========================================
    
        self.stdout.write(self.style.SUCCESS('Successfully loaded all data!'))
    
    finally:
        # Re-enable signals and trigger one reload
        home.signals._skip_signals = False
        
        # Manually trigger AI reload once after all data is loaded
        self.stdout.write("Triggering AI data reload...")
        home.signals.trigger_ai_data_reload()
