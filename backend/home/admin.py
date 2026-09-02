from django.contrib import admin, messages
from django.contrib.auth.models import User
from django.contrib.auth.admin import UserAdmin as AuthUserAdmin
from django.urls import reverse
from django.utils import timezone
from django.utils.html import format_html, format_html_join

from .models import (
	Eatery,
	GeocodedLocation,
	ItineraryResumeToken,
	PlaceReport,
	Poi,
	Profile,
)
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


class PlaceReportAdmin(admin.ModelAdmin):
	list_display = (
		'id', 'target_name', 'target_type', 'category', 'status',
		'created_at', 'edit_reported_place',
	)
	list_filter = ('status', 'category', 'target_type', 'created_at')
	search_fields = ('target_name', 'description', 'admin_note')
	readonly_fields = (
		'target_type', 'target_id', 'target_name', 'category', 'description',
		'snapshot_details', 'created_at', 'updated_at', 'resolved_at',
		'edit_reported_place',
	)
	fields = (
		'status', 'target_name', 'target_type', 'target_id', 'edit_reported_place',
		'category', 'description', 'snapshot_details', 'admin_note',
		'created_at', 'updated_at', 'resolved_at',
	)
	actions = ('mark_reviewing', 'mark_resolved', 'mark_rejected')

	@admin.display(description='Place record')
	def edit_reported_place(self, obj):
		if not obj or not obj.target_id:
			return '-'
		model_name = 'poi' if obj.target_type == PlaceReport.TargetType.POI else 'eatery'
		model = Poi if model_name == 'poi' else Eatery
		if not model.objects.filter(pk=obj.target_id).exists():
			return 'Place has been removed'
		url = reverse(f'admin:home_{model_name}_change', args=(obj.target_id,))
		return format_html('<a href="{}">Open and edit {}</a>', url, obj.target_name)

	@admin.display(description='Data when the report was submitted')
	def snapshot_details(self, obj):
		if not obj:
			return '-'
		rows = format_html_join(
			'',
			'<tr><th style="text-align:left;padding-right:16px">{}</th><td>{}</td></tr>',
			((key, value) for key, value in obj.current_snapshot.items()),
		)
		return format_html('<table>{}</table>', rows)

	@admin.action(description='Mark selected reports as reviewing')
	def mark_reviewing(self, request, queryset):
		queryset.update(status=PlaceReport.Status.REVIEWING, resolved_at=None)

	@admin.action(description='Mark selected reports as resolved')
	def mark_resolved(self, request, queryset):
		queryset.update(status=PlaceReport.Status.RESOLVED, resolved_at=timezone.now())

	@admin.action(description='Mark selected reports as rejected')
	def mark_rejected(self, request, queryset):
		queryset.update(status=PlaceReport.Status.REJECTED, resolved_at=timezone.now())

	def save_model(self, request, obj, form, change):
		if obj.status in (PlaceReport.Status.RESOLVED, PlaceReport.Status.REJECTED):
			obj.resolved_at = obj.resolved_at or timezone.now()
		else:
			obj.resolved_at = None
		super().save_model(request, obj, form, change)


admin.site.unregister(User)
admin.site.register(User, UserAdmin)

# Register other models for convenience
admin.site.register(Poi)
admin.site.register(Eatery)
admin.site.register(GeocodedLocation, GeocodedLocationAdmin)
admin.site.register(SharedItinerary, SharedItineraryAdmin)
admin.site.register(ItineraryResumeToken, ItineraryResumeTokenAdmin)
admin.site.register(PlaceReport, PlaceReportAdmin)
