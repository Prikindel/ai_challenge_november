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
    
    // Управление задачами
    const showTasksBtn = document.getElementById('showTasksBtn');
    const showStatusBtn = document.getElementById('showStatusBtn');
    const createTaskBtn = document.getElementById('createTaskBtn');
    const cancelCreateTaskBtn = document.getElementById('cancelCreateTaskBtn');
    const createTaskFormElement = document.getElementById('createTaskFormElement');
    const applyFiltersBtn = document.getElementById('applyFiltersBtn');
    
    if (showTasksBtn) {
        showTasksBtn.addEventListener('click', () => {
            document.getElementById('tasksFilters').classList.remove('hidden');
            document.getElementById('tasksManagementList').classList.remove('hidden');
            document.getElementById('projectStatusDashboard').classList.add('hidden');
            loadTasks();
        });
    }
    
    if (showStatusBtn) {
        showStatusBtn.addEventListener('click', () => {
            document.getElementById('tasksFilters').classList.add('hidden');
            document.getElementById('tasksManagementList').classList.add('hidden');
            document.getElementById('projectStatusDashboard').classList.remove('hidden');
            loadProjectStatus();
        });
    }
    
    if (createTaskBtn) {
        createTaskBtn.addEventListener('click', () => {
            document.getElementById('createTaskForm').classList.remove('hidden');
        });
    }
    
    if (cancelCreateTaskBtn) {
        cancelCreateTaskBtn.addEventListener('click', () => {
            document.getElementById('createTaskForm').classList.add('hidden');
            if (createTaskFormElement) {
                createTaskFormElement.reset();
            }
        });
    }
    
    if (createTaskFormElement) {
        createTaskFormElement.addEventListener('submit', handleCreateTask);
    }
    
    if (applyFiltersBtn) {
        applyFiltersBtn.addEventListener('click', loadTasks);
    }
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
        let actionButtons = '';
        if (rec.task) {
            taskInfo = `<div style="margin-top: 8px; font-size: 12px; color: #4a5568;">Задача: ${escapeHtml(rec.task.title)}</div>`;
            
            // Добавляем кнопки для быстрых действий
            if (rec.task.status !== 'IN_PROGRESS' && rec.task.status !== 'DONE') {
                actionButtons = `
                    <div class="recommendation-actions">
                        <button class="recommendation-btn" onclick="updateTaskStatus('${rec.task.id}', 'IN_PROGRESS')">
                            ▶️ Взять в работу
                        </button>
                        ${rec.task.status === 'BLOCKED' ? `
                            <button class="recommendation-btn" onclick="updateTaskStatus('${rec.task.id}', 'TODO')" style="background: #11998e;">
                                🔓 Разблокировать
                            </button>
                        ` : ''}
                    </div>
                `;
            }
        }
        
        recItem.innerHTML = `
            <div class="recommendation-priority">Приоритет: ${escapeHtml(rec.priority)}</div>
            <div class="recommendation-reason">${escapeHtml(rec.reason)}</div>
            ${taskInfo}
            ${actionButtons}
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
        let actionButton = '';
        
        if (action.task) {
            taskInfo = `<div style="margin-top: 8px; font-size: 12px; color: #2d3748;">Задача: ${escapeHtml(action.task.title)}</div>`;
            
            // Добавляем кнопку для выполнения действия
            if (action.type === 'UPDATE_TASK' && action.task.status !== 'DONE') {
                const newStatus = action.description.includes('IN_PROGRESS') ? 'IN_PROGRESS' : 
                                 action.description.includes('TODO') ? 'TODO' : null;
                if (newStatus) {
                    actionButton = `
                        <div style="margin-top: 12px;">
                            <button class="recommendation-btn" onclick="updateTaskStatus('${action.task.id}', '${newStatus}')">
                                ⚡ Выполнить действие
                            </button>
                        </div>
                    `;
                }
            } else if (action.type === 'VIEW_TASK' && action.task) {
                actionButton = `
                    <div style="margin-top: 12px;">
                        <button class="recommendation-btn" onclick="document.getElementById('showTasksBtn').click(); loadTasks();" style="background: #11998e;">
                            📋 Показать задачу
                        </button>
                    </div>
                `;
            } else if (action.type === 'VIEW_STATUS') {
                actionButton = `
                    <div style="margin-top: 12px;">
                        <button class="recommendation-btn" onclick="document.getElementById('showStatusBtn').click();" style="background: #11998e;">
                            📊 Показать статус
                        </button>
                    </div>
                `;
            }
        }
        
        actionItem.innerHTML = `
            <div class="action-description">
                <strong>${formatActionType(action.type)}:</strong> ${escapeHtml(action.description)}
            </div>
            ${taskInfo}
            ${actionButton}
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

/**
 * Загрузка задач с фильтрами
 */
