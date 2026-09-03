#!/usr/bin/env bash
# Exit on error
set -o errexit

pip install --upgrade pip

# Install CPU-only torch to keep the build light and within memory limits
pip install torch --index-url https://download.pytorch.org/whl/cpu

pip install -r requirements.txt

python manage.py collectstatic --no-input
python manage.py migrate
