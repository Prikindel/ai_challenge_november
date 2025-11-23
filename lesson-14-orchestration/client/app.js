const API_BASE = 'http://localhost:8080/api';

// Элементы интерфейса
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const loadingIndicator = document.getElementById('loadingIndicator');

// Отправка сообщения через WebSocket
function sendMessage() {
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
    
    // Создаем сообщение бота, которое будет обновляться в реальном времени
    let currentBotMessageDiv = null;
    let currentBotMessageContent = '';
    let toolCalls = [];
    let finalMessage = null;
    
    // Подключаемся к WebSocket
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/api/chat/ws`;
    const ws = new WebSocket(wsUrl);
    
    ws.onopen = () => {
        console.log('WebSocket connected');
        // Отправляем сообщение
        ws.send(JSON.stringify({ message }));
        console.log('Message sent to WebSocket:', message.substring(0, 50) + '...');
        
        // Создаем начальное сообщение бота
        currentBotMessageDiv = createStreamingBotMessage();
    };
    
    ws.onmessage = (event) => {
        try {
            console.log('WebSocket message received:', event.data.substring(0, 200));
            const data = JSON.parse(event.data);
            console.log('Parsed WebSocket data:', data);
            
            // Создаем или обновляем элемент сообщения бота
            if (!currentBotMessageDiv) {
                currentBotMessageDiv = createStreamingBotMessage();
            }
            
            switch (data.type) {
                case 'status':
                    // Промежуточное сообщение от LLM (например, "Анализирую данные...")
                    console.log('Status update:', data.message);
                    updateStreamingBotMessage(currentBotMessageDiv, data.message);
                    break;
                    
                case 'tool_call':
                    // Информация о вызове инструмента
                    console.log('Tool call update:', data.toolName, data.status);
                    addToolCallToMessage(currentBotMessageDiv, data.toolName, data.status, data.message);
                    if (data.status === 'success' || data.status === 'error') {
                        toolCalls.push({
                            toolName: data.toolName,
                            success: data.status === 'success',
                            serverId: null
                        });
                    }
                    break;
                    
                case 'final':
                    // Финальный ответ
                    console.log('Final response received');
                    finalMessage = data.message;
                    toolCalls = data.toolCalls || toolCalls;
                    hideLoadingIndicator();
                    updateBotMessageContent(currentBotMessageDiv, finalMessage, toolCalls, data.processingTime || 0);
                    ws.close();
                    sendButton.disabled = false;
                    break;
                    
                case 'error':
                    // Ошибка
                    console.error('Error received:', data.message);
                    hideLoadingIndicator();
                    addErrorMessage(data.message);
                    ws.close();
                    sendButton.disabled = false;
                    break;
                    
                default:
                    console.warn('Unknown WebSocket message type:', data.type, data);
            }
        } catch (error) {
            console.error('Error parsing WebSocket message:', error, event.data);
        }
    };
    
    ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        hideLoadingIndicator();
        addErrorMessage('Ошибка подключения к серверу');
        sendButton.disabled = false;
    };
    
    ws.onclose = (event) => {
        console.log('WebSocket closed', 'code:', event.code, 'reason:', event.reason, 'wasClean:', event.wasClean);
        if (!finalMessage) {
            console.warn('WebSocket closed without final message');
            hideLoadingIndicator();
            sendButton.disabled = false;
        }
    };
}

// Добавить сообщение пользователя
function addUserMessage(message) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message user-message';
    const timestamp = formatTime();
    messageDiv.innerHTML = `
        <div class="message-header">
            <span class="message-time">${timestamp}</span>
        </div>
        <div class="message-content">${escapeHtml(message)}</div>
    `;
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

// Создать сообщение бота для стриминга (обновляется в реальном времени)
function createStreamingBotMessage() {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message bot-message streaming';
    const timestamp = formatTime();
    messageDiv.innerHTML = `
        <div class="message-header">
            <span class="message-time">${timestamp}</span>
        </div>
        <div class="message-content">
            <div class="streaming-content"></div>
            <div class="streaming-tool-calls"></div>
        </div>
    `;
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
    return messageDiv;
}

// Обновить стриминг сообщение бота
function updateStreamingBotMessage(messageDiv, content) {
    const contentDiv = messageDiv.querySelector('.streaming-content');
    if (contentDiv) {
        contentDiv.innerHTML = formatMarkdown(content);
    }
    scrollToBottom();
}

// Добавить информацию о вызове инструмента в стриминг сообщение
function addToolCallToMessage(messageDiv, toolName, status, message) {
    const toolCallsDiv = messageDiv.querySelector('.streaming-tool-calls');
    if (!toolCallsDiv) return;
    
    const statusIcon = status === 'starting' ? '⏳' : status === 'success' ? '✓' : '✗';
    const statusText = status === 'starting' ? 'Выполняется' : status === 'success' ? 'Успешно' : 'Ошибка';
    const statusClass = status === 'starting' ? 'starting' : status === 'success' ? 'success' : 'error';
    const timestamp = formatTime();
    
    // Проверяем, есть ли уже этот инструмент в списке
    let toolDiv = toolCallsDiv.querySelector(`[data-tool-name="${toolName}"]`);
    if (!toolDiv) {
        toolDiv = document.createElement('div');
        toolDiv.className = `streaming-tool-call ${statusClass}`;
        toolDiv.setAttribute('data-tool-name', toolName);
        toolCallsDiv.appendChild(toolDiv);
    } else {
        toolDiv.className = `streaming-tool-call ${statusClass}`;
    }
    
    toolDiv.innerHTML = `
        <span class="tool-call-icon">${statusIcon}</span>
        <span class="tool-call-name">${escapeHtml(toolName)}</span>
        <span class="tool-call-status-text">${statusText}</span>
        <span class="tool-call-time">${timestamp}</span>
        ${message ? `<div class="tool-call-message">${escapeHtml(message)}</div>` : ''}
    `;
    
    scrollToBottom();
}

// Обновить содержимое сообщения бота
async function updateBotMessageContent(messageElement, message, toolCalls = [], processingTime = 0) {
    const contentDiv = messageElement.querySelector('.streaming-content');
    if (contentDiv) {
        contentDiv.innerHTML = formatMarkdown(message);
    }

    const toolCallsSection = messageElement.querySelector('.streaming-tool-calls');
    if (toolCallsSection) {
        // Обновляем только если это финальный ответ с полным списком toolCalls
        if (toolCalls && toolCalls.length > 0) {
            const serverInfo = await getServerInfo();
            const toolsByServer = {};
            toolCalls.forEach(tool => {
                const serverId = tool.serverId || findServerForTool(tool.toolName, serverInfo);
                if (!toolsByServer[serverId]) {
                    toolsByServer[serverId] = [];
                }
                toolsByServer[serverId].push(tool);
            });

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

            toolCallsSection.innerHTML = `
                <div class="tool-calls-label">Использованные инструменты (${toolCalls.length}):</div>
                ${flowHtml}
                ${processingTime > 0 ? `<div class="processing-time">Время обработки: ${(processingTime / 1000).toFixed(2)}с</div>` : ''}
            `;
        }
    }
    messageElement.classList.remove('streaming');
    scrollToBottom();
}

// Добавить сообщение бота с визуализацией оркестрации (используется как fallback)
async function addBotMessage(message, toolCalls = [], processingTime = 0) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message bot-message';
    const timestamp = formatTime();
    
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
        <div class="message-header">
            <span class="message-time">${timestamp}</span>
        </div>
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
    const timestamp = formatTime();
    messageDiv.innerHTML = `
        <div class="message-header">
            <span class="message-time">${timestamp}</span>
        </div>
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

// Форматирование времени в читаемый формат
function formatTime(timestamp = null) {
    const date = timestamp ? new Date(timestamp) : new Date();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    const milliseconds = String(date.getMilliseconds()).padStart(3, '0');
    return `${hours}:${minutes}:${seconds}.${milliseconds}`;
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

