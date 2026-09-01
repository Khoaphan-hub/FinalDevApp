# In home/views.py
from django.http import JsonResponse
from django.shortcuts import render, redirect, get_object_or_404
from django.urls import reverse
from django.contrib.auth import authenticate, login as auth_login, logout as auth_logout
from django.contrib.auth.models import User
from django.contrib.auth.forms import UserCreationForm
from django import forms
from django.views.decorators.http import require_GET, require_POST
from django.views.decorators.csrf import csrf_exempt
from decimal import Decimal, InvalidOperation
from datetime import timedelta
from django.utils import timezone
import json
import random
import secrets
import hashlib
from copy import deepcopy
from django.core.mail import send_mail
from django.conf import settings
from .forms import ForgotPasswordForm, OTPVerificationForm, ResetPasswordForm
from django.contrib.auth.decorators import login_required
from django.contrib.auth import update_session_auth_hash
from django.contrib.auth.forms import PasswordChangeForm
from django.contrib import messages
from django.db.models import Q
from .models import Poi, Eatery, Profile, ItineraryResumeToken
from .geocode import geocode_address
from .sort import get_sorted_pois, get_sorted_eateries
from .search import get_search_tree
from .constants import (
    DEFAULT_FALLBACK_COORDS,
    DEFAULT_FALLBACK_LABEL,
    SEARCH_SUGGESTION_LIMIT,
    TRAVEL_MOOD_TAGS,
)
from .forms import UserUpdateForm, ProfileUpdateForm, CustomPasswordChangeForm
from .shared_itineraries import SharedItinerary, SharedItineraryService
from .trip_planner import (
    clean_currency_string,
    trip_eatery_selection_view as process_trip_selection,
    auto_generate_itinerary_view,
)
from .utils import get_item_image_url
import base64
from io import BytesIO
import importlib

qrcode = None
try:
    qrcode = importlib.import_module('qrcode')
except ImportError:  # pragma: no cover - fallback for environments missing dependency
    qrcode = None

QR_LINK_TTL_DAYS = 30
QR_TOKEN_SESSION_KEY = 'qr_resume_token'
QR_TOKEN_FINGERPRINT_KEY = 'qr_resume_fingerprint'


def _compute_snapshot_fingerprint(snapshot: dict) -> str:
    try:
        normalized = json.dumps(snapshot, sort_keys=True, ensure_ascii=False, separators=(',', ':'), default=str)
    except TypeError:
        normalized = json.dumps(json.loads(json.dumps(snapshot, default=str)), sort_keys=True, ensure_ascii=False, separators=(',', ':'))
    return hashlib.sha256(normalized.encode('utf-8')).hexdigest()


def _snapshot_itinerary_for_resume(request):
    planner_itinerary = request.session.get('planner_itinerary') or {}
    trip_setup = request.session.get('trip_setup') or {}

    if not planner_itinerary or not planner_itinerary.get('results') or not trip_setup:
        return None

    snapshot = {
        'trip_setup': deepcopy(trip_setup),
        'planner_itinerary': deepcopy(planner_itinerary),
        'itinerary_results': deepcopy(planner_itinerary),
    }

    planner_step3 = request.session.get('planner_step3')
    if planner_step3 is not None:
        snapshot['planner_step3'] = deepcopy(planner_step3)

    selected_poi_ids = request.session.get('selected_poi_ids')
    if selected_poi_ids is not None:
        snapshot['selected_poi_ids'] = deepcopy(selected_poi_ids)

    shared_source = request.session.get('shared_itinerary_source')
    if shared_source is not None:
        snapshot['shared_itinerary_source'] = deepcopy(shared_source)

    shared_eateries = request.session.get('shared_selected_eatery_ids')
    if shared_eateries is not None:
        snapshot['shared_selected_eatery_ids'] = deepcopy(shared_eateries)

    return snapshot


def _generate_unique_resume_token():
    for _ in range(10):
        candidate = secrets.token_urlsafe(24)
        if not ItineraryResumeToken.objects.filter(token=candidate).exists():
            return candidate
    raise RuntimeError('Unable to allocate resume token')


def _ensure_resume_link(request):
    snapshot = _snapshot_itinerary_for_resume(request)
    if not snapshot:
        return None

    fingerprint = _compute_snapshot_fingerprint(snapshot)
    resume_token = request.session.get(QR_TOKEN_SESSION_KEY)
    stored_fingerprint = request.session.get(QR_TOKEN_FINGERPRINT_KEY)
    resume_obj = None

    if resume_token and stored_fingerprint == fingerprint:
        resume_obj = ItineraryResumeToken.objects.filter(token=resume_token).first()
        if resume_obj and resume_obj.is_expired():
            resume_obj.delete()
            resume_obj = None

    expires_at = timezone.now() + timedelta(days=QR_LINK_TTL_DAYS)
    owner = request.user if request.user.is_authenticated else None

    if resume_obj:
        resume_obj.payload = snapshot
        resume_obj.expires_at = expires_at
        if owner and resume_obj.owner_id != getattr(owner, 'id', None):
            resume_obj.owner = owner
        resume_obj.last_accessed = timezone.now()
        resume_obj.save(update_fields=['payload', 'expires_at', 'owner', 'last_accessed'])
        token_value = resume_obj.token
    else:
        token_value = _generate_unique_resume_token()
        resume_obj = ItineraryResumeToken.objects.create(
            token=token_value,
            payload=snapshot,
            owner=owner,
            expires_at=expires_at,
            last_accessed=timezone.now(),
        )
        request.session[QR_TOKEN_SESSION_KEY] = token_value
        request.session[QR_TOKEN_FINGERPRINT_KEY] = fingerprint
        request.session.modified = True

    if not request.session.get(QR_TOKEN_FINGERPRINT_KEY):
        request.session[QR_TOKEN_FINGERPRINT_KEY] = fingerprint
        request.session.modified = True

    return request.build_absolute_uri(reverse('resume-itinerary', args=[token_value]))

@login_required
def profile_view(request):
    profile, _ = Profile.objects.get_or_create(user=request.user)

    u_form = UserUpdateForm(instance=request.user)
    p_form = ProfileUpdateForm(instance=profile)
    pass_form = CustomPasswordChangeForm(request.user)

    owned_itineraries = list(
        SharedItinerary.objects.filter(owner=request.user)
        .select_related('owner')
        .order_by('-created_at')
    )

    shared_itineraries = [itinerary for itinerary in owned_itineraries if itinerary.is_public]
    saved_itineraries = [itinerary for itinerary in owned_itineraries if not itinerary.is_public]

    max_saved_itineraries = SharedItineraryService.MAX_PRIVATE_PER_USER
    max_shared_itineraries = SharedItineraryService.MAX_PUBLIC_PER_USER

    remaining_saved_slots = max(0, max_saved_itineraries - len(saved_itineraries))
    remaining_shared_slots = max(0, max_shared_itineraries - len(shared_itineraries))

    active_tab = 'info'

    if request.method == 'POST':
        if 'update_info' in request.POST:
            active_tab = 'info'
            u_form = UserUpdateForm(request.POST, instance=request.user)
            p_form = ProfileUpdateForm(request.POST, request.FILES, instance=profile)

            if u_form.is_valid() and p_form.is_valid():
                u_form.save()
                p_form.save()
                messages.success(request, 'Hồ sơ của bạn đã được cập nhật!')
                return redirect('/profile/?tab=info')
        elif 'change_password' in request.POST:
            active_tab = 'password'
            pass_form = CustomPasswordChangeForm(request.user, request.POST)

            if pass_form.is_valid():
                user = pass_form.save()
                update_session_auth_hash(request, user)
                messages.success(request, 'Đổi mật khẩu thành công!')
                return redirect('/profile/?tab=password')
            else:
                for field, errors in pass_form.errors.items():
                    for error in errors:
                        if field == '__all__':
                            messages.error(request, error)
                        else:
                            messages.error(request, f"{error}")

    tab_param = request.GET.get('tab')
    if tab_param in {'info', 'password', 'saved', 'shared'}:
        active_tab = tab_param
    elif request.method != 'POST':
        if saved_itineraries:
            active_tab = 'saved'
        elif shared_itineraries:
            active_tab = 'shared'

    context = {
        'u_form': u_form,
        'p_form': p_form,
        'pass_form': pass_form,
        'active_tab': active_tab,
        'shared_itineraries': shared_itineraries,
        'saved_itineraries': saved_itineraries,
        'max_saved_itineraries': max_saved_itineraries,
        'max_shared_itineraries': max_shared_itineraries,
        'remaining_saved_slots': remaining_saved_slots,
        'remaining_shared_slots': remaining_shared_slots,
    }
    return render(request, 'profile.html', context)


