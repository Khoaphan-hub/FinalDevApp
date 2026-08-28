from django.db.models.signals import post_save, post_delete
from django.dispatch import receiver
from .models import Poi, Eatery
from . import ai_service
import threading
import logging
from django.core.cache import cache
from django.db import transaction 
import sys

# Flag to skip signal during bulk import
_skip_signals = False

def trigger_ai_data_reload(sender=None, instance=None, **kwargs):
    """
    Kích hoạt việc tải lại dữ liệu AI trong một luồng riêng
    để không làm treo trang Admin.
    """
    global _skip_signals
    if _skip_signals:
        return
        
    logging.info("--- (Signal) Phát hiện thay đổi CSDL. Bắt đầu tải lại AI... ---")
    
    cache.clear()
    logging.info("--- (Signal) Đã xóa cache cũ. ---")
    
    t = threading.Thread(target=ai_service.load_data_and_vectorize)
    t.daemon = True
    t.start()
    logging.info("--- (Signal) Đã gửi yêu cầu tải lại AI. ---")

@receiver(post_save, sender=Poi)
def poi_saved_or_added(sender, instance, **kwargs):
    transaction.on_commit(trigger_ai_data_reload)

@receiver(post_delete, sender=Poi)
def poi_deleted(sender, instance, **kwargs):
    transaction.on_commit(trigger_ai_data_reload)

@receiver(post_save, sender=Eatery)
def eatery_saved_or_added(sender, instance, **kwargs):
    transaction.on_commit(trigger_ai_data_reload)

@receiver(post_delete, sender=Eatery)
def eatery_deleted(sender, instance, **kwargs):
    transaction.on_commit(trigger_ai_data_reload)