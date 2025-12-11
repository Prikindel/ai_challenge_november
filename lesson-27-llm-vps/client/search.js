// JavaScript для страницы поиска

// Используем глобальную константу из app.js
var API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Выполнение поиска
async function performSearch() {
    const query = document.getElementById('searchQuery').value.trim();
    if (!query) {
        showMessage('Введите поисковый запрос', 'error');
        return;
    }
    
    const limit = parseInt(document.getElementById('searchLimit').value) || 10;
    const statusDiv = document.getElementById('searchStatus');
    const resultsDiv = document.getElementById('searchResults');
    
    statusDiv.className = 'status';
    statusDiv.textContent = 'Поиск...';
    resultsDiv.innerHTML = '';
    
    try {
        const response = await fetch(`${API_BASE}/search/query`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ query, limit })
        });
        
        const data = await response.json();
        
        if (data.success && data.results && data.results.length > 0) {
            statusDiv.className = 'status success';
            statusDiv.textContent = `Найдено результатов: ${data.results.length}`;
            
            resultsDiv.innerHTML = data.results.map((result, index) => {
                // Рендерим Markdown в HTML
                const markdownHtml = marked.parse(result.content);
                
                return `
                <div class="search-result-card">
                    <div class="result-header">
                        <span class="result-number">#${index + 1}</span>
                        <span class="result-similarity">Сходство: ${(result.similarity * 100).toFixed(1)}%</span>
                    </div>
                    <div class="result-content markdown-content">
                        ${markdownHtml}
                    </div>
                    <div class="result-footer">
                        <span class="result-source">Источник: ${escapeHtml(result.documentTitle || result.documentFilePath || 'Неизвестно')}</span>
                        <span class="result-chunk">Чанк #${result.chunkIndex}</span>
                    </div>
                </div>
            `;
            }).join('');
        } else {
            statusDiv.className = 'status';
            statusDiv.textContent = 'Результаты не найдены';
            resultsDiv.innerHTML = '<p>Попробуйте изменить запрос</p>';
        }
    } catch (error) {
        statusDiv.className = 'status error';
        statusDiv.textContent = `Ошибка: ${error.message}`;
        resultsDiv.innerHTML = '';
    }
}

// Экранирование HTML для безопасности
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Поиск по Enter
document.getElementById('searchQuery')?.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        performSearch();
    }
});

