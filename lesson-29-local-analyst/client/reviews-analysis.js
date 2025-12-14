// Страница анализа отзывов

const API_BASE = window.API_BASE || 'http://localhost:8080/api';

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('analysisForm');
    if (form) {
        form.addEventListener('submit', handleAnalysisSubmit);
    }
});

async function handleAnalysisSubmit(e) {
    e.preventDefault();
    
    const fromDate = document.getElementById('fromDate').value;
    const toDate = document.getElementById('toDate').value;
    
    // Скрываем предыдущие результаты
    hideAllSections();
    
    // Показываем загрузку
    showLoading();
    
    try {
        console.log('Starting batch analysis...');
        
        // Определяем период
        const periodFrom = fromDate || '';
        const periodTo = toDate || '';
        
        if (!periodFrom || !periodTo) {
            throw new Error('Необходимо указать период (начало и конец)');
        }
        
        // Вызываем endpoint для батчингового анализа
        const analysisResponse = await fetch(`${API_BASE}/reviews/analyze-batch`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                fromDate: periodFrom,
                toDate: periodTo
            })
        });
        
        if (!analysisResponse.ok) {
            const errorData = await analysisResponse.json().catch(() => ({}));
            throw new Error(errorData.message || `HTTP error! status: ${analysisResponse.status}`);
        }
        
        const analysisData = await analysisResponse.json();
        console.log('Batch analysis completed:', analysisData);
        
        hideLoading();
        showResults({
            message: `Анализ выполнен успешно! Обработано ${analysisData.totalProcessed} отзывов, сохранено ${analysisData.totalSaved} саммари в ${analysisData.batchesProcessed} батчах.`,
            analysis: analysisData.message,
            totalProcessed: analysisData.totalProcessed,
            totalSaved: analysisData.totalSaved,
            batchesProcessed: analysisData.batchesProcessed
        });
    } catch (error) {
        console.error('Analysis error:', error);
        hideLoading();
        showError('Произошла ошибка при выполнении анализа: ' + error.message);
    }
}

function showLoading(message = 'Анализ выполняется, пожалуйста, подождите...') {
    const loadingSection = document.getElementById('loadingSection');
    const loadingText = loadingSection.querySelector('.loading-text');
    if (loadingText) {
        loadingText.textContent = message;
    }
    loadingSection.classList.remove('hidden');
}

function hideLoading() {
    document.getElementById('loadingSection').classList.add('hidden');
}

function showResults(data) {
    const resultsSection = document.getElementById('resultsSection');
    const resultsContent = document.getElementById('resultsContent');
    
    let analysisHtml = '';
    if (data.analysis) {
        // Конвертируем markdown в HTML (если используется marked.js)
        if (typeof marked !== 'undefined') {
            analysisHtml = marked.parse(data.analysis);
        } else {
            // Простое форматирование, если marked.js не загружен
            analysisHtml = data.analysis.replace(/\n/g, '<br>');
        }
    }
    
    resultsContent.innerHTML = `
        <div class="result-card">
            <h3>✅ Анализ завершен</h3>
            <p>${data.message || 'Анализ выполнен успешно'}</p>
            ${analysisHtml ? `
                <div style="margin-top: 20px; padding: 16px; background: var(--bg-secondary); border-radius: var(--radius-md); border: 1px solid var(--border-color);">
                    <h4 style="margin-top: 0; margin-bottom: 12px; color: var(--text-primary);">📊 Результаты анализа:</h4>
                    <div style="color: var(--text-secondary); line-height: 1.6;">${analysisHtml}</div>
                </div>
            ` : ''}
        </div>
        <div class="result-card" style="margin-top: 20px;">
            <h3>💡 Дальнейшие действия</h3>
            <p>Для более детального анализа отзывов используйте чат-интерфейс:</p>
            <ul style="margin-top: 12px; padding-left: 20px; color: var(--text-secondary);">
                <li>Сравнить статистику между неделями</li>
                <li>Составить план по критическим проблемам</li>
                <li>Отправить отчеты в Telegram</li>
            </ul>
            <div style="margin-top: 20px;">
                <a href="chat.html${data.sessionId ? `?session=${data.sessionId}` : ''}" class="btn btn-primary" style="text-decoration: none; display: inline-block;">
                    <span style="margin-right: 8px;">💬</span>
                    Перейти в чат${data.sessionId ? ' (продолжить анализ)' : ''}
                </a>
            </div>
        </div>
    `;
    
    resultsSection.classList.remove('hidden');
    resultsSection.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function showError(message) {
    const errorSection = document.getElementById('errorSection');
    const errorMessage = document.getElementById('errorMessage');
    
    errorMessage.textContent = message;
    errorSection.classList.remove('hidden');
    errorSection.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function hideAllSections() {
    document.getElementById('loadingSection').classList.add('hidden');
    document.getElementById('resultsSection').classList.add('hidden');
    document.getElementById('errorSection').classList.add('hidden');
}

