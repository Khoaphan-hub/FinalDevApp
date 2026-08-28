from django.apps import AppConfig
import sys
import threading


class HomeConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'home'

    def ready(self):
        is_runserver = 'runserver' in sys.argv
        is_import_data = 'import_data' in sys.argv
        
        from . import signals  # noqa: F401
        from . import shared_itineraries  # noqa: F401
        
        if is_runserver and not is_import_data:
            from . import ai_service
            if not ai_service.is_loaded:
                print("--- (Runserver) Khởi chạy luồng tải mô hình AI và dữ liệu ---")
                t = threading.Thread(target=ai_service.load_data_and_vectorize)
                t.daemon = True
                t.start()