@login_required
@require_POST
def delete_shared_itinerary_view(request, itinerary_id: int):
    itinerary = get_object_or_404(SharedItinerary, pk=itinerary_id, owner=request.user)
    redirect_tab = 'shared' if itinerary.is_public else 'saved'
    title = itinerary.title
    itinerary.delete()
    messages.success(request, f"Đã xóa lịch trình '{title}'.")
    return redirect(f'/profile/?tab={redirect_tab}')


@login_required
def load_saved_itinerary_view(request, itinerary_id: int):
    itinerary = get_object_or_404(SharedItinerary, pk=itinerary_id, owner=request.user)
    SharedItineraryService.prepare_session_for_adoption(request, itinerary)
    messages.success(request, f"Đang mở lịch trình '{itinerary.title}'. / Opening itinerary '{itinerary.title}'.")
    return redirect('plan-itinerary')

class SimpleUserCreationForm(UserCreationForm):
    class Meta:
        model = User
        fields = ("username", "password1", "password2")


def welcome_view(request):
    """
    Landing/greeting page with login and register options
    """
    context = {
        'user': request.user if request.user.is_authenticated else None
    }
    return render(request, 'welcome.html', context)


@login_required
def trip_setup_view(request):
    """Collect Step 1 & 2 inputs (constraints + starting point)."""
    stored = request.session.get('trip_setup') or {}
    available_moods = [
        {'name': name, 'tags': tags}
        for name, tags in TRAVEL_MOOD_TAGS.items()
    ]

    form_values = {
        'days_travel': stored.get('days', 3),
        'max_pois_per_day': stored.get('max_pois_per_day', 4),
        'budget': stored.get('budget', ''),
        'place_name': stored.get('place_name', ''),
        'user_address': stored.get('user_address', ''),
        'travel_mood': stored.get('mood', ''),
    }

    context = {
        'form_values': form_values,
        'form_errors': [],
        'start_location': stored.get('start_location'),
        'available_moods': available_moods,
    }

    session_errors = request.session.pop('trip_setup_errors', None)
    if session_errors:
        context['form_errors'].extend(session_errors)

    if request.method == 'POST':
        days_raw = (request.POST.get('days_travel') or '').strip()
        max_pois_raw = (request.POST.get('max_pois_per_day') or '').strip()
        budget_raw = (request.POST.get('budget') or '').strip()
        place_name_raw = (request.POST.get('place_name') or '').strip()
        address_raw = (request.POST.get('user_address') or '').strip()
        travel_mood_raw = (request.POST.get('travel_mood') or '').strip()

        budget_raw_clean = clean_currency_string(budget_raw)

        form_values.update({
            'days_travel': days_raw or form_values['days_travel'],
            'max_pois_per_day': max_pois_raw or form_values['max_pois_per_day'],
            'budget': budget_raw_clean or budget_raw,
            'place_name': place_name_raw,
            'user_address': address_raw,
            'travel_mood': travel_mood_raw or form_values['travel_mood'],
        })

        errors = []

        try:
            days = int(days_raw)
            if days <= 0:
                raise ValueError
        except (TypeError, ValueError):
            errors.append('Please enter a valid number of travel days (>0).')

        try:
            max_pois = int(max_pois_raw)
            if max_pois <= 0:
                raise ValueError
        except (TypeError, ValueError):
            errors.append('Please enter a valid POI limit per day (>0).')

        try:
            budget_amount = Decimal(budget_raw_clean)
            if budget_amount < 0:
                raise InvalidOperation
        except (InvalidOperation, TypeError):
            errors.append('Please enter a valid non-negative budget.')

        if not travel_mood_raw:
            errors.append('Please choose a travel mood to guide POI suggestions.')
        elif travel_mood_raw not in TRAVEL_MOOD_TAGS:
            errors.append('Selected travel mood is invalid. Please pick one from the list.')

        # Use place_name if provided, otherwise use address
        search_query = place_name_raw if place_name_raw else address_raw
        is_place_name = bool(place_name_raw)  # True if user entered a place name, False if address
        
        coords = DEFAULT_FALLBACK_COORDS
        address_label = search_query or DEFAULT_FALLBACK_LABEL
        fallback_used = not bool(search_query)

        if search_query:
            resolved = geocode_address(search_query, is_place_name=is_place_name)
            if resolved:
                coords = resolved
                print(f"[GEOCODE] User input '{search_query}' (place_name={is_place_name}) -> lat={coords[0]:.6f}, lon={coords[1]:.6f}")
            else:
                fallback_used = True
                print(f"[GEOCODE] Unable to resolve '{search_query}', falling back to default coords")

        if errors:
            context['form_errors'] = errors
            return render(request, 'trip_setup.html', context)

        trip_setup_payload = {
            'days': days,
            'max_pois_per_day': max_pois,
            'budget': str(budget_amount),
            'place_name': place_name_raw,
            'user_address': address_raw,
            'mood': travel_mood_raw,
            'start_location': {
                'lat': coords[0],
                'lon': coords[1],
                'address_label': address_label,
                'fallback_used': fallback_used,
            },
        }

        request.session['trip_setup'] = trip_setup_payload
        request.session.pop('planner_itinerary', None)
        request.session.pop('planner_step3', None)
        request.session.pop('selected_poi_ids', None)
        request.session.pop('shared_itinerary_source', None)
        request.session.pop('shared_selected_eatery_ids', None)
        request.session.pop(QR_TOKEN_SESSION_KEY, None)
        request.session.pop(QR_TOKEN_FINGERPRINT_KEY, None)
        request.session.modified = True

        next_step = 'auto_generate_itinerary' if 'generate_now' in request.POST else 'trip_selection_combined'
        return redirect(next_step)

    return render(request, 'trip_setup.html', context)


