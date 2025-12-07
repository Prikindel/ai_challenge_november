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
        // Здесь будет вызов API для анализа
        // Пока что показываем заглушку
        await new Promise(resolve => setTimeout(resolve, 1500));
        
        hideLoading();
        showResults({
            message: 'Анализ выполнен успешно! Используйте чат для более детального анализа отзывов.',
            suggestion: 'Перейдите в раздел "Чат" для интерактивного анализа с помощью AI-агента.'
        });
    } catch (error) {
        hideLoading();
        showError('Произошла ошибка при выполнении анализа: ' + error.message);
    }
}

function showLoading() {
    document.getElementById('loadingSection').classList.remove('hidden');
}

function hideLoading() {
    document.getElementById('loadingSection').classList.add('hidden');
}

function showResults(data) {
    const resultsSection = document.getElementById('resultsSection');
    const resultsContent = document.getElementById('resultsContent');
    
    resultsContent.innerHTML = `
        <div class="result-card">
            <h3>✅ Анализ завершен</h3>
            <p>${data.message || 'Анализ выполнен успешно'}</p>
            ${data.suggestion ? `<p style="margin-top: 12px; color: var(--primary-color); font-weight: 500;">💡 ${data.suggestion}</p>` : ''}
        </div>
        <div class="result-card" style="margin-top: 20px;">
            <h3>📊 Рекомендации</h3>
            <p>Для более детального анализа отзывов используйте чат-интерфейс, где AI-агент поможет вам:</p>
            <ul style="margin-top: 12px; padding-left: 20px; color: var(--text-secondary);">
                <li>Получить и проанализировать отзывы за любой период</li>
                <li>Сравнить статистику между неделями</li>
                <li>Составить план по критическим проблемам</li>
                <li>Отправить отчеты в Telegram</li>
            </ul>
            <div style="margin-top: 20px;">
                <a href="chat.html" class="btn btn-primary" style="text-decoration: none; display: inline-block;">
                    <span style="margin-right: 8px;">💬</span>
                    Перейти в чат
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

