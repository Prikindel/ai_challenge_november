// Управление базой знаний

const API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    loadStatistics();
    loadCategories();
});

/**
 * Загрузить статистику базы знаний
 */
async function loadStatistics() {
    try {
        const response = await fetch(`${API_BASE}/knowledge-base/statistics`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const stats = await response.json();
        renderStatistics(stats);
    } catch (error) {
        console.error('Failed to load statistics:', error);
        const statistics = document.getElementById('statistics');
        statistics.innerHTML = '<div class="error">Ошибка загрузки статистики</div>';
    }
}

/**
 * Отобразить статистику
 */
function renderStatistics(stats) {
    const statistics = document.getElementById('statistics');
    
    const categoryStats = Object.entries(stats.chunksByCategory || {})
        .map(([category, count]) => `
            <div class="stat-card">
                <h4>${getCategoryDisplayName(category)}</h4>
                <p class="stat-value">${count}</p>
                <p class="stat-label">чанков</p>
            </div>
        `).join('');
    
    statistics.innerHTML = `
        <div class="stat-card stat-card-total">
            <h4>Всего</h4>
            <p class="stat-value">${stats.totalChunks || 0}</p>
            <p class="stat-label">чанков</p>
        </div>
        ${categoryStats}
    `;
}

/**
 * Получить отображаемое имя категории
 */
function getCategoryDisplayName(category) {
    const names = {
        'projects': '📁 Проекты',
        'learning': '📖 Обучение',
        'personal': '👤 Личное',
        'references': '📋 Справочники'
    };
    return names[category] || category;
}

/**
 * Загрузить список категорий
 */
async function loadCategories() {
    try {
        const response = await fetch(`${API_BASE}/knowledge-base/categories`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        // Категории уже отображаются в HTML
    } catch (error) {
        console.error('Failed to load categories:', error);
    }
}

/**
 * Индексировать всю базу знаний
 */
async function indexAll() {
    showStatus('Индексация базы знаний...', 'info');
    
    try {
        const response = await fetch(`${API_BASE}/knowledge-base/index`, {
            method: 'POST'
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to index knowledge base');
        }
        
        showStatus('База знаний успешно проиндексирована', 'success');
        setTimeout(() => loadStatistics(), 2000);
    } catch (error) {
        console.error('Failed to index knowledge base:', error);
        showStatus(`Ошибка индексации: ${error.message}`, 'error');
    }
}

/**
 * Индексировать категорию
 */
async function indexCategory(categoryName) {
    showStatus(`Индексация категории "${categoryName}"...`, 'info');
    
    try {
        const response = await fetch(`${API_BASE}/knowledge-base/index/category/${categoryName}`, {
            method: 'POST'
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to index category');
        }
        
        showStatus(`Категория "${categoryName}" успешно проиндексирована`, 'success');
        setTimeout(() => loadStatistics(), 2000);
    } catch (error) {
        console.error('Failed to index category:', error);
        showStatus(`Ошибка индексации категории: ${error.message}`, 'error');
    }
}

/**
 * Выполнить поиск
 */
async function performSearch() {
    const query = document.getElementById('searchQuery').value.trim();
    const category = document.getElementById('searchCategory').value;
    
    if (!query) {
        showStatus('Введите запрос для поиска', 'error');
        return;
    }
    
    showStatus('Поиск...', 'info');
    
    try {
        const params = new URLSearchParams({ query });
        if (category) {
            params.append('category', category);
        }
        
        const response = await fetch(`${API_BASE}/knowledge-base/search?${params}`);
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to search');
        }
        
        const results = await response.json();
        renderSearchResults(results);
        
        if (results.length === 0) {
            showStatus('Ничего не найдено', 'info');
        } else {
            showStatus(`Найдено ${results.length} результатов`, 'success');
        }
    } catch (error) {
        console.error('Failed to search:', error);
        showStatus(`Ошибка поиска: ${error.message}`, 'error');
    }
}

/**
 * Отобразить результаты поиска
 */
function renderSearchResults(results) {
    const searchResults = document.getElementById('searchResults');
    
    if (results.length === 0) {
        searchResults.innerHTML = '<div class="empty">Ничего не найдено</div>';
        return;
    }
    
    searchResults.innerHTML = results.map((result, index) => `
        <div class="search-result-card">
            <div class="result-header">
                <span class="result-number">#${index + 1}</span>
                <span class="result-source">${escapeHtml(result.source)}</span>
                <span class="result-similarity">Сходство: ${(result.similarity * 100).toFixed(1)}%</span>
            </div>
            <div class="result-category">Категория: ${getCategoryDisplayName(result.category)}</div>
            <div class="result-content">${escapeHtml(result.content)}</div>
        </div>
    `).join('');
}

/**
 * Показать статус сообщение
 */
function showStatus(message, type = 'info') {
    const statusDiv = document.getElementById('statusMessage');
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

