// Service Worker for Journify PWA
const CACHE_NAME = 'journify-v1';
const urlsToCache = [
  '/',
  '/static/home/js/trip_planner.js',
  '/static/home/js/shared_itineraries_ui.js',
  '/static/home/js/chatbot.js',
  '/static/home/js/currency_inputs.js',
  '/static/home/manifest.json'
];

// Install event - cache resources
self.addEventListener('install', event => {
  console.log('Service Worker installing.');
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        return cache.addAll(urlsToCache);
      })
      .catch(error => {
        console.error('Cache addAll failed:', error);
      })
  );
  self.skipWaiting();
});

// Activate event - clean up old caches
self.addEventListener('activate', event => {
  console.log('Service Worker activating.');
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(cacheName => {
          if (cacheName !== CACHE_NAME) {
            console.log('Deleting old cache:', cacheName);
            return caches.delete(cacheName);
          }
        })
      );
    })
  );
  self.clients.claim();
});

// Fetch event - serve from cache when offline
self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request)
      .then(response => {
        // Return cached version or fetch from network
        return response || fetch(event.request);
      })
      .catch(error => {
        console.error('Fetch failed:', error);
        // Return a basic offline page or error
        return new Response('Offline content not available', {
          status: 503,
          statusText: 'Service Unavailable'
        });
      })
  );
});