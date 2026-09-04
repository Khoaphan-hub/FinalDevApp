"""Sharing and rating logic for generated itineraries.

This module defines standalone models, forms, services, and API endpoints
for capturing generated itineraries, collecting feedback, surfacing the top
options per travel mood, and letting newcomers adopt an existing plan back
into the planning wizard.
"""
from __future__ import annotations

from copy import deepcopy
from decimal import Decimal, InvalidOperation
from typing import Any, Dict, Iterable, List, Optional, Tuple

from django import forms
from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractBaseUser
from django.core.exceptions import ValidationError
from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models, transaction
from django.db.models import Case, Count, ExpressionWrapper, F, FloatField, Q, Sum, Value, When
from django.http import HttpRequest
from django.urls import path, reverse
from django.utils import timezone
from django.views.generic import TemplateView
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import ensure_csrf_cookie, csrf_exempt
from django.contrib.auth.decorators import login_required
from rest_framework import status
from rest_framework.authentication import SessionAuthentication
from rest_framework.response import Response
from rest_framework.views import APIView

User = get_user_model()


class SharedItineraryQuerySet(models.QuerySet):
    """Custom helpers for public itinerary discovery."""

    def public(self) -> "SharedItineraryQuerySet":
        return self.filter(is_public=True)

    def for_mood(self, mood: Optional[str]) -> "SharedItineraryQuerySet":
        if not mood:
            return self
        return self.filter(mood__iexact=mood.strip())

    def with_score(self) -> "SharedItineraryQuerySet":
        rating_part = Case(
            When(
                rating_count__gt=0,
                then=ExpressionWrapper(
                    F("rating_sum") * 1.0 / F("rating_count"),
                    output_field=FloatField(),
                ),
            ),
            default=Value(0.0),
            output_field=FloatField(),
        )
        adoption_bonus = ExpressionWrapper(
            F("adopted_count") * Value(0.15),
            output_field=FloatField(),
        )
        return self.annotate(discovery_score=rating_part + adoption_bonus)

    def top_ranked(self, limit: int = 5) -> List["SharedItinerary"]:
        return list(
            self.with_score()
            .order_by("-discovery_score", "-created_at")[: max(1, limit)]
        )


class SharedItinerary(models.Model):
    """Persisted snapshot of a generated itinerary."""

    objects = SharedItineraryQuerySet.as_manager()

    owner = models.ForeignKey(
        User,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="shared_itineraries",
    )
    title = models.CharField(max_length=150)
    mood = models.CharField(max_length=80, db_index=True)
    trip_days = models.PositiveSmallIntegerField()
    budget_amount = models.DecimalField(max_digits=12, decimal_places=2, default=0)
    budget_remaining = models.DecimalField(max_digits=12, decimal_places=2, default=0)
    poi_count = models.PositiveSmallIntegerField(default=0)
    eatery_count = models.PositiveSmallIntegerField(default=0)
    start_address = models.CharField(max_length=255, blank=True)
    start_lat = models.FloatField(null=True, blank=True)
    start_lon = models.FloatField(null=True, blank=True)
    start_fallback_used = models.BooleanField(default=False)
    trip_setup = models.JSONField(default=dict, blank=True)
    planner_step3 = models.JSONField(default=dict, blank=True)
    planner_itinerary = models.JSONField(default=dict, blank=True)
    metadata = models.JSONField(default=dict, blank=True)
    rating_sum = models.PositiveIntegerField(default=0)
    rating_count = models.PositiveIntegerField(default=0)
    adopted_count = models.PositiveIntegerField(default=0)
    is_public = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-created_at"]

    @property
    def average_rating(self) -> Optional[float]:
        if self.rating_count == 0:
            return None
        return round(self.rating_sum / self.rating_count, 2)

    def refresh_feedback_totals(self) -> None:
        aggregates = self.feedback.aggregate(total=Sum("rating"), count=Count("id"))
        self.rating_sum = int(aggregates.get("total") or 0)
        self.rating_count = int(aggregates.get("count") or 0)
        self.save(update_fields=["rating_sum", "rating_count", "updated_at"])

    def register_adoption(self) -> None:
        self.adopted_count = F("adopted_count") + 1
        self.save(update_fields=["adopted_count", "updated_at"])
        self.refresh_from_db(fields=["adopted_count"])

    def as_payload(self, include_feedback: bool = False) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "id": self.pk,
            "title": self.title,
            "mood": self.mood,
            "trip_days": self.trip_days,
            "budget_amount": str(self.budget_amount),
            "budget_remaining": str(self.budget_remaining),
            "poi_count": self.poi_count,
            "eatery_count": self.eatery_count,
            "start_address": self.start_address,
            "start_lat": self.start_lat,
            "start_lon": self.start_lon,
            "start_fallback_used": self.start_fallback_used,
            "average_rating": self.average_rating,
            "rating_count": self.rating_count,
            "adopted_count": self.adopted_count,
            "is_public": self.is_public,
            "created_at": self.created_at.isoformat(),
            "planner_itinerary": self.planner_itinerary,
            "metadata": self.metadata,
        }

        if include_feedback:
            payload["feedback"] = [entry.as_payload() for entry in self.feedback.all()]
        return payload