def trip_itinerary_view(request):
    trip_setup = request.session.get('trip_setup')
    if not trip_setup:
        return redirect('home')

    itinerary_payload = request.session.get('planner_itinerary')
    if not itinerary_payload:
        return redirect('trip_selection_combined')

    # Get both Vietnamese and English versions
    itinerary_results_vi = itinerary_payload.get('results')
    itinerary_results_en = itinerary_payload.get('results_en')
    
    if not itinerary_results_vi:
        return redirect('trip_selection_combined')
    
    # Get current language from session or cookie
    current_language = request.session.get('django_language', 'vi')
    if not current_language or current_language not in ['vi', 'en']:
        current_language = request.COOKIES.get('django_language', 'vi')
    
    # Select appropriate itinerary based on language for initial render
    itinerary_results = itinerary_results_en if current_language == 'en' and itinerary_results_en else itinerary_results_vi
    
    # Check if this itinerary was adopted from community
    shared_itinerary_source = request.session.get('shared_itinerary_source')
    is_adopted = bool(shared_itinerary_source and shared_itinerary_source.get('id'))
    adopted_itinerary_id = shared_itinerary_source.get('id') if shared_itinerary_source else None

    def _to_decimal(value):
        try:
            return Decimal(str(value))
        except (InvalidOperation, TypeError):
            return None

    try:
        budget_number = Decimal(str(trip_setup.get('budget')))
    except (InvalidOperation, TypeError):
        budget_number = None

    context = {
        'itinerary_results': itinerary_results,
        'itinerary_results_vi': itinerary_results_vi,
        'itinerary_results_en': itinerary_results_en,
        'trip_setup': trip_setup,
        'budget_number': budget_number,
        'total_selected_cost': _to_decimal(itinerary_payload.get('total_selected_cost')),
        'budget_remaining': _to_decimal(itinerary_payload.get('budget_remaining')),
        'selected_counts': itinerary_payload.get('selected_counts', {}),
        'is_adopted': is_adopted,
        'adopted_source': shared_itinerary_source,
        'adopted_itinerary_id': adopted_itinerary_id,
        'current_language': current_language,
    }

    if request.user.is_authenticated:
        max_saved_itineraries = SharedItineraryService.MAX_PRIVATE_PER_USER
        max_shared_itineraries = SharedItineraryService.MAX_PUBLIC_PER_USER
        saved_count = SharedItinerary.objects.filter(owner=request.user, is_public=False).count()
        shared_count = SharedItinerary.objects.filter(owner=request.user, is_public=True).count()

        context.update({
            'max_saved_itineraries': max_saved_itineraries,
            'max_shared_itineraries': max_shared_itineraries,
            'remaining_saved_slots': max(0, max_saved_itineraries - saved_count),
            'remaining_shared_slots': max(0, max_shared_itineraries - shared_count),
        })

    return render(request, 'itinerary.html', context)


