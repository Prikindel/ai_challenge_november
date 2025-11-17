let serversList = [];
let isConnected = false;

// Загрузка списка серверов
async function loadServers() {
    try {
        const response = await fetch('/api/mcp/servers');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        serversList = data.servers;
        
        const select = document.getElementById('serverSelect');
        select.innerHTML = '<option value="">-- Выберите сервер --</option>';
        
        data.servers.forEach(server => {
            if (server.enabled) {
                const option = document.createElement('option');
                option.value = server.name;
                option.textContent = `${server.name} (${server.type})`;
                option.dataset.description = server.description || '';
                select.appendChild(option);
            }
        });
        
        // Показываем описание при выборе сервера
        select.addEventListener('change', (e) => {
            const selectedOption = e.target.options[e.target.selectedIndex];
            const description = selectedOption.dataset.description || '';
            const descriptionEl = document.getElementById('serverDescription');
            descriptionEl.textContent = description;
        });
    } catch (error) {
        console.error('Failed to load servers:', error);
        showStatus('Ошибка загрузки списка серверов: ' + error.message, 'error');
    }
}

// Подключение к серверу
async function connectToServer(serverName) {
    try {
        showStatus('Подключение...', 'info');
        setConnectButtonDisabled(true);
        
        const response = await fetch('/api/mcp/connect', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ serverName })
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success) {
            isConnected = true;
            displayServerInfo(data);
            displayTools(data.tools);
            displayResources(data.resources);
            showStatus('Подключено успешно', 'success');
            updateConnectionButtons();
        } else {
            throw new Error(data.message || 'Неизвестная ошибка');
        }
    } catch (error) {
        console.error('Connection error:', error);
        showStatus('Ошибка подключения: ' + error.message, 'error');
        isConnected = false;
        updateConnectionButtons();
    } finally {
        setConnectButtonDisabled(false);
    }
}

// Отключение от сервера
async function disconnectFromServer() {
    try {
        showStatus('Отключение...', 'info');
        
        const response = await fetch('/api/mcp/disconnect', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        isConnected = false;
        hideServerInfo();
        hideTools();
        hideResources();
        showStatus('Отключено', 'success');
        updateConnectionButtons();
    } catch (error) {
        console.error('Disconnect error:', error);
        showStatus('Ошибка отключения: ' + error.message, 'error');
    }
}

// Отображение информации о сервере
function displayServerInfo(data) {
    const section = document.getElementById('serverInfo');
    const content = document.getElementById('serverInfoContent');
    
    content.innerHTML = `
        <div class="server-info-card">
            <h4>${data.serverName}</h4>
            <p><strong>Описание:</strong> ${data.serverDescription || 'Нет описания'}</p>
            <p><strong>Инструментов:</strong> ${data.tools.length}</p>
            <p><strong>Ресурсов:</strong> ${data.resources.length}</p>
        </div>
    `;
    
    section.style.display = 'block';
}

// Скрытие информации о сервере
function hideServerInfo() {
    const section = document.getElementById('serverInfo');
    section.style.display = 'none';
}

// Отображение инструментов
function displayTools(tools) {
    const section = document.getElementById('toolsSection');
    const list = document.getElementById('toolsList');
    
    if (tools.length === 0) {
        list.innerHTML = '<p class="empty-message">Инструменты не найдены</p>';
    } else {
        list.innerHTML = tools.map(tool => `
            <div class="tool-card">
                <h3>${escapeHtml(tool.name)}</h3>
                <p>${escapeHtml(tool.description || 'Нет описания')}</p>
            </div>
        `).join('');
    }
    
    section.style.display = 'block';
}

// Скрытие инструментов
function hideTools() {
    const section = document.getElementById('toolsSection');
    section.style.display = 'none';
}

// Отображение ресурсов
function displayResources(resources) {
    const section = document.getElementById('resourcesSection');
    const list = document.getElementById('resourcesList');
    
    if (resources.length === 0) {
        list.innerHTML = '<p class="empty-message">Ресурсы не найдены</p>';
    } else {
        list.innerHTML = resources.map(resource => `
            <div class="resource-card">
                <h3>${escapeHtml(resource.name)}</h3>
                <p><strong>URI:</strong> ${escapeHtml(resource.uri)}</p>
                <p>${escapeHtml(resource.description || 'Нет описания')}</p>
                ${resource.mimeType ? `<p><strong>MIME Type:</strong> ${escapeHtml(resource.mimeType)}</p>` : ''}
            </div>
        `).join('');
    }
    
    section.style.display = 'block';
}

// Скрытие ресурсов
function hideResources() {
    const section = document.getElementById('resourcesSection');
    section.style.display = 'none';
}

// Показать статус
function showStatus(message, type) {
    const statusEl = document.getElementById('connectionStatus');
    statusEl.textContent = message;
    statusEl.className = `status ${type} show`;
    
    // Автоматически скрыть статус через 5 секунд для success/info
    if (type === 'success' || type === 'info') {
        setTimeout(() => {
            statusEl.classList.remove('show');
        }, 5000);
    }
}

// Установить состояние кнопки подключения
function setConnectButtonDisabled(disabled) {
    const btn = document.getElementById('connectBtn');
    btn.disabled = disabled;
}

// Обновить кнопки подключения/отключения
function updateConnectionButtons() {
    const connectBtn = document.getElementById('connectBtn');
    const disconnectBtn = document.getElementById('disconnectBtn');
    const select = document.getElementById('serverSelect');
    
    if (isConnected) {
        connectBtn.style.display = 'none';
        disconnectBtn.style.display = 'block';
        select.disabled = true;
    } else {
        connectBtn.style.display = 'block';
        disconnectBtn.style.display = 'none';
        select.disabled = false;
    }
}

// Экранирование HTML
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    loadServers();
    
    document.getElementById('connectBtn').addEventListener('click', () => {
        const serverName = document.getElementById('serverSelect').value;
        if (serverName) {
            connectToServer(serverName);
        } else {
            showStatus('Выберите сервер из списка', 'error');
        }
    });
    
    document.getElementById('disconnectBtn').addEventListener('click', () => {
        disconnectFromServer();
    });
});
