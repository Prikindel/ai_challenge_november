// Ассистент команды

// Используем глобальную константу из app.js
const API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('teamForm');
    form.addEventListener('submit', handleQuestionSubmit);
    
    // Примеры вопросов
    document.querySelectorAll('.example-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const question = btn.getAttribute('data-question');
            document.getElementById('question').value = question;
            document.getElementById('question').focus();
        });
    });
});

/**
 * Обработка отправки вопроса
 */
async function handleQuestionSubmit(e) {
    e.preventDefault();
    
    const question = document.getElementById('question').value.trim();
    
    if (!question) {
        showStatus('error', '❌ Пожалуйста, введите ваш вопрос');
        return;
    }
    
    // Очищаем предыдущие результаты
    clearResults();
    
    // Показываем статус загрузки
    showStatus('loading', '⏳ Обработка вопроса...');
    
    // Отключаем кнопку отправки
    const submitBtn = document.getElementById('submitBtn');
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span>⏳</span> <span>Обработка...</span>';
    
    try {
        const response = await fetch(`${API_BASE}/team/ask`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ question })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Ошибка при обработке вопроса');
        }
        
        const data = await response.json();
        
        // Отображаем результаты
        displayAnswer(data);
        
        showStatus('success', '✅ Ответ получен');
        
    } catch (error) {
        console.error('Error:', error);
        showStatus('error', `❌ Ошибка: ${error.message}`);
    } finally {
        // Включаем кнопку отправки
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<span>💬</span> <span>Задать вопрос</span>';
    }
}

/**
 * Отображение ответа
 */
function displayAnswer(data) {
    const answerSection = document.getElementById('answerSection');
    answerSection.classList.remove('hidden');
    
    // Отображаем ответ
    document.getElementById('answerContent').textContent = data.answer;
    
    // Отображаем задачи
    if (data.tasks && data.tasks.length > 0) {
        displayTasks(data.tasks);
    }
    
    // Отображаем рекомендации
    if (data.recommendations && data.recommendations.length > 0) {
        displayRecommendations(data.recommendations);
    }
    
    // Отображаем действия
    if (data.actions && data.actions.length > 0) {
        displayActions(data.actions);
    }
    
    // Отображаем источники
    if (data.sources && data.sources.length > 0) {
        displaySources(data.sources);
    }
}

/**
 * Отображение задач
 */
function displayTasks(tasks) {
    const tasksList = document.getElementById('tasksList');
    const tasksContent = document.getElementById('tasksContent');
    
    tasksList.classList.remove('hidden');
    tasksContent.innerHTML = '';
    
    tasks.forEach(task => {
        const taskItem = document.createElement('div');
        taskItem.className = `task-item ${task.status.toLowerCase().replace('_', '-')}`;
        
        const statusClass = getStatusClass(task.status);
        const priorityClass = getPriorityClass(task.priority);
        
        taskItem.innerHTML = `
            <div class="task-header">
                <div class="task-title">${escapeHtml(task.title)}</div>
                <div class="task-badges">
                    <span class="badge badge-status">${formatStatus(task.status)}</span>
                    <span class="badge badge-priority ${priorityClass}">${formatPriority(task.priority)}</span>
                </div>
            </div>
            <div class="task-description">${escapeHtml(task.description)}</div>
            ${task.assignee ? `<div style="margin-top: 8px; font-size: 12px; color: #718096;">Исполнитель: ${escapeHtml(task.assignee)}</div>` : ''}
            ${task.blockedBy && task.blockedBy.length > 0 ? `<div style="margin-top: 8px; font-size: 12px; color: #f5576c;">⚠️ Блокируется: ${task.blockedBy.join(', ')}</div>` : ''}
            ${task.blocks && task.blocks.length > 0 ? `<div style="margin-top: 8px; font-size: 12px; color: #3494E6;">🔒 Блокирует: ${task.blocks.join(', ')}</div>` : ''}
        `;
        
        tasksContent.appendChild(taskItem);
    });
}

/**
 * Отображение рекомендаций
 */
function displayRecommendations(recommendations) {
    const recommendationsList = document.getElementById('recommendationsList');
    const recommendationsContent = document.getElementById('recommendationsContent');
    
    recommendationsList.classList.remove('hidden');
    recommendationsContent.innerHTML = '';
    
    recommendations.forEach(rec => {
        const recItem = document.createElement('div');
        recItem.className = 'recommendation-item';
        
        let taskInfo = '';
        if (rec.task) {
            taskInfo = `<div style="margin-top: 8px; font-size: 12px; color: #4a5568;">Задача: ${escapeHtml(rec.task.title)}</div>`;
        }
        
        recItem.innerHTML = `
            <div class="recommendation-priority">Приоритет: ${escapeHtml(rec.priority)}</div>
            <div class="recommendation-reason">${escapeHtml(rec.reason)}</div>
            ${taskInfo}
        `;
        
        recommendationsContent.appendChild(recItem);
    });
}