class ItineraryFeedback(models.Model):
    """User supplied ratings and optional comments."""

    itinerary = models.ForeignKey(
        SharedItinerary,
        related_name="feedback",
        on_delete=models.CASCADE,
    )
    user = models.ForeignKey(
        User,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="itinerary_feedback",
    )
    rating = models.PositiveSmallIntegerField(
        validators=[MinValueValidator(1), MaxValueValidator(5)]
    )
    comment = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["itinerary", "user"],
                condition=Q(user__isnull=False),
                name="unique_feedback_per_user",
            )
        ]

    def as_payload(self) -> Dict[str, Any]:
        return {
            "rating": self.rating,
            "comment": self.comment,
            "created_at": self.created_at.isoformat(),
            "user": self.user.username if self.user else None,
        }


class GeneratedItinerarySubmissionForm(forms.Form):
    """Validates payload when persisting a generated plan."""

    title = forms.CharField(max_length=150)
    share_public = forms.BooleanField(required=False)
    rating = forms.IntegerField(min_value=1, max_value=5, required=False)
    comment = forms.CharField(max_length=1000, required=False)

    def clean(self) -> Dict[str, Any]:
        cleaned = super().clean()
        comment = cleaned.get("comment") or ""
        rating = cleaned.get("rating")
        if comment and not rating:
            raise ValidationError("Rating is required when leaving a comment.")
        return cleaned


class ItineraryFeedbackForm(forms.ModelForm):
    class Meta:
        model = ItineraryFeedback
        fields = ["rating", "comment"]


