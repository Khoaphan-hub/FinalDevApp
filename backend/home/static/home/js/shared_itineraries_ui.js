(function() {
    const byId = (id) => document.getElementById(id);
    
    // Helper for bilingual messages
    const getCurrentLanguage = () => localStorage.getItem('language') || 'vi';
    
    const getMsg = (key) => {
        const messages = {
            viewComments: { vi: 'Xem bình luận', en: 'View comments' },
            hideComments: { vi: 'Ẩn bình luận', en: 'Hide comments' },
            loadingComments: { vi: 'Đang tải bình luận...', en: 'Loading comments...' },
            noComments: { vi: 'Chưa có bình luận. Hãy là người đầu tiên chia sẻ!', en: 'No comments yet. Be the first to share your thoughts!' },
            noItineraries: { vi: 'Chưa có lịch trình nào. Hãy là người đầu tiên đóng góp!', en: 'No shared itineraries yet. Be the first to contribute!' },
            useItinerary: { vi: 'Sử dụng lịch trình này', en: 'Use this itinerary' },
            loading: { vi: 'Đang tải...', en: 'Loading...' },
            cannotSubmit: { vi: 'Không thể gửi đánh giá lúc này.', en: 'Cannot submit rating at this time.' }
        };
        const lang = getCurrentLanguage();
        return messages[key] ? messages[key][lang] : '';
    };

    const formatCurrency = (value) => {
        if (value === null || value === undefined || value === '') {
            return 'n/a';
        }
        const numeric = Number(value);
        if (Number.isNaN(numeric)) {
            return value;
        }
        return `${numeric.toLocaleString('vi-VN')} ₫`;
    };

    const getCsrfToken = () => {
        if (typeof getCookie === 'function') {
            return getCookie('csrftoken');
        }
        const name = 'csrftoken=';
        const cookies = document.cookie ? document.cookie.split(';') : [];
        for (let i = 0; i < cookies.length; i += 1) {
            const cookie = cookies[i].trim();
            if (cookie.startsWith(name)) {
                return decodeURIComponent(cookie.substring(name.length));
            }
        }
        return '';
    };

    const renderTopItineraries = (items, moodLabelEl, listEl) => {
        listEl.innerHTML = '';
        if (!items || !items.length) {
            const empty = document.createElement('div');
            empty.className = 'top-itinerary-empty';
            empty.textContent = getMsg('noItineraries');
            listEl.appendChild(empty);
            return;
        }

        items.forEach((item) => {
            const card = document.createElement('div');
            card.className = 'top-itinerary-card';

            const title = document.createElement('h4');
            title.textContent = item.title || 'Shared itinerary';
            card.appendChild(title);

            const meta = document.createElement('div');
            meta.className = 'meta';

            const averageRating =
                typeof item.average_rating === 'number'
                    ? `${item.average_rating.toFixed(1)} / 5`
                    : 'Not rated yet';

            const budgetDisplay = formatCurrency(item.budget_amount);

            const lines = [
                `🗓️ ${item.trip_days} day(s) • ${item.poi_count} POIs • ${item.eatery_count} eateries`,
                `💰 Trip budget: ${budgetDisplay}`,
                `⭐ ${averageRating} (${item.rating_count} review${item.rating_count === 1 ? '' : 's'})`,
            ];

            meta.innerHTML = lines.join('<br>');
            card.appendChild(meta);

            const primaryAction = document.createElement('button');
            primaryAction.type = 'button';
            primaryAction.textContent = getMsg('useItinerary');
            primaryAction.addEventListener('click', () => adoptItinerary(item.id, primaryAction));
            card.appendChild(primaryAction);

            listEl.appendChild(card);
        });

        if (moodLabelEl && items.length && items[0].mood) {
            moodLabelEl.textContent = items[0].mood;
        }
    };

    const adoptItinerary = async (itineraryId, button) => {
        const originalLabel = button.textContent;
        button.disabled = true;
        button.textContent = getMsg('loading');

        try {
            const response = await fetch(`/api/shared-itineraries/${itineraryId}/adopt/`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRFToken': getCsrfToken(),
                },
            });

            const payload = await response.json();
            if (!response.ok) {
                throw new Error(payload.error || 'Unable to load itinerary.');
            }

            if (payload.redirect_url) {
                window.location.href = payload.redirect_url;
                return;
            }

            button.textContent = 'Ready to customize';
            button.disabled = false;
        } catch (error) {
            console.error('Adoption error:', error);
            alert(error.message || 'Unable to use this itinerary. Please try again later.');
            button.disabled = false;
            button.textContent = originalLabel;
        }
    };

    const getItineraryIdFromElement = (el, attrName = 'itineraryId') => {
        if (!el) {
            return '';
        }
        if (el.dataset && el.dataset[attrName]) {
            return el.dataset[attrName];
        }
        const attrKey = `data-${attrName.replace(/[A-Z]/g, (match) => `-${match.toLowerCase()}`)}`;
        return el.getAttribute(attrKey) || '';
    };

    const submitFeedback = async (form) => {
        const itineraryId = getItineraryIdFromElement(form, 'itineraryId');
        const ratingField = form.querySelector('select[name="rating"]');
        const commentField = form.querySelector('textarea[name="comment"]');
        const statusEl = form.querySelector('[data-feedback-status]');
        const submitButton = form.querySelector('.feedback-submit');

        if (!itineraryId || !ratingField || !submitButton) {
            return;
        }

        const rating = Number(ratingField.value);
        const comment = commentField ? commentField.value.trim() : '';

        if (!rating || Number.isNaN(rating)) {
            if (statusEl) {
                statusEl.textContent = 'Chọn số sao trước khi gửi đánh giá.';
                statusEl.style.color = '#b91c1c';
            }
            return;
        }

        if (statusEl) {
            statusEl.textContent = 'Đang gửi đánh giá…';
            statusEl.style.color = '#2563eb';
        }
        submitButton.disabled = true;

        try {
            const response = await fetch(`/api/shared-itineraries/${itineraryId}/feedback/`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRFToken': getCsrfToken(),
                },
                body: JSON.stringify({
                    rating,
                    comment,
                }),
            });

            const payload = await response.json();
            if (!response.ok) {
                const message = payload && payload.errors
                    ? Object.values(payload.errors).flat().join(' ')
                    : payload.error || 'Không thể gửi đánh giá lúc này.';
                throw new Error(message);
            }

            const card = form.closest('[data-itinerary-card]');
            if (card) {
                const detail = await refreshCommunityItineraryCard(card, itineraryId);
                if (detail && statusEl) {
                    statusEl.textContent = 'Cảm ơn bạn đã phản hồi!';
                    statusEl.style.color = '#15803d';
                }
            }

            if (commentField) {
                commentField.value = '';
            }
            ratingField.value = '';
        } catch (error) {
            console.error('Submit feedback error:', error);
            if (statusEl) {
                statusEl.textContent = error.message || getMsg('cannotSubmit');
                statusEl.style.color = '#b91c1c';
            }
        } finally {
            submitButton.disabled = false;
        }
    };

    const updateCommentToggleLabel = (card, count) => {
        const toggleBtn = card.querySelector('[data-toggle-feedback]');
        if (!toggleBtn) {
            return;
        }
        const lang = getCurrentLanguage();
        const viewText = getMsg('viewComments');
        const baseLabel = count > 0 ? `${viewText} (${count})` : viewText;
        const panel = card.querySelector('[data-feedback-panel]');
        if (panel && panel.classList.contains('active')) {
            toggleBtn.textContent = getMsg('hideComments');
        } else {
            toggleBtn.textContent = baseLabel;
        }
        toggleBtn.dataset.collapsedLabel = baseLabel;
    };

    const renderFeedbackList = (listEl, feedback) => {
        if (!listEl) {
            return;
        }

        listEl.innerHTML = '';

        if (!Array.isArray(feedback) || feedback.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'feedback-empty';
            empty.textContent = getMsg('noComments');
            listEl.appendChild(empty);
            return;
        }

        feedback.forEach((entry) => {
            const item = document.createElement('article');
            item.className = 'feedback-entry';

            const header = document.createElement('div');
            header.className = 'feedback-entry-header';

            const author = document.createElement('span');
            author.textContent = entry.user || 'Ẩn danh';
            header.appendChild(author);

            const meta = document.createElement('span');
            const rating = Number(entry.rating) || 0;
            const createdAt = entry.created_at ? new Date(entry.created_at).toLocaleDateString() : '';
            meta.textContent = `${'⭐'.repeat(rating)}${rating ? ` • ${rating}/5` : ''}${createdAt ? ` • ${createdAt}` : ''}`;
            header.appendChild(meta);

            item.appendChild(header);

            if (entry.comment) {
                const body = document.createElement('p');
                body.className = 'feedback-entry-comment';
                body.textContent = entry.comment;
                item.appendChild(body);
            }

            listEl.appendChild(item);
        });
    };

    const updateCardWithDetail = (card, detail) => {
        if (!card || !detail) {
            return;
        }

        const averageEl = card.querySelector('[data-average-rating]');
        const countEl = card.querySelector('[data-rating-count]');
        const listEl = card.querySelector('[data-feedback-list]');

        const averageRating = detail.average_rating;
        if (averageEl) {
            const parsedAverage = typeof averageRating === 'number'
                ? averageRating
                : averageRating !== null && averageRating !== undefined
                    ? Number(averageRating)
                    : NaN;
            if (!Number.isNaN(parsedAverage)) {
                averageEl.textContent = `${parsedAverage.toFixed(1)} / 5`;
                averageEl.dataset.value = parsedAverage.toString();
            } else {
                averageEl.textContent = 'Not rated yet';
                delete averageEl.dataset.value;
            }
        }

        if (countEl) {
            const ratingCount = Number(detail.rating_count) || 0;
            countEl.textContent = `${ratingCount} review${ratingCount === 1 ? '' : 's'}`;
            updateCommentToggleLabel(card, ratingCount);
        }

        renderFeedbackList(listEl, detail.feedback);
    };

    const refreshCommunityItineraryCard = async (card, itineraryId) => {
        const listEl = card ? card.querySelector('[data-feedback-list]') : null;
        try {
            if (listEl) {
                listEl.innerHTML = '';
                const loading = document.createElement('div');
                loading.className = 'feedback-empty';
                loading.textContent = 'Loading comments…';
                listEl.appendChild(loading);
            }
            const response = await fetch(`/api/shared-itineraries/${itineraryId}/`);
            if (!response.ok) {
                throw new Error('Không thể tải dữ liệu lịch trình.');
            }
            const detail = await response.json();
            updateCardWithDetail(card, detail);
            return detail;
        } catch (error) {
            console.error('Refresh itinerary detail failed:', error);
            if (listEl) {
                listEl.innerHTML = '';
                const failure = document.createElement('div');
                failure.className = 'feedback-empty';
                failure.textContent = 'Không thể tải bình luận. Vui lòng thử lại sau.';
                listEl.appendChild(failure);
            }
            return null;
        }
    };

    const fetchTopItineraries = async (mood, limit, moodLabelEl, listEl) => {
        const url = new URL('/api/shared-itineraries/', window.location.origin);
        if (mood) {
            url.searchParams.set('mood', mood);
        }
        if (limit) {
            url.searchParams.set('limit', limit);
        }

        try {
            const response = await fetch(url.toString());
            if (!response.ok) {
                throw new Error('Unable to load community itineraries.');
            }
            const payload = await response.json();
            renderTopItineraries(payload.itineraries || [], moodLabelEl, listEl);
        } catch (error) {
            console.error('Top itineraries error:', error);
            listEl.innerHTML = '';
            const empty = document.createElement('div');
            empty.className = 'top-itinerary-empty';
            empty.textContent = error.message;
            listEl.appendChild(empty);
        }
    };

    const handleShareSubmit = async (event, contextEl, statusEl, refresh) => {
        event.preventDefault();
        const form = event.target;
        const formData = new FormData(form);
        const submitter = event.submitter || null;
        const submitMode = submitter && submitter.dataset && submitter.dataset.submitMode
            ? submitter.dataset.submitMode
            : 'share';
        const isSaveOnly = submitMode === 'save';

        // Check if this is an adopted itinerary
        const adoptedItineraryId = contextEl.dataset.adoptedItineraryId;
        const isAdoptedItinerary = !!adoptedItineraryId;

        const title = (formData.get('title') || '').trim();
        const sharePublic = !isSaveOnly;

        if (!title) {
            statusEl.textContent = 'Please provide a title.';
            statusEl.style.color = '#b91c1c';
            return;
        }

        const ratingRaw = formData.get('rating');
        const rating = ratingRaw ? Number(ratingRaw) : null;

        const commentRaw = formData.get('comment');
        const comment = commentRaw ? commentRaw.trim() : '';
        
        if (!isSaveOnly && comment && !rating) {
            statusEl.textContent = 'Please add a rating when leaving a comment.';
            statusEl.style.color = '#b91c1c';
            return;
        }

        if (isSaveOnly) {
            statusEl.textContent = 'Saving your itinerary…';
        } else {
            statusEl.textContent = 'Sharing your itinerary…';
        }
        statusEl.style.color = '#2563eb';

        try {
            const submitPayload = {
                title,
                share_public: sharePublic,
            };
            if (!isSaveOnly && rating) {
                submitPayload.rating = rating;
            }
            if (!isSaveOnly && comment) {
                submitPayload.comment = comment;
            }

            const response = await fetch('/api/shared-itineraries/submit/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRFToken': getCsrfToken(),
                },
                body: JSON.stringify(submitPayload),
            });

            const data = await response.json();
            if (!response.ok) {
                const errors = data && data.errors ? Object.values(data.errors).flat().join(' ') : 'Submission failed.';
                throw new Error(errors);
            }

            if (isSaveOnly) {
                statusEl.textContent = 'Itinerary saved to your profile.';
            } else if (sharePublic) {
                statusEl.textContent = 'Itinerary shared! Thanks for contributing.';
            } else {
                statusEl.textContent = 'Itinerary saved to your profile.';
            }
            statusEl.style.color = '#15803d';
            
            // Reset form fields appropriately
            if (!isAdoptedItinerary) {
                form.reset();
            } else {
                // Only reset rating and comment fields
                const ratingInputs = form.querySelectorAll('input[name="rating"]');
                ratingInputs.forEach(input => input.checked = false);
                const commentField = form.querySelector('textarea[name="comment"]');
                if (commentField) commentField.value = '';
                const ratingText = document.getElementById('rating-text');
                if (ratingText) ratingText.textContent = '';
                
                // Reset star display
                const stars = document.querySelectorAll('#star-rating label');
                stars.forEach(star => {
                    star.style.color = '#d1d5db';
                    star.style.transform = 'scale(1)';
                });
            }
            
            if (!isSaveOnly) {
                fetchTopItineraries(contextEl.dataset.mood, 5, byId('top-itineraries-mood-label'), byId('top-itinerary-list'));
            }
        } catch (error) {
            console.error('Share itinerary error:', error);
            statusEl.textContent = error.message || 'Unable to process itinerary right now.';
            statusEl.style.color = '#b91c1c';
        }
    };

    document.addEventListener('DOMContentLoaded', () => {
        const contextEl = byId('shared-itinerary-context');
        const shareForm = byId('share-itinerary-form');
        const statusEl = byId('share-itinerary-status');
        const listEl = byId('top-itinerary-list');
        const moodLabelEl = byId('top-itineraries-mood-label');
        const hasSessionItinerary = !!(contextEl && contextEl.dataset.hasItinerary === '1');

        if (shareForm && statusEl && contextEl) {
            shareForm.addEventListener('submit', (event) => handleShareSubmit(event, contextEl, statusEl));
            const titleInput = byId('share-itinerary-title');
            if (titleInput && !titleInput.value) {
                const mood = contextEl.dataset.mood || 'adventure';
                titleInput.value = `My ${mood} trip`;
            }
        }

        if (listEl && contextEl) {
            fetchTopItineraries(contextEl.dataset.mood, 5, moodLabelEl, listEl);
        }

        document.querySelectorAll('[data-adopt-itinerary]').forEach((button) => {
            button.addEventListener('click', (event) => {
                event.preventDefault();
                const itineraryId = button.getAttribute('data-adopt-itinerary');
                if (!itineraryId) {
                    return;
                }
                adoptItinerary(itineraryId, button);
            });
        });
        document.querySelectorAll('[data-toggle-feedback]').forEach((button) => {
            button.addEventListener('click', async () => {
                const card = button.closest('[data-itinerary-card]');
                if (!card) {
                    return;
                }
                const panel = card.querySelector('[data-feedback-panel]');
                const itineraryId = getItineraryIdFromElement(card, 'itineraryCard');
                if (!panel || !itineraryId) {
                    return;
                }

                const isActive = panel.classList.contains('active');
                if (isActive) {
                    panel.classList.remove('active');
                    const collapsedLabel = button.dataset.collapsedLabel || getMsg('viewComments');
                    button.textContent = collapsedLabel;
                    return;
                }

                panel.classList.add('active');
                button.disabled = true;
                const previousLabel = button.textContent;
                button.textContent = getMsg('loadingComments');
                const detail = await refreshCommunityItineraryCard(card, itineraryId);
                if (detail) {
                    button.textContent = getMsg('hideComments');
                } else {
                    const collapsedLabel = button.dataset.collapsedLabel || getMsg('viewComments');
                    button.textContent = collapsedLabel;
                    panel.classList.remove('active');
                }
                button.disabled = false;
                if (!detail) {
                    button.textContent = previousLabel;
                }
            });
        });

        document.querySelectorAll('[data-feedback-form]').forEach((form) => {
            form.addEventListener('submit', (event) => {
                event.preventDefault();
                submitFeedback(form);
            });
        });

        if (!hasSessionItinerary && listEl && !contextEl) {
            fetchTopItineraries(null, 5, moodLabelEl, listEl);
        }
        
        // Listen for language changes and update all button labels
        window.addEventListener('languageChanged', () => {
            // Update all comment toggle buttons
            document.querySelectorAll('[data-toggle-feedback]').forEach((button) => {
                const card = button.closest('[data-itinerary-card]');
                if (!card) return;
                
                const panel = card.querySelector('[data-feedback-panel]');
                const ratingCountEl = card.querySelector('[data-rating-count]');
                const count = ratingCountEl ? parseInt(ratingCountEl.getAttribute('data-rating-count')) : 0;
                
                if (panel && panel.classList.contains('active')) {
                    button.textContent = getMsg('hideComments');
                } else {
                    const viewText = getMsg('viewComments');
                    button.textContent = count > 0 ? `${viewText} (${count})` : viewText;
                }
            });
            
            // Update all "Use this itinerary" buttons
            document.querySelectorAll('[data-adopt-itinerary]').forEach((button) => {
                if (!button.disabled && button.textContent !== getMsg('loading')) {
                    button.textContent = getMsg('useItinerary');
                }
            });
        });
    });
})();
