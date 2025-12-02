// Ревью кода

// Используем глобальную константу из app.js
const API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Текущий ID ревью
let currentReviewId = null;
let pollInterval = null;

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('reviewForm');
    form.addEventListener('submit', handleReviewSubmit);
});

/**
 * Обработка отправки формы ревью
 */
async function handleReviewSubmit(e) {
    e.preventDefault();
    
    const baseBranch = document.getElementById('baseBranch').value.trim();
    const headBranch = document.getElementById('headBranch').value.trim();
    
    if (!baseBranch || !headBranch) {
        showError('Пожалуйста, укажите обе ветки');
        return;
    }
    
    // Очищаем предыдущие результаты
    clearResults();
    
    // Показываем статус "pending"
    showStatus('pending', 'Запуск ревью...');
    
    try {
        // Отправляем запрос на ревью
        const response = await fetch(`${API_BASE}/review/pr`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                base: baseBranch,
                head: headBranch
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Ошибка при запуске ревью');
        }
        
        const data = await response.json();
        currentReviewId = data.reviewId;
        
        // Начинаем опрос статуса
        startPolling(data.reviewId);
        
    } catch (error) {
        console.error('Failed to start review:', error);
        showStatus('error', `Ошибка: ${error.message}`);
    }
}

/**
 * Начинает опрос статуса ревью
 */
function startPolling(reviewId) {
    // Очищаем предыдущий интервал, если есть
    if (pollInterval) {
        clearInterval(pollInterval);
    }
    
    // Опрашиваем сразу
    pollReviewStatus(reviewId);
    
    // Затем каждые 2 секунды
    pollInterval = setInterval(() => {
        pollReviewStatus(reviewId);
    }, 2000);
}

/**
 * Опрашивает статус ревью
 */
async function pollReviewStatus(reviewId) {
    try {
        const response = await fetch(`${API_BASE}/review/pr/${reviewId}`);
        
        if (!response.ok) {
            throw new Error('Ошибка при получении статуса ревью');
        }
        
        const review = await response.json();
        
        // Проверяем статус
        const status = determineStatus(review);
        
        if (status === 'pending') {
            showStatus('pending', 'Ревью выполняется...');
        } else if (status === 'error') {
            clearInterval(pollInterval);
            showStatus('error', review.summary || 'Ошибка при выполнении ревью');
        } else if (status === 'completed') {
            clearInterval(pollInterval);
            showStatus('completed', 'Ревью завершено!');
            displayResults(review);
        }
        
    } catch (error) {
        console.error('Failed to poll review status:', error);
        clearInterval(pollInterval);
        showStatus('error', `Ошибка: ${error.message}`);
    }
}

/**
 * Определяет статус ревью
 */
function determineStatus(review) {
    if (review.summary && review.summary.includes('Ошибка')) {
        return 'error';
    }
    if (review.issues.length === 0 && review.suggestions.length === 0 && 
        review.summary.includes('Ревью выполняется')) {
        return 'pending';
    }
    return 'completed';
}

/**
 * Отображает результаты ревью
 */
function displayResults(review) {
    const resultsDiv = document.getElementById('reviewResults');
    resultsDiv.style.display = 'block';
    
    // Отображаем резюме
    displaySummary(review);
    
    // Отображаем изменённые файлы
    displayChangedFiles(review.changedFiles);
    
    // Отображаем проблемы
    displayIssues(review.issues);
    
    // Отображаем предложения
    displaySuggestions(review.suggestions);
}

/**
 * Отображает резюме ревью
 */
function displaySummary(review) {
    const summaryDiv = document.getElementById('reviewSummary');
    
    let scoreClass = '';
    let scoreText = '';
    if (review.overallScore) {
        scoreClass = review.overallScore;
        scoreText = translateScore(review.overallScore);
    }
    
    summaryDiv.innerHTML = `
        <h3>Резюме ревью</h3>
        <p>${review.summary}</p>
        ${review.overallScore ? `<span class="score ${scoreClass}">${scoreText}</span>` : ''}
        <div style="margin-top: 15px; color: #666; font-size: 14px;">
            <strong>Проблем найдено:</strong> ${review.issues.length} | 
            <strong>Предложений:</strong> ${review.suggestions.length}
        </div>
    `;
}

/**
 * Переводит оценку на русский
 */
function translateScore(score) {
    const translations = {
        'approve': 'Одобрено',
        'request_changes': 'Требуются изменения',
        'comment': 'Комментарии'
    };
    return translations[score] || score;
}

