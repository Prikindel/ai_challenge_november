// JavaScript для страницы тестирования цитат

// Используем глобальную константу из app.js
var API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Запуск теста (глобальная функция)
window.runCitationTest = async function() {
    const questionsText = document.getElementById('questionsInput').value.trim();
    if (!questionsText) {
        showMessage('Введите вопросы', 'error');
        return;
    }
    
    const questions = questionsText.split('\n')
        .map(q => q.trim())
        .filter(q => q.length > 0);
    
    if (questions.length === 0) {
        showMessage('Введите хотя бы один вопрос', 'error');
        return;
    }
    
    if (questions.length > 20) {
        showMessage('Максимум 20 вопросов', 'error');
        return;
    }
    
    const topK = parseInt(document.getElementById('testTopK').value) || 5;
    const minSimilarity = parseFloat(document.getElementById('testMinSimilarity').value) || 0.4;
    const strategy = document.getElementById('testStrategy').value;
    
    const statusDiv = document.getElementById('testStatus');
    const resultsDiv = document.getElementById('testResults');
    const testButton = document.getElementById('testButton');
    
    // Показываем статус загрузки
    statusDiv.className = 'status';
    statusDiv.textContent = `Выполняется тест на ${questions.length} вопросах... Это может занять несколько минут.`;
    resultsDiv.style.display = 'none';
    testButton.disabled = true;
    testButton.textContent = 'Тестирование...';
    
    try {
        const response = await fetch(`${API_BASE}/rag/test-citations`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                questions: questions,
                topK: topK,
                minSimilarity: minSimilarity,
                applyFilter: strategy !== 'none',
                strategy: strategy
            })
        });
        
                if (!response.ok) {
                    const errorText = await response.text();
            let errorData;
            try {
                errorData = JSON.parse(errorText);
            } catch (e) {
                errorData = { message: errorText || `HTTP ${response.status}` };
            }
            throw new Error(errorData.message || `HTTP ${response.status}`);
        }
        
        const report = await response.json();
        displayTestResults(report);
        
        statusDiv.className = 'status success';
        statusDiv.textContent = `Тест завершён успешно! Обработано ${report.results.length} вопросов.`;
        resultsDiv.style.display = 'block';
        
    } catch (error) {
        statusDiv.className = 'status error';
        statusDiv.textContent = `Ошибка: ${error.message}`;
        resultsDiv.style.display = 'none';
        
        // Показываем более подробную информацию об ошибке
        if (error.message.includes('fetch')) {
            statusDiv.textContent = `Ошибка подключения к серверу. Убедитесь, что сервер запущен на ${API_BASE}`;
        }
    } finally {
        testButton.disabled = false;
        testButton.textContent = 'Запустить тест';
    }
};

// Показ сообщения
window.showMessage = function(message, type) {
    const statusDiv = document.getElementById('testStatus');
    statusDiv.className = `status ${type}`;
    statusDiv.textContent = message;
};

// Отображение результатов теста
function displayTestResults(report) {
    // Метрики
    displayMetrics(report.metrics);
    
    // Результаты по вопросам
    displayQuestionResults(report.results);
}

// Отображение метрик
function displayMetrics(metrics) {
    document.getElementById('totalQuestions').textContent = metrics.totalQuestions;
    document.getElementById('questionsWithCitations').textContent = 
        `${metrics.questionsWithCitations} (${((metrics.questionsWithCitations / metrics.totalQuestions) * 100).toFixed(1)}%)`;
    document.getElementById('avgCitations').textContent = metrics.averageCitationsPerAnswer.toFixed(2);
    document.getElementById('validCitationsPct').textContent = `${metrics.validCitationsPercentage.toFixed(1)}%`;
    document.getElementById('answersWithoutHallucinations').textContent = 
        `${metrics.answersWithoutHallucinations} (${((metrics.answersWithoutHallucinations / metrics.totalQuestions) * 100).toFixed(1)}%)`;
}

// Отображение результатов по вопросам
function displayQuestionResults(results) {
    const resultsList = document.getElementById('testResultsList');
    
    resultsList.innerHTML = results.map((result, index) => {
        const citationsStatus = result.hasCitations 
            ? `<span class="status-badge success">${result.citationsCount} цитат</span>` 
            : '<span class="status-badge error">Нет цитат</span>';
        
        const validStatus = result.hasCitations
            ? result.validCitationsCount === result.citationsCount
                ? '<span class="status-badge success">Все валидны</span>'
                : `<span class="status-badge warning">${result.validCitationsCount}/${result.citationsCount} валидны</span>`
            : '';
        
        // Сначала рендерим Markdown
        const answerText = result.answer;
        const answerHtml = typeof marked !== 'undefined' 
            ? marked.parse(answerText)
            : escapeHtml(answerText).replace(/\n/g, '<br>');
        
        return `
            <div class="test-result-item">
                <div class="result-header">
                    <h3>Вопрос ${index + 1}: ${escapeHtml(result.question)}</h3>
                    <div class="result-badges">
                        ${citationsStatus}
                        ${validStatus}
                    </div>
                </div>
                <div class="result-answer markdown-content" data-citations='${JSON.stringify(result.citations || [])}'>
                    ${answerHtml}
                </div>
                ${result.citations && result.citations.length > 0 ? `
                    <div class="result-citations">
                        <h4>Цитаты:</h4>
                        <ul>
                            ${result.citations.map(cit => `
                                <li>
                                    <a href="#" class="citation-link" 
                                       data-document-path="${escapeHtml(cit.documentPath)}" 
                                       data-document-title="${escapeHtml(cit.documentTitle)}">
                                        ${escapeHtml(cit.documentTitle || cit.documentPath)}
                                    </a>
                                    ${cit.documentPath !== cit.text ? ` (${escapeHtml(cit.text)})` : ''}
                                </li>
                            `).join('')}
                        </ul>
                    </div>
                ` : ''}
            </div>
        `;
    }).join('');
    
    // Обрабатываем ссылки на цитаты в HTML ответов
    resultsList.querySelectorAll('.result-answer').forEach(answerDiv => {
        const citationsData = answerDiv.getAttribute('data-citations');
        if (citationsData) {
            try {
                const citations = JSON.parse(citationsData);
                if (citations.length > 0 && typeof window.replaceCitationLinksInHTML === 'function') {
                    const citationsForReplace = citations.map(cit => ({
                        text: cit.text || '',
                        title: cit.documentTitle || '',
                        path: cit.documentPath || '',
                        start: 0,
                        end: 0
                    }));
                    window.replaceCitationLinksInHTML(answerDiv, citationsForReplace);
                }
            } catch (e) {
                // Ignore citation parsing errors
            }
        }
    });
    
    // Инициализируем ссылки на цитаты
    resultsList.querySelectorAll('.citation-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const path = link.getAttribute('data-document-path');
            const title = link.getAttribute('data-document-title');
            if (path && typeof window.openDocumentViewer === 'function') {
                window.openDocumentViewer(path, title);
            }
        });
    });
}

// Экранирование HTML (глобальная функция, если не определена в других скриптах)
if (typeof escapeHtml === 'undefined') {
    window.escapeHtml = function(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    };
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    const testButton = document.getElementById('testButton');
    if (testButton && typeof window.runCitationTest === 'function') {
        testButton.addEventListener('click', (e) => {
            e.preventDefault();
            window.runCitationTest();
        });
    }
});
