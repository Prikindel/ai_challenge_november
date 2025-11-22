const API_BASE = 'http://localhost:8080/api';

// Элементы интерфейса
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const loadingIndicator = document.getElementById('loadingIndicator');

// Отправка сообщения
async function sendMessage() {
    const message = messageInput.value.trim();
    if (!message) return;
    
    // Добавляем сообщение пользователя в чат
    addUserMessage(message);
    
    // Очищаем поле ввода
    messageInput.value = '';
    
    // Показываем индикатор загрузки
    showLoadingIndicator();
    
    // Блокируем кнопку отправки
    sendButton.disabled = true;
    
    const startTime = Date.now();
    
    try {
        const response = await fetch(`${API_BASE}/chat/message`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ message })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ message: 'Unknown error' }));
            throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        const processingTime = Date.now() - startTime;
        
        // Скрываем индикатор загрузки
        hideLoadingIndicator();
        
        // Добавляем ответ бота с информацией об оркестрации
        addBotMessage(data.message, data.toolCalls, data.processingTime || processingTime);
        
    } catch (error) {
        console.error('Error sending message:', error);
        hideLoadingIndicator();
        addErrorMessage(error.message || 'Ошибка отправки сообщения');
    } finally {
        // Разблокируем кнопку отправки
        sendButton.disabled = false;
    }
}

// Добавить сообщение пользователя
function addUserMessage(message) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message user-message';
    messageDiv.innerHTML = `
        <div class="message-content">${escapeHtml(message)}</div>
    `;
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

