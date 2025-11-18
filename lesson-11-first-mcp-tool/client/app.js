let isConnected = false;

// Подключение к MCP серверу
async function connectToMCPServer() {
    try {
        updateStatus('Подключение...', 'connecting');
        document.getElementById('connectBtn').disabled = true;
        
        // Режим разработки: запуск через Gradle (не требует сборки JAR)
        // Для production можно указать путь к JAR: 'mcp-server/build/libs/telegram-bot-mcp-server-1.0.0.jar'
        const serverJarPath = null; // null или "class" = режим разработки через Gradle
        
        const response = await fetch('/api/mcp/connect', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ serverJarPath })
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success) {
            isConnected = true;
            updateStatus('Подключено', 'connected');
            showChatSection();
            updateConnectionButtons();
        } else {
            throw new Error(data.message || 'Неизвестная ошибка');
        }
    } catch (error) {
        console.error('Connection error:', error);
        updateStatus('Ошибка подключения: ' + error.message, 'error');
        isConnected = false;
        updateConnectionButtons();
    } finally {
        document.getElementById('connectBtn').disabled = false;
    }
}

// Отключение от MCP сервера
async function disconnectFromMCPServer() {
    try {
        updateStatus('Отключение...', 'connecting');
        
        const response = await fetch('/api/mcp/disconnect', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        isConnected = false;
        updateStatus('Не подключено', 'error');
        hideChatSection();
        updateConnectionButtons();
        clearChat();
    } catch (error) {
        console.error('Disconnect error:', error);
        updateStatus('Ошибка отключения: ' + error.message, 'error');
    }
}

// Обновление статуса
function updateStatus(message, type) {
    const statusEl = document.getElementById('mcpStatus');
    statusEl.textContent = message;
    statusEl.className = 'status-indicator';
    
    if (type === 'connected') {
        statusEl.classList.add('connected');
    } else if (type === 'connecting') {
        statusEl.classList.add('connecting');
    }
}

// Обновление кнопок подключения
function updateConnectionButtons() {
    const connectBtn = document.getElementById('connectBtn');
    const disconnectBtn = document.getElementById('disconnectBtn');
    
    if (isConnected) {
        connectBtn.style.display = 'none';
        disconnectBtn.style.display = 'block';
    } else {
        connectBtn.style.display = 'block';
        disconnectBtn.style.display = 'none';
    }
}

// Показать секцию чата
function showChatSection() {
    document.getElementById('chatSection').style.display = 'block';
}

// Скрыть секцию чата
function hideChatSection() {
    document.getElementById('chatSection').style.display = 'none';
}

// Очистить чат
function clearChat() {
    document.getElementById('chatMessages').innerHTML = '';
    document.getElementById('toolsInfo').style.display = 'none';
    document.getElementById('toolsUsedList').innerHTML = '';
}

// Отправка сообщения LLM агенту
async function sendMessage() {
    const input = document.getElementById('userMessageInput');
    const message = input.value.trim();
    
    if (!message) return;
    
    if (!isConnected) {
        alert('Сначала подключитесь к MCP серверу');
        return;
    }
    
    // Добавляем сообщение пользователя в чат
    addMessageToChat('user', message);
    input.value = '';
    
    // Показываем индикатор загрузки
    const loadingId = addMessageToChat('assistant', 'Думаю...', true);
    
    try {
        const response = await fetch('/api/chat/message', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message })
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Удаляем индикатор загрузки
        removeMessage(loadingId);
        
        // Добавляем ответ ассистента
        addMessageToChat('assistant', data.message);
        
        // Если использовался инструмент, показываем информацию
        if (data.toolUsed) {
            showToolUsage(data.toolUsed, data.toolResult);
        }
    } catch (error) {
        console.error('Message send error:', error);
        removeMessage(loadingId);
        addMessageToChat('assistant', 'Ошибка: ' + error.message, false, true);
    }
}

// Добавление сообщения в чат
function addMessageToChat(role, text, isLoading = false, isError = false) {
    const messagesContainer = document.getElementById('chatMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}-message`;
    if (isLoading) {
        messageDiv.id = 'loading-message';
        messageDiv.innerHTML = '<span class="loading-dots">...</span> ' + text;
    } else if (isError) {
        messageDiv.classList.add('error-message');
        messageDiv.textContent = text;
    } else {
        // Форматируем Markdown (простая версия)
        messageDiv.innerHTML = formatMarkdown(text);
    }
    messagesContainer.appendChild(messageDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
    return messageDiv.id || null;
}

// Удаление сообщения
function removeMessage(messageId) {
    if (messageId) {
        const message = document.getElementById(messageId);
        if (message) {
            message.remove();
        }
    }
}

// Простое форматирование Markdown
function formatMarkdown(text) {
    // Заменяем **текст** на <strong>текст</strong>
    text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    // Заменяем *текст* на <em>текст</em>
    text = text.replace(/\*(.+?)\*/g, '<em>$1</em>');
    // Заменяем переносы строк на <br>
    text = text.replace(/\n/g, '<br>');
    // Заменяем код в обратных кавычках
    text = text.replace(/`(.+?)`/g, '<code>$1</code>');
    return text;
}

// Показ информации об использованном инструменте
function showToolUsage(toolName, toolResult) {
    const section = document.getElementById('toolsInfo');
    const list = document.getElementById('toolsUsedList');
    
    const toolDiv = document.createElement('div');
    toolDiv.className = 'tool-used-item';
    toolDiv.innerHTML = `
        <div class="tool-used-header">
            <strong>🔧 ${toolName}</strong>
        </div>
        <div class="tool-used-result">
            <pre>${escapeHtml(toolResult)}</pre>
        </div>
    `;
    
    list.appendChild(toolDiv);
    section.style.display = 'block';
    
    // Прокрутка к секции инструментов
    section.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// Экранирование HTML
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('connectBtn').addEventListener('click', connectToMCPServer);
    document.getElementById('disconnectBtn').addEventListener('click', disconnectFromMCPServer);
    document.getElementById('sendMessageBtn').addEventListener('click', sendMessage);
    document.getElementById('userMessageInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendMessage();
        }
    });
    
    // Проверка статуса подключения при загрузке
    checkConnectionStatus();
});

// Проверка статуса подключения
async function checkConnectionStatus() {
    try {
        const response = await fetch('/api/mcp/status');
        if (response.ok) {
            const data = await response.json();
            if (data.success) {
                isConnected = true;
                updateStatus('Подключено', 'connected');
                showChatSection();
                updateConnectionButtons();
            }
        }
    } catch (error) {
        // Игнорируем ошибки при проверке статуса
        console.log('Status check failed:', error);
    }
}