class SharedItineraryService:
    """High level helpers coordinating storage and discovery."""

    MAX_PRIVATE_PER_USER = 4
    MAX_PUBLIC_PER_USER = 4

    @staticmethod
    def _safe_decimal(value: Any, default: str = "0") -> Decimal:
        raw = value if value not in (None, "") else default
        try:
            return Decimal(str(raw))
        except (InvalidOperation, TypeError):
            return Decimal(default)

    @staticmethod
    def _session_snapshot(request: HttpRequest) -> Tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
        trip_setup = request.session.get("trip_setup") or {}
        planner_step3 = request.session.get("planner_step3") or {}
        planner_itinerary = request.session.get("planner_itinerary") or {}
        return trip_setup, planner_step3, planner_itinerary

    @staticmethod
    def _append_positive_unique_id(target: List[int], candidate: Any) -> None:
        try:
            numeric = int(candidate)
        except (TypeError, ValueError):
            return
        if numeric <= 0 or numeric in target:
            return
        target.append(numeric)

    @staticmethod
    def _stop_token(stop: Dict[str, Any], *, stop_type: str) -> Optional[str]:
        identifier = stop.get("id")
        type_prefix = stop_type.lower() if stop_type else "stop"

        if identifier not in (None, ""):
            token = str(identifier).strip()
            if token:
                return f"{type_prefix}:{token.lower()}"

        name = stop.get("name")
        if isinstance(name, str) and name.strip():
            return f"{type_prefix}:{name.strip().lower()}"

        lat = stop.get("lat")
        lon = stop.get("lon")
        if lat is not None and lon is not None:
            try:
                lat_f = float(lat)
                lon_f = float(lon)
            except (TypeError, ValueError):
                lat_f = lon_f = None
            if lat_f is not None and lon_f is not None:
                return f"{type_prefix}:{lat_f:.4f}:{lon_f:.4f}"

        return None

    @staticmethod
    def _final_selection_signature(final_selection: Optional[Dict[str, Any]]) -> Tuple[Tuple[Any, ...], Tuple[Any, ...]]:
        if not isinstance(final_selection, dict):
            return (), ()

        def _normalize(values: Any) -> Tuple[Any, ...]:
            if not isinstance(values, Iterable):
                return ()
            if isinstance(values, (str, bytes)):
                return ()
            normalized: List[Any] = []
            for item in values:
                candidate = item
                if isinstance(candidate, dict):
                    candidate = candidate.get("token") or candidate.get("id")
                if candidate in (None, ""):
                    continue
                try:
                    numeric = int(candidate)
                except (TypeError, ValueError):
                    normalized_token = str(candidate).strip().lower()
                    if not normalized_token or normalized_token in normalized:
                        continue
                    normalized.append(normalized_token)
                else:
                    if numeric <= 0 or numeric in normalized:
                        continue
                    normalized.append(numeric)
            return tuple(sorted(normalized, key=lambda value: (str(type(value)), value)))

        poi_tokens = final_selection.get("poi_tokens")
        eatery_tokens = final_selection.get("eatery_tokens")

        poi_signature = _normalize(poi_tokens) or _normalize(final_selection.get("poi_ids"))
        eatery_signature = _normalize(eatery_tokens) or _normalize(final_selection.get("eatery_ids"))
        return poi_signature, eatery_signature

    @classmethod
    def _planner_results_fingerprint(cls, planner_itinerary: Optional[Dict[str, Any]]) -> Tuple[str, ...]:
        if not isinstance(planner_itinerary, dict):
            return ()

        results = planner_itinerary.get("results")
        if not isinstance(results, dict):
            return ()

        try:
            ordered_days = sorted(
                results.keys(),
                key=lambda value: (0, int(value)) if str(value).isdigit() else (1, str(value)),
            )
        except Exception:
            ordered_days = list(results.keys())

        sequence: List[str] = []
        for day_key in ordered_days:
            stops = results.get(day_key)
            if not isinstance(stops, list):
                continue
            for index, stop in enumerate(stops):
                if not isinstance(stop, dict):
                    continue
                stop_type = (stop.get("type") or "").upper()
                token = cls._stop_token(stop, stop_type=stop_type) or "unknown"
                sequence.append(f"{day_key}:{index}:{stop_type}:{token}")

        return tuple(sequence)

    @classmethod
    def _itinerary_matches_signature(
        cls,
        itinerary: "SharedItinerary",
        target_selection_signature: Tuple[Tuple[Any, ...], Tuple[Any, ...]],
        target_sequence_signature: Tuple[str, ...],
    ) -> bool:
        candidate_selection: Optional[Dict[str, Any]] = None
        metadata = itinerary.metadata or {}
        if isinstance(metadata, dict):
            candidate_selection = metadata.get("final_selection")

        if not candidate_selection:
            candidate_selection = cls._extract_final_selection(itinerary.planner_itinerary or {})

        candidate_signature = cls._final_selection_signature(candidate_selection)
        if candidate_signature != target_selection_signature:
            return False

        candidate_sequence = cls._planner_results_fingerprint(itinerary.planner_itinerary)
        if not candidate_sequence and isinstance(metadata, dict):
            stored_sequence = metadata.get("results_sequence")
            if isinstance(stored_sequence, (list, tuple)):
                candidate_sequence = tuple(str(item) for item in stored_sequence)

        if target_sequence_signature:
            if candidate_sequence:
                return candidate_sequence == target_sequence_signature
            # Fall back to selection-only comparison when no sequence stored yet
            return True

        return True

    @classmethod
    def _extract_final_selection(cls, planner_itinerary: Dict[str, Any]) -> Dict[str, Any]:
        results = planner_itinerary.get("results") if isinstance(planner_itinerary, dict) else None
        final_poi_ids: List[int] = []
        final_eatery_ids: List[int] = []
        custom_stops: List[Dict[str, Any]] = []

        eatery_slots: Dict[int, str] = {}
        poi_tokens: List[str] = []
        eatery_tokens: List[str] = []

        if isinstance(results, dict):
            day_iterable = results.values()
        else:
            day_iterable = []

        for stops in day_iterable:
            if not isinstance(stops, list):
                continue
            for stop in stops:
                if not isinstance(stop, dict):
                    continue
                stop_type = (stop.get("type") or "").upper()
                stop_id = stop.get("id")
                if stop_type == "POI":
                    cls._append_positive_unique_id(final_poi_ids, stop_id)
                    try:
                        numeric = int(stop_id)
                    except (TypeError, ValueError):
                        custom_stops.append(stop)
                        token = cls._stop_token(stop, stop_type=stop_type)
                        if token and token not in poi_tokens:
                            poi_tokens.append(token)
                        continue
                    if numeric <= 0:
                        custom_stops.append(stop)
                        token = cls._stop_token(stop, stop_type=stop_type)
                        if token and token not in poi_tokens:
                            poi_tokens.append(token)
                        continue
                    token = cls._stop_token(stop, stop_type=stop_type)
                    if token and token not in poi_tokens:
                        poi_tokens.append(token)
                elif stop_type == "EATERY":
                    cls._append_positive_unique_id(final_eatery_ids, stop_id)
                    try:
                        numeric = int(stop_id)
                    except (TypeError, ValueError):
                        custom_stops.append(stop)
                        token = cls._stop_token(stop, stop_type=stop_type)
                        if token and token not in eatery_tokens:
                            eatery_tokens.append(token)
                        continue
                    if numeric <= 0:
                        custom_stops.append(stop)
                        token = cls._stop_token(stop, stop_type=stop_type)
                        if token and token not in eatery_tokens:
                            eatery_tokens.append(token)
                        continue
                    slot_label = stop.get("slot")
                    if isinstance(slot_label, str) and slot_label:
                        eatery_slots[numeric] = slot_label
                    token = cls._stop_token(stop, stop_type=stop_type)
                    if token and token not in eatery_tokens:
                        eatery_tokens.append(token)

        return {
            "poi_ids": final_poi_ids,
            "eatery_ids": final_eatery_ids,
            "custom_stops": custom_stops,
            "eatery_slots": eatery_slots,
            "poi_tokens": sorted(poi_tokens),
            "eatery_tokens": sorted(eatery_tokens),
        }

    @classmethod
    @transaction.atomic
    def store_generated_itinerary(
        cls,
        request: HttpRequest,
        *,
        title: str,
        share_public: bool,
        planner_itinerary_override: Optional[Dict[str, Any]] = None,
    ) -> SharedItinerary:
        trip_setup, planner_step3, planner_itinerary = cls._session_snapshot(request)
        if planner_itinerary_override:
            planner_itinerary = planner_itinerary_override
        if not planner_itinerary or not planner_itinerary.get("results"):
            raise ValidationError("No generated itinerary found in session.")

        stored_trip_setup: Dict[str, Any] = deepcopy(trip_setup) if trip_setup else {}
        stored_planner_step3: Dict[str, Any] = deepcopy(planner_step3) if planner_step3 else {}
        stored_planner_itinerary: Dict[str, Any] = deepcopy(planner_itinerary)

        final_selection = cls._extract_final_selection(stored_planner_itinerary)
        if final_selection.get("poi_ids"):
            stored_planner_step3["selected_poi_ids"] = final_selection["poi_ids"]

        owner = request.user if request.user.is_authenticated else None

        if owner:
            if share_public:
                public_count = SharedItinerary.objects.filter(owner=owner, is_public=True).count()
                if public_count >= cls.MAX_PUBLIC_PER_USER:
                    raise ValidationError(
                        [
                            f"Bạn đã chia sẻ tối đa {cls.MAX_PUBLIC_PER_USER} lịch trình. Hãy xóa bớt trước khi chia sẻ thêm.",
                            f"You have reached the limit of {cls.MAX_PUBLIC_PER_USER} shared itineraries. Please delete one before sharing a new plan.",
                        ]
                    )
            else:
                private_count = SharedItinerary.objects.filter(owner=owner, is_public=False).count()
                if private_count >= cls.MAX_PRIVATE_PER_USER:
                    raise ValidationError(
                        [
                            f"Bạn đã lưu tối đa {cls.MAX_PRIVATE_PER_USER} lịch trình riêng tư. Hãy xóa bớt trước khi lưu thêm.",
                            f"You have reached the limit of {cls.MAX_PRIVATE_PER_USER} saved itineraries. Please delete one before saving a new plan.",
                        ]
                    )
        signature = cls._final_selection_signature(final_selection)
        results_sequence = cls._planner_results_fingerprint(stored_planner_itinerary)
        has_meaningful_selection = bool(signature[0] or signature[1] or results_sequence)

        if has_meaningful_selection and share_public:
            global_candidates = SharedItinerary.objects.filter(is_public=True)

            # Limit comparison set using basic attributes to reduce workload
            trip_days_value = int((trip_setup or {}).get("days") or len(planner_itinerary.get("results", {})))
            if trip_days_value < 1:
                trip_days_value = 1
            global_candidates = global_candidates.filter(trip_days=trip_days_value)

            poi_ids_signature, eatery_ids_signature = signature
            if poi_ids_signature:
                global_candidates = global_candidates.filter(poi_count=len(poi_ids_signature))
            if eatery_ids_signature:
                global_candidates = global_candidates.filter(eatery_count=len(eatery_ids_signature))

            if owner:
                global_candidates = global_candidates.exclude(owner=owner)

            for existing in global_candidates.iterator():
                if cls._itinerary_matches_signature(existing, signature, results_sequence):
                    raise ValidationError(
                        [
                            "Lịch trình này đã được chia sẻ bởi người khác. Hãy tùy chỉnh trước khi chia sẻ lại.",
                            "This itinerary already exists in the community. Please customize it before sharing again.",
                        ]
                    )

        if owner and has_meaningful_selection:
            existing_shares = SharedItinerary.objects.filter(owner=owner, is_public=share_public)
            for shared in existing_shares:
                if cls._itinerary_matches_signature(shared, signature, results_sequence):
                    if share_public:
                        raise ValidationError(
                            [
                                "Bạn đã chia sẻ lịch trình này rồi. Hãy tùy chỉnh trước khi chia sẻ lại.",
                                "You already shared this itinerary. Make some adjustments before sharing again.",
                            ]
                        )
                    raise ValidationError(
                        [
                            "Bạn đã lưu lịch trình này rồi. Hãy tùy chỉnh trước khi lưu lại.",
                            "You already saved this itinerary. Make some adjustments before saving again.",
                        ]
                    )

        metadata_payload = {
            "preferred_poi_tags": stored_planner_step3.get("preferred_poi_tags") or [],
            "custom_pois": stored_planner_step3.get("custom_pois") or [],
            "poi_price_overrides": stored_planner_step3.get("poi_price_overrides") or {},
            "generated_at": timezone.now().isoformat(),
            "final_selection": final_selection,
            "results_sequence": list(results_sequence) if results_sequence else [],
            "shared_by_user_id": owner.id if owner else None,
        }

        auto_fill_snapshot = stored_planner_itinerary.get("auto_fill_snapshot") if isinstance(stored_planner_itinerary, dict) else None
        if auto_fill_snapshot:
            metadata_payload["auto_fill_snapshot"] = auto_fill_snapshot

        mood = (trip_setup.get("mood") or "").strip() or "unspecified"
        start_location = trip_setup.get("start_location") or {}
        counts = planner_itinerary.get("selected_counts") or {}

        itinerary = SharedItinerary.objects.create(
            owner=owner,
            title=title,
            mood=mood,
            trip_days=int(stored_trip_setup.get("days") or len(planner_itinerary.get("results", {})) or 1),
            budget_amount=cls._safe_decimal(stored_trip_setup.get("budget")),
            budget_remaining=cls._safe_decimal(planner_itinerary.get("budget_remaining")),
            poi_count=int(counts.get("pois") or 0),
            eatery_count=int(counts.get("eateries") or 0),
            start_address=start_location.get("address_label", ""),
            start_lat=start_location.get("lat"),
            start_lon=start_location.get("lon"),
            start_fallback_used=bool(start_location.get("fallback_used")),
            trip_setup=stored_trip_setup,
            planner_step3=stored_planner_step3,
            planner_itinerary=stored_planner_itinerary,
            metadata=metadata_payload,
            is_public=share_public,
        )
        return itinerary

    @staticmethod
    @transaction.atomic
    def record_feedback(
        itinerary: SharedItinerary,
        *,
        user: Optional[AbstractBaseUser],
        rating: int,
        comment: str = "",
    ) -> ItineraryFeedback:
        if user and getattr(user, "is_authenticated", False):
            feedback, created = ItineraryFeedback.objects.get_or_create(
                itinerary=itinerary,
                user=user,
                defaults={"rating": rating, "comment": comment},
            )
            if not created:
                feedback.rating = rating
                feedback.comment = comment
                feedback.save(update_fields=["rating", "comment"])
        else:
            feedback = ItineraryFeedback.objects.create(
                itinerary=itinerary,
                user=None,
                rating=rating,
                comment=comment,
            )

        itinerary.refresh_feedback_totals()
        return feedback

    @staticmethod
    def top_itineraries(mood: Optional[str], limit: int = 5) -> List["SharedItinerary"]:
        return SharedItinerary.objects.public().for_mood(mood).top_ranked(limit)

    @staticmethod
    def serialize_collection(
        itineraries: Iterable[SharedItinerary],
        *,
        include_feedback: bool = False,
    ) -> List[Dict[str, Any]]:
        return [itinerary.as_payload(include_feedback=include_feedback) for itinerary in itineraries]

    @staticmethod
    def prepare_session_for_adoption(
        request: HttpRequest,
        itinerary: SharedItinerary,
        *,
        planner_itinerary_override: Optional[Dict[str, Any]] = None,
        final_selection_override: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        stored_trip_setup = deepcopy(itinerary.trip_setup or {})
        stored_planner_step3 = deepcopy(itinerary.planner_step3 or {})
        stored_planner_itinerary = deepcopy(
            planner_itinerary_override if planner_itinerary_override is not None else itinerary.planner_itinerary or {}
        )

        metadata = itinerary.metadata or {}
        final_selection = final_selection_override or metadata.get("final_selection") or {}
        if not final_selection.get("poi_ids") and stored_planner_itinerary:
            final_selection = SharedItineraryService._extract_final_selection(stored_planner_itinerary)

        final_poi_ids = list(final_selection.get("poi_ids") or stored_planner_step3.get("selected_poi_ids") or [])
        stored_planner_step3["selected_poi_ids"] = final_poi_ids

        request.session["trip_setup"] = stored_trip_setup
        request.session["planner_step3"] = stored_planner_step3
        request.session["planner_itinerary"] = stored_planner_itinerary
        request.session["itinerary_results"] = stored_planner_itinerary
        request.session["selected_poi_ids"] = final_poi_ids
        request.session["shared_itinerary_source"] = {
            "id": itinerary.pk,
            "title": itinerary.title,
            "mood": itinerary.mood,
        }

        final_eatery_ids = list(final_selection.get("eatery_ids") or [])
        request.session["shared_selected_eatery_ids"] = final_eatery_ids

        eatery_slot_map = final_selection.get("eatery_slots") or {}
        normalized_map: Dict[int, str] = {}
        for raw_id, slot in eatery_slot_map.items():
            try:
                numeric_id = int(raw_id)
            except (TypeError, ValueError):
                continue
            if numeric_id <= 0:
                continue
            if not isinstance(slot, str) or not slot:
                continue
            normalized_map[numeric_id] = slot
        if normalized_map:
            request.session["shared_eatery_slot_map"] = normalized_map
        else:
            request.session.pop("shared_eatery_slot_map", None)

        auto_fill_snapshot = metadata.get("auto_fill_snapshot") or stored_planner_itinerary.get("auto_fill_snapshot")
        if auto_fill_snapshot:
            request.session["shared_auto_fill_snapshot"] = auto_fill_snapshot
        else:
            request.session.pop("shared_auto_fill_snapshot", None)

        request.session.modified = True

        return {
            "poi_ids": final_poi_ids,
            "eatery_ids": final_eatery_ids,
            "eatery_slots": normalized_map,
            "custom_stops": final_selection.get("custom_stops") or [],
            "poi_tokens": final_selection.get("poi_tokens") or [],
            "eatery_tokens": final_selection.get("eatery_tokens") or [],
        }


class SharedItineraryTopAPI(APIView):
    """Return the top itineraries for a mood."""

    permission_classes = []
    authentication_classes = [SessionAuthentication]

    def get(self, request: HttpRequest) -> Response:
        mood = request.query_params.get("mood")
        try:
            limit = int(request.query_params.get("limit", 5))
        except (TypeError, ValueError):
            limit = 5

        itineraries = SharedItineraryService.top_itineraries(mood, limit)
        data = SharedItineraryService.serialize_collection(itineraries, include_feedback=False)
        return Response({"itineraries": data})


from rest_framework.authentication import SessionAuthentication

class CsrfExemptSessionAuthentication(SessionAuthentication):
    def enforce_csrf(self, request):
        return  # Bypass CSRF for mobile clients

@method_decorator(csrf_exempt, name='dispatch')
class GeneratedItinerarySubmissionAPI(APIView):
    """Persist the itinerary stored in session and optional feedback."""

    permission_classes = []
    authentication_classes = [CsrfExemptSessionAuthentication]

    form_class = GeneratedItinerarySubmissionForm

    def post(self, request: HttpRequest) -> Response:
        # Require authentication to submit/share an itinerary
        if not request.user.is_authenticated:
            return Response(
                {"error": "Bạn phải đăng nhập để chia sẻ lịch trình / You must be logged in to share an itinerary"},
                status=status.HTTP_401_UNAUTHORIZED
            )
        
        form = self.form_class(data=request.data)
        if not form.is_valid():
            return Response({"errors": form.errors}, status=status.HTTP_400_BAD_REQUEST)

        try:
            planner_itinerary_override = request.data.get("planner_itinerary")
            if isinstance(planner_itinerary_override, str):
                import json
                try:
                    planner_itinerary_override = json.loads(planner_itinerary_override)
                except json.JSONDecodeError:
                    pass
            itinerary = SharedItineraryService.store_generated_itinerary(
                request,
                title=form.cleaned_data["title"],
                share_public=form.cleaned_data.get("share_public", False),
                planner_itinerary_override=planner_itinerary_override,
            )
        except ValidationError as exc:
            return Response(
                {"errors": exc.messages},
                status=status.HTTP_400_BAD_REQUEST,
            )

        rating = form.cleaned_data.get("rating")
        comment = form.cleaned_data.get("comment", "")
        if rating:
            SharedItineraryService.record_feedback(
                itinerary,
                user=request.user if request.user.is_authenticated else None,
                rating=rating,
                comment=comment,
            )

        payload = itinerary.as_payload(include_feedback=True)
        return Response(payload, status=status.HTTP_201_CREATED)


class SharedItineraryDetailAPI(APIView):
    """Expose a single itinerary and its feedback."""

    permission_classes = []
    authentication_classes = [CsrfExemptSessionAuthentication]

    def get(self, request: HttpRequest, itinerary_id: int) -> Response:
        itinerary = SharedItinerary.objects.filter(pk=itinerary_id, is_public=True).first()
        if not itinerary:
            return Response({"error": "Itinerary not found."}, status=status.HTTP_404_NOT_FOUND)
        return Response(itinerary.as_payload(include_feedback=True))

    def delete(self, request: HttpRequest, itinerary_id: int) -> Response:
        itinerary = SharedItinerary.objects.filter(pk=itinerary_id).first()
        if not itinerary:
            return Response({"error": "Itinerary not found."}, status=status.HTTP_404_NOT_FOUND)

        user = request.user
        if not user.is_authenticated:
            return Response({"error": "Authentication required to delete."}, status=status.HTTP_403_FORBIDDEN)

        if itinerary.owner_id not in {user.id} and not user.is_staff:
            return Response({"error": "You do not have permission to delete this itinerary."}, status=status.HTTP_403_FORBIDDEN)

        itinerary.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)