def export_itinerary_pdf(request):
    """Export itinerary to PDF"""
    from django.http import HttpResponse
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.units import cm
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak
    from reportlab.lib import colors
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.lib.enums import TA_CENTER, TA_LEFT
    import io
    import os
    
    trip_setup = request.session.get('trip_setup')
    itinerary_payload = request.session.get('planner_itinerary')
    
    if not trip_setup or not itinerary_payload:
        return HttpResponse("No itinerary found", status=404)
    
    # Get current language from session or cookie or GET parameter
    current_language = request.GET.get('lang')
    if not current_language:
        current_language = request.session.get('django_language')
    if not current_language or current_language not in ['vi', 'en']:
        current_language = request.COOKIES.get('django_language', 'vi')
    
    print(f"[PDF Export] Language detected: {current_language}")
    print(f"[PDF Export] Available keys in itinerary_payload: {list(itinerary_payload.keys())}")
    
    # Select appropriate itinerary based on language
    if current_language == 'en' and 'results_en' in itinerary_payload and itinerary_payload.get('results_en'):
        itinerary_results = itinerary_payload.get('results_en')
        print(f"[PDF Export] Using English itinerary")
    else:
        itinerary_results = itinerary_payload.get('results')
        print(f"[PDF Export] Using Vietnamese itinerary (fallback or default)")
    
    if not itinerary_results:
        return HttpResponse("No itinerary results found", status=404)
    
    # Language-specific labels
    labels = {
        'vi': {
            'title': 'Hành Trình Đà Lạt Cùng Journify',
            'duration': 'Thời gian:',
            'days': 'ngày',
            'mood': 'Phong cách:',
            'budget': 'Ngân sách:',
            'start_date': 'Ngày bắt đầu:',
            'day': 'Ngày',
            'number': 'STT',
            'location': 'Địa điểm',
            'type': 'Loại',
            'address': 'Địa chỉ',
            'accommodation': 'Chỗ ở',
            'destination': 'Điểm đến',
            'eatery': 'Nhà hàng',
            'morning': 'Sáng',
            'afternoon': 'Trưa',
            'evening': 'Tối',
            'footer': 'Tạo bởi Journify - Computational Thinking'
        },
        'en': {
            'title': 'Dalat Journey with Journify',
            'duration': 'Duration:',
            'days': 'days',
            'mood': 'Mood:',
            'budget': 'Budget:',
            'start_date': 'Start Date:',
            'day': 'Day',
            'number': '#',
            'location': 'Location',
            'type': 'Type',
            'address': 'Address',
            'accommodation': 'Accommodation',
            'destination': 'Destination',
            'eatery': 'Eatery',
            'morning': 'Morning',
            'afternoon': 'Afternoon',
            'evening': 'Evening',
            'footer': 'Generated by Journify - Computational Thinking'
        }
    }
    
    lang = labels[current_language]
    
    # Register Unicode font for Vietnamese support
    font_name = 'Courier'
    font_name_bold = 'Courier-Bold'
    
    try:
        import sys
        
        # Try Windows Arial first (best Vietnamese support on Windows)
        if sys.platform == 'win32':
            try:
                arial_path = 'C:/Windows/Fonts/arial.ttf'
                arialbd_path = 'C:/Windows/Fonts/arialbd.ttf'
                
                if os.path.exists(arial_path) and os.path.exists(arialbd_path):
                    pdfmetrics.registerFont(TTFont('Arial', arial_path))
                    pdfmetrics.registerFont(TTFont('Arial-Bold', arialbd_path))
                    font_name = 'Arial'
                    font_name_bold = 'Arial-Bold'
                    print("[PDF] Using Windows Arial font for Vietnamese support")
            except Exception as e:
                print(f"[PDF] Failed to load Arial: {e}")
        
        # If not Windows or Arial failed, try DejaVu Sans from site-packages
        if font_name == 'Courier':
            try:
                import site
                for site_pkg in site.getsitepackages():
                    dejavu_path = os.path.join(site_pkg, 'reportlab', 'fonts', 'DejaVuSans.ttf')
                    dejavu_bold_path = os.path.join(site_pkg, 'reportlab', 'fonts', 'DejaVuSans-Bold.ttf')
                    
                    if os.path.exists(dejavu_path) and os.path.exists(dejavu_bold_path):
                        pdfmetrics.registerFont(TTFont('DejaVuSans', dejavu_path))
                        pdfmetrics.registerFont(TTFont('DejaVuSans-Bold', dejavu_bold_path))
                        font_name = 'DejaVuSans'
                        font_name_bold = 'DejaVuSans-Bold'
                        print("[PDF] Using DejaVu Sans font for Vietnamese support")
                        break
            except Exception as e:
                print(f"[PDF] Failed to load DejaVu Sans: {e}")
        
        # Final fallback
        if font_name == 'Courier':
            print("[PDF] Using Courier fallback (limited Unicode support)")
            
    except Exception as e:
        print(f"[PDF] Font loading error: {e}, using Courier fallback")
    
    # Create PDF in memory
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A4, rightMargin=2*cm, leftMargin=2*cm, topMargin=2*cm, bottomMargin=2*cm)
    
    # Container for PDF elements
    elements = []
    
    # Styles
    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        'CustomTitle',
        parent=styles['Heading1'],
        fontSize=24,
        textColor=colors.HexColor('#0071E3'),
        spaceAfter=30,
        alignment=TA_CENTER,
        fontName=font_name_bold
    )
    
    heading_style = ParagraphStyle(
        'CustomHeading',
        parent=styles['Heading2'],
        fontSize=16,
        textColor=colors.HexColor('#1D1D1F'),
        spaceAfter=12,
        spaceBefore=12,
        fontName=font_name_bold
    )
    
    normal_style = ParagraphStyle(
        'CustomNormal',
        parent=styles['Normal'],
        fontSize=10,
        leading=14,
        fontName=font_name
    )
    
    # Title
    elements.append(Paragraph(lang['title'], title_style))
    elements.append(Spacer(1, 0.5*cm))
    
    # Trip Summary
    summary_data = [
        [lang['duration'], f"{trip_setup.get('days', 'N/A')} {lang['days']}"],
        [lang['mood'], trip_setup.get('mood', 'N/A').title()],
        [lang['budget'], f"₫{int(trip_setup.get('budget', 0)):,}"],
        [lang['start_date'], trip_setup.get('start_date', 'N/A')]
    ]
    
    summary_table = Table(summary_data, colWidths=[4*cm, 10*cm])
    summary_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (0, -1), colors.HexColor('#F5F5F7')),
        ('TEXTCOLOR', (0, 0), (-1, -1), colors.black),
        ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
        ('FONTNAME', (0, 0), (0, -1), font_name_bold),
        ('FONTNAME', (1, 0), (1, -1), font_name),
        ('FONTSIZE', (0, 0), (-1, -1), 10),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 8),
        ('TOPPADDING', (0, 0), (-1, -1), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
    ]))
    
    elements.append(summary_table)
    elements.append(Spacer(1, 1*cm))
    
    # Itinerary Details
    for day_num in sorted(itinerary_results.keys()):
        stops = itinerary_results[day_num]
        
        # Day heading
        elements.append(Paragraph(f"{lang['day']} {day_num}", heading_style))
        elements.append(Spacer(1, 0.3*cm))
        
        # Create table for locations
        location_data = [[lang['number'], lang['location'], lang['type'], lang['address']]]
        
        stop_number = 0
        for idx, item in enumerate(stops):
            item_type = item.get('type', 'Unknown')
            
            # Skip accommodation or number only POI/Eatery
            if item_type == 'ACCOMMODATION':
                type_label = lang['accommodation']
                number = '-'
            else:
                stop_number += 1
                number = str(stop_number)
                if item_type == 'POI':
                    type_label = lang['destination']
                elif item_type == 'EATERY':
                    slot = item.get('slot', '')
                    if slot == 'morning':
                        type_label = lang['morning']
                    elif slot == 'afternoon':
                        type_label = lang['afternoon']
                    elif slot == 'evening':
                        type_label = lang['evening']
                    else:
                        type_label = lang['eatery']
                else:
                    type_label = item_type
            
            location_data.append([
                number,
                Paragraph(item.get('name', 'Unknown'), normal_style),
                type_label,
                Paragraph(item.get('address', 'N/A'), normal_style)
            ])
        
        location_table = Table(location_data, colWidths=[1*cm, 4.5*cm, 3*cm, 6.5*cm])
        location_table.setStyle(TableStyle([
            ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#0071E3')),
            ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
            ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
            ('FONTNAME', (0, 0), (-1, 0), font_name_bold),
            ('FONTSIZE', (0, 0), (-1, 0), 10),
            ('BOTTOMPADDING', (0, 0), (-1, 0), 10),
            ('TOPPADDING', (0, 0), (-1, 0), 10),
            ('BACKGROUND', (0, 1), (-1, -1), colors.beige),
            ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
            ('FONTNAME', (0, 1), (-1, -1), font_name),
            ('FONTSIZE', (0, 1), (-1, -1), 9),
            ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#F9F9F9')]),
            ('VALIGN', (0, 0), (-1, -1), 'TOP'),
            ('LEFTPADDING', (0, 0), (-1, -1), 6),
            ('RIGHTPADDING', (0, 0), (-1, -1), 6),
            ('TOPPADDING', (0, 1), (-1, -1), 8),
            ('BOTTOMPADDING', (0, 1), (-1, -1), 8),
        ]))
        
        elements.append(location_table)
        elements.append(Spacer(1, 0.8*cm))
    
    # Add QR Code
    if qrcode:
        try:
            from reportlab.platypus import Image as RLImage
            
            # Get or create resume link for this itinerary
            resume_link = _ensure_resume_link(request)
            qr_url = resume_link if resume_link else request.build_absolute_uri('/')
            
            print(f"[PDF Export] QR URL: {qr_url}")
            
            # Create QR code
            qr = qrcode.QRCode(
                version=1,
                error_correction=qrcode.constants.ERROR_CORRECT_H,
                box_size=10,
                border=4,
            )
            qr.add_data(qr_url)
            qr.make(fit=True)
            
            # Generate QR code image
            qr_img = qr.make_image(fill_color="#0f172a", back_color="#ffffff")
            
            # Save to BytesIO
            qr_buffer = BytesIO()
            qr_img.save(qr_buffer, format='PNG')
            qr_buffer.seek(0)
            
            # Add QR code section
            elements.append(Spacer(1, 1.5*cm))
            
            # QR code title
            qr_title_style = ParagraphStyle(
                'QRTitle',
                parent=styles['Normal'],
                fontSize=10,
                textColor=colors.HexColor('#1D1D1F'),
                alignment=TA_CENTER,
                fontName=font_name_bold
            )
            
            if resume_link:
                qr_title_text = "Quét mã để xem lại lịch trình" if current_language == 'vi' else "Scan to view itinerary"
            else:
                qr_title_text = "Quét mã để truy cập Journify" if current_language == 'vi' else "Scan to access Journify"
            
            elements.append(Paragraph(qr_title_text, qr_title_style))
            elements.append(Spacer(1, 0.3*cm))
            
            # Add QR code image
            qr_image = RLImage(qr_buffer, width=4*cm, height=4*cm)
            
            # Center the QR code using a table
            qr_table = Table([[qr_image]], colWidths=[4*cm])
            qr_table.setStyle(TableStyle([
                ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
                ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
            ]))
            elements.append(qr_table)
            elements.append(Spacer(1, 0.3*cm))
            
            # QR URL text (shorten for display)
            qr_url_style = ParagraphStyle(
                'QRURL',
                parent=styles['Normal'],
                fontSize=7,
                textColor=colors.grey,
                alignment=TA_CENTER,
                fontName=font_name
            )
            # Display shortened URL
            display_url = qr_url if len(qr_url) < 60 else qr_url[:57] + '...'
            print(f"[PDF Export] Display URL: {display_url}")
            elements.append(Paragraph(display_url, qr_url_style))
            
        except Exception as e:
            print(f"[PDF Export] Failed to add QR code: {e}")
    
    # Footer
    elements.append(Spacer(1, 0.5*cm))
    footer_style = ParagraphStyle(
        'Footer',
        parent=styles['Normal'],
        fontSize=8,
        textColor=colors.black,
        alignment=TA_CENTER,
        fontName=font_name
    )
    elements.append(Paragraph(lang['footer'], footer_style))
    
    # Build PDF
    doc.build(elements)
    
    # Get PDF from buffer
    pdf = buffer.getvalue()
    buffer.close()
    
    # Create response
    filename_prefix = "hanh_trinh_dalat" if current_language == 'vi' else "dalat_itinerary"
    response = HttpResponse(content_type='application/pdf')
    response['Content-Disposition'] = f'attachment; filename="{filename_prefix}_{trip_setup.get("days", "N")}days.pdf"'
    response.write(pdf)
    
    return response