// Добавить сообщение бота с визуализацией оркестрации
async function addBotMessage(message, toolCalls = [], processingTime = 0) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message bot-message';
    
    let toolCallsHtml = '';
    if (toolCalls && toolCalls.length > 0) {
        // Загружаем информацию о серверах для отображения
        const serverInfo = await getServerInfo();
        
        // Группируем инструменты по серверам для визуализации флоу
        const toolsByServer = {};
        toolCalls.forEach(tool => {
            const serverId = tool.serverId || findServerForTool(tool.toolName, serverInfo);
            if (!toolsByServer[serverId]) {
                toolsByServer[serverId] = [];
            }
            toolsByServer[serverId].push(tool);
        });
        
        // Создаём визуализацию флоу оркестрации
        let flowHtml = '<div class="orchestration-flow">';
        flowHtml += '<div class="flow-label">Оркестрация инструментов:</div>';
        
        let stepNumber = 1;
        for (const [serverId, tools] of Object.entries(toolsByServer)) {
            const serverName = getServerName(serverId, serverInfo);
            flowHtml += `
                <div class="flow-step">
                    <div class="flow-step-header">
                        <span class="flow-step-number">${stepNumber++}</span>
                        <span class="flow-step-server">${escapeHtml(serverName || serverId)}</span>
                    </div>
                    <div class="flow-step-tools">
                        ${tools.map(tool => `
                            <div class="tool-call-item ${tool.success ? 'success' : 'error'}">
                                <span class="tool-call-name">${escapeHtml(tool.toolName)}</span>
                                <span class="tool-call-status">${tool.success ? '✓' : '✗'}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            `;
        }
        
        flowHtml += '</div>';
        
        toolCallsHtml = `
            <div class="tool-calls">
                <div class="tool-calls-label">Использованные инструменты (${toolCalls.length}):</div>
                ${flowHtml}
                ${processingTime > 0 ? `<div class="processing-time">Время обработки: ${(processingTime / 1000).toFixed(2)}с</div>` : ''}
            </div>
        `;
    }
    
    messageDiv.innerHTML = `
        <div class="message-content">
            ${formatMarkdown(message)}
            ${toolCallsHtml}
        </div>
    `;
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

// Найти сервер для инструмента
function findServerForTool(toolName, serverInfo) {
    if (!serverInfo || !serverInfo.tools) return 'unknown';
    
    for (const server of serverInfo.tools) {
        if (server.tools && server.tools.some(t => t.name === toolName)) {
            return server.server;
        }
    }
    return 'unknown';
}

// Получить имя сервера
function getServerName(serverId, serverInfo) {
    if (!serverInfo || !serverInfo.tools) return serverId;
    
    const server = serverInfo.tools.find(s => s.server === serverId);
    return server ? server.serverName : serverId;
}

// Получить информацию о серверах и инструментах
async function getServerInfo() {
    try {
        const response = await fetch(`${API_BASE}/tools`);
        if (response.ok) {
            return await response.json();
        }
    } catch (error) {
        console.error('Error loading server info:', error);
    }
    return null;
}

// Добавить сообщение об ошибке
function addErrorMessage(errorMessage) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message error-message';
    messageDiv.innerHTML = `
        <div class="message-content">
            <strong>Ошибка:</strong> ${escapeHtml(errorMessage)}
        </div>
    `;
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

// Показать индикатор загрузки
function showLoadingIndicator() {
    loadingIndicator.style.display = 'flex';
    scrollToBottom();
}

// Скрыть индикатор загрузки
function hideLoadingIndicator() {
    loadingIndicator.style.display = 'none';
}

// Прокрутить чат вниз
function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Простое форматирование Markdown
function formatMarkdown(text) {
    if (!text) return '';
    
    // Экранируем HTML
    let html = escapeHtml(text);
    
    // Заголовки
    html = html.replace(/^### (.*$)/gim, '<h3>$1</h3>');
    html = html.replace(/^## (.*$)/gim, '<h2>$1</h2>');
    html = html.replace(/^# (.*$)/gim, '<h1>$1</h1>');
    
    // Жирный текст
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    
    // Курсив
    html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
    
    // Списки
    html = html.replace(/^\- (.*$)/gim, '<li>$1</li>');
    html = html.replace(/^(\d+)\. (.*$)/gim, '<li>$2</li>');
    
    // Обёртываем списки
    html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
    
    // Переносы строк
    html = html.replace(/\n/g, '<br>');
    
    return html;
}

// Экранирование HTML
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Загрузка статуса подключений
async function loadConnections() {
    const container = document.getElementById('connectionsStatus');
    container.innerHTML = '<div class="loading">Загрузка...</div>';
    
    try {
        const response = await fetch(`${API_BASE}/connections`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.connections && data.connections.length > 0) {
            let html = '';
            data.connections.forEach(conn => {
                const statusClass = conn.connected ? 'connected' : 'disconnected';
                const statusText = conn.connected ? 'Подключено' : 'Отключено';
                
                html += `
                    <div class="status-item ${statusClass}">
                        <span class="status-item-name">${escapeHtml(conn.server)}</span>
                        <span class="status-badge ${statusClass}">${statusText}</span>
                    </div>
                `;
            });
            
            html += `
                <div style="margin-top: 10px; padding: 10px; background: #f0f9ff; border-radius: 8px; font-size: 0.9em; color: #0369a1;">
                    Всего: ${data.connected} из ${data.total} подключено
                </div>
            `;
            
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="error">Нет доступных серверов</div>';
        }
    } catch (error) {
        console.error('Error loading connections:', error);
        container.innerHTML = `<div class="error">Ошибка загрузки: ${error.message}</div>`;
    }
}

// Загрузка списка инструментов
async function loadTools() {
    const container = document.getElementById('toolsList');
    container.innerHTML = '<div class="loading">Загрузка...</div>';
    
    try {
        const response = await fetch(`${API_BASE}/tools`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.tools && data.tools.length > 0) {
            let html = '';
            
            data.tools.forEach(sourceTools => {
                html += `
                    <div class="server-tools-group">
                        <div class="server-tools-header">
                            <span class="server-name">${escapeHtml(sourceTools.serverName)}</span>
                            <span class="tools-count">${sourceTools.tools.length} инструментов</span>
                        </div>
                        <div class="server-tools-list">
                            ${sourceTools.tools.map(tool => `
                                <div class="tool-card">
                                    <div class="tool-name">${escapeHtml(tool.name)}</div>
                                    <div class="tool-description">${escapeHtml(tool.description || 'Нет описания')}</div>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                `;
            });
            
            if (html === '') {
                html = '<div class="error">Нет доступных инструментов</div>';
            } else {
                html = `<div style="margin-bottom: 15px; color: #6b7280; font-size: 0.9em;">
                    Всего инструментов: ${data.total} из ${data.tools.length} серверов
                </div>${html}`;
            }
            
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="error">Нет доступных инструментов</div>';
        }
    } catch (error) {
        console.error('Error loading tools:', error);
        container.innerHTML = `<div class="error">Ошибка загрузки: ${error.message}</div>`;
    }
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    // Обработчик отправки сообщения
    sendButton.addEventListener('click', sendMessage);
    
    // Отправка по Enter (Shift+Enter для новой строки)
    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
    
    // Загружаем данные при загрузке страницы
    loadConnections();
    loadTools();
    
    // Обработчики кнопок обновления
    document.getElementById('refreshConnectionsBtn').addEventListener('click', loadConnections);
    document.getElementById('refreshToolsBtn').addEventListener('click', loadTools);
});

