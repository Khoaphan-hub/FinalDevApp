// Helper function to format numbers as VND
function formatVND(value) {
    if (value === null || value === undefined || value === '') {
        return '';
    }
    const num = Math.round(parseFloat(value));
    if (isNaN(num)) {
        return value;
    }
    return num.toLocaleString('vi-VN').replace(/,/g, '.') + 'VND';
}

function setupBudgetTracker() {
    const budgetInput = document.getElementById("budget");
    const remainingEl = document.getElementById("budget-remaining");
    const warningEl = document.getElementById("budget-warning");
    const selectedTotalEl = document.getElementById("budget-selected-total");

    const checkboxSelector = 'input[type="checkbox"][name="selected_pois"], input[type="checkbox"][name="selected_eateries"]';
    const priceInputSelector = '.custom-price';

    const getSelectableCheckboxes = () => document.querySelectorAll(checkboxSelector);
    const getCustomPriceInputs = () => document.querySelectorAll(priceInputSelector);

    const initialCommitted = (() => {
        const source = budgetInput?.dataset.initialCommitted ?? selectedTotalEl?.dataset.initialCommitted ?? '0';
        const parsed = parseFloat(source);
        return Number.isNaN(parsed) ? 0 : parsed;
    })();

    const parseCurrencyValue = (rawValue) => {
        if (window.currencyInputUtils && typeof window.currencyInputUtils.cleanCurrencyValue === 'function') {
            const cleaned = window.currencyInputUtils.cleanCurrencyValue(rawValue);
            if (!cleaned) {
                return NaN;
            }
            const parsed = parseFloat(cleaned);
            return Number.isNaN(parsed) ? NaN : parsed;
        }
        if (typeof rawValue === 'string') {
            return parseFloat(rawValue.replace(/,/g, ''));
        }
        return parseFloat(rawValue);
    };

    const calculateSelectedTotal = () => {
        let total = initialCommitted;

        getSelectableCheckboxes().forEach((checkbox) => {
            if (checkbox.checked) {
                const price = parseFloat(checkbox.dataset.price ?? '0');
                if (!Number.isNaN(price)) {
                    total += price;
                }
            }
        });

        getCustomPriceInputs().forEach((input) => {
            const value = parseCurrencyValue(input.value);
            if (!Number.isNaN(value)) {
                total += value;
            }
        });

        return total;
    };

    const update = () => {
        const selectedTotal = calculateSelectedTotal();

        if (selectedTotalEl) {
            selectedTotalEl.textContent = formatVND(selectedTotal);
        }

        if (!budgetInput) {
            if (remainingEl) {
                remainingEl.textContent = formatVND(selectedTotal);
            }
            if (warningEl) {
                warningEl.style.display = 'none';
            }
            return;
        }

        const budgetValue = parseFloat(budgetInput.value);
        const usableBudget = Number.isNaN(budgetValue) ? 0 : budgetValue;
        const remaining = usableBudget - selectedTotal;

        if (remainingEl) {
            remainingEl.textContent = formatVND(remaining);
        }

        if (warningEl) {
            if (remaining < 0) {
                warningEl.style.display = 'block';
                warningEl.style.color = 'red';
            } else {
                warningEl.style.display = 'none';
            }
        }
    };

    const registerCheckboxes = () => {
        getSelectableCheckboxes().forEach((checkbox) => {
            if (!checkbox.dataset.budgetListenerAttached) {
                checkbox.addEventListener('change', update);
                checkbox.dataset.budgetListenerAttached = '1';
            }
        });
    };

    const registerCustomPrices = () => {
        getCustomPriceInputs().forEach((input) => {
            if (!input.dataset.budgetListenerAttached) {
                input.addEventListener('input', update);
                input.dataset.budgetListenerAttached = '1';
            }
        });
    };

    budgetInput?.addEventListener('input', update);
    registerCheckboxes();
    registerCustomPrices();
    update();

    return {
        update,
        registerCheckboxes,
        registerCustomPrices,
    };
}