def geocode_and_sort_view(request):
    """
    Takes either 'place_name' or 'address' string, geocodes it, and returns a
    list of POIs sorted by distance from that location.
    """
    place_name = (request.GET.get('place_name') or '').strip()
    address = (request.GET.get('address') or '').strip()

    if not place_name and not address:
        return JsonResponse({'error': 'Please provide either a place name or an address.'}, status=400)

    # Use place_name if provided, otherwise use address
    search_query = place_name if place_name else address
    display_label = search_query

    coords = geocode_address(search_query)
    used_fallback = False
    if not coords:
        print(f"[GEOCODE] Unable to resolve '{search_query}'. Falling back to default coordinates.")
        coords = DEFAULT_FALLBACK_COORDS
        used_fallback = True

    start_lat, start_lon = coords
    print(f"[GEOCODE] '{search_query}' -> lat={start_lat:.6f}, lon={start_lon:.6f}")

    def parse_limit(param):
        raw_value = request.GET.get(param)
        if not raw_value:
            return None
        try:
            parsed = int(raw_value)
        except (TypeError, ValueError):
            return None
        return max(1, min(parsed, 200))

    poi_limit = parse_limit('poi_limit')
    eatery_limit = parse_limit('eatery_limit')

    sorted_pois = get_sorted_pois(start_lat, start_lon, limit=poi_limit)
    sorted_eateries = get_sorted_eateries(start_lat, start_lon, limit=eatery_limit)

    return JsonResponse({
        'start': {'lat': start_lat, 'lon': start_lon},
        'address': display_label or DEFAULT_FALLBACK_LABEL,
        'fallback_used': used_fallback,
        'pois': sorted_pois,
        'eateries': sorted_eateries,
    })


@require_POST
def generate_qr_code_api(request):
    """Generate a data-URL QR code for a given itinerary link."""
    if qrcode is None:
        return JsonResponse({'error': 'QR generator unavailable on this server.'}, status=503)

    try:
        payload = json.loads(request.body or '{}')
    except json.JSONDecodeError:
        return JsonResponse({'error': 'Invalid JSON payload.'}, status=400)

    link = (payload.get('link') or '').strip()
    if not link:
        return JsonResponse({'error': 'Missing itinerary link.'}, status=400)

    resume_link = _ensure_resume_link(request)
    target_link = resume_link or link

    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_H,
        box_size=8,
        border=2,
    )
    qr.add_data(target_link)
    qr.make(fit=True)
    image = qr.make_image(fill_color="#0f172a", back_color="#ffffff")

    buffer = BytesIO()
    image.save(buffer, format='PNG')
    buffer.seek(0)
    base64_png = base64.b64encode(buffer.getvalue()).decode('ascii')
    data_url = f"data:image/png;base64,{base64_png}"

    return JsonResponse({'qr_data_url': data_url, 'share_url': target_link})


def resume_itinerary_via_token(request, token):
    resume_obj = ItineraryResumeToken.objects.filter(token=token).first()
    if not resume_obj:
        messages.error(request, 'Liên kết lịch trình không hợp lệ hoặc đã bị xóa.')
        return redirect('welcome')

    if resume_obj.is_expired():
        resume_obj.delete()
        messages.error(request, 'Liên kết lịch trình đã hết hạn. Vui lòng tạo lại mã QR mới.')
        return redirect('welcome')

    payload = resume_obj.payload or {}
    planner_itinerary = payload.get('planner_itinerary')
    trip_setup = payload.get('trip_setup')

    if not planner_itinerary or not planner_itinerary.get('results') or not trip_setup:
        messages.error(request, 'Không thể khôi phục lịch trình từ liên kết này.')
        return redirect('welcome')

    session_updates = {
        'trip_setup': trip_setup,
        'planner_itinerary': planner_itinerary,
        'itinerary_results': payload.get('itinerary_results') or planner_itinerary,
        'planner_step3': payload.get('planner_step3'),
        'selected_poi_ids': payload.get('selected_poi_ids'),
        'shared_itinerary_source': payload.get('shared_itinerary_source'),
        'shared_selected_eatery_ids': payload.get('shared_selected_eatery_ids'),
    }

    for key, value in session_updates.items():
        if value is None:
            request.session.pop(key, None)
        else:
            request.session[key] = value

    request.session[QR_TOKEN_SESSION_KEY] = resume_obj.token
    try:
        request.session[QR_TOKEN_FINGERPRINT_KEY] = _compute_snapshot_fingerprint(payload)
    except Exception:
        request.session.pop(QR_TOKEN_FINGERPRINT_KEY, None)
    request.session.modified = True

    resume_obj.last_accessed = timezone.now()
    resume_obj.save(update_fields=['last_accessed'])

    return redirect('plan-itinerary')


from django.views.decorators.csrf import csrf_exempt

@csrf_exempt
def login_api(request):
    """
    API endpoint for login (returns JSON for AJAX requests)
    """
    if request.method == 'POST':
        username = request.POST.get('username', '').strip()
        password = request.POST.get('password', '').strip()

        if not username or not password:
            return JsonResponse({
                'success': False,
                'error': 'Please fill in all fields.'
            }, status=400)

        user = authenticate(request, username=username, password=password)
        if user is not None:
            auth_login(request, user)
            return JsonResponse({
                'success': True,
                'username': user.username,
                'redirect': '/generate/'
            })
        else:
            return JsonResponse({
                'success': False,
                'error': 'Invalid username or password.'
            }, status=401)
    
    return JsonResponse({'success': False, 'error': 'Method not allowed'}, status=405)


@csrf_exempt
def register_api(request):
    """
    API endpoint for registration (returns JSON for AJAX requests)
    """
    if request.method == 'POST':
        form = SimpleUserCreationForm(request.POST)
        if form.is_valid():
            user = form.save()
            auth_login(request, user)
            return JsonResponse({
                'success': True,
                'username': user.username,
                'redirect': '/generate/'
            })
        else:
            # Extract errors from form
            errors = []
            for field, error_list in form.errors.items():
                for error in error_list:
                    errors.append(str(error))
            
            error_message = ' '.join(errors) if errors else 'Registration failed. Please check your input.'
            return JsonResponse({
                'success': False,
                'error': error_message
            }, status=400)
    
    return JsonResponse({'success': False, 'error': 'Method not allowed'}, status=405)


def logout_view(request):
    """Log the user out and always send them to the welcome page."""
    auth_logout(request)
    return redirect('welcome')


def itinerary_view(request):
    """
    Display the generated itinerary on a separate page
    Retrieves the itinerary from the session
    """
    itinerary_results = request.session.get('itinerary_results')
    user = request.user if request.user.is_authenticated else None
    
    # Check if this itinerary was adopted from community
    shared_itinerary_source = request.session.get('shared_itinerary_source')
    is_adopted = bool(shared_itinerary_source and shared_itinerary_source.get('id'))
    
    # Get both language versions
    itinerary_results_vi = None
    itinerary_results_en = None
    
    # If no itinerary_results but we have planner_itinerary (from adoption), use that
    if not itinerary_results:
        planner_itinerary = request.session.get('planner_itinerary')
        if planner_itinerary:
            # Get both Vietnamese and English versions
            itinerary_results_vi = planner_itinerary.get('results', planner_itinerary)
            itinerary_results_en = planner_itinerary.get('results_en')
            
            # Get current language from session or cookie
            current_language = request.session.get('django_language', 'vi')
            if not current_language or current_language not in ['vi', 'en']:
                current_language = request.COOKIES.get('django_language', 'vi')
            
            # Select appropriate itinerary based on language for initial render
            itinerary_results = itinerary_results_en if current_language == 'en' and itinerary_results_en else itinerary_results_vi
        else:
            return redirect('home')
    else:
        # If we have itinerary_results directly, use it as VI version
        itinerary_results_vi = itinerary_results
    
    # Enrich itinerary with address and image_code (always check and enrich if missing)
    if itinerary_results:
        # Check if itinerary_results has 'results' key (from community adoption)
        actual_itinerary = itinerary_results.get('results', itinerary_results)
        
        # Determine which address field to use based on current language
        current_language = request.session.get('django_language', 'vi')
        if not current_language or current_language not in ['vi', 'en']:
            current_language = request.COOKIES.get('django_language', 'vi')
        
        address_field = 'address_en' if current_language == 'en' else 'address'
        
        for day_num, stops in list(actual_itinerary.items()):
            for item in stops:
                # Only enrich if image_code is missing
                if 'image_code' not in item or not item['image_code']:
                    try:
                        if item.get('type') == 'POI' and item.get('id') and item['id'] > 0:
                            poi_data = Poi.objects.filter(id=item['id']).values('address', 'address_en', 'image_code').first()
                            if poi_data:
                                # Use language-appropriate address field
                                item['address'] = poi_data.get(address_field) or poi_data['address']
                                item['image_code'] = poi_data['image_code']
                        elif item.get('type') == 'EATERY' and item.get('id') and item['id'] > 0:
                            eatery_data = Eatery.objects.filter(id=item['id']).values('address', 'address_en', 'image_code').first()
                            if eatery_data:
                                # Use language-appropriate address field
                                item['address'] = eatery_data.get(address_field) or eatery_data['address']
                                item['image_code'] = eatery_data['image_code']
                        else:
                            item['address'] = item.get('address')
                    except Exception:
                        item['address'] = item.get('address')
        
        # Save back to session after enrichment
        request.session['itinerary_results'] = itinerary_results
        request.session.modified = True
    
    context = {
        'itinerary_results': itinerary_results,
        'itinerary_results_vi': itinerary_results_vi,
        'itinerary_results_en': itinerary_results_en,
        'user': user,
        'is_adopted': is_adopted,
        'adopted_source': shared_itinerary_source,
    }
    
    return render(request, 'itinerary.html', context)


