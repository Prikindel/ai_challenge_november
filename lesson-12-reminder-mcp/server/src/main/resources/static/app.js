const API_BASE = 'http://localhost:8080/api';

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
        
        if (data.sources && Object.keys(data.sources).length > 0) {
            let html = '';
            for (const [sourceId, isConnected] of Object.entries(data.sources)) {
                const statusClass = isConnected ? 'connected' : 'disconnected';
                const statusText = isConnected ? 'Подключено' : 'Отключено';
                const sourceName = getSourceDisplayName(sourceId);
                
                html += `
                    <div class="status-item ${statusClass}">
                        <span class="status-item-name">${sourceName}</span>
                        <span class="status-badge ${statusClass}">${statusText}</span>
                    </div>
                `;
            }
            
            html += `
                <div style="margin-top: 10px; padding: 10px; background: #f0f9ff; border-radius: 8px; font-size: 0.9em; color: #0369a1;">
                    Всего: ${data.connectedCount} из ${data.totalCount} подключено
                </div>
            `;
            
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="error">Нет доступных источников данных</div>';
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
            
            for (const sourceTools of data.tools) {
                const sourceName = getSourceDisplayName(sourceTools.source);
                
                for (const tool of sourceTools.tools) {
                    html += `
                        <div class="tool-card">
                            <div class="tool-name">${tool.name}</div>
                            <div class="tool-description">${tool.description || 'Нет описания'}</div>
                            <div>
                                <span class="tool-source">${sourceName}</span>
                            </div>
                        </div>
                    `;
                }
            }
            
            if (html === '') {
                html = '<div class="error">Нет доступных инструментов</div>';
            } else {
                html = `<div style="margin-bottom: 15px; color: #6b7280; font-size: 0.9em;">
                    Всего инструментов: ${data.total}
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

// Получить отображаемое имя источника
function getSourceDisplayName(sourceId) {
    const names = {
        'webChat': 'Web Chat',
        'telegram': 'Telegram'
    };
    return names[sourceId] || sourceId;
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    // Загружаем данные при загрузке страницы
    loadConnections();
    loadTools();
    
    // Обработчики кнопок обновления
    document.getElementById('refreshConnectionsBtn').addEventListener('click', loadConnections);
    document.getElementById('refreshToolsBtn').addEventListener('click', loadTools);
    
    // Автообновление каждые 5 секунд
    setInterval(() => {
        loadConnections();
        loadTools();
    }, 5000);
});