async function loadTasks() {
    const status = document.getElementById('filterStatus').value;
    const priority = document.getElementById('filterPriority').value;
    const assignee = document.getElementById('filterAssignee').value.trim();
    
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (priority) params.append('priority', priority);
    if (assignee) params.append('assignee', assignee);
    
    try {
        showStatus('loading', '⏳ Загрузка задач...');
        const response = await fetch(`${API_BASE}/team/tasks?${params.toString()}`);
        
        if (!response.ok) {
            throw new Error('Ошибка при загрузке задач');
        }
        
        const data = await response.json();
        displayTasksManagement(data.tasks);
        showStatus('success', `✅ Загружено задач: ${data.tasks.length}`);
        
    } catch (error) {
        console.error('Error loading tasks:', error);
        showStatus('error', `❌ Ошибка: ${error.message}`);
    }
}

/**
 * Отображение задач для управления
 */
function displayTasksManagement(tasks) {
    const tasksList = document.getElementById('tasksManagementList');
    tasksList.innerHTML = '';
    
    if (tasks.length === 0) {
        tasksList.innerHTML = '<p style="text-align: center; color: #718096; padding: 20px;">Задачи не найдены</p>';
        return;
    }
    
    tasks.forEach(task => {
        const taskItem = document.createElement('div');
        taskItem.className = `task-item ${task.status.toLowerCase().replace('_', '-')}`;
        
        const statusClass = getStatusClass(task.status);
        const priorityClass = getPriorityClass(task.priority);
        
        taskItem.innerHTML = `
            <div class="task-header">
                <div>
                    <div class="task-title">${escapeHtml(task.title)}</div>
                    <div style="font-size: 12px; color: #718096; margin-top: 4px;">ID: ${escapeHtml(task.id)}</div>
                </div>
                <div class="task-badges">
                    <span class="badge badge-status">${formatStatus(task.status)}</span>
                    <span class="badge badge-priority ${priorityClass}">${formatPriority(task.priority)}</span>
                </div>
            </div>
            <div class="task-description">${escapeHtml(task.description)}</div>
            ${task.assignee ? `<div style="margin-top: 8px; font-size: 12px; color: #718096;">Исполнитель: ${escapeHtml(task.assignee)}</div>` : ''}
            ${task.blockedBy && task.blockedBy.length > 0 ? `<div style="margin-top: 8px; font-size: 12px; color: #f5576c;">⚠️ Блокируется: ${task.blockedBy.join(', ')}</div>` : ''}
            ${task.blocks && task.blocks.length > 0 ? `<div style="margin-top: 8px; font-size: 12px; color: #3494E6;">🔒 Блокирует: ${task.blocks.join(', ')}</div>` : ''}
            <div class="task-actions">
                <button class="task-action-btn" style="background: #3494E6; color: white;" onclick="editTask('${task.id}')">
                    ✏️ Редактировать
                </button>
                ${task.status !== 'DONE' ? `<button class="task-action-btn" style="background: #38ef7d; color: white;" onclick="updateTaskStatus('${task.id}', 'DONE')">✅ Завершить</button>` : ''}
                ${task.status === 'BLOCKED' ? `<button class="task-action-btn" style="background: #11998e; color: white;" onclick="updateTaskStatus('${task.id}', 'IN_PROGRESS')">▶️ Взять в работу</button>` : ''}
            </div>
        `;
        
        tasksList.appendChild(taskItem);
    });
}

/**
 * Загрузка статуса проекта
 */
async function loadProjectStatus() {
    try {
        showStatus('loading', '⏳ Загрузка статуса проекта...');
        const response = await fetch(`${API_BASE}/team/status`);
        
        if (!response.ok) {
            throw new Error('Ошибка при загрузке статуса проекта');
        }
        
        const data = await response.json();
        displayProjectStatus(data);
        showStatus('success', '✅ Статус проекта загружен');
        
    } catch (error) {
        console.error('Error loading project status:', error);
        showStatus('error', `❌ Ошибка: ${error.message}`);
    }
}

/**
 * Отображение статуса проекта
 */
function displayProjectStatus(status) {
    const dashboard = document.getElementById('projectStatusDashboard');
    
    dashboard.innerHTML = `
        <div class="status-dashboard">
            <div class="stat-card">
                <div class="stat-label">Всего задач</div>
                <div class="stat-value">${status.totalTasks}</div>
            </div>
            <div class="stat-card info">
                <div class="stat-label">В работе</div>
                <div class="stat-value">${status.tasksInProgress}</div>
            </div>
            <div class="stat-card success">
                <div class="stat-label">Выполнено</div>
                <div class="stat-value">${status.tasksDone}</div>
            </div>
            <div class="stat-card warning">
                <div class="stat-label">Заблокировано</div>
                <div class="stat-value">${status.blockedTasks}</div>
            </div>
        </div>
        <div style="margin-top: 24px;">
            <h3 style="margin-bottom: 16px;">Распределение по статусам:</h3>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px;">
                ${Object.entries(status.tasksByStatus).map(([status, count]) => `
                    <div style="padding: 16px; background: #f8f9fa; border-radius: 8px; text-align: center;">
                        <div style="font-size: 24px; font-weight: bold; color: #1a1a1a;">${count}</div>
                        <div style="font-size: 12px; color: #718096;">${formatStatus(status)}</div>
                    </div>
                `).join('')}
            </div>
        </div>
        <div style="margin-top: 24px;">
            <h3 style="margin-bottom: 16px;">Распределение по приоритетам:</h3>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px;">
                ${Object.entries(status.tasksByPriority).map(([priority, count]) => `
                    <div style="padding: 16px; background: #f8f9fa; border-radius: 8px; text-align: center;">
                        <div style="font-size: 24px; font-weight: bold; color: #1a1a1a;">${count}</div>
                        <div style="font-size: 12px; color: #718096;">${formatPriority(priority)}</div>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
}