function parsePoiItemTags(rawValue) {
    if (!rawValue) {
        return [];
    }
    return rawValue
        .split(/[\/,]/)
        .map((token) => token.trim().toLowerCase())
        .filter(Boolean);
}

function collectSelectedPoiTags() {
    return Array.from(document.querySelectorAll('input[name="preferred_poi_tags"]:checked'))
        .map((input) => {
            const value = input.value.trim().toLowerCase();
            const label = input.closest('label')?.querySelector('span')?.textContent?.trim() || input.value;
            return value ? { value, label } : null;
        })
        .filter(Boolean);
}

function applyPoiTagFilter() {
    const items = document.querySelectorAll('.poi-item');
    if (!items.length) {
        return;
    }

    const selectedTagInfo = collectSelectedPoiTags();
    const selectedTags = selectedTagInfo.map((tag) => tag.value);
    const tagCount = selectedTags.length;
    const messageEl = document.getElementById('poi-tag-filter-message');

    let matchingNonSelected = 0;

    items.forEach((item) => {
        const checkbox = item.querySelector('input[name="selected_pois"]');
        const isSelected = checkbox ? checkbox.checked : false;
        const itemTags = parsePoiItemTags(item.dataset.tags);
        const matches = tagCount === 0 || itemTags.some((tag) => selectedTags.includes(tag));

        const shouldDisplay = matches || isSelected;
        item.style.display = shouldDisplay ? '' : 'none';

        if (!isSelected && matches) {
            matchingNonSelected += 1;
        }
    });

    if (!messageEl) {
        return;
    }

    if (tagCount === 0) {
        messageEl.style.display = 'none';
        messageEl.textContent = '';
        return;
    }

    if (matchingNonSelected === 0) {
        messageEl.style.display = 'block';
        messageEl.style.color = '#b91c1c';
        const labelText = selectedTagInfo.map((tag) => tag.label).filter(Boolean).join(', ');
        const labelPhrase = labelText ? `(${labelText})` : '';
        messageEl.textContent = `No POIs match your selected mood tags ${labelPhrase}. Adjust or clear them to see more options.`;
    } else {
        messageEl.style.display = 'none';
        messageEl.textContent = '';
    }
}

function registerPoiTagFilterListeners() {
    document.querySelectorAll('input[name="preferred_poi_tags"]').forEach((input) => {
        if (input.dataset.poiTagListenerAttached) {
            return;
        }
        input.addEventListener('change', () => {
            applyPoiTagFilter();
        });
        input.dataset.poiTagListenerAttached = '1';
    });
}

