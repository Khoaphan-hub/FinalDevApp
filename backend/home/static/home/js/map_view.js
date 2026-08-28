// home/static/home/js/map_view.js

document.addEventListener('DOMContentLoaded', function() {
    initMap();
    initWeatherSlideshow(); // Bắt đầu slideshow thời tiết
});

let map;
let routingControl;
let routeLayer = null;
let routeStartMarker = null;
let routeEndMarker = null;
let slideshowInterval; // Biến lưu bộ đếm thời gian
let weatherDataCache = []; // Lưu trữ dữ liệu thời tiết đã tải
let currentSlideIndex = 0;
let isRouteMode = false; // Cờ kiểm tra đang ở chế độ xem lộ trình hay slide
// i18n storage (will be initialized from #map-wrapper dataset)
let i18n = {
    lang: 'vi',
    dict: {
        vi: {
            my_location: 'Vị trí của tôi',
            swap: 'Đổi chỗ',
            location_name: 'Vị trí của tôi',
            geo_error: 'Không thể truy cập vị trí',
            geo_permission_denied: 'Quyền vị trí bị từ chối. Vui lòng bật lại trong cài đặt trình duyệt.'
        },
        en: {
            my_location: 'My location',
            swap: 'Swap',
            location_name: 'My location',
            geo_error: 'Unable to access location',
            geo_permission_denied: 'Location permission denied. Please enable it in your browser settings.'
        }
    }
};

const FAMOUS_CITIES = [
    { name: "Hà Nội", lat: 21.0285, lon: 105.8542 },
    { name: "TP. Hồ Chí Minh", lat: 10.8231, lon: 106.6297 },
    { name: "Đà Nẵng", lat: 16.0544, lon: 108.2022 },
    { name: "Đà Lạt", lat: 11.9404, lon: 108.4583 },
    { name: "Nha Trang", lat: 12.2388, lon: 109.1967 },
    { name: "Sapa", lat: 22.3364, lon: 103.8438 },
    { name: "Hội An", lat: 15.8801, lon: 108.3380 },
    { name: "Phú Quốc", lat: 10.2899, lon: 103.9840 }
];

function initMap() {
    map = L.map('map').setView([16.047079, 108.206230], 6);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors'
    }).addTo(map);

    const findRouteBtn = document.getElementById('btn-find-route');
    if (findRouteBtn) {
        findRouteBtn.addEventListener('click', handleRouteRequest);
    }

    // Thiết lập autocomplete/gợi ý cho 2 input bắt đầu/đích
    const startEl = document.getElementById('start-point');
    const endEl = document.getElementById('end-point');
    if (startEl) setupPlaceAutocomplete(startEl);
    if (endEl) setupPlaceAutocomplete(endEl);

    // Swap and My Location buttons
    const swapBtn = document.getElementById('btn-swap-points');
    const myLocBtn = document.getElementById('btn-my-location');
    if (swapBtn) swapBtn.addEventListener('click', swapPoints);
    if (myLocBtn) myLocBtn.addEventListener('click', () => useMyLocation());

    // Initialize i18n from map-wrapper dataset
    const wrapper = document.getElementById('map-wrapper');
    try {
        if (wrapper) {
            i18n.lang = wrapper.dataset.lang || document.documentElement.lang || 'vi';
            i18n.dict.vi = wrapper.dataset.vi ? JSON.parse(wrapper.dataset.vi) : {};
            i18n.dict.en = wrapper.dataset.en ? JSON.parse(wrapper.dataset.en) : {};
            applyLanguageToUI();
        }
    } catch (e) {
        console.warn('i18n init error', e);
    }
}

// --- LOGIC SLIDESHOW THỜI TIẾT ---

