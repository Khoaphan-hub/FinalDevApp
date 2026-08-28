document.addEventListener("DOMContentLoaded", () => {
    const chatPopup = document.getElementById("chat-popup");
    const chatFab = document.getElementById("chat-fab");
    const chatCloseBtn = document.querySelector(".chat-close");
    
    const sendBtn = document.getElementById("chat-send-btn");
    const userInput = document.getElementById("chat-input");
    const chatMessages = document.getElementById("chat-messages");
    
    const refreshBtn = document.getElementById("refresh-btn");
    const suggestionList = document.querySelector('.suggestion-chips');

    let chatHistory = [];
    
    // Bilingual messages
    const messages = {
        httpError: {
            vi: (status) => `Lỗi HTTP: ${status}`,
            en: (status) => `HTTP Error: ${status}`
        },
        networkError: {
            vi: "Xin lỗi, mạng đang chập chờn. Bạn thử lại nhé?",
            en: "Sorry, network is unstable. Please try again?"
        },
        refreshSuccess: {
            vi: "Đã làm mới cuộc trò chuyện!",
            en: "Chat refreshed!"
        }
    };
    
    function getMsg(key, ...args) {
        const lang = localStorage.getItem('preferredLanguage') || 'vi';
        const msg = messages[key];
        if (!msg) return '';
        const text = msg[lang];
        return typeof text === 'function' ? text(...args) : text;
    }

    // Handle suggestion chips
    const chips = document.querySelectorAll('.suggestion-chip');
    chips.forEach(chip => {
        chip.addEventListener('click', function() {
            // Determine text based on current language
            const currentLang = localStorage.getItem('language') || 'vi';
            let text = this.getAttribute('data-suggestion');
            
            const textVi = this.getAttribute('data-suggestion-vi');
            const textEn = this.getAttribute('data-suggestion-en');
            
            if (currentLang === 'en' && textEn) {
                text = textEn;
            } else if (currentLang === 'vi' && textVi) {
                text = textVi;
            }
            
            if (userInput && sendBtn) {
                userInput.value = text || this.textContent;
                
                // Ẩn bảng gợi ý đi
                if (suggestionList) suggestionList.style.display = 'none';
                
                // Tự động bấm nút gửi
                sendBtn.click(); 
            }
        });
    });
    
    // Refresh button functionality
    if (refreshBtn) {
        refreshBtn.addEventListener("click", () => {
            // 1. Xóa sạch biến nhớ lịch sử
            chatHistory = [];
            
            // 2. Xóa giao diện: Giữ lại tin nhắn chào mừng, xóa các tin nhắn khác
            const messages = chatMessages.querySelectorAll('.message');
            
            messages.forEach(msg => {
                // Nếu không phải là tin nhắn chào mừng ban đầu
                if (!msg.classList.contains('welcome-message') && msg.parentElement === chatMessages) {
                    // Skip first message (welcome message)
                    if (msg !== messages[0]) {
                        msg.remove();
                    }
                }
            });

            // 3. Hiện lại bảng gợi ý (nếu nó đang bị ẩn)
            if (suggestionList) {
                suggestionList.style.display = 'flex';
            }

            // 4. Reset ô nhập liệu
            if (userInput) {
                userInput.value = "";
                userInput.disabled = false;
                userInput.focus();
            }
            
            // 5. Bật lại nút gửi (đề phòng đang bị disable)
            if (sendBtn) sendBtn.disabled = false;

            console.log(getMsg('refreshSuccess'));
        });
    }
    
    // --- 1. LOGIC BẬT/TẮT CỬA SỔ CHAT ---
    if (chatFab) {
        chatFab.addEventListener("click", () => {
            chatPopup.classList.toggle("popup-open");
            chatFab.classList.toggle("fab-open");
        });
    }

    if (chatCloseBtn) {
        chatCloseBtn.addEventListener("click", () => {
            chatPopup.classList.remove("popup-open");
            chatFab.classList.remove("fab-open");
        });
    }

    // --- 2. LOGIC GỬI TIN NHẮN ---
    if (sendBtn) {
        sendBtn.addEventListener("click", sendMessage);
    }
    
    if (userInput) {
        userInput.addEventListener("keyup", (event) => {
            if (event.key === "Enter") sendMessage();
        });
    }

    async function sendMessage() {
        const message = userInput.value.trim();
        if (message === "") return;

        // Ẩn gợi ý nếu người dùng tự chat
        if (suggestionList) suggestionList.style.display = 'none';

        // Hiển thị tin nhắn người dùng
        addMessageToChat(message, false); 
        userInput.value = ""; 
        userInput.disabled = true;
        sendBtn.disabled = true;

        // Hiển thị hiệu ứng "đang nhập..."
        const loadingMsgElement = addMessageToChat("...", true, true);
        loadingMsgElement.classList.add("loading");

        try {
            // Cắt ngắn lịch sử: chỉ lấy 12 tin nhắn gần nhất
            const recentHistory = chatHistory.slice(-12);

            const response = await fetch('/api/chat/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRFToken': getCookie('csrftoken') 
                },
                body: JSON.stringify({ 
                    message: message,
                    history: recentHistory 
                })
            });

            if (!response.ok) {
                throw new Error(getMsg('httpError', response.status));
            }

            const data = await response.json();
            
            // Xóa hiệu ứng loading
            loadingMsgElement.remove();

            // Hiển thị câu trả lời của Bot
            addMessageToChat(data.answer || data.response, true);

            // Lưu vào lịch sử client (Gemini format)
            chatHistory.push({ "role": "user", "parts": [{ "text": message }] });
            chatHistory.push({ "role": "model", "parts": [{ "text": data.answer || data.response }] });

        } catch (error) {
            console.error("Lỗi:", error);
            loadingMsgElement.remove();
            addMessageToChat(getMsg('networkError'), true);
        } finally {
            userInput.disabled = false;
            sendBtn.disabled = false;
            userInput.focus();
        }
    }

    // --- 3. HÀM HIỂN THỊ TIN NHẮN ---
    function addMessageToChat(message, isBot, isLoading = false) {
        const msgElement = document.createElement("div");
        msgElement.classList.add("message");

        if (isBot) {
            msgElement.classList.add("bot");
            if (isLoading) {
                msgElement.innerHTML = `
                    <div class="message-avatar">🤖</div>
                    <div class="typing-indicator"><span></span><span></span><span></span></div>
                `;
            } else {
                // Dùng marked.js nếu có, không thì hiện text thường
                if (typeof marked !== 'undefined') {
                    msgElement.innerHTML = `<div class="message-avatar">🤖</div><div class="message-content markdown-body">${marked.parse(message)}</div>`;
                } else {
                    msgElement.innerHTML = `<div class="message-avatar">🤖</div><div class="message-content">${message}</div>`;
                }
            }
        } else {
            msgElement.classList.add("user");
            msgElement.innerHTML = `<div class="message-avatar">👤</div><div class="message-content">${message}</div>`;
        }
        
        chatMessages.appendChild(msgElement);
        
        requestAnimationFrame(() => {
            chatMessages.scrollTo({ top: chatMessages.scrollHeight, behavior: 'smooth' });
        });
    
        return msgElement;
    }

    function getCookie(name) {
        let cookieValue = null;
        if (document.cookie && document.cookie !== '') {
            const cookies = document.cookie.split(';');
            for (let i = 0; i < cookies.length; i++) {
                const cookie = cookies[i].trim();
                if (cookie.substring(0, name.length + 1) === (name + '=')) {
                    cookieValue = decodeURIComponent(cookie.substring(name.length + 1));
                    break;
                }
            }
        }
        return cookieValue;
    }
    
    // Function to update suggestion chips when language changes
    function updateSuggestionChips() {
        // Use the same key as base.html: 'language'
        const currentLang = localStorage.getItem('language') || 'vi';
        const chips = document.querySelectorAll('.suggestion-chip');
        chips.forEach(chip => {
            const viText = chip.getAttribute('data-vi');
            const enText = chip.getAttribute('data-en');
            if (viText && enText) {
                chip.textContent = currentLang === 'en' ? enText : viText;
            }
        });
    }
    
    // Listen for language change events
    window.addEventListener('languageChanged', updateSuggestionChips);
    
    // Initial update
    updateSuggestionChips();
});
