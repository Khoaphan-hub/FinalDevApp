"""
Shrinks the served place photos so lists stop waiting on multi-hundred-kilobyte downloads.

The catalog ships full-resolution originals: 242 files, 87 MB, averaging 370 KB and peaking at
2.9 MB. A list thumbnail is 104dp, about 293 pixels on a 450 dpi phone, so nearly all of that
is transferred only to be thrown away. Measured over LAN the originals cost roughly 320 ms per
image; at 720px they are about five times smaller.

Filenames keep the .png extension on purpose. The mobile API, the legacy web templates and
home/utils.py all build paths as "<code>.png", and the files were already a mix of PNG, JPEG
and WebP under that name before this command existed. Rewriting the extension would mean
touching every one of those call sites for no functional gain, since both Android's
BitmapFactory and browsers detect the real format from the file contents.

Originals are copied to home/static_originals/ first, which is git-ignored and outside the
served static tree. Re-running always reads from that copy, so repeated runs cannot compress
an already-compressed image again and again.

    python manage.py optimize_place_images
    python manage.py optimize_place_images --max-edge 480 --quality 80
"""

import os
import shutil

from django.core.management.base import BaseCommand

try:
    from PIL import Image
except ImportError:  # pragma: no cover - Pillow is in requirements-mobile.txt
    Image = None

FOLDERS = ('pois', 'eateries')


class Command(BaseCommand):
    help = 'Resizes the served POI/eatery photos, keeping the originals in static_originals/'

    def add_arguments(self, parser):
        parser.add_argument('--max-edge', type=int, default=720,
                            help='Longest edge in pixels (default 720).')
        parser.add_argument('--quality', type=int, default=82,
                            help='WebP quality, 1-100 (default 82).')
        parser.add_argument('--dry-run', action='store_true',
                            help='Report the sizes without writing anything.')

    def handle(self, *args, **options):
        if Image is None:
            self.stderr.write('Pillow is not installed; run pip install -r requirements-mobile.txt')
            return

        max_edge = options['max_edge']
        quality = options['quality']
        dry_run = options['dry_run']

        app_directory = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        served_root = os.path.join(app_directory, 'static', 'home', 'images')
        backup_root = os.path.join(app_directory, 'static_originals', 'images')

        before = after = 0
        processed = skipped = 0

        for folder in FOLDERS:
            served_directory = os.path.join(served_root, folder)
            backup_directory = os.path.join(backup_root, folder)
            if not os.path.isdir(served_directory):
                continue
            os.makedirs(backup_directory, exist_ok=True)

            for name in sorted(os.listdir(served_directory)):
                served_path = os.path.join(served_directory, name)
                backup_path = os.path.join(backup_directory, name)
                if not os.path.isfile(served_path):
                    continue

                # First run copies the original aside; later runs always read that copy, so
                # quality never degrades across repeated runs.
                if not os.path.exists(backup_path):
                    shutil.copy2(served_path, backup_path)

                source_size = os.path.getsize(backup_path)
                before += source_size

                try:
                    with Image.open(backup_path) as image:
                        # Flattening to RGB drops alpha and palette quirks that WebP encodes
                        # poorly; these are all photographs, so transparency is not in play.
                        converted = image.convert('RGB')
                        converted.thumbnail((max_edge, max_edge), Image.LANCZOS)
                        if dry_run:
                            import io
                            buffer = io.BytesIO()
                            converted.save(buffer, 'WEBP', quality=quality)
                            after += buffer.tell()
                        else:
                            converted.save(served_path, 'WEBP', quality=quality)
                            after += os.path.getsize(served_path)
                    processed += 1
                except Exception as error:
                    # A file that cannot be read stays exactly as it was.
                    self.stderr.write('Skipped %s: %s' % (name, error))
                    skipped += 1

        verb = 'Would rewrite' if dry_run else 'Rewrote'
        self.stdout.write('%s %d images (%d skipped) at max edge %dpx, quality %d' % (
            verb, processed, skipped, max_edge, quality))
        self.stdout.write('  before: %.1f MB (%.0f KB each)' % (
            before / 1048576, before / max(processed, 1) / 1024))
        self.stdout.write('  after:  %.1f MB (%.0f KB each)  -> %.1fx smaller' % (
            after / 1048576, after / max(processed, 1) / 1024, before / max(after, 1)))
        self.stdout.write('  originals kept in %s' % backup_root)