async function initWeatherSlideshow() {
    const weatherWidget = document.getElementById('weather-widget');
    const weatherContent = document.getElementById('weather-content');
    
    // Hiển thị khung widget trước
    weatherWidget.style.display = 'block';
    setTimeout(() => { weatherWidget.style.opacity = '1'; }, 100);
    weatherContent.innerHTML = '<div class="text-center p-2"><span class="spinner" style="display:inline-block"></span> Đang tải dữ liệu...</div>';

    // 1. Tải dữ liệu 1 lần duy nhất cho tất cả thành phố
    try {
        const promises = FAMOUS_CITIES.map(city => fetchWeatherData(city));
        const results = await Promise.all(promises);
        weatherDataCache = results.filter(item => item !== null); // Lọc bỏ cái nào lỗi

        if (weatherDataCache.length > 0) {
            // 2. Bắt đầu hiển thị slide đầu tiên
            renderSlide(0);
            
            // 3. Cài đặt tự động chuyển slide sau 5 giây
            startAutoRotate();

            // 4. Bắt sự kiện click để đổi slide ngay lập tức
            weatherWidget.onclick = function() {
                if (!isRouteMode) {
                    manualNextSlide();
                }
            };
        }
    } catch (e) {
        console.error("Lỗi khởi tạo slide:", e);
    }
}

function startAutoRotate() {
    // Xóa interval cũ nếu có để tránh trùng lặp
    if (slideshowInterval) clearInterval(slideshowInterval);
    
    slideshowInterval = setInterval(() => {
        if (!isRouteMode) {
            currentSlideIndex = (currentSlideIndex + 1) % weatherDataCache.length;
            renderSlide(currentSlideIndex);
        }
    }, 5000); // 5000ms = 5 giây
}

function manualNextSlide() {
    // Reset thời gian đếm ngược (để tránh vừa click xong nó lại tự đổi tiếp)
    startAutoRotate(); 
    
    // Chuyển slide
    currentSlideIndex = (currentSlideIndex + 1) % weatherDataCache.length;
    renderSlide(currentSlideIndex);
}

function renderSlide(index) {
    const data = weatherDataCache[index];
    const container = document.getElementById('weather-content');
    const title = document.getElementById('weather-title');

    if (title) title.innerHTML = '<i class="fas fa-star text-warning"></i> Điểm đến nổi bật';

    // Hiệu ứng Fade out nhẹ
    container.style.opacity = '0';
    
    setTimeout(() => {
        container.innerHTML = generateWeatherHTML(data);
        // Hiệu ứng Fade in
        container.style.opacity = '1';
    }, 300); // Khớp với transition CSS
}