/**
 * Отображение действий
 */
function displayActions(actions) {
    const actionsList = document.getElementById('actionsList');
    const actionsContent = document.getElementById('actionsContent');
    
    actionsList.classList.remove('hidden');
    actionsContent.innerHTML = '';
    
    actions.forEach(action => {
        const actionItem = document.createElement('div');
        actionItem.className = 'action-item';
        
        let taskInfo = '';
        if (action.task) {
            taskInfo = `<div style="margin-top: 8px; font-size: 12px; color: #2d3748;">Задача: ${escapeHtml(action.task.title)}</div>`;
        }
        
        actionItem.innerHTML = `
            <div class="action-description">
                <strong>${formatActionType(action.type)}:</strong> ${escapeHtml(action.description)}
            </div>
            ${taskInfo}
        `;
        
        actionsContent.appendChild(actionItem);
    });
}

/**
 * Отображение источников
 */
function displaySources(sources) {
    const sourcesList = document.getElementById('sourcesList');
    const sourcesContent = document.getElementById('sourcesContent');
    
    sourcesList.classList.remove('hidden');
    sourcesContent.innerHTML = '';
    
    sources.forEach(source => {
        const sourceItem = document.createElement('div');
        sourceItem.className = 'source-item';
        
        sourceItem.innerHTML = `
            <div class="source-title">${escapeHtml(source.title)}</div>
            <div class="source-content">${escapeHtml(source.content)}</div>
            ${source.url ? `<div style="margin-top: 8px; font-size: 12px; color: #667eea;">📄 ${escapeHtml(source.url)}</div>` : ''}
        `;
        
        sourcesContent.appendChild(sourceItem);
    });
}

/**
 * Очистка результатов
 */
function clearResults() {
    document.getElementById('answerSection').classList.add('hidden');
    document.getElementById('tasksList').classList.add('hidden');
    document.getElementById('recommendationsList').classList.add('hidden');
    document.getElementById('actionsList').classList.add('hidden');
    document.getElementById('sourcesList').classList.add('hidden');
    
    document.getElementById('answerContent').textContent = '';
    document.getElementById('tasksContent').innerHTML = '';
    document.getElementById('recommendationsContent').innerHTML = '';
    document.getElementById('actionsContent').innerHTML = '';
    document.getElementById('sourcesContent').innerHTML = '';
}

/**
 * Показать статус
 */
function showStatus(type, message) {
    const statusMessage = document.getElementById('statusMessage');
    statusMessage.className = `status-message ${type} show`;
    statusMessage.textContent = message;
    
    // Автоматически скрываем через 5 секунд для success/error
    if (type === 'success' || type === 'error') {
        setTimeout(() => {
            statusMessage.classList.remove('show');
        }, 5000);
    }
}

/**
 * Форматирование статуса задачи
 */
function formatStatus(status) {
    const statusMap = {
        'TODO': 'К выполнению',
        'IN_PROGRESS': 'В работе',
        'IN_REVIEW': 'На проверке',
        'DONE': 'Выполнено',
        'BLOCKED': 'Заблокировано'
    };
    return statusMap[status] || status;
}

/**
 * Форматирование приоритета
 */
function formatPriority(priority) {
    const priorityMap = {
        'LOW': 'Низкий',
        'MEDIUM': 'Средний',
        'HIGH': 'Высокий',
        'URGENT': 'Срочный'
    };
    return priorityMap[priority] || priority;
}

/**
 * Получить CSS класс для статуса
 */
function getStatusClass(status) {
    return status.toLowerCase().replace('_', '-');
}

/**
 * Получить CSS класс для приоритета
 */
function getPriorityClass(priority) {
    if (priority === 'HIGH' || priority === 'URGENT') {
        return priority.toLowerCase();
    }
    return '';
}

/**
 * Форматирование типа действия
 */
function formatActionType(type) {
    const typeMap = {
        'UPDATE_TASK': 'Обновить задачу',
        'CREATE_TASK': 'Создать задачу',
        'VIEW_TASK': 'Просмотреть задачу',
        'VIEW_STATUS': 'Просмотреть статус'
    };
    return typeMap[type] || type;
}

/**
 * Экранирование HTML
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