@require_GET
def search_suggestions_view(request):
    """Return prefix-tree backed suggestions for POIs and eateries."""
    query = (request.GET.get('q') or '').strip()
    limit_raw = request.GET.get('limit')

    try:
        limit = int(limit_raw) if limit_raw else SEARCH_SUGGESTION_LIMIT
    except (TypeError, ValueError):
        limit = SEARCH_SUGGESTION_LIMIT

    limit = max(1, min(limit, 25))

    if not query:
        return JsonResponse({'suggestions': []})

    tree = get_search_tree()
    suggestions = tree.suggest(query, limit=limit)
    payloads = [dict(item) for item in suggestions]
    return JsonResponse({'suggestions': payloads})


@csrf_exempt
def toggle_poi_selection_view(request):
    """Toggle POI selection in session and return updated status."""
    if request.method != 'POST':
        return JsonResponse({'error': 'POST required'}, status=405)
    
    try:
        data = json.loads(request.body)
        poi_id = data.get('poi_id')
        action = data.get('action', 'add')  # 'add' or 'remove'
        
        if poi_id is None:
            return JsonResponse({'error': 'poi_id required'}, status=400)
        
        poi_id = int(poi_id)
        
        # Get current selections from session
        selected_poi_ids = request.session.get('selected_poi_ids', [])
        
        if action == 'add':
            if poi_id not in selected_poi_ids:
                selected_poi_ids.append(poi_id)
        elif action == 'remove':
            if poi_id in selected_poi_ids:
                selected_poi_ids.remove(poi_id)
        
        # Save back to session
        request.session['selected_poi_ids'] = selected_poi_ids
        request.session.modified = True
        
        return JsonResponse({
            'success': True,
            'poi_id': poi_id,
            'action': action,
            'selected_count': len(selected_poi_ids)
        })
    
    except (ValueError, json.JSONDecodeError) as e:
        return JsonResponse({'error': str(e)}, status=400)

from django.utils.decorators import method_decorator
from django_ratelimit.decorators import ratelimit
from django.views.generic import TemplateView
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from . import ai_service

from django.shortcuts import render, redirect
from django.contrib.auth import login, authenticate
# --- CẬP NHẬT IMPORT ---
# Thêm UserCreationForm và login (để tự động đăng nhập sau khi đăng ký)
from django.contrib.auth.forms import AuthenticationForm, UserCreationForm
from django.views import View

# (Class ChatAppView và ChatAPI giữ nguyên như cũ)
class ChatAppView(TemplateView):
    template_name = "index.html"

@method_decorator(csrf_exempt, name='dispatch')
class ChatAPI(APIView):
    permission_classes = []  # Allow access without authentication
    authentication_classes = []  # Disable authentication requirement
    
    def post(self, request, *args, **kwargs):
        user_message = request.data.get('message')
        chat_history = request.data.get('history',[])
        if not user_message:
            return Response(
                {"error": "Không có 'message' nào được cung cấp."},
                status=status.HTTP_400_BAD_REQUEST
            )
    
        bot_response = ai_service.find_best_answer(user_message, chat_history)

        return Response(
            {"response": bot_response},  # Changed from 'answer' to 'response'
            status=status.HTTP_200_OK
        )


@method_decorator(ratelimit(key='ip', rate='5/m', block=True), name='post')
class LoginView(View):
    template_name = "login.html"

    def get(self, request):
        form = AuthenticationForm()
        return render(request, self.template_name, {"form": form})

    def post(self, request):
        form = AuthenticationForm(request, data=request.POST)
        
        if form.is_valid():
            username = form.cleaned_data.get('username')
            password = form.cleaned_data.get('password')
            user = authenticate(username=username, password=password)
            
            if user is not None:
                login(request, user)
                
                if request.POST.get('remember_me'):
                    request.session.set_expiry(1209600) 
                else:
                    request.session.set_expiry(0)
                
                next_page = request.GET.get('next')
                if next_page:
                    return redirect(next_page)
                else:
                    return redirect('chat-page') 
        
        return render(request, self.template_name, {"form": form, "error": "Tên đăng nhập hoặc mật khẩu không đúng."})

class RegisterView(View):
    template_name = "register.html"

    def get(self, request):
        form = UserCreationForm()

        return render(request, self.template_name, {"form": form})

    def post(self, request):
        form = UserCreationForm(request.POST)
        
        if form.is_valid():
            user = form.save()
            login(request, user)
            return redirect('chat-page')
        
        return render(request, self.template_name, {"form": form})


def trip_selection_combined(request):
    """Display the combined destinations and eateries selection page (steps 3 & 4)"""
    user = request.user if request.user.is_authenticated else None
    
    # Get trip setup from session
    trip_setup = request.session.get('trip_setup', {})
    mood = trip_setup.get('mood')
    start_location = trip_setup.get('start_location', {})
    start_lat = start_location.get('lat', DEFAULT_FALLBACK_COORDS[0])
    start_lon = start_location.get('lon', DEFAULT_FALLBACK_COORDS[1])
    
    # Get pre-selected items from adopted itinerary
    selected_poi_ids = request.session.get('selected_poi_ids', [])
    selected_eatery_ids = request.session.get('shared_selected_eatery_ids', [])
    
    # Get mood tags for filtering
    mood_tags = []
    if mood and mood in TRAVEL_MOOD_TAGS:
        mood_tags = [tag.lower() for tag in TRAVEL_MOOD_TAGS[mood]]
    
    # Get sorted POIs (keep all, don't filter yet)
    sorted_pois = get_sorted_pois(start_lat, start_lon)
    
    # Get sorted eateries
    sorted_eateries = get_sorted_eateries(start_lat, start_lon)
    
    # Convert to objects for template (add budget_price, distance_km, and matched_mood flag)
    # Separate mood-matched and remaining POIs, both sorted by distance
    mood_matched_pois = []
    remaining_pois = []
    
    for poi_dict in sorted_pois:
        poi = Poi.objects.get(id=poi_dict['id'])
        poi.budget_price = poi.price_per_person if poi.price_per_person is not None else Decimal('0')
        poi.distance_km = poi_dict.get('distance_km')
        poi.image_code = poi.image_code or (f"P{poi.id:03d}" if poi.id else None)
        poi.image_url = get_item_image_url('poi', poi.image_code or poi.id)
        
        # Mark if this POI matches the mood
        if mood_tags:
            poi_tags = poi_dict.get('tags', '') or ''
            poi_tags_list = [t.strip().lower() for t in poi_tags.split(',') if t.strip()]
            poi.matched_mood = any(poi_tag in mood_tags for poi_tag in poi_tags_list)
        else:
            poi.matched_mood = False
        
        # Separate into matched and remaining lists (both already sorted by distance)
        if poi.matched_mood:
            mood_matched_pois.append(poi)
        else:
            remaining_pois.append(poi)
    
    # Combine: matched first, then remaining (all sorted by distance)
    pois_with_distance = mood_matched_pois + remaining_pois
    
    eateries_with_distance = []
    for eatery_dict in sorted_eateries:
        eatery = Eatery.objects.get(id=eatery_dict['id'])
        eatery.budget_price = eatery_dict.get('budget_price', 0)
        eatery.distance_km = eatery_dict.get('distance_km')
        eatery.image_code = eatery.image_code or (f"E{eatery.id:03d}" if eatery.id else None)
        eatery.image_url = get_item_image_url('eatery', eatery.image_code or eatery.id)
        eateries_with_distance.append(eatery)
    
    # Get form errors from session if any
    form_errors = request.session.pop('form_errors', None)
    
    # Convert lists to JSON for JavaScript
    selected_poi_ids_json = json.dumps(selected_poi_ids)
    selected_eatery_ids_json = json.dumps(selected_eatery_ids)
    
    context = {
        'user': user,
        'pois': pois_with_distance,
        'eateries': eateries_with_distance,
        'mood': mood,
        'mood_tags': TRAVEL_MOOD_TAGS.get(mood, []) if mood else [],
        'form_errors': form_errors,
        'selected_poi_ids_json': selected_poi_ids_json,
        'selected_eatery_ids_json': selected_eatery_ids_json
    }
    return render(request, 'trip_selection_combined.html', context)

