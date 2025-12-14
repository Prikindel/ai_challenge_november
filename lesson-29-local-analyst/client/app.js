// API базовый URL
const API_BASE = '';

// Загрузка файла отключена - используется БД из урока 24
async function uploadFile(type) {
    showStatus('Загрузка данных отключена. Используется БД из урока 24.', 'info');
}

// Показать статус
function showStatus(message, type = 'info') {
    const statusDiv = document.getElementById('uploadStatus');
    if (!statusDiv) return;
    
    statusDiv.textContent = message;
    statusDiv.className = `status ${type}`;
    
    if (type === 'success' || type === 'error') {
        setTimeout(() => {
            statusDiv.textContent = '';
            statusDiv.className = 'status';
        }, 5000);
    }
}

// Загрузка статистики
async function loadStats() {
    const statsDiv = document.getElementById('stats');
    if (!statsDiv) return;
    
    try {
        const response = await fetch('/api/data/stats');
        if (!response.ok) {
            throw new Error('Ошибка загрузки статистики');
        }
        
        const stats = await response.json();
        
        statsDiv.innerHTML = `
            <div class="stat-item">
                <span class="stat-label">Всего записей:</span>
                <span class="stat-value">${stats.total}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Положительные:</span>
                <span class="stat-value">${stats.bySource.positive || 0}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Отрицательные:</span>
                <span class="stat-value">${stats.bySource.negative || 0}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Нейтральные:</span>
                <span class="stat-value">${stats.bySource.neutral || 0}</span>
            </div>
        `;
    } catch (error) {
        statsDiv.innerHTML = `<p class="error">Ошибка: ${error.message}</p>`;
    }
}

// Установить вопрос из примера
function setQuestion(question) {
    const input = document.getElementById('questionInput');
    if (input) {
        input.value = question;
    }
}

// Задать вопрос
async function askQuestion() {
    const questionInput = document.getElementById('questionInput');
    const sourceFilter = document.getElementById('sourceFilter');
    const answerSection = document.getElementById('answerSection');
    const answerDiv = document.getElementById('answer');
    const loadingDiv = document.getElementById('loading');
    const askBtn = document.getElementById('askBtn');
    
    const question = questionInput.value.trim();
    
    if (!question) {
        alert('Пожалуйста, введите вопрос');
        return;
    }
    
    // Показываем загрузку
    loadingDiv.style.display = 'block';
    answerSection.style.display = 'none';
    askBtn.disabled = true;
    askBtn.textContent = 'Анализирую...';
    
    try {
        const request = {
            question: question,
            source: sourceFilter.value || null,
            limit: 1000
        };
        
        const response = await fetch('/api/analyze', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(request)
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Ошибка анализа');
        }
        
        const result = await response.json();
        
        // Показываем ответ
        answerDiv.innerHTML = `
            <div class="answer-question">
                <strong>Вопрос:</strong> ${result.question}
            </div>
            <div class="answer-text">
                ${formatAnswer(result.answer)}
            </div>
            <div class="answer-meta">
                <small>Проанализировано записей: ${result.recordsCount}</small>
            </div>
        `;
        
        answerSection.style.display = 'block';
    } catch (error) {
        answerDiv.innerHTML = `<p class="error">Ошибка: ${error.message}</p>`;
        answerSection.style.display = 'block';
    } finally {
        loadingDiv.style.display = 'none';
        askBtn.disabled = false;
        askBtn.textContent = 'Задать вопрос';
    }
}

// Форматирование ответа (поддержка markdown-подобного форматирования)
function formatAnswer(text) {
    // Простое форматирование: заменяем переносы строк на <br>
    return text
        .replace(/\n\n/g, '</p><p>')
        .replace(/\n/g, '<br>')
        .replace(/### (.*?)(<br>|$)/g, '<h3>$1</h3>')
        .replace(/## (.*?)(<br>|$)/g, '<h2>$1</h2>')
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>');
}

// Загрузка статистики при загрузке страницы
if (typeof loadStats === 'function') {
    document.addEventListener('DOMContentLoaded', loadStats);
}

// Поддержка Enter для отправки вопроса
const questionInput = document.getElementById('questionInput');
if (questionInput) {
    questionInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && e.ctrlKey) {
            askQuestion();
        }
    });
}
