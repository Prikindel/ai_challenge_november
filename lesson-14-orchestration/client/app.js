const API_BASE = 'http://localhost:8080/api';
const WS_BASE = 'ws://localhost:8080/api';

// Элементы интерфейса
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const loadingIndicator = document.getElementById('loadingIndicator');

// WebSocket соединение
let ws = null;
let currentBotMessageDiv = null;
let toolCallElements = {}; // Храним элементы инструментов для обновления

// Отправка сообщения через WebSocket
async function sendMessage() {
    const message = messageInput.value.trim();
    if (!message) return;
    
    // Добавляем сообщение пользователя в чат
    addUserMessage(message);
    
    // Очищаем поле ввода
    messageInput.value = '';
    
    // Блокируем кнопку отправки
    sendButton.disabled = true;
    
    // Создаем сообщение бота для отображения статусов
    currentBotMessageDiv = createBotMessageDiv();
    chatMessages.appendChild(currentBotMessageDiv);
    scrollToBottom();
    
    // Показываем "Думаю..." по умолчанию
    updateStreamingBotMessage(currentBotMessageDiv, "Думаю...");
    
    // Очищаем элементы инструментов
    toolCallElements = {};
    
    try {
        // Подключаемся к WebSocket, если еще не подключены
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            await connectWebSocket();
        }
        
        // Проверяем, что соединение установлено
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            throw new Error('WebSocket соединение не установлено');
        }
        
        // Отправляем сообщение через WebSocket
        const request = JSON.stringify({ message });
        ws.send(request);
        
    } catch (error) {
        console.error('Error sending message:', error);
        hideLoadingIndicator();
        addErrorMessage(error.message || 'Ошибка отправки сообщения');
        sendButton.disabled = false;
        if (currentBotMessageDiv) {
            currentBotMessageDiv.remove();
            currentBotMessageDiv = null;
        }
    }
}

// Подключение к WebSocket
function connectWebSocket() {
    return new Promise((resolve, reject) => {
        try {
            // Если уже есть открытое соединение, не создаем новое
            if (ws && ws.readyState === WebSocket.OPEN) {
                resolve();
                return;
            }
            
            ws = new WebSocket(`${WS_BASE}/chat/ws`);
            
            ws.onopen = () => {
                console.log('WebSocket connected');
                resolve();
            };
            
            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    handleWebSocketMessage(data);
                } catch (error) {
                    console.error('Error parsing WebSocket message:', error);
                }
            };
            
            ws.onerror = (error) => {
                console.error('WebSocket error:', error);
                // Не отклоняем промис, чтобы не блокировать отправку сообщений
                // reject(error);
            };
            
            ws.onclose = () => {
                console.log('WebSocket closed');
                // Можно попытаться переподключиться при следующей отправке
            };
            
        } catch (error) {
            reject(error);
        }
    });
}

// Обработка сообщений от WebSocket
function handleWebSocketMessage(data) {
    // Проверяем, есть ли сообщение об ошибке
    if (data.message && !data.type) {
        // Это сообщение об ошибке (ErrorDto)
        addErrorMessage(data.message);
        sendButton.disabled = false;
        currentBotMessageDiv = null;
        toolCallElements = {};
        return;
    }
    
    switch (data.type) {
        case 'status':
            // Обновляем описание действия от LLM
            if (currentBotMessageDiv) {
                updateStreamingBotMessage(currentBotMessageDiv, data.message);
            }
            break;
            
        case 'tool_call':
            // Обновляем статус инструмента
            if (currentBotMessageDiv) {
                addToolCallToMessage(
                    currentBotMessageDiv,
                    data.toolName,
                    data.status,
                    data.message
                );
            }
            break;
            
        case 'final':
            // Финальный ответ
            if (currentBotMessageDiv) {
                finalizeBotMessage(
                    currentBotMessageDiv,
                    data.message,
                    data.toolCalls || [],
                    data.processingTime || 0
                );
            }
            sendButton.disabled = false;
            currentBotMessageDiv = null;
            toolCallElements = {};
            break;
            
        default:
            console.warn('Unknown message type:', data.type);
    }
}

// Создать элемент сообщения бота
function createBotMessageDiv() {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message bot-message streaming';
    messageDiv.innerHTML = `
        <div class="message-content">
            <div class="streaming-content">Думаю...</div>
            <div class="tool-calls-container"></div>
        </div>
    `;
    return messageDiv;
}

// Обновить текст streaming сообщения
function updateStreamingBotMessage(messageDiv, text) {
    const streamingContent = messageDiv.querySelector('.streaming-content');
    if (streamingContent) {
        streamingContent.textContent = text;
    }
    scrollToBottom();
}

// Добавить/обновить статус инструмента
function addToolCallToMessage(messageDiv, toolName, status, message) {
    const toolCallsContainer = messageDiv.querySelector('.tool-calls-container');
    if (!toolCallsContainer) return;
    
    // Проверяем, есть ли уже элемент для этого инструмента
    let toolElement = toolCallElements[toolName];
    
    if (!toolElement) {
        // Создаем новый элемент
        toolElement = document.createElement('div');
        toolElement.className = `tool-call-item tool-call-${status}`;
        toolElement.setAttribute('data-tool-name', toolName);
        toolCallsContainer.appendChild(toolElement);
        toolCallElements[toolName] = toolElement;
    } else {
        // Обновляем класс существующего элемента
        toolElement.className = `tool-call-item tool-call-${status}`;
    }
    
    // Обновляем содержимое
    const statusText = getStatusText(status);
    const statusIcon = getStatusIcon(status);
    
    toolElement.innerHTML = `
        <span class="tool-call-name">${escapeHtml(toolName)}</span>
        <span class="tool-call-status ${status}">${statusIcon} ${statusText}</span>
        ${message ? `<div class="tool-call-message">${escapeHtml(message)}</div>` : ''}
    `;
    
    scrollToBottom();
}

// Получить текст статуса
function getStatusText(status) {
    switch (status) {
        case 'starting': return 'Выполняется';
        case 'success': return 'Успешно';
        case 'error': return 'Ошибка';
        default: return status;
    }
}

// Получить иконку статуса
function getStatusIcon(status) {
    switch (status) {
        case 'starting': return '⏳';
        case 'success': return '✓';
        case 'error': return '✗';
        default: return '';
    }
}

// Завершить сообщение бота финальным ответом
async function finalizeBotMessage(messageDiv, message, toolCalls, processingTime) {
    // Убираем класс streaming
    messageDiv.classList.remove('streaming');
    
    // Обновляем содержимое
    const messageContent = messageDiv.querySelector('.message-content');
    if (messageContent) {
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
        
        messageContent.innerHTML = `
            ${formatMarkdown(message)}
            ${toolCallsHtml}
        `;
    }
    
    scrollToBottom();
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

// Добавить сообщение бота с визуализацией оркестрации (legacy, для совместимости)
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
    
    // Подключаемся к WebSocket при загрузке страницы (не блокируем загрузку)
    connectWebSocket().catch(error => {
        console.warn('WebSocket connection will be established on first message:', error);
    });
});

