"""
URL configuration for firstsite project.

The `urlpatterns` list routes URLs to views. For more information please see:
    https://docs.djangoproject.com/en/5.2/topics/http/urls/
Examples:
Function views
    1. Add an import:  from my_app import views
    2. Add a URL to urlpatterns:  path('', views.home, name='home')
Class-based views
    1. Add an import:  from other_app.views import Home
    2. Add a URL to urlpatterns:  path('', Home.as_view(), name='home')
Including another URLconf
    1. Import the include() function: from django.urls import include, path
    2. Add a URL to urlpatterns:  path('blog/', include('blog.urls'))
"""
from django.contrib import admin
from django.urls import path, include
from home import views as home_views
from home.views import profile_view, generate_qr_code_api
from home.shared_itineraries import shared_itinerary_urlpatterns
from django.conf import settings
from django.conf.urls.static import static
from django.contrib.auth import views as auth_views
from home import views
from home.mobile_api import mobile_catalog, mobile_generate_itinerary

urlpatterns = [
    path('admin/', admin.site.urls),
    path('', home_views.welcome_view, name='welcome'),
    path('generate/', home_views.trip_setup_view, name='home'),
    path('trip-selection/', home_views.trip_selection_combined, name='trip_selection_combined'),
    path('trip-selection/process/', home_views.process_trip_selection, name='process_trip_selection'),
    path('trip-selection/auto-generate/', home_views.auto_generate_itinerary_view, name='auto_generate_itinerary'),

    path('plan/itinerary/', home_views.trip_itinerary_view, name='plan-itinerary'),
    path('plan/itinerary/export-pdf/', home_views.export_itinerary_pdf, name='export-itinerary-pdf'),
    path('resume/<slug:token>/', home_views.resume_itinerary_via_token, name='resume-itinerary'),
    path('api/geocode-and-sort/', home_views.geocode_and_sort_view, name='geocode-and-sort'),
    path('api/search-suggestions/', home_views.search_suggestions_view, name='search-suggestions'),
    path('api/toggle-poi-selection/', home_views.toggle_poi_selection_view, name='toggle-poi-selection'),
    path('api/get-replacement-locations/', home_views.get_replacement_locations, name='get-replacement-locations'),
    path('api/get-location-details/', home_views.get_location_details, name='get-location-details'),
    path('api/get-dalat-route/', home_views.get_dalat_route, name='get-dalat-route'),
    path('api/login/', home_views.login_api, name='login-api'),
    path('api/register/', home_views.register_api, name='register-api'),
    path('logout/', home_views.logout_view, name='logout'),
    path('chat/', home_views.ChatAppView.as_view(), name='chat'),
    path('api/chat/', home_views.ChatAPI.as_view(), name='chat-api'),
    path('profile/', profile_view, name='profile'),
    path('forgot-password/', home_views.forgot_password_view, name='forgot_password'),
    path('verify-otp/', home_views.verify_otp_view, name='verify_otp'),
    path('reset-new-password/', home_views.reset_new_password_view, name='reset_new_password'),
    path('map/', views.map_view, name='map'),
    path('profile/saved-itineraries/<int:itinerary_id>/load/', home_views.load_saved_itinerary_view, name='load-saved-itinerary'),
    path('profile/shared-itineraries/<int:itinerary_id>/delete/', home_views.delete_shared_itinerary_view, name='delete-shared-itinerary'),
    path('api/itinerary-qr/', generate_qr_code_api, name='qr-code-api'),
    path('api/mobile/catalog/', mobile_catalog, name='mobile-catalog'),
    path('api/mobile/itineraries/generate/', mobile_generate_itinerary, name='mobile-generate-itinerary'),
]

urlpatterns += shared_itinerary_urlpatterns

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
