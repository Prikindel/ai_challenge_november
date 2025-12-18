// Управление MCP серверами

const API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    loadServers();
    loadTools();
});

/**
 * Загрузить список MCP серверов
 */
async function loadServers() {
    try {
        const response = await fetch(`${API_BASE}/mcp-servers`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        renderServers(data);
    } catch (error) {
        console.error('Failed to load servers:', error);
        const serversList = document.getElementById('serversList');
        serversList.innerHTML = '<div class="error">Ошибка загрузки серверов</div>';
    }
}

/**
 * Отобразить список серверов
 */
function renderServers(data) {
    const serversList = document.getElementById('serversList');
    const statusDiv = document.getElementById('serversStatus');
    
    if (!data.enabled) {
        statusDiv.innerHTML = '<div class="warning">MCP серверы отключены в конфигурации</div>';
        serversList.innerHTML = '';
        return;
    }
    
    statusDiv.innerHTML = '';
    
    if (!data.servers || data.servers.length === 0) {
        serversList.innerHTML = '<div class="empty">Нет настроенных MCP серверов</div>';
        return;
    }
    
    serversList.innerHTML = data.servers.map(server => {
        // Статус подключения: показывает реальное подключение через MCP протокол
        // Для заглушек и адаптеров (например, Telegram) это может быть false,
        // но сервер все равно доступен и может работать
        let statusClass, statusIcon, statusText;
        
        if (!server.enabled) {
            // Сервер выключен в конфигурации
            statusClass = 'disconnected';
            statusIcon = '⚫';
            statusText = 'Выключен';
        } else if (server.isConnected) {
            // Сервер подключен через MCP протокол
            statusClass = 'connected';
            statusIcon = '🟢';
            statusText = 'Подключен';
        } else {
            // Сервер включен, но не подключен (заглушка или адаптер без подключения)
            statusClass = 'available';
            statusIcon = '🟡';
            statusText = 'Доступен';
        }
        
        return `
            <div class="server-card">
                <div class="server-header">
                    <h4>${escapeHtml(server.name)}</h4>
                    <span class="server-status ${statusClass}">
                        ${statusIcon} ${statusText}
                    </span>
                </div>
                <p class="server-description">${escapeHtml(server.description)}</p>
                <div class="server-info">
                    <span class="badge ${server.enabled ? 'badge-success' : 'badge-disabled'}">
                        ${server.enabled ? 'Включен' : 'Выключен'}
                    </span>
                </div>
            </div>
        `;
    }).join('');
}

/**
 * Загрузить список инструментов
 */
async function loadTools() {
    const toolsList = document.getElementById('toolsList');
    toolsList.innerHTML = '<div class="loading">Загрузка инструментов...</div>';
    
    try {
        console.log('Loading tools from:', `${API_BASE}/mcp-servers/tools`);
        const response = await fetch(`${API_BASE}/mcp-servers/tools`);
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('HTTP error:', response.status, errorText);
            throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
        }
        
        const tools = await response.json();
        console.log('Received tools:', tools);
        renderTools(tools);
    } catch (error) {
        console.error('Failed to load tools:', error);
        toolsList.innerHTML = `<div class="error">Ошибка загрузки инструментов: ${error.message}</div>`;
    }
}

/**
 * Отобразить список инструментов
 */
function renderTools(tools) {
    const toolsList = document.getElementById('toolsList');
    
    console.log('renderTools called with:', tools);
    console.log('toolsList element:', toolsList);
    
    if (!tools || tools.length === 0) {
        console.log('No tools to render');
        toolsList.innerHTML = '<div class="empty">Нет доступных инструментов</div>';
        return;
    }
    
    console.log(`Rendering ${tools.length} tools`);
    
    // Группируем инструменты по серверам
    const toolsByServer = tools.reduce((acc, tool) => {
        if (!acc[tool.serverName]) {
            acc[tool.serverName] = [];
        }
        acc[tool.serverName].push(tool);
        return acc;
    }, {});
    
    console.log('Tools grouped by server:', toolsByServer);
    
    const html = Object.entries(toolsByServer).map(([serverName, serverTools]) => {
        return `
            <div class="tools-group">
                <h4>${escapeHtml(serverName)}</h4>
                <div class="tools-grid">
                    ${serverTools.map(tool => {
                        const isNotImplemented = tool.name === 'not_implemented';
                        const isDisabled = tool.name === 'disabled';
                        return `
                        <div class="tool-card ${isNotImplemented || isDisabled ? 'tool-card-disabled' : ''}">
                            <h5>${escapeHtml(
                                isNotImplemented ? '⚠️ Не реализовано' : 
                                isDisabled ? '⚫ Выключен' : 
                                tool.name
                            )}</h5>
                            <p>${escapeHtml(tool.description)}</p>
                            ${Object.keys(tool.parameters || {}).length > 0 ? `
                                <div class="tool-params">
                                    <strong>Параметры:</strong>
                                    <ul>
                                        ${Object.entries(tool.parameters).map(([key, value]) => 
                                            `<li><code>${escapeHtml(key)}</code>: ${escapeHtml(String(value))}</li>`
                                        ).join('')}
                                    </ul>
                                </div>
                            ` : ''}
                        </div>
                    `;
                    }).join('')}
                </div>
            </div>
        `;
    }).join('');
    
    console.log('Generated HTML length:', html.length);
    toolsList.innerHTML = html;
    console.log('HTML inserted into toolsList');
}

/**
 * Подключить все серверы
 */
async function connectAllServers() {
    try {
        const response = await fetch(`${API_BASE}/mcp-servers/connect`, {
            method: 'POST'
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to connect servers');
        }
        
        showStatus('Все серверы подключены', 'success');
        setTimeout(() => loadServers(), 1000);
    } catch (error) {
        console.error('Failed to connect servers:', error);
        showStatus(`Ошибка подключения: ${error.message}`, 'error');
    }
}

/**
 * Отключить все серверы
 */
async function disconnectAllServers() {
    try {
        const response = await fetch(`${API_BASE}/mcp-servers/disconnect`, {
            method: 'POST'
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to disconnect servers');
        }
        
        showStatus('Все серверы отключены', 'success');
        setTimeout(() => loadServers(), 1000);
    } catch (error) {
        console.error('Failed to disconnect servers:', error);
        showStatus(`Ошибка отключения: ${error.message}`, 'error');
    }
}

/**
 * Показать статус сообщение
 */
function showStatus(message, type = 'info') {
    const statusDiv = document.getElementById('serversStatus');
    const className = type === 'error' ? 'error' : type === 'success' ? 'success' : 'info';
    statusDiv.innerHTML = `<div class="${className}">${escapeHtml(message)}</div>`;
    setTimeout(() => {
        statusDiv.innerHTML = '';
    }, 5000);
}

/**
 * Экранирование HTML
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