@method_decorator(csrf_exempt, name='dispatch')
class SharedItineraryFeedbackAPI(APIView):
    """Create or update feedback for a public itinerary."""

    permission_classes = []
    authentication_classes = [CsrfExemptSessionAuthentication]

    form_class = ItineraryFeedbackForm

    def post(self, request: HttpRequest, itinerary_id: int) -> Response:
        itinerary = SharedItinerary.objects.filter(pk=itinerary_id, is_public=True).first()
        if not itinerary:
            return Response({"error": "Itinerary not found."}, status=status.HTTP_404_NOT_FOUND)

        form = self.form_class(data=request.data)
        if not form.is_valid():
            return Response({"errors": form.errors}, status=status.HTTP_400_BAD_REQUEST)

        feedback = SharedItineraryService.record_feedback(
            itinerary,
            user=request.user if request.user.is_authenticated else None,
            rating=form.cleaned_data["rating"],
            comment=form.cleaned_data.get("comment", ""),
        )
        return Response(
            {
                "average_rating": itinerary.average_rating,
                "rating_count": itinerary.rating_count,
                "entry": feedback.as_payload(),
            }
        )


@method_decorator(csrf_exempt, name='dispatch')
class AdoptSharedItineraryAPI(APIView):
    """Copy a shared itinerary back into the planner session."""

    permission_classes = []
    authentication_classes = [CsrfExemptSessionAuthentication]

    def post(self, request: HttpRequest, itinerary_id: int) -> Response:
        # Require authentication to adopt an itinerary
        if not request.user.is_authenticated:
            return Response(
                {"error": "Bạn phải đăng nhập để sử dụng lịch trình / You must be logged in to use an itinerary"},
                status=status.HTTP_401_UNAUTHORIZED
            )
        
        from .models import Poi, Eatery
        
        itinerary = SharedItinerary.objects.filter(pk=itinerary_id, is_public=True).first()
        if not itinerary:
            return Response({"error": "Itinerary not found."}, status=status.HTTP_404_NOT_FOUND)

        # Enrich the planner_itinerary BEFORE saving to session
        planner_itinerary_data = deepcopy(itinerary.planner_itinerary or {})
        
        if planner_itinerary_data:
            # Enrich with image_code and address from database
            actual_itinerary = planner_itinerary_data.get('results', planner_itinerary_data)
            
            for day_num, stops in list(actual_itinerary.items()):
                for item in stops:
                    # Only enrich if image_code is missing
                    if 'image_code' not in item or not item['image_code']:
                        try:
                            if item.get('type') == 'POI' and item.get('id') and item['id'] > 0:
                                poi_data = Poi.objects.filter(id=item['id']).values('address', 'image_code').first()
                                if poi_data:
                                    item['address'] = poi_data['address']
                                    item['image_code'] = poi_data['image_code']
                            elif item.get('type') == 'EATERY' and item.get('id') and item['id'] > 0:
                                eatery_data = Eatery.objects.filter(id=item['id']).values('address', 'image_code').first()
                                if eatery_data:
                                    item['address'] = eatery_data['address']
                                    item['image_code'] = eatery_data['image_code']
                            else:
                                item['address'] = item.get('address')
                        except Exception:
                            item['address'] = item.get('address')
        
        selection_override = None
        if planner_itinerary_data:
            selection_override = SharedItineraryService._extract_final_selection(planner_itinerary_data)

        _session_selection = SharedItineraryService.prepare_session_for_adoption(
            request,
            itinerary,
            planner_itinerary_override=planner_itinerary_data,
            final_selection_override=selection_override,
        )
        itinerary.register_adoption()
        
        try:
            redirect_url = reverse("trip_selection_combined")
        except Exception:
            redirect_url = "/trip-selection/"

        return Response({
            "redirect_url": redirect_url,
            "itinerary": itinerary.as_payload(include_feedback=False),
        })


