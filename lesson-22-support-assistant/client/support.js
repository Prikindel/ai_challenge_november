// Поддержка пользователей

// Используем глобальную константу из app.js
const API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('supportForm');
    form.addEventListener('submit', handleQuestionSubmit);
    
    // Загружаем историю тикета, если ticketId указан в URL
    const urlParams = new URLSearchParams(window.location.search);
    const ticketId = urlParams.get('ticketId');
    if (ticketId) {
        document.getElementById('ticketId').value = ticketId;
        loadTicketHistory(ticketId);
    }
    
    // Загружаем историю при изменении ticketId
    const ticketIdInput = document.getElementById('ticketId');
    ticketIdInput.addEventListener('change', () => {
        const id = ticketIdInput.value.trim();
        if (id) {
            loadTicketHistory(id);
        } else {
            hideTicketHistory();
        }
    });
});

/**
 * Обработка отправки вопроса
 */
async function handleQuestionSubmit(e) {
    e.preventDefault();
    
    const ticketId = document.getElementById('ticketId').value.trim() || null;
    const userId = document.getElementById('userId').value.trim() || null;
    const question = document.getElementById('question').value.trim();
    
    if (!question) {
        showStatus('error', 'Пожалуйста, введите ваш вопрос');
        return;
    }
    
    // Очищаем предыдущие результаты
    clearResults();
    
    // Показываем статус загрузки
    showStatus('loading', 'Обработка вопроса...');
    
    // Отключаем кнопку отправки
    const submitBtn = document.getElementById('submitBtn');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Обработка...';
    
    try {
        const response = await fetch(`${API_BASE}/support/ask`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                ticketId: ticketId,
                userId: userId,
                question: question
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Ошибка при обработке вопроса');
        }
        
        const data = await response.json();
        
        // Скрываем статус загрузки
        hideStatus();
        
        // Отображаем ответ
        displayAnswer(data);
        
        // Отображаем источники
        if (data.sources && data.sources.length > 0) {
            displaySources(data.sources);
        }
        
        // Отображаем предложения
        if (data.suggestions && data.suggestions.length > 0) {
            displaySuggestions(data.suggestions);
        }
        
        // Если создан новый тикет, обновляем ticketId и загружаем историю
        if (data.ticketId && !ticketId) {
            document.getElementById('ticketId').value = data.ticketId;
            loadTicketHistory(data.ticketId);
        } else if (ticketId) {
            // Обновляем историю существующего тикета
            loadTicketHistory(ticketId);
        }
        
    } catch (error) {
        console.error('Failed to process question:', error);
        showStatus('error', `Ошибка: ${error.message}`);
    } finally {
        // Включаем кнопку отправки
        submitBtn.disabled = false;
        submitBtn.textContent = 'Отправить вопрос';
    }
}

/**
 * Отображение ответа
 */
function displayAnswer(data) {
    const answerSection = document.getElementById('answerSection');
    const answerContent = document.getElementById('answerContent');
    
    answerContent.textContent = data.answer;
    answerSection.style.display = 'block';
}

/**
 * Отображение источников
 */
function displaySources(sources) {
    const sourcesSection = document.getElementById('sourcesSection');
    const sourcesList = document.getElementById('sourcesList');
    
    sourcesList.innerHTML = '';
    
    sources.forEach(source => {
        const sourceItem = document.createElement('div');
        sourceItem.className = 'source-item';
        
        sourceItem.innerHTML = `
            <h4>${escapeHtml(source.title)}</h4>
            <div class="source-content">${escapeHtml(source.content.substring(0, 200))}${source.content.length > 200 ? '...' : ''}</div>
            ${source.url ? `<a href="${source.url}" class="source-url" target="_blank">${source.url}</a>` : ''}
        `;
        
        sourcesList.appendChild(sourceItem);
    });
    
    sourcesSection.style.display = 'block';
}

/**
 * Отображение предложений
 */
function displaySuggestions(suggestions) {
    const suggestionsSection = document.getElementById('suggestionsSection');
    const suggestionsList = document.getElementById('suggestionsList');
    
    suggestionsList.innerHTML = '';
    
    suggestions.forEach(suggestion => {
        const btn = document.createElement('button');
        btn.className = 'suggestion-btn';
        btn.textContent = suggestion;
        btn.addEventListener('click', () => {
            document.getElementById('question').value = suggestion;
            document.getElementById('question').focus();
        });
        
        suggestionsList.appendChild(btn);
    });
    
    suggestionsSection.style.display = 'block';
}

/**
 * Загрузка истории тикета
 */
async function loadTicketHistory(ticketId) {
    if (!ticketId) {
        hideTicketHistory();
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/support/ticket/${ticketId}`);
        
        if (!response.ok) {
            if (response.status === 404) {
                hideTicketHistory();
                return;
            }
            throw new Error('Ошибка при загрузке истории тикета');
        }
        
        const ticket = await response.json();
        
        displayTicketHistory(ticket);
        
    } catch (error) {
        console.error('Failed to load ticket history:', error);
        hideTicketHistory();
    }
}

/**
 * Отображение истории тикета
 */
function displayTicketHistory(ticket) {
    const ticketHistory = document.getElementById('ticketHistory');
    const historyList = document.getElementById('historyList');
    
    historyList.innerHTML = '';
    
    if (!ticket.messages || ticket.messages.length === 0) {
        historyList.innerHTML = '<p style="color: #666;">История сообщений пуста</p>';
        ticketHistory.style.display = 'block';
        return;
    }
    
    ticket.messages.forEach(message => {
        const messageItem = document.createElement('div');
        messageItem.className = `message-item ${message.author}`;
        
        const date = new Date(message.timestamp);
        const dateStr = date.toLocaleString('ru-RU');
        
        messageItem.innerHTML = `
            <div class="message-header">
                <span><strong>${message.author === 'user' ? 'Пользователь' : 'Поддержка'}</strong></span>
                <span>${dateStr}</span>
            </div>
            <div class="message-content">${escapeHtml(message.content)}</div>
        `;
        
        historyList.appendChild(messageItem);
    });
    
    ticketHistory.style.display = 'block';
}

/**
 * Скрытие истории тикета
 */
function hideTicketHistory() {
    const ticketHistory = document.getElementById('ticketHistory');
    ticketHistory.style.display = 'none';
}

/**
 * Очистка результатов
 */
function clearResults() {
    hideStatus();
    document.getElementById('answerSection').style.display = 'none';
    document.getElementById('sourcesSection').style.display = 'none';
    document.getElementById('suggestionsSection').style.display = 'none';
}

/**
 * Показать статус
 */
function showStatus(type, message) {
    const status = document.getElementById('status');
    status.className = `support-status ${type}`;
    status.textContent = message;
    status.style.display = 'block';
}

/**
 * Скрыть статус
 */
function hideStatus() {
    const status = document.getElementById('status');
    status.style.display = 'none';
}

/**
 * Экранирование HTML
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