def send_sms_fake(phone_number, otp):
    """
    Giả lập gửi SMS. Trong thực tế, bạn sẽ gọi API của nhà mạng tại đây.
    Ví dụ: twilio_client.messages.create(...)
    """
    print(f"\n[MOCK SMS] Gửi tới {phone_number}: Mã OTP của bạn là {otp}\n")
    return True

def forgot_password_view(request):
    if request.method == 'POST':
        form = ForgotPasswordForm(request.POST)
        if form.is_valid():
            contact_info = form.cleaned_data['contact_info']
            user = form.user_cache      # Lấy user đã tìm được ở bước validate form
            contact_type = form.contact_type # 'email' hoặc 'phone'
            
            # 1. Tạo OTP
            otp = str(random.randint(100000, 999999))
            
            # 2. Lưu vào Session
            request.session['reset_otp'] = otp
            request.session['reset_user_id'] = user.id # Lưu ID user thay vì email để an toàn hơn
            request.session['reset_contact_info'] = contact_info # Để hiển thị lại ở bước sau
            
            # 3. Gửi OTP tùy theo loại liên lạc
            if contact_type == 'email':
                subject = 'Mã xác nhận đổi mật khẩu - Journify'
                message = f'Mã xác nhận của bạn là: {otp}'
                send_mail(subject, message, settings.EMAIL_HOST_USER, [user.email], fail_silently=False)
                messages.success(request, f"Mã xác nhận đã gửi tới email {contact_info}")
            
            else: # contact_type == 'phone'
                send_sms_fake(contact_info, otp)
                messages.success(request, f"Mã xác nhận đã gửi tới SĐT {contact_info}")
            
            return redirect('verify_otp')
    else:
        form = ForgotPasswordForm()
    
    return render(request, 'registration/forgot_password.html', {'form': form})

def verify_otp_view(request):
    if 'reset_user_id' not in request.session:
        return redirect('forgot_password')
        
    if request.method == 'POST':
        form = OTPVerificationForm(request.POST)
        if form.is_valid():
            input_otp = form.cleaned_data['otp']
            session_otp = request.session.get('reset_otp')
            
            if input_otp == session_otp:
                request.session['otp_verified'] = True
                return redirect('reset_new_password')
            else:
                messages.error(request, "Mã xác nhận không đúng!")
    else:
        form = OTPVerificationForm()
    
    # Lấy thông tin liên lạc để hiển thị (nếu có)
    contact_display = request.session.get('reset_contact_info', 'bạn')
    return render(request, 'registration/verify_otp.html', {'form': form, 'contact_display': contact_display})

def reset_new_password_view(request):
    if not request.session.get('otp_verified'):
        return redirect('forgot_password')
        
    if request.method == 'POST':
        form = ResetPasswordForm(request.POST)
        if form.is_valid():
            user_id = request.session.get('reset_user_id')
            new_pass = form.cleaned_data['new_password']
            
            try:
                user = User.objects.get(id=user_id)
                user.set_password(new_pass)
                user.save()
                
                # Dọn dẹp session
                keys_to_delete = ['reset_otp', 'reset_user_id', 'reset_contact_info', 'otp_verified']
                for key in keys_to_delete:
                    if key in request.session:
                        del request.session[key]
                
                messages.success(request, "Đổi mật khẩu thành công! Vui lòng đăng nhập lại.")
                return redirect('welcome')
                
            except User.DoesNotExist:
                messages.error(request, "Lỗi hệ thống: User không tồn tại.")
    else:
        form = ResetPasswordForm()
        
    return render(request, 'registration/reset_new_password.html', {'form': form})


@require_GET
def get_replacement_locations(request):
    """
    API endpoint to get nearby locations of the same type for replacement.
    
    Query Parameters:
    - location_type: 'POI' or 'EATERY'
    - location_id: ID of the location to replace
    - radius_km: Search radius in kilometers (default: 2)
    
    Returns:
    JSON response with list of nearby locations of the same type
    """
    from math import radians, cos, sin, asin, sqrt
    
    def haversine(lon1, lat1, lon2, lat2):
        """
        Calculate the great circle distance between two points 
        on the earth (specified in decimal degrees)
        Returns distance in kilometers
        """
        # Convert decimal degrees to radians
        lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])
        
        # Haversine formula
        dlon = lon2 - lon1
        dlat = lat2 - lat1
        a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
        c = 2 * asin(sqrt(a))
        km = 6371 * c  # Radius of earth in kilometers
        return km
    
    try:
        location_type = request.GET.get('location_type', '').upper()
        location_id = request.GET.get('location_id')
        radius_km = float(request.GET.get('radius_km', 2.0))
        
        if not location_type or not location_id:
            return JsonResponse({
                'error': 'Missing required parameters: location_type and location_id'
            }, status=400)
        
        if location_type not in ['POI', 'EATERY']:
            return JsonResponse({
                'error': 'Invalid location_type. Must be POI or EATERY'
            }, status=400)
        
        # Get the original location coordinates
        if location_type == 'POI':
            try:
                original = Poi.objects.get(id=location_id)
                original_lat = float(original.latitude)
                original_lon = float(original.longitude)
                
                # Get all POIs except the current one
                all_locations = Poi.objects.exclude(id=location_id)
            except Poi.DoesNotExist:
                return JsonResponse({'error': 'POI not found'}, status=404)
        else:  # EATERY
            try:
                original = Eatery.objects.get(id=location_id)
                original_lat = float(original.latitude)
                original_lon = float(original.longitude)
                
                # Get all Eateries except the current one
                all_locations = Eatery.objects.exclude(id=location_id)
            except Eatery.DoesNotExist:
                return JsonResponse({'error': 'Eatery not found'}, status=404)
        
        # Filter locations within radius
        nearby_locations = []
        for location in all_locations:
            try:
                loc_lat = float(location.latitude)
                loc_lon = float(location.longitude)
                
                distance = haversine(original_lon, original_lat, loc_lon, loc_lat)
                
                if distance <= radius_km:
                    location_data = {
                        'id': location.id,
                        'name': location.name,
                        'address': location.address,
                        'lat': str(location.latitude),
                        'lon': str(location.longitude),
                        'distance_km': round(distance, 2),
                        'type': location_type,
                        'image_code': location.image_code if hasattr(location, 'image_code') else None
                    }
                    
                    # Add specific fields based on type
                    if location_type == 'POI':
                        location_data['tags'] = location.tags if hasattr(location, 'tags') else ''
                    else:  # EATERY
                        location_data['rating'] = float(location.rating) if location.rating else None
                        location_data['price_min'] = int(location.price_min) if location.price_min else None
                        location_data['price_max'] = int(location.price_max) if location.price_max else None
                    
                    nearby_locations.append(location_data)
            except (ValueError, AttributeError) as e:
                # Skip locations with invalid coordinates
                continue
        
        # Sort by distance
        nearby_locations.sort(key=lambda x: x['distance_km'])
        
        return JsonResponse({
            'success': True,
            'original_location': {
                'id': original.id,
                'name': original.name,
                'type': location_type
            },
            'radius_km': radius_km,
            'count': len(nearby_locations),
            'locations': nearby_locations
        })
        
    except Exception as e:
        return JsonResponse({
            'error': f'Server error: {str(e)}'
        }, status=500)


