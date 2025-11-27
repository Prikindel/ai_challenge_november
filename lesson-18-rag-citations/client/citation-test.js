// JavaScript для страницы тестирования цитат

const API_BASE = 'http://localhost:8080/api';

// Запуск теста
async function runCitationTest() {
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
            const errorData = await response.json().catch(() => ({ message: 'Unknown error' }));
            throw new Error(errorData.message || `HTTP ${response.status}`);
        }
        
        const report = await response.json();
        
        // Отображаем результаты
        displayTestResults(report);
        
        statusDiv.className = 'status success';
        statusDiv.textContent = `Тест завершён успешно! Обработано ${report.results.length} вопросов.`;
        resultsDiv.style.display = 'block';
        
    } catch (error) {
        statusDiv.className = 'status error';
        statusDiv.textContent = `Ошибка: ${error.message}`;
        resultsDiv.style.display = 'none';
    } finally {
        testButton.disabled = false;
        testButton.textContent = 'Запустить тест';
    }
}

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
        
        // Извлекаем цитаты и делаем их кликабельными
        let answerText = result.answer;
        if (result.citations && result.citations.length > 0) {
            const citations = result.citations.map(cit => ({
                text: cit.text,
                title: cit.documentTitle,
                path: cit.documentPath,
                start: 0,
                end: 0
            }));
            answerText = replaceCitationsWithLinks(answerText, citations);
        }
        
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
                <div class="result-answer markdown-content">
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
    
    // Инициализируем ссылки на цитаты
    resultsList.querySelectorAll('.citation-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const path = link.getAttribute('data-document-path');
            const title = link.getAttribute('data-document-title');
            if (typeof window.openDocumentViewer === 'function') {
                window.openDocumentViewer(path, title);
            }
        });
    });
}

// Показ сообщения
function showMessage(message, type) {
    const statusDiv = document.getElementById('testStatus');
    statusDiv.className = `status ${type}`;
    statusDiv.textContent = message;
}

// Экранирование HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

