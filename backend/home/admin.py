from django.contrib import admin, messages
from django.contrib.auth.models import User
from django.contrib.auth.admin import UserAdmin as AuthUserAdmin

from .models import Profile, Poi, Eatery, GeocodedLocation, ItineraryResumeToken
from .shared_itineraries import SharedItinerary


class ProfileInline(admin.StackedInline):
	model = Profile
	can_delete = False
	verbose_name_plural = 'profile'


class UserAdmin(AuthUserAdmin):
	inlines = (ProfileInline,)


class GeocodedLocationAdmin(admin.ModelAdmin):
	list_display = ('raw_address', 'street', 'ward', 'latitude', 'longitude', 'last_used')
	list_filter = ('created_at', 'last_used')
	search_fields = ('raw_address', 'normalized_address', 'street', 'ward')
	readonly_fields = ('created_at', 'last_used')


class SharedItineraryAdmin(admin.ModelAdmin):
	list_display = ('title', 'mood', 'trip_days', 'owner', 'created_at', 'is_public')
	actions = ('reset_shared_itineraries',)

	@admin.action(description='Delete all shared itineraries')
	def reset_shared_itineraries(self, request, queryset):
		total = SharedItinerary.objects.count()
		if total == 0:
			self.message_user(request, 'No shared itineraries to delete.', level=messages.INFO)
			return
		# Admins often want a clean slate, so ignore the selected queryset and purge everything.
		SharedItinerary.objects.all().delete()
		self.message_user(
			request,
			f'Removed {total} shared itineraries.',
			level=messages.SUCCESS,
		)


class ItineraryResumeTokenAdmin(admin.ModelAdmin):
	list_display = ('token', 'owner', 'created_at', 'expires_at', 'last_accessed')
	search_fields = ('token', 'owner__username')
	list_filter = ('expires_at',)
	readonly_fields = ('token', 'created_at', 'last_accessed')


admin.site.unregister(User)
admin.site.register(User, UserAdmin)

# Register other models for convenience
admin.site.register(Poi)
admin.site.register(Eatery)
admin.site.register(GeocodedLocation, GeocodedLocationAdmin)
admin.site.register(SharedItinerary, SharedItineraryAdmin)
admin.site.register(ItineraryResumeToken, ItineraryResumeTokenAdmin)