document.addEventListener("DOMContentLoaded", () => {
    const budgetTracker = setupBudgetTracker();
    window.budgetTracker = budgetTracker;

    // Function to sync selected POIs to hidden inputs
    const syncSelectedPois = () => {
        const hiddenContainer = document.getElementById('hidden-selected-pois');
        if (!hiddenContainer) return;

        // Get all checked POI checkboxes
        const checkedBoxes = document.querySelectorAll('input[name="selected_pois"]:checked');
        const selectedIds = Array.from(checkedBoxes).map(cb => cb.value);

        // Clear existing hidden inputs
        hiddenContainer.innerHTML = '';

        // Add hidden inputs for each selected POI
        selectedIds.forEach(id => {
            const hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = 'selected_pois';
            hidden.value = id;
            hidden.className = 'hidden-poi-selection';
            hiddenContainer.appendChild(hidden);
        });
    };

    const syncSelectedEateries = () => {
        const hiddenContainer = document.getElementById('hidden-selected-eateries');
        if (!hiddenContainer) {
            return;
        }

        const checkedBoxes = document.querySelectorAll('input[name="selected_eateries"]:checked');
        hiddenContainer.innerHTML = '';

        checkedBoxes.forEach((checkbox) => {
            const slot = checkbox.dataset.slot || '';
            if (!slot) {
                return;
            }

            const hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = 'selected_eateries_with_slot';
            hidden.value = `${checkbox.value}|${slot}`;
            hidden.className = 'hidden-eatery-selection';
            hiddenContainer.appendChild(hidden);
        });
    };
    window.syncSelectedEateries = syncSelectedEateries;

    // Function to update POI selection in session via API
    const updatePoiSelectionInSession = (poiId, isChecked) => {
        fetch('/api/toggle-poi-selection/', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                poi_id: poiId,
                action: isChecked ? 'add' : 'remove'
            })
        })
        .then(response => response.json())
        .then(data => {
            if (!data.success) {
                console.error('Error updating POI selection:', data.error);
            }
        })
        .catch(error => {
            console.error('Error updating POI selection:', error);
        });
    };

    // Listen for checkbox changes and sync
    document.addEventListener('change', (e) => {
        if (e.target.matches('input[name="selected_pois"]')) {
            syncSelectedPois();
            // Also update session via API
            const poiId = parseInt(e.target.value);
            const isChecked = e.target.checked;
            updatePoiSelectionInSession(poiId, isChecked);

            // Recalculate budgets after POI selection changes
            window.budgetTracker?.registerCheckboxes();
            window.budgetTracker?.update();
            
            // Update visual styling
            const poiItem = e.target.closest('.poi-item');
            if (poiItem) {
                if (isChecked) {
                    poiItem.classList.add('selected');
                } else {
                    poiItem.classList.remove('selected');
                }
            }

            applyPoiTagFilter();
        }
        
        // Handle eatery checkbox changes
        if (e.target.matches('input[name="selected_eateries"]')) {
            const eateryItem = e.target.closest('.eatery-item');
            if (eateryItem) {
                if (e.target.checked) {
                    eateryItem.classList.add('selected');
                } else {
                    eateryItem.classList.remove('selected');
                }
            }
            syncSelectedEateries();
            window.budgetTracker?.registerCheckboxes();
            window.budgetTracker?.update();
        }
    });

    // Initial sync
    syncSelectedPois();
    syncSelectedEateries();
    registerPoiTagFilterListeners();
    applyPoiTagFilter();

    const cloneRow = (containerSelector, rowClass) => {
        const container = document.querySelector(containerSelector);
                syncSelectedEateries();
        const templateRow = container?.querySelector(rowClass);
        if (!container || !templateRow) {
            return;
        }
        const clone = templateRow.cloneNode(true);
        clone.querySelectorAll('input').forEach((input) => {
            input.value = '';
        });
        if (window.currencyInputUtils && typeof window.currencyInputUtils.initCurrencyInputs === 'function') {
            window.currencyInputUtils.initCurrencyInputs(clone);
        }
        container.appendChild(clone);
        budgetTracker?.registerCustomPrices();
        budgetTracker?.update();
    };

    document.getElementById("add-poi")?.addEventListener("click", () => {
        cloneRow('#custom-pois', '.poi-row');
    });

    document.getElementById("add-eatery")?.addEventListener("click", () => {
        cloneRow('#custom-eateries', '.eatery-row');
    });

    const placeNameInput = document.getElementById("place-name-input");
    const addressInput = document.getElementById("address-input");
    const sortButton = document.getElementById("address-sort-btn");
    const loadingMessage = document.getElementById("address-loading-msg");
    const addressSuccessMsg = document.getElementById("address-success-msg");
    const addressErrorMsg = document.getElementById("address-error-msg");

    // Disable one input when the other is filled
    placeNameInput?.addEventListener("input", () => {
        if (placeNameInput.value.trim() !== "") {
            addressInput.disabled = true;
            addressInput.style.backgroundColor = "#f3f4f6";
        } else {
            addressInput.disabled = false;
            addressInput.style.backgroundColor = "";
        }
    });

    addressInput?.addEventListener("input", () => {
        if (addressInput.value.trim() !== "") {
            placeNameInput.disabled = true;
            placeNameInput.style.backgroundColor = "#f3f4f6";
        } else {
            placeNameInput.disabled = false;
            placeNameInput.style.backgroundColor = "";
        }
    });

    const setAddressStatus = (type, message) => {
        if (addressSuccessMsg) {
            addressSuccessMsg.style.display = type === 'success' ? 'block' : 'none';
            if (type === 'success') {
                addressSuccessMsg.textContent = message;
            }
        }
        if (addressErrorMsg) {
            addressErrorMsg.style.display = type === 'error' ? 'block' : 'none';
            if (type === 'error') {
                addressErrorMsg.textContent = message;
            }
        }
        if (type === 'idle') {
            if (addressSuccessMsg) addressSuccessMsg.style.display = 'none';
            if (addressErrorMsg) addressErrorMsg.style.display = 'none';
        }
    };

    sortButton?.addEventListener("click", () => {
        const placeName = placeNameInput?.value.trim() ?? '';
        const address = addressInput?.value.trim() ?? '';

        if (placeName === "" && address === "") {
            alert("Please enter either a place name or an address.");
            return;
        }
        
        if (placeName !== "" && address !== "") {
            alert("Please fill in only one field: either place name OR address, not both.");
            return;
        }

        if (loadingMessage) {
            loadingMessage.style.display = "block";
        }
        if (sortButton) {
            sortButton.disabled = true;
            sortButton.textContent = "Loading...";
        }

        // Build URL with both parameters
        const params = new URLSearchParams();
        if (placeName) params.append('place_name', placeName);
        if (address) params.append('address', address);
        
        const url = `/api/geocode-and-sort/?${params.toString()}`;
        setAddressStatus('idle');

        fetch(url)
            .then(response => {
                if (!response.ok) {
                    return response.json().then(data => {
                        const message = data && data.error ? data.error : `Server error: ${response.status}`;
                        throw new Error(message);
                    }).catch(() => {
                        throw new Error(`Server error: ${response.status}`);
                    });
                }
                return response.json();
            })
            .then(data => {
                if (loadingMessage) {
                    loadingMessage.style.display = "none";
                }
                if (sortButton) {
                    sortButton.disabled = false;
                    sortButton.textContent = "Sort by Location";
                }

                if (data.error) {
                    setAddressStatus('error', data.error);
                    return;
                }

                renderPoiList(data.pois);
                renderEateryLists(data.eateries);

                let message;
                if (data.start && typeof data.start.lat === 'number' && typeof data.start.lon === 'number') {
                    message = `Sorted by ${data.address} (${data.start.lat.toFixed(4)}, ${data.start.lon.toFixed(4)})`;
                } else {
                    message = `Sorted by ${data.address}`;
                }

                if (data.fallback_used) {
                    message += ' — using default center (Đà Lạt Market) because the address could not be located.';
                }
                setAddressStatus('success', message);
            })
            .catch(error => {
                console.error("Fetch error:", error);
                if (loadingMessage) {
                    loadingMessage.style.display = "none";
                }
                if (sortButton) {
                    sortButton.disabled = false;
                    sortButton.textContent = "Sort by Address";
                }
                setAddressStatus('error', error.message || 'An error occurred. Please try again.');
            });
    });

    const searchInput = document.getElementById("search-input");
    const searchButton = document.getElementById("search-button");
    const searchResults = document.getElementById("search-results");
    let searchTimer = null;

    const renderSearchResults = (items) => {
        if (!searchResults) {
            return;
        }

        searchResults.innerHTML = '';

        if (!items || items.length === 0) {
            searchResults.innerHTML = '<p class="search-empty">No matches found.</p>';
            return;
        }

        const list = document.createElement('ul');
        list.className = 'search-results-list';

        items.forEach((item) => {
            const listItem = document.createElement('li');
            listItem.className = 'search-result-item';

            const typeLabel = item.type === 'POI' ? 'POI' : 'Eatery';
            const price = typeof item.price === 'number' && !Number.isNaN(item.price)
                ? formatVND(item.price)
                : null;

            const metaParts = [];
            if (item.address) {
                metaParts.push(`<small>${item.address}</small>`);
            }
            if (price) {
                metaParts.push(`<small>Price: ${price}</small>`);
            }
            if (item.time_tags) {
                metaParts.push(`<small>Time tags: ${item.time_tags}</small>`);
            }

            listItem.innerHTML = `
                <div class="search-result-text">
                    <strong>${item.name}</strong> <span class="search-type">(${typeLabel})</span>
                    ${metaParts.length ? `<br>${metaParts.join('<br>')}` : ''}
                </div>
            `;

            const addButton = document.createElement('button');
            addButton.type = 'button';
            addButton.textContent = 'Add';
            addButton.className = 'search-add-button';
            addButton.addEventListener('click', () => {
                const selector = item.type === 'POI' ? 'input[name="selected_pois"]' : 'input[name="selected_eateries"]';
                const inputs = document.querySelectorAll(`${selector}[value="${item.id}"]`);

                if (item.type === 'POI') {
                    // For POIs, use the API to add to session
                    addButton.disabled = true;
                    addButton.textContent = 'Adding...';
                    
                    fetch('/api/toggle-poi-selection/', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify({
                            poi_id: item.id,
                            action: 'add'
                        })
                    })
                    .then(response => response.json())
                    .then(data => {
                        if (data.success) {
                            // Find and check the checkbox if it exists
                            const checkbox = document.querySelector(`input[name="selected_pois"][value="${item.id}"]`);
                            if (checkbox) {
                                checkbox.checked = true;
                                
                                // Move the POI to the top by scrolling to it
                                const poiItem = checkbox.closest('.poi-item');
                                if (poiItem) {
                                    poiItem.classList.add('selected');
                                    poiItem.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                                }
                                
                                // Update budget tracker
                                window.budgetTracker?.registerCheckboxes();
                                window.budgetTracker?.update();
                                syncSelectedPois();
                                
                                // Show success feedback
                                addButton.textContent = 'Added ✓';
                                setTimeout(() => {
                                    addButton.textContent = 'Add';
                                    addButton.disabled = false;
                                }, 2000);
                            } else {
                                // POI not in current view, reload to show it
                                window.location.href = '/';
                            }
                        } else {
                            alert('Error adding POI: ' + (data.error || 'Unknown error'));
                            addButton.disabled = false;
                            addButton.textContent = 'Add';
                        }
                    })
                    .catch(error => {
                        console.error('Error adding POI:', error);
                        alert('Error adding POI. Please try again.');
                        addButton.disabled = false;
                        addButton.textContent = 'Add';
                    });
                } else {
                    // For eateries, check the checkbox if visible
                    if (!inputs.length) {
                        alert('This eatery is not currently visible in the form.');
                        return;
                    }

                    inputs.forEach((checkbox) => {
                        checkbox.checked = true;
                        
                        // Add visual feedback
                        const eateryItem = checkbox.closest('.eatery-item');
                        if (eateryItem) {
                            eateryItem.classList.add('selected');
                            eateryItem.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                        }
                    });

                    window.budgetTracker?.registerCheckboxes();
                    window.budgetTracker?.update();
                    
                    // Show success feedback
                    addButton.textContent = 'Added ✓';
                    addButton.disabled = true;
                    setTimeout(() => {
                        addButton.textContent = 'Add';
                        addButton.disabled = false;
                    }, 2000);
                }
            });

            listItem.appendChild(addButton);
            list.appendChild(listItem);
        });

        searchResults.appendChild(list);
    };

    const performSearch = () => {
        if (!searchInput) {
            return;
        }
        const query = searchInput.value.trim();
        if (query.length < 2) {
            renderSearchResults([]);
            return;
        }

        if (searchResults) {
            searchResults.innerHTML = '<p class="search-loading">Searching...</p>';
        }

        fetch(`/api/search-suggestions/?q=${encodeURIComponent(query)}`)
            .then((response) => {
                if (!response.ok) {
                    throw new Error(`Server error: ${response.status}`);
                }
                return response.json();
            })
            .then((data) => {
                renderSearchResults(data.suggestions || []);
            })
            .catch((error) => {
                console.error('Search error:', error);
                if (searchResults) {
                    searchResults.innerHTML = '<p class="search-error">Unable to fetch suggestions.</p>';
                }
            });
    };

    const scheduleSearch = () => {
        if (!searchInput) {
            return;
        }

        clearTimeout(searchTimer);

        const query = searchInput.value.trim();
        if (query.length < 2) {
            renderSearchResults([]);
            return;
        }

        searchTimer = setTimeout(performSearch, 250);
    };

    searchButton?.addEventListener('click', performSearch);
    searchInput?.addEventListener('input', scheduleSearch);
    searchInput?.addEventListener('keypress', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            performSearch();
        }
    });
});

