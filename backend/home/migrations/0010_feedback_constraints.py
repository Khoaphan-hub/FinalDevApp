from django.db import migrations, models
from django.db.models import Count, Q, Sum


def recompute_feedback_totals(apps, schema_editor):
    SharedItinerary = apps.get_model('home', 'SharedItinerary')
    ItineraryFeedback = apps.get_model('home', 'ItineraryFeedback')

    for itinerary in SharedItinerary.objects.all():
        aggregates = ItineraryFeedback.objects.filter(itinerary=itinerary).aggregate(
            total=Sum('rating'),
            count=Count('id'),
        )
        itinerary.rating_sum = int(aggregates.get('total') or 0)
        itinerary.rating_count = int(aggregates.get('count') or 0)
        itinerary.save(update_fields=['rating_sum', 'rating_count'])


def noop(apps, schema_editor):
    # No reverse operation required; keep existing aggregates as-is.
    pass


class Migration(migrations.Migration):

    dependencies = [
        ('home', '0009_shareditinerary_itineraryfeedback'),
    ]

    operations = [
        migrations.AlterUniqueTogether(
            name='itineraryfeedback',
            unique_together=set(),
        ),
        migrations.AddConstraint(
            model_name='itineraryfeedback',
            constraint=models.UniqueConstraint(
                condition=Q(('user__isnull', False)),
                fields=('itinerary', 'user'),
                name='unique_feedback_per_user',
            ),
        ),
        migrations.RunPython(recompute_feedback_totals, noop),
    ]
