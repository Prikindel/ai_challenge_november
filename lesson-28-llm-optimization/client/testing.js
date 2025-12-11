// Скрипт для тестирования LLM

let testHistory = [];
const HISTORY_STORAGE_KEY = 'llm_test_history';

// Загрузка доступных тестов
async function loadTests() {
    try {
        const response = await fetch('/api/test/tests');
        const tests = await response.json();
        
        const select = document.getElementById('testSelect');
        select.innerHTML = '';
        
        tests.forEach(test => {
            const option = document.createElement('option');
            option.value = test.id;
            option.textContent = `${test.name} (${test.questionCount} вопросов)`;
            select.appendChild(option);
        });
    } catch (error) {
        console.error('Failed to load tests:', error);
        document.getElementById('testSelect').innerHTML = '<option value="">Ошибка загрузки</option>';
    }
}

// Запуск теста
async function runTest() {
    const testSelect = document.getElementById('testSelect');
    const testId = testSelect.value;
    
    if (!testId) {
        alert('Выберите тест для запуска');
        return;
    }
    
    const runButton = document.getElementById('runTestButton');
    runButton.disabled = true;
    runButton.textContent = 'Запуск теста...';
    
    const resultsDiv = document.getElementById('testResults');
    resultsDiv.innerHTML = '<p>Запуск теста...</p>';
    
    try {
        // Получаем текущие настройки
        let savedSettings = null;
        try {
            const saved = localStorage.getItem('llm_optimization_settings');
            if (saved) {
                savedSettings = JSON.parse(saved);
            }
        } catch (e) {
            console.warn('Failed to load saved settings:', e);
        }
        
        // Формируем запрос
        const request = {
            testId: testId,
            templateId: savedSettings?.templateId || 'default',
            parameters: savedSettings ? {
                temperature: savedSettings.temperature,
                maxTokens: savedSettings.maxTokens,
                topP: savedSettings.topP,
                topK: savedSettings.topK,
                repeatPenalty: savedSettings.repeatPenalty,
                contextWindow: savedSettings.contextWindow,
                seed: savedSettings.seed
            } : null
        };
        
        const response = await fetch('/api/test/run', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(request)
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        
        // Добавляем результат в историю
        testHistory.push(result);
        saveTestHistory();
        displayTestResult(result);
        updateTestHistory();
        
        document.getElementById('compareButton').disabled = false;
    } catch (error) {
        console.error('Test run error:', error);
        resultsDiv.innerHTML = `<p style="color: red;">Ошибка запуска теста: ${error.message}</p>`;
    } finally {
        runButton.disabled = false;
        runButton.textContent = 'Запустить тест';
    }
}

// Отображение результата теста
function displayTestResult(result) {
    const resultsDiv = document.getElementById('testResults');
    
    const html = `
        <div style="background: #e8f5e9; border: 1px solid #4caf50; border-radius: 8px; padding: 15px; margin-bottom: 15px;">
            <h4>Результаты теста</h4>
            <p><strong>Конфигурация:</strong> <code>${result.configuration}</code></p>
            <p><strong>Общее время:</strong> ${result.totalTime}ms</p>
            <p><strong>Среднее время ответа:</strong> ${result.averageTime}ms</p>
            <p><strong>Количество вопросов:</strong> ${result.results.length}</p>
        </div>
        <div style="max-height: 500px; overflow-y: auto;">
            ${result.results.map((r, idx) => `
                <div style="border: 1px solid #ddd; border-radius: 8px; padding: 15px; margin-bottom: 10px;">
                    <h5>Вопрос ${idx + 1}: ${r.question}</h5>
                    <p><strong>Время ответа:</strong> ${r.responseTime}ms</p>
                    ${r.tokenCount ? `<p><strong>Токены:</strong> ${r.tokenCount}</p>` : ''}
                    <div style="background: #f5f5f5; padding: 10px; border-radius: 4px; margin-top: 10px;">
                        <strong>Ответ:</strong>
                        <pre style="white-space: pre-wrap; word-wrap: break-word;">${r.answer}</pre>
                    </div>
                </div>
            `).join('')}
        </div>
    `;
    
    resultsDiv.innerHTML = html;
}

// Сохранение истории тестов
function saveTestHistory() {
    try {
        // Ограничиваем историю последними 10 тестами
        const historyToSave = testHistory.slice(-10);
        localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(historyToSave));
    } catch (error) {
        console.error('Failed to save test history:', error);
    }
}

// Загрузка истории тестов
function loadTestHistory() {
    try {
        const saved = localStorage.getItem(HISTORY_STORAGE_KEY);
        if (saved) {
            testHistory = JSON.parse(saved);
            updateTestHistory();
        }
    } catch (error) {
        console.error('Failed to load test history:', error);
    }
}

// Обновление отображения истории
function updateTestHistory() {
    const historyDiv = document.getElementById('testHistory');
    
    if (testHistory.length === 0) {
        historyDiv.innerHTML = '<p>История тестов пуста</p>';
        return;
    }
    
    const html = `
        <div style="max-height: 300px; overflow-y: auto;">
            ${testHistory.slice().reverse().map((result, idx) => `
                <div style="border: 1px solid #ddd; border-radius: 8px; padding: 10px; margin-bottom: 10px; cursor: pointer;" 
                     onclick='displayTestResult(${JSON.stringify(result)})'>
                    <strong>Тест ${testHistory.length - idx}</strong> - ${result.configuration}
                    <br><small>Время: ${result.totalTime}ms, Среднее: ${result.averageTime}ms</small>
                </div>
            `).join('')}
        </div>
    `;
    
    historyDiv.innerHTML = html;
}

// Сравнение результатов (простая версия)
function compareResults() {
    if (testHistory.length < 2) {
        alert('Нужно запустить хотя бы 2 теста для сравнения');
        return;
    }
    
    const lastTwo = testHistory.slice(-2);
    
    const comparisonHtml = `
        <div style="background: #fff3cd; border: 1px solid #ffc107; border-radius: 8px; padding: 15px; margin-top: 20px;">
            <h4>Сравнение результатов</h4>
            <table style="width: 100%; border-collapse: collapse;">
                <tr>
                    <th style="border: 1px solid #ddd; padding: 8px;">Метрика</th>
                    <th style="border: 1px solid #ddd; padding: 8px;">Тест 1</th>
                    <th style="border: 1px solid #ddd; padding: 8px;">Тест 2</th>
                </tr>
                <tr>
                    <td style="border: 1px solid #ddd; padding: 8px;">Общее время</td>
                    <td style="border: 1px solid #ddd; padding: 8px;">${lastTwo[0].totalTime}ms</td>
                    <td style="border: 1px solid #ddd; padding: 8px;">${lastTwo[1].totalTime}ms</td>
                </tr>
                <tr>
                    <td style="border: 1px solid #ddd; padding: 8px;">Среднее время</td>
                    <td style="border: 1px solid #ddd; padding: 8px;">${lastTwo[0].averageTime}ms</td>
                    <td style="border: 1px solid #ddd; padding: 8px;">${lastTwo[1].averageTime}ms</td>
                </tr>
                <tr>
                    <td style="border: 1px solid #ddd; padding: 8px;">Конфигурация</td>
                    <td style="border: 1px solid #ddd; padding: 8px;"><code>${lastTwo[0].configuration}</code></td>
                    <td style="border: 1px solid #ddd; padding: 8px;"><code>${lastTwo[1].configuration}</code></td>
                </tr>
            </table>
        </div>
    `;
    
    document.getElementById('testResults').insertAdjacentHTML('afterend', comparisonHtml);
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    loadTests();
    loadTestHistory();
});

