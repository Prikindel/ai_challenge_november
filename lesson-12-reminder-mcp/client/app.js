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

// Загрузка статуса планировщика
async function loadSchedulerStatus() {
    const container = document.getElementById('schedulerStatus');
    container.innerHTML = '<div class="loading">Загрузка...</div>';
    
    try {
        const response = await fetch(`${API_BASE}/scheduler`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success && data.status) {
            const status = data.status;
            const statusClass = status.isRunning ? 'connected' : 'disconnected';
            const statusText = status.isRunning ? 'Работает' : 'Остановлен';
            
            const html = `
                <div class="status-item ${statusClass}">
                    <span class="status-item-name">Статус</span>
                    <span class="status-badge ${statusClass}">${statusText}</span>
                </div>
                <div class="status-item">
                    <span class="status-item-name">Включен</span>
                    <span>${status.enabled ? 'Да' : 'Нет'}</span>
                </div>
                <div class="status-item">
                    <span class="status-item-name">Интервал</span>
                    <span>${status.intervalMinutes} минут</span>
                </div>
                <div class="status-item">
                    <span class="status-item-name">Период анализа</span>
                    <span>${status.periodHours} часов</span>
                </div>
                <div class="status-item">
                    <span class="status-item-name">Активный источник</span>
                    <span>${getSourceDisplayName(status.activeSource)}</span>
                </div>
            `;
            
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="error">Ошибка загрузки статуса</div>';
        }
    } catch (error) {
        console.error('Error loading scheduler status:', error);
        container.innerHTML = `<div class="error">Ошибка загрузки: ${error.message}</div>`;
    }
}

// Загрузка истории summary
async function loadSummaries() {
    const container = document.getElementById('summariesList');
    container.innerHTML = '<div class="loading">Загрузка...</div>';
    
    try {
        const response = await fetch(`${API_BASE}/summaries?limit=20`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success && data.summaries) {
            const summaries = data.summaries;
            
            if (summaries.length === 0) {
                container.innerHTML = '<div class="info">Нет созданных summary</div>';
                return;
            }
            
            let html = '';
            summaries.forEach(summary => {
                const generatedAt = new Date(summary.generatedAt).toLocaleString('ru-RU');
                const periodStart = new Date(summary.periodStart).toLocaleString('ru-RU');
                const periodEnd = new Date(summary.periodEnd).toLocaleString('ru-RU');
                const sourceName = getSourceDisplayName(summary.source);
                const deliveredIcon = summary.deliveredToTelegram ? '✓' : '✗';
                
                html += `
                    <div class="summary-card">
                        <div class="summary-header">
                            <div class="summary-source">${sourceName}</div>
                            <div class="summary-date">${generatedAt}</div>
                        </div>
                        <div class="summary-period">
                            Период: ${periodStart} - ${periodEnd}
                        </div>
                        <div class="summary-text">${summary.summaryText}</div>
                        <div class="summary-footer">
                            <span>Сообщений: ${summary.messageCount}</span>
                            <span>Telegram: ${deliveredIcon}</span>
                        </div>
                    </div>
                `;
            });
            
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="error">Ошибка загрузки summary</div>';
        }
    } catch (error) {
        console.error('Error loading summaries:', error);
        container.innerHTML = `<div class="error">Ошибка загрузки: ${error.message}</div>`;
    }
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    // Загружаем данные при загрузке страницы
    loadSchedulerStatus();
    loadConnections();
    loadTools();
    loadSummaries();
    
    // Обработчики кнопок обновления
    document.getElementById('refreshSchedulerBtn').addEventListener('click', loadSchedulerStatus);
    document.getElementById('refreshConnectionsBtn').addEventListener('click', loadConnections);
    document.getElementById('refreshToolsBtn').addEventListener('click', loadTools);
    document.getElementById('refreshSummariesBtn').addEventListener('click', loadSummaries);
    
    // Автообновление каждые 10 секунд
    setInterval(() => {
        loadSchedulerStatus();
        loadConnections();
        loadTools();
        loadSummaries();
    }, 10000);
});

