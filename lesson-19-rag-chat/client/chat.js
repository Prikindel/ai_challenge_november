// Чат с RAG и историей диалога

// Используем глобальную константу из app.js
const API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Текущая сессия
let currentSessionId = null;

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', async () => {
    // Загружаем список сессий
    await loadSessions();
    
    // Проверяем, есть ли сохраненная сессия в localStorage
    const savedSessionId = localStorage.getItem('chatSessionId');
    if (savedSessionId) {
        currentSessionId = savedSessionId;
        await loadHistory();
        updateActiveSession();
    } else {
        // Создаем новую сессию
        await createNewSession();
    }
    
    // Настройка обработчика Enter для отправки сообщения
    const messageInput = document.getElementById('messageInput');
    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
});

/**
 * Загружает список всех сессий
 */
async function loadSessions() {
    try {
        const response = await fetch(`${API_BASE}/chat/sessions`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const sessions = await response.json();
        renderSessionsList(sessions);
    } catch (error) {
        console.error('Failed to load sessions:', error);
        const sessionsList = document.getElementById('sessionsList');
        sessionsList.innerHTML = '<div class="sessions-error">Ошибка загрузки сессий</div>';
    }
}

/**
 * Отображает список сессий в боковой панели
 */
function renderSessionsList(sessions) {
    const sessionsList = document.getElementById('sessionsList');
    
    if (!sessions || sessions.length === 0) {
        sessionsList.innerHTML = '<div class="sessions-empty">Нет сессий</div>';
        return;
    }
    
    sessionsList.innerHTML = sessions.map(session => {
        const date = new Date(session.updatedAt);
        const dateStr = formatDate(date);
        const title = session.title || `Сессия ${dateStr}`;
        const isActive = session.id === currentSessionId;
        
        return `
            <div class="session-item ${isActive ? 'active' : ''}" data-session-id="${session.id}">
                <div class="session-content" onclick="switchSession('${session.id}')">
                    <div class="session-title">${escapeHtml(title)}</div>
                    <div class="session-date">${dateStr}</div>
                </div>
                <button class="session-delete" onclick="deleteSession('${session.id}', event)" title="Удалить сессию">
                    ×
                </button>
            </div>
        `;
    }).join('');
}

/**
 * Переключается на другую сессию
 */
async function switchSession(sessionId) {
    if (sessionId === currentSessionId) {
        return;
    }
    
    currentSessionId = sessionId;
    localStorage.setItem('chatSessionId', currentSessionId);
    
    updateActiveSession();
    await loadHistory();
}

/**
 * Обновляет выделение активной сессии
 */
function updateActiveSession() {
    const sessionItems = document.querySelectorAll('.session-item');
    sessionItems.forEach(item => {
        const sessionId = item.getAttribute('data-session-id');
        if (sessionId === currentSessionId) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });
}

/**
 * Удаляет сессию
 */
async function deleteSession(sessionId, event) {
    if (event) {
        event.stopPropagation();
    }
    
    if (!confirm('Вы уверены, что хотите удалить эту сессию? Все сообщения будут удалены.')) {
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/chat/sessions/${sessionId}`, {
            method: 'DELETE'
        });
        
        // Успешное удаление (200 OK или 204 No Content)
        if (response.ok || response.status === 204) {
            // Если удалили текущую сессию, создаем новую
            if (sessionId === currentSessionId) {
                currentSessionId = null;
                localStorage.removeItem('chatSessionId');
                await createNewSession();
            } else {
                // Обновляем список сессий
                await loadSessions();
            }
            return;
        }
        
        // Обработка ошибок
        let errorMessage = `HTTP error! status: ${response.status}`;
        try {
            const errorData = await response.json();
            errorMessage = errorData.message || errorMessage;
        } catch (e) {
            // Игнорируем ошибку парсинга JSON для 204
        }
        throw new Error(errorMessage);
    } catch (error) {
        console.error('Failed to delete session:', error);
        alert('Ошибка удаления сессии: ' + error.message);
    }
}

/**
 * Создает новую сессию чата
 */
async function createNewSession() {
    try {
        showStatus('Создание новой сессии...');
        
        const response = await fetch(`${API_BASE}/chat/sessions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                title: null
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const session = await response.json();
        currentSessionId = session.id;
        localStorage.setItem('chatSessionId', currentSessionId);
        
        showStatus('');
        clearMessages();
        addWelcomeMessage();
        
        // Обновляем список сессий
        await loadSessions();
        updateActiveSession();
        
        console.log('Session created:', currentSessionId);
    } catch (error) {
        console.error('Failed to create session:', error);
        showStatus('Ошибка создания сессии: ' + error.message, 'error');
    }
}

/**
 * Отправляет сообщение пользователя
 */
async function sendMessage() {
    const messageInput = document.getElementById('messageInput');
    const message = messageInput.value.trim();
    
    if (!message) {
        return;
    }
    
    // Проверяем наличие сессии
    if (!currentSessionId) {
        await createNewSession();
        if (!currentSessionId) {
            showStatus('Не удалось создать сессию', 'error');
            return;
        }
    }
    
    // Отключаем кнопку отправки
    const sendButton = document.getElementById('sendButton');
    sendButton.disabled = true;
    sendButton.textContent = 'Отправка...';
    
    // Очищаем поле ввода
    messageInput.value = '';
    
    // Добавляем сообщение пользователя в UI
    addMessage('user', message);
    
    // Показываем индикатор загрузки
    const loadingId = addLoadingMessage();
    
    try {
        showStatus('Поиск ответа в базе знаний...');
        
        // Получаем выбранные стратегии
        const ragStrategy = document.getElementById('ragStrategy').value;
        const historyStrategy = document.getElementById('historyStrategy').value;
        
        const response = await fetch(`${API_BASE}/chat/sessions/${currentSessionId}/messages`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                message: message,
                topK: 5,
                minSimilarity: 0.4,
                applyFilter: ragStrategy !== 'none',
                strategy: ragStrategy !== 'none' ? ragStrategy : null,
                historyStrategy: historyStrategy
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Удаляем индикатор загрузки
        removeLoadingMessage(loadingId);
        
        // Добавляем ответ ассистента
        addMessage('assistant', data.message.content, data.message.citations);
        
        // Обновляем список сессий (сессия обновилась)
        await loadSessions();
        updateActiveSession();
        
        showStatus('');
    } catch (error) {
        console.error('Failed to send message:', error);
        removeLoadingMessage(loadingId);
        showStatus('Ошибка отправки сообщения: ' + error.message, 'error');
        addMessage('assistant', 'Извините, произошла ошибка при обработке вашего запроса. Попробуйте еще раз.', []);
    } finally {
        // Включаем кнопку отправки
        sendButton.disabled = false;
        sendButton.textContent = 'Отправить';
    }
}

/**
 * Загружает историю сообщений
 */
async function loadHistory() {
    if (!currentSessionId) {
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/chat/sessions/${currentSessionId}/messages`);
        
        if (!response.ok) {
            if (response.status === 404) {
                // Сессия не найдена, создаем новую
                await createNewSession();
                return;
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Очищаем сообщения
        clearMessages();
        
        // Добавляем сообщения из истории
        if (data.messages && data.messages.length > 0) {
            data.messages.forEach(msg => {
                const role = msg.role.toLowerCase();
                addMessage(role, msg.content, msg.citations || [], false);
            });
        } else {
            addWelcomeMessage();
        }
    } catch (error) {
        console.error('Failed to load history:', error);
        // При ошибке создаем новую сессию
        await createNewSession();
    }
}

/**
 * Добавляет сообщение в чат
 */
function addMessage(role, content, citations = [], scroll = true) {
    const messagesContainer = document.getElementById('chatMessages');
    
    // Удаляем приветственное сообщение, если оно есть
    const welcome = messagesContainer.querySelector('.chat-welcome');
    if (welcome) {
        welcome.remove();
    }
    
    const messageDiv = document.createElement('div');
    messageDiv.className = `chat-message chat-message-${role}`;
    
    const messageContent = document.createElement('div');
    messageContent.className = 'chat-message-content';
    
    // Рендерим Markdown контент
    if (typeof marked !== 'undefined') {
        messageContent.innerHTML = marked.parse(content);
    } else {
        messageContent.textContent = content;
    }
    
    // Добавляем цитаты, если они есть
    if (citations && citations.length > 0) {
        const citationsDiv = document.createElement('div');
        citationsDiv.className = 'chat-citations';
        
        citations.forEach(citation => {
            const citationLink = document.createElement('a');
            citationLink.href = '#';
            citationLink.className = 'citation-link';
            citationLink.textContent = `📄 ${citation.documentTitle || citation.documentPath}`;
            citationLink.onclick = (e) => {
                e.preventDefault();
                if (typeof window.openDocumentViewer === 'function') {
                    window.openDocumentViewer(citation.documentPath, citation.documentTitle);
                }
            };
            citationsDiv.appendChild(citationLink);
        });
        
        messageContent.appendChild(citationsDiv);
    }
    
    // Обрабатываем ссылки на документы в тексте
    processCitationLinks(messageContent);
    
    messageDiv.appendChild(messageContent);
    messagesContainer.appendChild(messageDiv);
    
    if (scroll) {
        scrollToBottom();
    }
}

/**
 * Обрабатывает ссылки на документы в тексте сообщения
 */
function processCitationLinks(element) {
    // Находим все ссылки в формате [Источник: название](путь)
    const links = element.querySelectorAll('a[href^="documents/"], a[href*="/documents/"]');
    links.forEach(link => {
        const href = link.getAttribute('href');
        if (href && (href.startsWith('documents/') || href.includes('/documents/'))) {
            link.onclick = (e) => {
                e.preventDefault();
                const documentPath = href;
                const documentTitle = link.textContent.replace(/^\[Источник:\s*/, '').replace(/\]$/, '');
                if (typeof window.openDocumentViewer === 'function') {
                    window.openDocumentViewer(documentPath, documentTitle);
                }
            };
            link.style.cursor = 'pointer';
            link.style.color = '#667eea';
            link.style.textDecoration = 'underline';
        }
    });
}

/**
 * Добавляет индикатор загрузки
 */
function addLoadingMessage() {
    const messagesContainer = document.getElementById('chatMessages');
    const loadingId = 'loading-' + Date.now();
    
    const loadingDiv = document.createElement('div');
    loadingDiv.id = loadingId;
    loadingDiv.className = 'chat-message chat-message-assistant chat-message-loading';
    
    const loadingContent = document.createElement('div');
    loadingContent.className = 'chat-message-content';
    loadingContent.innerHTML = '<div class="loading-dots"><span></span><span></span><span></span></div>';
    
    loadingDiv.appendChild(loadingContent);
    messagesContainer.appendChild(loadingDiv);
    
    scrollToBottom();
    
    return loadingId;
}

/**
 * Удаляет индикатор загрузки
 */
function removeLoadingMessage(loadingId) {
    const loadingDiv = document.getElementById(loadingId);
    if (loadingDiv) {
        loadingDiv.remove();
    }
}

/**
 * Добавляет приветственное сообщение
 */
function addWelcomeMessage() {
    const messagesContainer = document.getElementById('chatMessages');
    const welcome = document.createElement('div');
    welcome.className = 'chat-welcome';
    welcome.innerHTML = `
        <h2>Добро пожаловать в чат!</h2>
        <p>Задайте вопрос, и я найду ответ в базе знаний с указанием источников.</p>
    `;
    messagesContainer.appendChild(welcome);
}

/**
 * Очищает все сообщения
 */
function clearMessages() {
    const messagesContainer = document.getElementById('chatMessages');
    messagesContainer.innerHTML = '';
}

/**
 * Прокручивает чат вниз
 */
function scrollToBottom() {
    const messagesContainer = document.getElementById('chatMessages');
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

/**
 * Показывает статус
 */
function showStatus(message, type = 'info') {
    const statusDiv = document.getElementById('chatStatus');
    if (message) {
        statusDiv.textContent = message;
        statusDiv.className = `chat-status chat-status-${type}`;
        statusDiv.style.display = 'block';
    } else {
        statusDiv.style.display = 'none';
    }
}

/**
 * Форматирует дату для отображения
 */
function formatDate(date) {
    const now = new Date();
    const diff = now - date;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (minutes < 1) {
        return 'только что';
    } else if (minutes < 60) {
        return `${minutes} мин назад`;
    } else if (hours < 24) {
        return `${hours} ч назад`;
    } else if (days < 7) {
        return `${days} дн назад`;
    } else {
        return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
    }
}

/**
 * Экранирует HTML для безопасности
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

