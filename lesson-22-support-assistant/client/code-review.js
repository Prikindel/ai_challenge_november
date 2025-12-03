// Ревью кода

// Используем глобальную константу из app.js
const API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Текущий ID ревью
let currentReviewId = null;
let pollInterval = null;

// Текущий объект ревью для экспорта
let currentReview = null;

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
 * Переключение между табами
 */
function switchTab(tab) {
    const branchesForm = document.getElementById('reviewForm');
    const diffForm = document.getElementById('diffForm');
    const tabBtns = document.querySelectorAll('.tab-btn');
    
    tabBtns.forEach(btn => btn.classList.remove('active'));
    
    if (tab === 'branches') {
        branchesForm.style.display = 'block';
        diffForm.style.display = 'none';
        tabBtns[0].classList.add('active');
    } else {
        branchesForm.style.display = 'none';
        diffForm.style.display = 'block';
        tabBtns[1].classList.add('active');
    }
}

/**
 * Обработка выбора diff файла
 */
function handleDiffFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;
    
    const reader = new FileReader();
    reader.onload = (e) => {
        document.getElementById('diffText').value = e.target.result;
    };
    reader.readAsText(file);
}

/**
 * Обработка отправки формы diff
 */
async function handleDiffSubmit() {
    const diffText = document.getElementById('diffText').value.trim();
    const baseBranch = document.getElementById('diffBaseBranch').value.trim() || 'unknown';
    const headBranch = document.getElementById('diffHeadBranch').value.trim() || 'unknown';
    
    if (!diffText) {
        showError('Пожалуйста, загрузите diff файл или вставьте diff');
        return;
    }
    
    // Очищаем предыдущие результаты
    clearResults();
    
    // Показываем статус "pending"
    showStatus('pending', 'Запуск ревью по diff...');
    
    try {
        // Извлекаем список файлов из diff
        const changedFiles = extractFilesFromDiff(diffText);
        
        // Отправляем запрос на ревью по diff
        const response = await fetch(`${API_BASE}/review/diff`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                diff: diffText,
                baseBranch: baseBranch,
                headBranch: headBranch,
                changedFiles: changedFiles
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
        console.error('Failed to start diff review:', error);
        showStatus('error', `Ошибка: ${error.message}`);
    }
}

/**
 * Извлекает список файлов из diff
 */
function extractFilesFromDiff(diff) {
    const files = new Set();
    diff.split('\n').forEach(line => {
        if (line.startsWith('+++') || line.startsWith('---')) {
            const filePath = line.substring(3).trim();
            if (filePath && !filePath.startsWith('/dev/null')) {
                // Убираем префикс "a/" или "b/"
                const cleanPath = filePath.replace(/^[ab]\//, '');
                files.add(cleanPath);
            }
        }
    });
    return Array.from(files);
}

/**
 * Отображает результаты ревью
 */
function displayResults(review) {
    currentReview = review; // Сохраняем для экспорта
    
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
    const exportButtons = document.getElementById('exportButtons');
    
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
    
    // Показываем кнопки экспорта
    exportButtons.style.display = 'flex';
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

/**
 * Экспорт ревью в различных форматах
 */
function exportReview(format) {
    if (!currentReview) {
        alert('Нет данных для экспорта');
        return;
    }
    
    let content = '';
    let filename = '';
    let mimeType = '';
    
    switch (format) {
        case 'json':
            content = JSON.stringify(currentReview, null, 2);
            filename = `code-review-${currentReview.reviewId}.json`;
            mimeType = 'application/json';
            break;
            
        case 'markdown':
            content = exportToMarkdown(currentReview);
            filename = `code-review-${currentReview.reviewId}.md`;
            mimeType = 'text/markdown';
            break;
            
        case 'text':
            content = exportToText(currentReview);
            filename = `code-review-${currentReview.reviewId}.txt`;
            mimeType = 'text/plain';
            break;
    }
    
    // Создаём blob и скачиваем
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

/**
 * Экспорт ревью в Markdown
 */
function exportToMarkdown(review) {
    let md = `# Code Review\n\n`;
    md += `**Base Branch:** ${review.baseBranch}\n`;
    md += `**Head Branch:** ${review.headBranch}\n`;
    md += `**Review ID:** ${review.reviewId}\n`;
    md += `**Date:** ${new Date(review.timestamp).toLocaleString()}\n\n`;
    
    if (review.overallScore) {
        md += `**Overall Score:** ${translateScore(review.overallScore)}\n\n`;
    }
    
    md += `## Summary\n\n${review.summary}\n\n`;
    
    if (review.changedFiles && review.changedFiles.length > 0) {
        md += `## Changed Files (${review.changedFiles.length})\n\n`;
        review.changedFiles.forEach(file => {
            md += `- \`${file}\`\n`;
        });
        md += '\n';
    }
    
    if (review.issues && review.issues.length > 0) {
        md += `## Issues (${review.issues.length})\n\n`;
        review.issues.forEach((issue, index) => {
            md += `### ${index + 1}. ${issue.type} - ${translateSeverity(issue.severity)}\n\n`;
            md += `- **File:** \`${issue.file}\`${issue.line ? `:${issue.line}` : ''}\n`;
            md += `- **Message:** ${issue.message}\n`;
            if (issue.suggestion) {
                md += `- **Suggestion:** ${issue.suggestion}\n`;
            }
            md += '\n';
        });
    }
    
    if (review.suggestions && review.suggestions.length > 0) {
        md += `## Suggestions (${review.suggestions.length})\n\n`;
        review.suggestions.forEach((suggestion, index) => {
            md += `### ${index + 1}. ${translatePriority(suggestion.priority)} Priority\n\n`;
            md += `- **File:** \`${suggestion.file}\`${suggestion.line ? `:${suggestion.line}` : ''}\n`;
            md += `- **Message:** ${suggestion.message}\n\n`;
        });
    }
    
    return md;
}

/**
 * Экспорт ревью в текст
 */
function exportToText(review) {
    let text = `CODE REVIEW\n`;
    text += `${'='.repeat(50)}\n\n`;
    text += `Base Branch: ${review.baseBranch}\n`;
    text += `Head Branch: ${review.headBranch}\n`;
    text += `Review ID: ${review.reviewId}\n`;
    text += `Date: ${new Date(review.timestamp).toLocaleString()}\n\n`;
    
    if (review.overallScore) {
        text += `Overall Score: ${translateScore(review.overallScore)}\n\n`;
    }
    
    text += `SUMMARY\n`;
    text += `${'-'.repeat(50)}\n`;
    text += `${review.summary}\n\n`;
    
    if (review.changedFiles && review.changedFiles.length > 0) {
        text += `CHANGED FILES (${review.changedFiles.length})\n`;
        text += `${'-'.repeat(50)}\n`;
        review.changedFiles.forEach(file => {
            text += `  - ${file}\n`;
        });
        text += '\n';
    }
    
    if (review.issues && review.issues.length > 0) {
        text += `ISSUES (${review.issues.length})\n`;
        text += `${'-'.repeat(50)}\n`;
        review.issues.forEach((issue, index) => {
            text += `${index + 1}. [${issue.type}] ${translateSeverity(issue.severity)} - ${issue.file}${issue.line ? `:${issue.line}` : ''}\n`;
            text += `   ${issue.message}\n`;
            if (issue.suggestion) {
                text += `   Suggestion: ${issue.suggestion}\n`;
            }
            text += '\n';
        });
    }
    
    if (review.suggestions && review.suggestions.length > 0) {
        text += `SUGGESTIONS (${review.suggestions.length})\n`;
        text += `${'-'.repeat(50)}\n`;
        review.suggestions.forEach((suggestion, index) => {
            text += `${index + 1}. [${translatePriority(suggestion.priority)} Priority] ${suggestion.file}${suggestion.line ? `:${suggestion.line}` : ''}\n`;
            text += `   ${suggestion.message}\n\n`;
        });
    }
    
    return text;
}