/**
 * Отображает изменённые файлы
 */
function displayChangedFiles(files) {
    const filesDiv = document.getElementById('changedFiles');
    
    if (!files || files.length === 0) {
        filesDiv.style.display = 'none';
        return;
    }
    
    filesDiv.style.display = 'block';
    filesDiv.innerHTML = `
        <h4>Изменённые файлы (${files.length}):</h4>
        <ul>
            ${files.map(file => `<li>${escapeHtml(file)}</li>`).join('')}
        </ul>
    `;
}

/**
 * Отображает проблемы
 */
function displayIssues(issues) {
    const issuesDiv = document.getElementById('issuesList');
    
    if (!issues || issues.length === 0) {
        issuesDiv.innerHTML = '<p style="color: #28a745; font-weight: 600;">✓ Проблем не найдено</p>';
        return;
    }
    
    issuesDiv.innerHTML = `
        <h3 style="margin-bottom: 15px; color: #667eea;">Найденные проблемы (${issues.length})</h3>
        ${issues.map(issue => renderIssue(issue)).join('')}
    `;
}

/**
 * Рендерит одну проблему
 */
function renderIssue(issue) {
    const severityClass = issue.severity || 'medium';
    const typeClass = issue.type || 'STYLE';
    
    return `
        <div class="issue-item ${severityClass}">
            <div class="issue-header">
                <div>
                    <span class="issue-type ${typeClass}">${issue.type}</span>
                    <span class="issue-file">${escapeHtml(issue.file)}</span>
                    ${issue.line ? `<span class="issue-line">:${issue.line}</span>` : ''}
                </div>
                <span style="font-weight: 600; color: #666;">${translateSeverity(issue.severity)}</span>
            </div>
            <div class="issue-message">
                ${escapeHtml(issue.message)}
            </div>
            ${issue.suggestion ? `
                <div class="issue-suggestion">
                    <strong>Предложение:</strong> ${escapeHtml(issue.suggestion)}
                </div>
            ` : ''}
        </div>
    `;
}

/**
 * Переводит серьёзность на русский
 */
function translateSeverity(severity) {
    const translations = {
        'critical': 'Критично',
        'high': 'Высокая',
        'medium': 'Средняя',
        'low': 'Низкая'
    };
    return translations[severity] || severity;
}

/**
 * Отображает предложения
 */
function displaySuggestions(suggestions) {
    const suggestionsDiv = document.getElementById('suggestionsList');
    
    if (!suggestions || suggestions.length === 0) {
        suggestionsDiv.innerHTML = '';
        return;
    }
    
    suggestionsDiv.innerHTML = `
        <h3 style="margin-bottom: 15px; color: #667eea;">Предложения по улучшению (${suggestions.length})</h3>
        ${suggestions.map(suggestion => renderSuggestion(suggestion)).join('')}
    `;
}

/**
 * Рендерит одно предложение
 */
function renderSuggestion(suggestion) {
    return `
        <div class="suggestion-item">
            <div class="suggestion-header">
                <div>
                    <span class="issue-file">${escapeHtml(suggestion.file)}</span>
                    ${suggestion.line ? `<span class="issue-line">:${suggestion.line}</span>` : ''}
                </div>
                <span style="font-weight: 600; color: #666;">${translatePriority(suggestion.priority)}</span>
            </div>
            <div class="issue-message">
                ${escapeHtml(suggestion.message)}
            </div>
        </div>
    `;
}

/**
 * Переводит приоритет на русский
 */
function translatePriority(priority) {
    const translations = {
        'high': 'Высокий',
        'medium': 'Средний',
        'low': 'Низкий'
    };
    return translations[priority] || priority;
}

/**
 * Показывает статус ревью
 */
function showStatus(type, message) {
    const statusDiv = document.getElementById('reviewStatus');
    statusDiv.className = `review-status ${type}`;
    statusDiv.textContent = message;
    statusDiv.style.display = 'block';
}

/**
 * Показывает ошибку
 */
function showError(message) {
    showStatus('error', message);
}

/**
 * Очищает результаты
 */
function clearResults() {
    document.getElementById('reviewResults').style.display = 'none';
    document.getElementById('reviewSummary').innerHTML = '';
    document.getElementById('changedFiles').innerHTML = '';
    document.getElementById('issuesList').innerHTML = '';
    document.getElementById('suggestionsList').innerHTML = '';
    currentReviewId = null;
    if (pollInterval) {
        clearInterval(pollInterval);
        pollInterval = null;
    }
}

/**
 * Экранирует HTML
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