function renderPoiList(pois) {
    const container = document.getElementById("poi-list-container");
    if (!container) {
        return;
    }

    container.innerHTML = "";

    if (!Array.isArray(pois) || pois.length === 0) {
        container.innerHTML = "<p>No destinations found.</p>";
        window.budgetTracker?.registerCheckboxes();
        window.budgetTracker?.update();
        return;
    }

    // Get currently selected POI IDs from session/checkboxes
    const selectedIds = new Set();
    document.querySelectorAll('input[name="selected_pois"]:checked').forEach(cb => {
        selectedIds.add(parseInt(cb.value));
    });

    // Separate selected and unselected POIs
    const selectedPois = pois.filter(poi => selectedIds.has(poi.id));
    const unselectedPois = pois.filter(poi => !selectedIds.has(poi.id));
    
    // Combine: selected first
    const orderedPois = [...selectedPois, ...unselectedPois];

    const fragment = document.createDocumentFragment();

    orderedPois.forEach((poi) => {
        const wrapper = document.createElement('div');
        wrapper.className = 'poi-item';
        const isSelected = selectedIds.has(poi.id);

        if (isSelected) {
            wrapper.classList.add('selected');
        }

        wrapper.dataset.tags = poi.tags || '';

        let priceValue = 0;
        if (poi.price_per_person !== undefined && poi.price_per_person !== null) {
            const parsedPrice = parseFloat(poi.price_per_person);
            if (!Number.isNaN(parsedPrice)) {
                priceValue = parsedPrice;
            }
        }

        let distanceText = '';
        if (poi.distance_km !== undefined && poi.distance_km !== null) {
            const parsedDistance = parseFloat(poi.distance_km);
            if (!Number.isNaN(parsedDistance)) {
                distanceText = `<span style="color: blue; font-weight: bold;">(${parsedDistance.toFixed(2)} km away)</span>`;
            }
        }

        const selectedLabel = isSelected ? ' <span style="color: #007bff; font-weight: bold;">(Selected)</span>' : '';

        wrapper.innerHTML = `
            <input type="checkbox" name="selected_pois" value="${poi.id}" id="poi_${poi.id}" data-price="${priceValue}"${isSelected ? ' checked' : ''}>
            <label for="poi_${poi.id}">
                <strong>${poi.name}</strong>${selectedLabel} ${distanceText}
                ${priceValue > 0 ? `<br><small>Giá dự kiến: ${formatVND(priceValue)}</small>` : ''}
                ${poi.tags ? `<br><small>Tags: ${poi.tags}</small>` : ''}
                ${poi.address ? `<br><small>${poi.address}</small>` : ''}
                ${poi.open_hours ? `<br><small>Giờ mở cửa: ${poi.open_hours}</small>` : ''}
                ${poi.rating ? `<br><small>Đánh giá: ${poi.rating}</small>` : ''}
                ${poi.tiktok_link ? `<br><small><a href="${poi.tiktok_link}" target="_blank">Xem TikTok</a></small>` : ''}
            </label>
        `;

        fragment.appendChild(wrapper);
    });

    container.appendChild(fragment);
    window.budgetTracker?.registerCheckboxes();
    window.budgetTracker?.update();
    if (window.syncSelectedEateries) {
        window.syncSelectedEateries();
    }
    registerPoiTagFilterListeners();
    applyPoiTagFilter();
}