@method_decorator(login_required, name="dispatch")
@method_decorator(ensure_csrf_cookie, name="dispatch")
class SharedItineraryGalleryView(TemplateView):
    """Render a curated list of shared itineraries."""

    template_name = "community_itineraries.html"

    def get_limit(self) -> int:
        try:
            limit = int(self.request.GET.get("limit", 12))
        except (TypeError, ValueError):
            limit = 12
        return max(3, min(limit, 48))

    def get_selected_mood(self) -> Optional[str]:
        mood = (self.request.GET.get("mood") or "").strip()
        return mood or None

    def get_queryset(self, mood: Optional[str], limit: int) -> List[SharedItinerary]:
        base_qs = (
            SharedItinerary.objects.public()
            .for_mood(mood)
            .with_score()
            .select_related("owner")
            .prefetch_related("feedback__user")
            .order_by("-discovery_score", "-created_at")
        )
        return list(base_qs[:limit])

    def get_context_data(self, **kwargs: Any) -> Dict[str, Any]:
        context = super().get_context_data(**kwargs)
        selected_mood = self.get_selected_mood()
        limit = self.get_limit()

        itineraries = self.get_queryset(selected_mood, limit)
        all_moods = (
            SharedItinerary.objects.public()
            .order_by("mood")
            .values_list("mood", flat=True)
            .distinct()
        )

        context.update(
            {
                "itineraries": itineraries,
                "selected_mood": selected_mood,
                "available_moods": [mood for mood in all_moods if mood],
                "total_public_itineraries": SharedItinerary.objects.public().count(),
                "page_limit": limit,
            }
        )
        return context


shared_itinerary_urlpatterns = [
    path(
        "community/itineraries/",
        SharedItineraryGalleryView.as_view(),
        name="shared-itineraries-gallery",
    ),
    path("api/shared-itineraries/", SharedItineraryTopAPI.as_view(), name="shared-itineraries-top"),
    path(
        "api/shared-itineraries/submit/",
        GeneratedItinerarySubmissionAPI.as_view(),
        name="shared-itineraries-submit",
    ),
    path(
        "api/shared-itineraries/<int:itinerary_id>/",
        SharedItineraryDetailAPI.as_view(),
        name="shared-itineraries-detail",
    ),
    path(
        "api/shared-itineraries/<int:itinerary_id>/feedback/",
        SharedItineraryFeedbackAPI.as_view(),
        name="shared-itineraries-feedback",
    ),
    path(
        "api/shared-itineraries/<int:itinerary_id>/adopt/",
        AdoptSharedItineraryAPI.as_view(),
        name="shared-itineraries-adopt",
    ),
]
