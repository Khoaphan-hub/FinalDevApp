"""
Script to keep the server awake by pinging it every 10 minutes.
Default target: https://journify-backend-hiky.onrender.com/
"""

import sys
import time
import urllib.request
import urllib.error
from datetime import datetime

# Default configuration
DEFAULT_URL = "https://journify-backend-hiky.onrender.com/"
DEFAULT_INTERVAL_MINUTES = 10


def ping_server(url: str) -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "JournifyKeepAlive/1.0"}
    )
    start_time = time.time()
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            duration = (time.time() - start_time) * 1000
            print(f"[{timestamp}] PING SUCCESS -> Status: {response.status} ({duration:.0f}ms)")
    except urllib.error.HTTPError as e:
        duration = (time.time() - start_time) * 1000
        print(f"[{timestamp}] PING RESPONSE -> HTTP {e.code}: {e.reason} ({duration:.0f}ms)")
    except urllib.error.URLError as e:
        print(f"[{timestamp}] PING FAILED -> Connection error: {e.reason}")
    except Exception as e:
        print(f"[{timestamp}] PING FAILED -> Error: {e}")


def main():
    url = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_URL
    interval_minutes = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_INTERVAL_MINUTES
    interval_seconds = interval_minutes * 60

    print("=" * 60)
    print("🚀 Keep-Alive Service Started")
    print(f"🌐 Target URL: {url}")
    print(f"⏱️  Interval:   Every {interval_minutes} minutes ({interval_seconds}s)")
    print("Press Ctrl+C to stop.")
    print("=" * 60)

    try:
        while True:
            ping_server(url)
            print(f"Sleeping for {interval_minutes} minutes...")
            time.sleep(interval_seconds)
    except KeyboardInterrupt:
        print("\n🛑 Keep-Alive Service stopped by user.")


if __name__ == "__main__":
    main()