// Hàm lấy dữ liệu thô (không render HTML ngay)
async function fetchWeatherData(cityInfo) {
    try {
        const res = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${cityInfo.lat}&longitude=${cityInfo.lon}&current_weather=true`);
        const data = await res.json();
        if (data.current_weather) {
            return { ...cityInfo, weather: data.current_weather };
        }
        return null;
    } catch (e) {
        return null;
    }
}

// ------------------ PLACE AUTOCOMPLETE / SUGGESTIONS (Việt Nam only) ------------------
function debounce(fn, wait) {
    let t;
    return function(...args) {
        clearTimeout(t);
        t = setTimeout(() => fn.apply(this, args), wait);
    };
}

function setupPlaceAutocomplete(inputEl) {
    // ensure parent is positioned so absolutely-positioned suggestion box can align
    if (getComputedStyle(inputEl.parentElement).position === 'static') {
        inputEl.parentElement.style.position = 'relative';
    }

    const box = createSuggestionBox(inputEl);

    const onInput = debounce(async function() {
        const q = inputEl.value.trim();
        if (!q) { box.innerHTML = ''; box.style.display = 'none'; return; }
        const results = await suggestPlaces(q);
        renderSuggestions(box, results, inputEl);
    }, 300);

    inputEl.addEventListener('input', onInput);
    inputEl.addEventListener('focus', onInput);

    // Hide suggestions when clicking outside
    document.addEventListener('click', (e) => {
        if (!inputEl.contains(e.target) && !box.contains(e.target)) {
            box.style.display = 'none';
        }
    });
}

function createSuggestionBox(inputEl) {
    const box = document.createElement('div');
    box.className = 'place-suggestion-box';
    // basic inline styles so no template change required
    box.style.position = 'absolute';
    box.style.left = '0';
    box.style.top = (inputEl.offsetHeight + 6) + 'px';
    box.style.zIndex = 9999;
    box.style.background = '#fff';
    box.style.border = '1px solid rgba(0,0,0,0.12)';
    box.style.boxShadow = '0 2px 6px rgba(0,0,0,0.12)';
    box.style.width = (inputEl.offsetWidth) + 'px';
    box.style.maxHeight = '220px';
    box.style.overflowY = 'auto';
    box.style.display = 'none';
    box.style.fontSize = '0.95rem';
    box.style.borderRadius = '4px';
    inputEl.parentElement.appendChild(box);
    return box;
}

async function suggestPlaces(query) {
    // Use Nominatim and restrict to Vietnam: countrycodes=vn
    const url = `https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit=6&countrycodes=vn&q=${encodeURIComponent(query)}`;
    try {
        const res = await fetch(url, { headers: { 'Accept-Language': 'vi' } });
        const data = await res.json();
        return data || [];
    } catch (e) {
        console.error('Autocomplete fetch error', e);
        return [];
    }
}

function renderSuggestions(box, items, inputEl) {
    box.innerHTML = '';
    if (!items || items.length === 0) { box.style.display = 'none'; return; }

    items.forEach((it, idx) => {
        const row = document.createElement('div');
        row.className = 'place-suggestion-item';
        row.style.padding = '8px 10px';
        row.style.cursor = 'pointer';
        row.style.borderBottom = '1px solid rgba(0,0,0,0.04)';
        row.innerHTML = `<div style="font-weight:600">${escapeHtml(it.display_name.split(',')[0] || it.display_name)}</div><div style="color:#555;font-size:0.85rem">${escapeHtml(it.display_name)}</div>`;
        row.addEventListener('click', (ev) => {
            ev.preventDefault();
            // Set input value to readable name, store lat/lon for later
            inputEl.value = it.display_name;
            inputEl.dataset.lat = it.lat;
            inputEl.dataset.lon = it.lon;
            box.style.display = 'none';
            // center map slightly on selection
            try { map.flyTo([parseFloat(it.lat), parseFloat(it.lon)], 12, { duration: 0.8 }); } catch (e) {}
        });
        box.appendChild(row);
    });
    box.style.display = 'block';
}

function escapeHtml(s) {
    return (s + '').replace(/[&<>\"']/g, function(c) { return {'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":"&#39;"}[c]; });
}

// End autocomplete

// Hàm sinh HTML
function generateWeatherHTML(data) {
    const temp = Math.round(data.weather.temperature);
    const code = data.weather.weathercode;
    
    // Map icon
    let iconClass = "fa-sun";
    let desc = "Trời quang";
    let color = "#f59e0b"; 

    if (code > 3) { iconClass = "fa-cloud"; desc = "Nhiều mây"; color = "#9ca3af"; }
    if (code > 45) { iconClass = "fa-smog"; desc = "Sương mù"; color = "#6b7280"; }
    if (code > 50) { iconClass = "fa-cloud-rain"; desc = "Mưa nhỏ"; color = "#3b82f6"; }
    if (code > 60) { iconClass = "fa-cloud-showers-heavy"; desc = "Mưa rào"; color = "#2563eb"; }
    if (code > 95) { iconClass = "fa-bolt"; desc = "Dông bão"; color = "#7c3aed"; }

    return `
        <div class="weather-slide">
            <div class="slide-header">
                <div class="w-icon-large">
                    <i class="fas ${iconClass}" style="color: ${color};"></i>
                </div>
                <div class="w-temp-large">${temp}°C</div>
            </div>
            <div class="slide-body">
                <div class="w-loc-large">${data.name}</div>
                <div class="w-desc">${desc}</div>
                <div class="slide-hint">(Ấn để xem tiếp)</div>
            </div>
            <button class="btn-fly-map" onclick="event.stopPropagation(); flyToCity(${data.lat}, ${data.lon})">
                <i class="fas fa-location-arrow"></i> Xem trên bản đồ
            </button>
        </div>
    `;
}

// --- LOGIC KHI NGƯỜI DÙNG TÌM ĐƯỜNG (Dừng Slide) ---
async function handleRouteRequest() {
    const startEl = document.getElementById('start-point');
    const endEl = document.getElementById('end-point');
    const btn = document.getElementById('btn-find-route');
    const spinner = document.getElementById('route-spinner');

    const startInput = startEl ? startEl.value.trim() : '';
    const endInput = endEl ? endEl.value.trim() : '';
    if (!startInput || !endInput) { alert("Thiếu thông tin!"); return; }

    btn.disabled = true;
    spinner.style.display = 'inline-block';

    try {
        // geocodeLocation có thể nhận string hoặc input element (nếu input đã có data-lat/lon thì dùng trực tiếp)
        const [startCoords, endCoords] = await Promise.all([
            geocodeLocation(startEl),
            geocodeLocation(endEl)
        ]);

        if (startCoords && endCoords) {
            drawRoute(startCoords, endCoords);

            // QUAN TRỌNG: Chuyển sang chế độ Route -> Dừng slide
            isRouteMode = true;
            if (slideshowInterval) clearInterval(slideshowInterval);

            // Hiển thị thời tiết điểm đến (sử dụng text hiển thị của end input)
            updateDestinationWeather(endCoords.lat, endCoords.lon, endInput);
        } else {
            alert('Không tìm thấy tọa độ cho điểm đi/đến. Vui lòng chọn gợi ý phù hợp.');
        }
    } catch (e) {
        console.error(e);
        alert('Lỗi khi tìm đường');
    } finally {
        btn.disabled = false;
        spinner.style.display = 'none';
    }
}

async function updateDestinationWeather(lat, lon, name) {
    const title = document.getElementById('weather-title');
    if (title) title.innerHTML = '<i class="fas fa-map-pin text-danger"></i> Thời tiết điểm đến';
    
    const weatherData = await fetchWeatherData({ name: name, lat: lat, lon: lon });
    if (weatherData) {
        const container = document.getElementById('weather-content');
        container.style.opacity = '0';
        setTimeout(() => {
            container.innerHTML = generateWeatherHTML(weatherData);
            // Ẩn nút "Xem trên bản đồ" vì đã ở đó rồi
            const flyBtn = container.querySelector('.btn-fly-map');
            if(flyBtn) flyBtn.style.display = 'none';
            // Ẩn gợi ý
            const hint = container.querySelector('.slide-hint');
            if(hint) hint.style.display = 'none';
            
            container.style.opacity = '1';
        }, 300);
    }
}

// (Giữ nguyên các hàm geocodeLocation, drawRoute, flyToCity cũ)
async function geocodeLocation(queryOrElement) {
    // If an input element is passed and it already has data-lat/lon (user picked a suggestion), use it
    if (queryOrElement && typeof queryOrElement === 'object' && queryOrElement.dataset && queryOrElement.dataset.lat && queryOrElement.dataset.lon) {
        return { lat: parseFloat(queryOrElement.dataset.lat), lon: parseFloat(queryOrElement.dataset.lon) };
    }

    // Otherwise treat it as a string query. Restrict to Vietnam for better accuracy.
    const query = (typeof queryOrElement === 'string') ? queryOrElement : (queryOrElement ? queryOrElement.value : '');
    if (!query) return null;

    const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=vn&q=${encodeURIComponent(query)}`;
    try {
        const response = await fetch(url, { headers: { 'Accept-Language': 'vi' } });
        const data = await response.json();
        return (data && data.length > 0) ? { lat: parseFloat(data[0].lat), lon: parseFloat(data[0].lon) } : null;
    } catch (e) {
        console.error('Geocode error', e);
        return null;
    }
}

async function drawRoute(start, end) {
    // Remove previous route layer and markers
    if (routeLayer) { map.removeLayer(routeLayer); routeLayer = null; }
    if (routeStartMarker) { map.removeLayer(routeStartMarker); routeStartMarker = null; }
    if (routeEndMarker) { map.removeLayer(routeEndMarker); routeEndMarker = null; }

    // Add simple markers
    try {
        routeStartMarker = L.marker([start.lat, start.lon]).addTo(map).bindPopup('Điểm đi');
        routeEndMarker = L.marker([end.lat, end.lon]).addTo(map).bindPopup('Điểm đến');
    } catch (e) {}

    // Query OSRM HTTP API directly (lon,lat order)
    const url = `https://router.project-osrm.org/route/v1/driving/${start.lon},${start.lat};${end.lon},${end.lat}?overview=full&geometries=geojson`;
    try {
        const res = await fetch(url);
        const j = await res.json();
        if (j && j.routes && j.routes.length > 0) {
            const geom = j.routes[0].geometry;
            routeLayer = L.geoJSON(geom, {
                style: { color: '#3b82f6', weight: 6, opacity: 0.85 }
            }).addTo(map);
            // fit to route
            try {
                map.fitBounds(routeLayer.getBounds(), { padding: [50, 50] });
            } catch (e) {}
        } else {
            alert('Không tìm thấy tuyến đường.');
        }
    } catch (e) {
        console.error('Route fetch error', e);
        alert('Lỗi khi lấy tuyến đường. Vui lòng thử lại.');
    }
}

window.flyToCity = function(lat, lon) {
    map.flyTo([lat, lon], 12, { duration: 1.5 });
};

// ------------------ SWAP & MY LOCATION ------------------
function swapPoints() {
    const startEl = document.getElementById('start-point');
    const endEl = document.getElementById('end-point');
    if (!startEl || !endEl) return;

    // swap values
    const sVal = startEl.value;
    const eVal = endEl.value;
    startEl.value = eVal;
    endEl.value = sVal;

    // swap dataset lat/lon (if present)
    const sLat = startEl.dataset.lat;
    const sLon = startEl.dataset.lon;
    const eLat = endEl.dataset.lat;
    const eLon = endEl.dataset.lon;

    if (eLat && eLon) {
        startEl.dataset.lat = eLat;
        startEl.dataset.lon = eLon;
    } else {
        delete startEl.dataset.lat; delete startEl.dataset.lon;
    }

    if (sLat && sLon) {
        endEl.dataset.lat = sLat;
        endEl.dataset.lon = sLon;
    } else {
        delete endEl.dataset.lat; delete endEl.dataset.lon;
    }
}

async function useMyLocation() {
    const btn = document.getElementById('btn-my-location');
    const startEl = document.getElementById('start-point');
    const texts = (i18n.dict && i18n.dict[i18n.lang]) ? i18n.dict[i18n.lang] : {};
    const locName = texts.location_name || texts.my_location || 'Vị trí của tôi';

    if (!('geolocation' in navigator)) {
        alert(texts.geo_error || 'Trình duyệt không hỗ trợ truy cập vị trí.');
        return;
    }
    if (btn) btn.disabled = true;

    // Check permission state when possible
    if (navigator.permissions && navigator.permissions.query) {
        try {
            const p = await navigator.permissions.query({ name: 'geolocation' });
            if (p.state === 'denied') {
                alert(texts.geo_permission_denied || 'Quyền vị trí bị từ chối. Vui lòng bật lại trong cài đặt trình duyệt.');
                if (btn) btn.disabled = false;
                return;
            }
        } catch (e) {
            // ignore
        }
    }

    navigator.geolocation.getCurrentPosition(async (pos) => {
        const lat = pos.coords.latitude;
        const lon = pos.coords.longitude;
        // Try reverse geocode to get a friendly name
        let display = locName;
        try {
            const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=12`, { headers: { 'Accept-Language': i18n.lang } });
            const j = await res.json();
            if (j && j.display_name) display = j.display_name;
        } catch (e) {
            // ignore reverse errors
        }

        if (startEl) {
            startEl.value = display;
            startEl.dataset.lat = lat;
            startEl.dataset.lon = lon;
        }
        try { map.flyTo([lat, lon], 14, { duration: 1.0 }); } catch (e) {}
        if (btn) btn.disabled = false;
    }, (err) => {
        const msg = (err && err.code === 1) ? (texts.geo_permission_denied || 'Quyền vị trí bị từ chối.') : (err.message || texts.geo_error || 'Không thể truy cập vị trí');
        alert(msg);
        if (btn) btn.disabled = false;
    }, { enableHighAccuracy: true, timeout: 10000 });
}

function applyLanguageToUI() {
    const btnSwap = document.getElementById('btn-swap-points');
    const btnMy = document.getElementById('btn-my-location');
    const texts = (i18n.dict && i18n.dict[i18n.lang]) ? i18n.dict[i18n.lang] : {};
    if (btnSwap) btnSwap.innerHTML = `<i class="fas fa-exchange-alt"></i>&nbsp;${texts.swap || 'Đổi chỗ'}`;
    if (btnMy) btnMy.innerHTML = `<i class="fas fa-location-arrow"></i>&nbsp;${texts.my_location || 'Vị trí của tôi'}`;
}

// allow external switch: window.setMapLanguage('en'|'vi')
window.setMapLanguage = function(lang) {
    if (!lang) return;
    const wrapper = document.getElementById('map-wrapper');
    if (wrapper) wrapper.dataset.lang = lang;
    i18n.lang = lang;
    applyLanguageToUI();
};