from django.db import models
from django.contrib.auth.models import User
from django.db.models.signals import post_save
from django.dispatch import receiver
from django.utils import timezone

# Create your models here.

class Poi(models.Model):
    name = models.CharField(max_length=255)
    name_en = models.CharField(max_length=255, blank=True, null=True)
    address = models.TextField()
    address_en = models.TextField(blank=True, null=True)
    open_hours = models.CharField(max_length=100, blank=True, null=True)
    tiktok_link = models.URLField(blank=True, null=True)
    rating = models.FloatField(blank=True, null=True)
    price_per_person = models.DecimalField(max_digits=10, decimal_places=2, blank=True, null=True)
    tags = models.CharField(max_length=255, blank=True, null=True) # e.g., "manmade,history"
    tags_en = models.CharField(max_length=255, blank=True, null=True)
    highlight = models.TextField(blank=True, null=True)
    highlight_en = models.TextField(blank=True, null=True)
    latitude = models.FloatField()
    longitude = models.FloatField()
    image_code = models.CharField(max_length=16, blank=True, null=True, db_index=True)

    def __str__(self):
        return self.name
   
   
class Eatery(models.Model):
    name = models.CharField(max_length=255)
    name_en = models.CharField(max_length=255, blank=True, null=True)
    address = models.TextField()
    address_en = models.TextField(blank=True, null=True)
    open_hours = models.CharField(max_length=100, blank=True, null=True)
    # e.g., "morning,afternoon"
    time_tags = models.CharField(max_length=100, blank=True, null=True)
    time_tags_en = models.CharField(max_length=100, blank=True, null=True)
    latitude = models.FloatField()
    longitude = models.FloatField()
    tiktok_link = models.URLField(blank=True, null=True)
    rating = models.FloatField(blank=True, null=True)
    price_min = models.IntegerField(blank=True, null=True)
    price_max = models.IntegerField(blank=True, null=True)
    image_code = models.CharField(max_length=16, blank=True, null=True, db_index=True)
    
    def __str__(self):
        return self.name


class PlaceReport(models.Model):
    """A user-submitted data issue for an existing POI or eatery.

    Reports intentionally do not update the place automatically. An admin reviews the
    saved snapshot, edits the source record when appropriate, then closes the report.
    """

    class TargetType(models.TextChoices):
        POI = 'POI', 'Attraction'
        EATERY = 'EATERY', 'Eatery'

    class Category(models.TextChoices):
        CLOSED = 'CLOSED', 'Permanently closed'
        TEMPORARILY_CLOSED = 'TEMPORARILY_CLOSED', 'Temporarily closed'
        WRONG_PRICE = 'WRONG_PRICE', 'Incorrect price'
        WRONG_HOURS = 'WRONG_HOURS', 'Incorrect opening hours'
        WRONG_ADDRESS = 'WRONG_ADDRESS', 'Incorrect address or location'
        BROKEN_REVIEW = 'BROKEN_REVIEW', 'Broken review link'
        WRONG_IMAGE = 'WRONG_IMAGE', 'Incorrect image'
        DUPLICATE = 'DUPLICATE', 'Duplicate place'
        OTHER = 'OTHER', 'Other'

    class Status(models.TextChoices):
        NEW = 'NEW', 'New'
        REVIEWING = 'REVIEWING', 'Reviewing'
        RESOLVED = 'RESOLVED', 'Resolved'
        REJECTED = 'REJECTED', 'Rejected'

    target_type = models.CharField(max_length=16, choices=TargetType.choices, db_index=True)
    target_id = models.PositiveIntegerField(db_index=True)
    target_name = models.CharField(max_length=255)
    category = models.CharField(max_length=32, choices=Category.choices, db_index=True)
    description = models.TextField()
    current_snapshot = models.JSONField(default=dict, blank=True)
    status = models.CharField(
        max_length=16,
        choices=Status.choices,
        default=Status.NEW,
        db_index=True,
    )
    admin_note = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    updated_at = models.DateTimeField(auto_now=True)
    resolved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ('-created_at',)
        indexes = [
            models.Index(fields=('target_type', 'target_id')),
            models.Index(fields=('status', 'created_at')),
        ]

    def __str__(self):
        return f'#{self.pk} {self.target_name} - {self.get_category_display()}'

class GeocodedLocation(models.Model):
    """Cache for geocoded addresses to avoid redundant API calls."""
    raw_address = models.TextField()  # Original address as entered
    normalized_address = models.TextField(db_index=True)  # Lowercase, stripped version
    place_name = models.CharField(max_length=255, blank=True, null=True, db_index=True)  # Name of the place/building
    house_number = models.CharField(max_length=50, blank=True, null=True)
    street = models.CharField(max_length=255, blank=True, null=True, db_index=True)
    ward = models.CharField(max_length=255, blank=True, null=True, db_index=True)
    latitude = models.FloatField()
    longitude = models.FloatField()
    created_at = models.DateTimeField(auto_now_add=True)
    last_used = models.DateTimeField(auto_now=True)
    
    class Meta:
        indexes = [
            models.Index(fields=['normalized_address']),
            models.Index(fields=['street', 'ward']),
        ]
    
    def __str__(self):
        return f"{self.raw_address} -> ({self.latitude}, {self.longitude})"

class Profile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE)
    phone_number = models.CharField(max_length=15, blank=True, null=True)
    avatar = models.ImageField(upload_to='profile_pics', blank=True, null=True)

    def __str__(self):
        return f'{self.user.username} Profile'


class ItineraryResumeToken(models.Model):
    """Stores a resumable copy of a generated itinerary for QR links."""

    token = models.CharField(max_length=48, unique=True, db_index=True)
    owner = models.ForeignKey(
        User,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name='itinerary_resume_tokens',
    )
    payload = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField()
    last_accessed = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return f"Itinerary resume token {self.token}"

    def is_expired(self) -> bool:
        return bool(self.expires_at and timezone.now() > self.expires_at)

# Tự động tạo Profile khi tạo User mới
@receiver(post_save, sender=User)
def create_user_profile(sender, instance, created, **kwargs):
    if created:
        Profile.objects.create(user=instance)

@receiver(post_save, sender=User)
def save_user_profile(sender, instance, **kwargs):
    instance.profile.save()