@require_GET
def get_location_details(request):
    """
    API endpoint to get detailed information about a specific location.
    
    Query Parameters:
    - location_type: 'POI' or 'EATERY'
    - location_id: ID of the location
    
    Returns:
    JSON response with complete location details
    """
    try:
        location_type = request.GET.get('location_type', '').upper()
        location_id = request.GET.get('location_id')
        
        if not location_type or not location_id:
            return JsonResponse({
                'error': 'Missing required parameters: location_type and location_id'
            }, status=400)
        
        if location_type not in ['POI', 'EATERY']:
            return JsonResponse({
                'error': 'Invalid location_type. Must be POI or EATERY'
            }, status=400)
        
        if location_type == 'POI':
            try:
                location = Poi.objects.get(id=location_id)
                location_data = {
                    'id': location.id,
                    'name': location.name,
                    'address': location.address,
                    'lat': str(location.latitude),
                    'lon': str(location.longitude),
                    'type': 'POI',
                    'tags': location.tags if location.tags else '',
                    'rating': float(location.rating) if location.rating else None,
                    'open_hours': location.open_hours if location.open_hours else '',
                }
            except Poi.DoesNotExist:
                return JsonResponse({'error': 'POI not found'}, status=404)
        else:  # EATERY
            try:
                location = Eatery.objects.get(id=location_id)
                location_data = {
                    'id': location.id,
                    'name': location.name,
                    'address': location.address,
                    'lat': str(location.latitude),
                    'lon': str(location.longitude),
                    'type': 'EATERY',
                    'rating': float(location.rating) if location.rating else None,
                    'price_min': int(location.price_min) if location.price_min else None,
                    'price_max': int(location.price_max) if location.price_max else None,
                    'open_hours': location.open_hours if location.open_hours else '',
                    'time_tags': location.time_tags if location.time_tags else '',
                }
            except Eatery.DoesNotExist:
                return JsonResponse({'error': 'Eatery not found'}, status=404)
        
        return JsonResponse({
            'success': True,
            'location': location_data
        })
        
    except Exception as e:
        return JsonResponse({
            'error': f'Server error: {str(e)}'
        }, status=500)

def map_view(request):
    # Bạn có thể lấy dữ liệu các điểm từ database tại đây để truyền sang template
    return render(request, 'map.html')


@require_GET
def get_dalat_route(request):
    """
    API endpoint to get pre-computed route between two Dalat locations.
    
    Query Parameters:
    - origin: Origin location code (e.g., 'E001', 'P001')
    - dest: Destination location code (e.g., 'E002', 'P002')
    
    Returns:
    JSON response compatible with OSRM format:
    {
        'code': 'Ok' or 'NoRoute',
        'routes': [{
            'geometry': {'coordinates': [[lon, lat], ...]},
            'distance': distance_in_meters,
            'duration': duration_in_seconds
        }]
    }
    """
    from .route_service import get_route_data, is_dalat_location
    
    try:
        origin = request.GET.get('origin', '').strip()
        dest = request.GET.get('dest', '').strip()
        
        if not origin or not dest:
            return JsonResponse({
                'code': 'InvalidRequest',
                'message': 'Missing required parameters: origin and dest'
            }, status=400)
        
        # Check if both locations are valid Dalat locations
        if not is_dalat_location(origin):
            return JsonResponse({
                'code': 'NoRoute',
                'message': f'Origin {origin} is not a valid Dalat location'
            })
        
        if not is_dalat_location(dest):
            return JsonResponse({
                'code': 'NoRoute',
                'message': f'Destination {dest} is not a valid Dalat location'
            })
        
        # Get route data from database
        route_data = get_route_data(origin, dest)
        
        if not route_data:
            return JsonResponse({
                'code': 'NoRoute',
                'message': f'No route found between {origin} and {dest}'
            })
        
        # Format response to match OSRM format
        # OSRM uses [lon, lat] order, our coordinates are [lat, lon]
        osrm_coordinates = [[lon, lat] for lat, lon in route_data['coordinates']]
        
        return JsonResponse({
            'code': 'Ok',
            'routes': [{
                'geometry': {
                    'coordinates': osrm_coordinates,
                    'type': 'LineString'
                },
                'distance': route_data['distance'],
                'duration': route_data['duration']
            }],
            'waypoints': [
                {'location': osrm_coordinates[0] if osrm_coordinates else [0, 0]},
                {'location': osrm_coordinates[-1] if osrm_coordinates else [0, 0]}
            ]
        })
        
    except Exception as e:
        return JsonResponse({
            'code': 'Error',
            'message': f'Server error: {str(e)}'
        }, status=500)

# Legacy Firebase/Google OAuth credentials were removed during the Android
# migration. If social login is restored, load new credentials from environment
# variables or a secret manager; never commit them to source control.

# firebase_app = firebase.initialize_app(FIREBASE_CONFIG)
# auth = firebase_app.auth(client_secret = GOOGLE_CLIENT_SECRET)

# def google_login_view(request):
#     """Bước 1: Chuyển hướng sang Google"""
#     try:
#         login_url = auth.authenticate_login_with_google()
#         return redirect(login_url)
#     except Exception as e:
#         print(f"Lỗi khởi tạo Google Login: {e}")
#         messages.error(request, "Lỗi kết nối đăng nhập Google.")
#         return redirect('welcome')

# def google_callback_view(request):
#     """Bước 2: Xử lý callback và đăng nhập"""
#     current_url = request.build_absolute_uri()
    
#     try:
#         # Thư viện sẽ tự parse code từ URL và đổi lấy token
#         user_info = auth.sign_in_with_oauth_credential(current_url)
#         email = user_info.get('email')
        
#         if not email:
#             messages.error(request, "Không lấy được email từ Google.")
#             return redirect('welcome')

#         # Đồng bộ user vào Django DB
#         try:
#             user = User.objects.get(username=email)
#         except User.DoesNotExist:
#             user = User.objects.create_user(username=email, email=email)
#             user.set_unusable_password()
#             if 'displayName' in user_info:
#                 user.first_name = user_info['displayName']
#             user.save()
#             # Tạo profile nếu cần
#             Profile.objects.get_or_create(user=user)

#         # Đăng nhập vào session Django
#         auth_login(request, user, backend='django.contrib.auth.backends.ModelBackend')
        
#         messages.success(request, f"Đăng nhập thành công! Xin chào {user.username}")
#         return redirect('welcome')

#     except Exception as e:
#         print(f"Google Callback Error: {e}")
#         messages.error(request, "Đăng nhập thất bại. Vui lòng kiểm tra lại cấu hình.")
#         return redirect('welcome')