/**
 * Создание задачи
 */
async function handleCreateTask(e) {
    e.preventDefault();
    
    const title = document.getElementById('taskTitle').value.trim();
    const description = document.getElementById('taskDescription').value.trim();
    const priority = document.getElementById('taskPriority').value;
    const assignee = document.getElementById('taskAssignee').value.trim() || null;
    
    if (!title || !description) {
        showStatus('error', '❌ Заполните все обязательные поля');
        return;
    }
    
    try {
        showStatus('loading', '⏳ Создание задачи...');
        
        const response = await fetch(`${API_BASE}/team/tasks`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                title,
                description,
                priority,
                assignee
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Ошибка при создании задачи');
        }
        
        const task = await response.json();
        showStatus('success', `✅ Задача создана: ${task.id}`);
        
        // Закрываем форму и обновляем список
        document.getElementById('createTaskForm').classList.add('hidden');
        document.getElementById('createTaskFormElement').reset();
        
        // Обновляем список задач, если он открыт
        if (!document.getElementById('tasksManagementList').classList.contains('hidden')) {
            loadTasks();
        }
        
    } catch (error) {
        console.error('Error creating task:', error);
        showStatus('error', `❌ Ошибка: ${error.message}`);
    }
}

/**
 * Обновление статуса задачи
 */
async function updateTaskStatus(taskId, newStatus) {
    try {
        showStatus('loading', '⏳ Обновление задачи...');
        
        const response = await fetch(`${API_BASE}/team/tasks/${taskId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                status: newStatus
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Ошибка при обновлении задачи');
        }
        
        showStatus('success', '✅ Задача обновлена');
        loadTasks();
        
    } catch (error) {
        console.error('Error updating task:', error);
        showStatus('error', `❌ Ошибка: ${error.message}`);
    }
}

/**
 * Редактирование задачи
 */
async function editTask(taskId) {
    try {
        const response = await fetch(`${API_BASE}/team/tasks/${taskId}`);
        
        if (!response.ok) {
            throw new Error('Ошибка при загрузке задачи');
        }
        
        const task = await response.json();
        
        // Заполняем форму создания задачи для редактирования
        document.getElementById('taskTitle').value = task.title;
        document.getElementById('taskDescription').value = task.description;
        document.getElementById('taskPriority').value = task.priority;
        document.getElementById('taskAssignee').value = task.assignee || '';
        
        // Показываем форму
        document.getElementById('createTaskForm').classList.remove('hidden');
        
        // Изменяем обработчик формы для обновления
        const form = document.getElementById('createTaskFormElement');
        const oldHandler = form.onsubmit;
        form.onsubmit = async (e) => {
            e.preventDefault();
            await handleUpdateTask(taskId);
            form.onsubmit = oldHandler;
        };
        
        // Прокручиваем к форме
        document.getElementById('createTaskForm').scrollIntoView({ behavior: 'smooth' });
        
    } catch (error) {
        console.error('Error loading task:', error);
        showStatus('error', `❌ Ошибка: ${error.message}`);
    }
}

/**
 * Обновление задачи
 */
async function handleUpdateTask(taskId) {
    const title = document.getElementById('taskTitle').value.trim();
    const description = document.getElementById('taskDescription').value.trim();
    const priority = document.getElementById('taskPriority').value;
    const assignee = document.getElementById('taskAssignee').value.trim() || null;
    
    if (!title || !description) {
        showStatus('error', '❌ Заполните все обязательные поля');
        return;
    }
    
    try {
        showStatus('loading', '⏳ Обновление задачи...');
        
        const response = await fetch(`${API_BASE}/team/tasks/${taskId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                title,
                description,
                priority,
                assignee
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Ошибка при обновлении задачи');
        }
        
        showStatus('success', '✅ Задача обновлена');
        
        // Закрываем форму и обновляем список
        document.getElementById('createTaskForm').classList.add('hidden');
        document.getElementById('createTaskFormElement').reset();
        loadTasks();
        
    } catch (error) {
        console.error('Error updating task:', error);
        showStatus('error', `❌ Ошибка: ${error.message}`);
    }
}