function normalizeEaterySlots(timeTags) {
    if (!timeTags) {
        return [];
    }
    const normalized = [];
    const tokens = timeTags.split(/[\/,]/).map((token) => token.trim().toLowerCase()).filter(Boolean);
    tokens.forEach((token) => {
        if (['sáng', 'sang', 'morning', 'breakfast'].includes(token)) {
            normalized.push('morning');
        } else if (['trưa', 'trua', 'afternoon', 'lunch'].includes(token)) {
            normalized.push('afternoon');
        } else if (['tối', 'toi', 'evening', 'dinner', 'night'].includes(token)) {
            normalized.push('evening');
        }
    });
    return Array.from(new Set(normalized));
}

function renderEateryLists(eateries) {
    if (!Array.isArray(eateries)) {
        return;
    }

    const sections = {
        morning: {
            container: document.getElementById('morning-eatery-list'),
            emptyText: 'No morning eateries.'
        },
        afternoon: {
            container: document.getElementById('afternoon-eatery-list'),
            emptyText: 'No afternoon eateries.'
        },
        evening: {
            container: document.getElementById('evening-eatery-list'),
            emptyText: 'No evening eateries.'
        }
    };

    const selectedIds = new Set();
    document.querySelectorAll('input[name="selected_eateries"]:checked').forEach((cb) => {
        selectedIds.add(parseInt(cb.value, 10));
    });

    const buckets = { morning: [], afternoon: [], evening: [] };

    eateries.forEach((eatery) => {
        const slots = normalizeEaterySlots(eatery.time_tags);
        if (slots.length === 0) {
            buckets.afternoon.push(eatery);
            return;
        }
        slots.forEach((slot) => {
            if (buckets[slot]) {
                buckets[slot].push(eatery);
            }
        });
    });

    Object.entries(sections).forEach(([slot, meta]) => {
        const container = meta.container;
        if (!container) {
            return;
        }

        container.innerHTML = '';
        const entries = buckets[slot];

        if (!entries || entries.length === 0) {
            const empty = document.createElement('p');
            empty.className = 'eatery-empty';
            empty.textContent = meta.emptyText;
            container.appendChild(empty);
            return;
        }

        entries.forEach((eatery) => {
            const wrapper = document.createElement('div');
            wrapper.className = 'eatery-item';

            const isSelected = selectedIds.has(eatery.id);
            if (isSelected) {
                wrapper.classList.add('selected');
            }

            const checkboxId = `eatery_${slot}_${eatery.id}`;
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.name = 'selected_eateries';
            checkbox.value = eatery.id;
            checkbox.id = checkboxId;
            checkbox.dataset.price = eatery.budget_price ?? 0;
            checkbox.dataset.slot = slot;
            if (isSelected) {
                checkbox.checked = true;
            }

            const label = document.createElement('label');
            label.setAttribute('for', checkboxId);

            const lines = [];
            lines.push(`<strong>${eatery.name}</strong>`);

            if (typeof eatery.distance_km === 'number') {
                lines.push(`<small style="color: blue">${eatery.distance_km.toFixed(2)} km away</small>`);
            }

            if (eatery.address) {
                lines.push(`<small>Địa chỉ: ${eatery.address}</small>`);
            }

            lines.push(`<small>Giờ mở cửa: ${eatery.open_hours || '-'}</small>`);

            const priceMin = eatery.price_min;
            const priceMax = eatery.price_max;
            let priceText = '-';
            if (priceMin && priceMax) {
                priceText = `${formatVND(priceMin)} - ${formatVND(priceMax)}`;
            } else if (priceMin) {
                priceText = formatVND(priceMin);
            } else if (priceMax) {
                priceText = formatVND(priceMax);
            }
            lines.push(`<small>Giá: ${priceText}</small>`);

            const budgetPrice = parseFloat(eatery.budget_price);
            if (!Number.isNaN(budgetPrice) && budgetPrice > 0) {
                lines.push(`<small>Dùng cho ngân sách: ${formatVND(budgetPrice)}</small>`);
            }

            if (eatery.tiktok_link) {
                lines.push(`<small><a href="${eatery.tiktok_link}" target="_blank">Xem TikTok</a></small>`);
            }

            label.innerHTML = lines.join('<br>');

            wrapper.appendChild(checkbox);
            wrapper.appendChild(label);
            container.appendChild(wrapper);
        });
    });

    window.budgetTracker?.registerCheckboxes();
    window.budgetTracker?.update();
}
