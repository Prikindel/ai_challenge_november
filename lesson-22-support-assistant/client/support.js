// Поддержка пользователей

// Используем глобальную константу из app.js
const API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Текущий тикет для экспорта
let currentTicket = null;

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('supportForm');
    form.addEventListener('submit', handleQuestionSubmit);
    
    // Примеры вопросов
    document.querySelectorAll('.example-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const question = btn.getAttribute('data-question');
            document.getElementById('question').value = question;
            document.getElementById('question').focus();
        });
    });
    
    // Управление тикетами
    document.getElementById('loadTicketsBtn').addEventListener('click', loadUserTickets);
    document.getElementById('createTicketBtn').addEventListener('click', () => {
        document.getElementById('createTicketForm').classList.add('active');
    });
    document.getElementById('cancelCreateTicketBtn').addEventListener('click', () => {
        document.getElementById('createTicketForm').classList.remove('active');
        document.getElementById('createTicketFormElement').reset();
    });
    document.getElementById('createTicketFormElement').addEventListener('submit', handleCreateTicket);
    document.getElementById('statusFilter').addEventListener('change', filterTickets);
    document.getElementById('ticketsSearch').addEventListener('input', filterTickets);
    
    // Экспорт
    document.getElementById('exportJsonBtn').addEventListener('click', exportTicketJson);
    document.getElementById('exportTextBtn').addEventListener('click', exportTicketText);
    
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
    
    // Автозаполнение userId из поля тикетов
    const ticketsUserIdInput = document.getElementById('ticketsUserId');
    ticketsUserIdInput.addEventListener('change', () => {
        const userId = ticketsUserIdInput.value.trim();
        if (userId) {
            document.getElementById('userId').value = userId;
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
    
    // Сохраняем текущий тикет для экспорта
    currentTicket = ticket;
    
    historyList.innerHTML = '';
    
    if (!ticket.messages || ticket.messages.length === 0) {
        historyList.innerHTML = '<p style="color: #666;">История сообщений пуста</p>';
        ticketHistory.style.display = 'block';
        return;
    }
    
    // Сортируем сообщения по времени
    const sortedMessages = [...ticket.messages].sort((a, b) => a.timestamp - b.timestamp);
    
    sortedMessages.forEach(message => {
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
    
    // Прокручиваем к последнему сообщению
    historyList.scrollTop = historyList.scrollHeight;
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

/**
 * Загрузка тикетов пользователя
 */
async function loadUserTickets() {
    const userId = document.getElementById('ticketsUserId').value.trim();
    
    if (!userId) {
        showStatus('error', 'Пожалуйста, укажите User ID');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/support/user/${userId}/tickets`);
        
        if (!response.ok) {
            throw new Error('Ошибка при загрузке тикетов');
        }
        
        const data = await response.json();
        displayTickets(data.tickets);
        
    } catch (error) {
        console.error('Failed to load tickets:', error);
        showStatus('error', `Ошибка: ${error.message}`);
    }
}

/**
 * Отображение списка тикетов
 */
function displayTickets(tickets) {
    const ticketsList = document.getElementById('ticketsList');
    ticketsList.innerHTML = '';
    
    if (tickets.length === 0) {
        ticketsList.innerHTML = '<p style="color: #666;">Тикеты не найдены</p>';
        return;
    }
    
    // Сохраняем оригинальный список для фильтрации
    window.allTickets = tickets;
    
    // Применяем фильтры
    filterTickets();
}

/**
 * Фильтрация тикетов
 */
function filterTickets() {
    if (!window.allTickets) return;
    
    const statusFilter = document.getElementById('statusFilter').value;
    const searchQuery = document.getElementById('ticketsSearch').value.toLowerCase();
    const ticketsList = document.getElementById('ticketsList');
    
    let filteredTickets = window.allTickets;
    
    // Фильтр по статусу
    if (statusFilter) {
        filteredTickets = filteredTickets.filter(ticket => ticket.status === statusFilter);
    }
    
    // Поиск по теме
    if (searchQuery) {
        filteredTickets = filteredTickets.filter(ticket => 
            ticket.subject.toLowerCase().includes(searchQuery) ||
            ticket.description.toLowerCase().includes(searchQuery)
        );
    }
    
    // Сортировка по дате (новые сначала)
    filteredTickets.sort((a, b) => b.createdAt - a.createdAt);
    
    ticketsList.innerHTML = '';
    
    filteredTickets.forEach(ticket => {
        const ticketItem = document.createElement('div');
        ticketItem.className = `ticket-item ${ticket.status.toLowerCase()}`;
        
        const date = new Date(ticket.createdAt);
        const dateStr = date.toLocaleString('ru-RU');
        
        const statusLabels = {
            'OPEN': 'Открыт',
            'IN_PROGRESS': 'В работе',
            'RESOLVED': 'Решён',
            'CLOSED': 'Закрыт'
        };
        
        const priorityLabels = {
            'LOW': 'Низкий',
            'MEDIUM': 'Средний',
            'HIGH': 'Высокий',
            'URGENT': 'Срочный'
        };
        
        ticketItem.innerHTML = `
            <div class="ticket-header">
                <div>
                    <div class="ticket-title">${escapeHtml(ticket.subject)}</div>
                    <div class="ticket-meta">
                        <span class="ticket-status ${ticket.status.toLowerCase()}">${statusLabels[ticket.status]}</span>
                        <span>Приоритет: ${priorityLabels[ticket.priority]}</span>
                        <span>Сообщений: ${ticket.messageCount}</span>
                        <span>${dateStr}</span>
                    </div>
                </div>
            </div>
            <div style="color: #666; font-size: 14px; margin-top: 5px;">
                ${escapeHtml(ticket.description.substring(0, 150))}${ticket.description.length > 150 ? '...' : ''}
            </div>
        `;
        
        ticketItem.addEventListener('click', () => {
            document.getElementById('ticketId').value = ticket.id;
            loadTicketHistory(ticket.id);
            // Прокручиваем к форме вопроса
            document.getElementById('supportForm').scrollIntoView({ behavior: 'smooth' });
        });
        
        ticketsList.appendChild(ticketItem);
    });
    
    if (filteredTickets.length === 0) {
        ticketsList.innerHTML = '<p style="color: #666;">Тикеты не найдены</p>';
    }
}

/**
 * Создание нового тикета
 */
async function handleCreateTicket(e) {
    e.preventDefault();
    
    const userId = document.getElementById('createTicketUserId').value.trim();
    const subject = document.getElementById('createTicketSubject').value.trim();
    const description = document.getElementById('createTicketDescription').value.trim();
    
    if (!userId || !subject || !description) {
        showStatus('error', 'Пожалуйста, заполните все поля');
        return;
    }
    
    showStatus('loading', 'Создание тикета...');
    
    try {
        const response = await fetch(`${API_BASE}/support/ticket`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                userId: userId,
                subject: subject,
                description: description
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Ошибка при создании тикета');
        }
        
        const ticket = await response.json();
        
        hideStatus();
        document.getElementById('createTicketForm').classList.remove('active');
        document.getElementById('createTicketFormElement').reset();
        
        // Заполняем поля формы вопроса
        document.getElementById('ticketId').value = ticket.id;
        document.getElementById('userId').value = userId;
        document.getElementById('question').value = description;
        
        // Загружаем историю нового тикета
        loadTicketHistory(ticket.id);
        
        showStatus('completed', 'Тикет успешно создан!');
        setTimeout(hideStatus, 3000);
        
    } catch (error) {
        console.error('Failed to create ticket:', error);
        showStatus('error', `Ошибка: ${error.message}`);
    }
}

/**
 * Экспорт тикета в JSON
 */
function exportTicketJson() {
    if (!currentTicket) {
        showStatus('error', 'Нет данных для экспорта. Загрузите историю тикета.');
        return;
    }
    
    const json = JSON.stringify(currentTicket, null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ticket-${currentTicket.id}.json`;
    a.click();
    URL.revokeObjectURL(url);
}

/**
 * Экспорт тикета в текстовый формат
 */
function exportTicketText() {
    if (!currentTicket) {
        showStatus('error', 'Нет данных для экспорта. Загрузите историю тикета.');
        return;
    }
    
    let text = `Тикет: ${currentTicket.id}\n`;
    text += `Тема: ${currentTicket.subject}\n`;
    text += `Статус: ${currentTicket.status}\n`;
    text += `Приоритет: ${currentTicket.priority}\n`;
    text += `Создан: ${new Date(currentTicket.createdAt).toLocaleString('ru-RU')}\n`;
    text += `Обновлён: ${new Date(currentTicket.updatedAt).toLocaleString('ru-RU')}\n`;
    text += `\nОписание:\n${currentTicket.description}\n\n`;
    text += `История сообщений:\n${'='.repeat(50)}\n\n`;
    
    currentTicket.messages.forEach((message, index) => {
        const date = new Date(message.timestamp);
        const author = message.author === 'user' ? 'Пользователь' : 'Поддержка';
        text += `[${index + 1}] ${author} (${date.toLocaleString('ru-RU')}):\n`;
        text += `${message.content}\n\n`;
    });
    
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ticket-${currentTicket.id}.txt`;
    a.click();
    URL.revokeObjectURL(url);
}

