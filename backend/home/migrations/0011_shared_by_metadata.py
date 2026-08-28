from django.db import migrations


def populate_shared_by(apps, schema_editor):
    SharedItinerary = apps.get_model('home', 'SharedItinerary')

    for itinerary in SharedItinerary.objects.exclude(owner__isnull=True):
        metadata = itinerary.metadata or {}
        if metadata.get('shared_by_user_id') == itinerary.owner_id:
            continue
        metadata['shared_by_user_id'] = itinerary.owner_id
        itinerary.metadata = metadata
        itinerary.save(update_fields=['metadata'])


def noop(apps, schema_editor):
    pass


class Migration(migrations.Migration):

    dependencies = [
        ('home', '0010_feedback_constraints'),
    ]

    operations = [
        migrations.RunPython(populate_shared_by, noop),
    ]
