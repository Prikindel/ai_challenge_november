// Конфигурация
const API_BASE_URL = 'http://localhost:8080';

// Элементы DOM
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const errorMessage = document.getElementById('errorMessage');
const loadingIndicator = document.getElementById('loadingIndicator');
const clearHistoryBtn = document.getElementById('clearHistoryBtn');
const showHistoryBtn = document.getElementById('showHistoryBtn');
const tzResult = document.getElementById('tzResult');
const tzContent = document.getElementById('tzContent');

// Хранение последнего LLM запроса и ответа для каждого сообщения
let lastLlmRequest = null;
let lastLlmResponse = null;

// Функция для добавления сообщения пользователя
function addUserMessage(text) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message user-message';
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = text;
    
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    
    scrollToBottom();
}

// Функция для добавления сообщения бота
function addBotMessage(text) {
    // Убеждаемся, что чат виден
    if (chatMessages.style.display === 'none') {
        chatMessages.style.display = 'block';
        tzResult.style.display = 'none';
    }
    
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message bot-message';
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = text;
    
    messageDiv.appendChild(contentDiv);
    
    // Добавляем кнопку просмотра JSON, если есть данные
    if (lastLlmRequest && lastLlmResponse) {
        addJsonViewButton(messageDiv);
    }
    
    chatMessages.appendChild(messageDiv);
    
    scrollToBottom();
}

// Функция для отображения ТЗ
function displayTechnicalSpec(tz) {
    // Проверяем, что tz не пустой
    if (!tz) {
        console.error('TechnicalSpec is null or undefined');
        showError('Получено пустое техническое задание');
        return;
    }
    
    // Скрываем чат
    chatMessages.style.display = 'none';
    
    // Показываем результат ТЗ
    tzResult.style.display = 'block';
    
    // Формируем HTML для ТЗ
    let html = `
        <div class="tz-section">
            <h3>📋 ${tz.title || 'Техническое задание'}</h3>
            <p class="tz-description">${tz.description || 'Описание отсутствует'}</p>
        </div>
    `;
    
    if (tz.requirements && tz.requirements.length > 0) {
        html += `
            <div class="tz-section">
                <h4>Требования:</h4>
                <ul>
                    ${tz.requirements.map(req => `<li>${req}</li>`).join('')}
                </ul>
            </div>
        `;
    }
    
    if (tz.features && tz.features.length > 0) {
        html += `
            <div class="tz-section">
                <h4>Функциональные возможности:</h4>
                <ul>
                    ${tz.features.map(feature => `<li>${feature}</li>`).join('')}
                </ul>
            </div>
        `;
    }
    
    if (tz.constraints && tz.constraints.length > 0) {
        html += `
            <div class="tz-section">
                <h4>Ограничения:</h4>
                <ul>
                    ${tz.constraints.map(constraint => `<li>${constraint}</li>`).join('')}
                </ul>
            </div>
        `;
    }
    
    if (tz.timeline) {
        html += `
            <div class="tz-section">
                <h4>Временные рамки:</h4>
                <p>${tz.timeline}</p>
            </div>
        `;
    }
    
    if (tz.targetAudience) {
        html += `
            <div class="tz-section">
                <h4>Целевая аудитория:</h4>
                <p>${tz.targetAudience}</p>
            </div>
        `;
    }
    
    if (tz.successCriteria && tz.successCriteria.length > 0) {
        html += `
            <div class="tz-section">
                <h4>Критерии успеха:</h4>
                <ul>
                    ${tz.successCriteria.map(criteria => `<li>${criteria}</li>`).join('')}
                </ul>
            </div>
        `;
    }
    
    tzContent.innerHTML = html;
    
    // Добавляем кнопку просмотра JSON, если есть данные
    if (lastLlmRequest && lastLlmResponse) {
        const jsonButtonContainer = document.createElement('div');
        jsonButtonContainer.className = 'json-view-button-container';
        jsonButtonContainer.style.marginTop = '20px';
        jsonButtonContainer.style.textAlign = 'center';
        
        const jsonButton = document.createElement('button');
        jsonButton.className = 'json-view-button';
        jsonButton.innerHTML = '📋 Показать JSON';
        jsonButton.onclick = () => showJsonModal(lastLlmRequest, lastLlmResponse);
        
        jsonButtonContainer.appendChild(jsonButton);
        tzContent.appendChild(jsonButtonContainer);
    }
    
    scrollToBottom();
}

// Функция для отображения ошибки
function showError(message) {
    errorMessage.textContent = message;
    errorMessage.classList.add('show');
    
    setTimeout(() => {
        errorMessage.classList.remove('show');
    }, 5000);
}

// Функция для скролла вниз
function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
    if (tzResult.style.display !== 'none') {
        tzResult.scrollIntoView({ behavior: 'smooth' });
    }
}

// Функция для отправки сообщения
async function sendMessage() {
    const message = messageInput.value.trim();
    
    if (!message) {
        return;
    }
    
    // Валидация длины сообщения
    if (message.length > 2000) {
        showError('Сообщение слишком длинное (максимум 2000 символов)');
        return;
    }
    
    // Добавляем сообщение пользователя в чат
    addUserMessage(message);
    
    // Очищаем поле ввода и блокируем его
    messageInput.value = '';
    messageInput.disabled = true;
    sendButton.disabled = true;
    loadingIndicator.style.display = 'flex';
    errorMessage.classList.remove('show');
    
    try {
        // Отправляем запрос на сервер
        const response = await fetch(`${API_BASE_URL}/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ message: message }),
        });
        
        // Парсим JSON ответ
        let data;
        try {
            data = await response.json();
        } catch (e) {
            const errorText = await response.text().catch(() => 'Неизвестная ошибка');
            console.error('Failed to parse JSON:', e, 'Response:', errorText);
            showError('Ошибка при парсинге ответа от сервера: ' + e.message);
            return;
        }
        
        // Проверяем статус ответа
        if (!response.ok) {
            const errorMessage = data.error || 'Произошла ошибка при отправке сообщения';
            showError(errorMessage);
            return;
        }
        
        // Сохраняем JSON запрос и ответ для отображения
        if (data.debug) {
            lastLlmRequest = data.debug.llmRequest;
            lastLlmResponse = data.debug.llmResponse;
        } else {
            lastLlmRequest = null;
            lastLlmResponse = null;
        }
        
        // Нормализуем тип ответа (убираем пробелы, приводим к нижнему регистру)
        const responseType = data.type?.trim()?.toLowerCase();
        
        // Обрабатываем ответ (полиморфная сериализация с полем "type")
        if (responseType === 'continue') {
            // Продолжаем диалог
            if (!data.message || data.message.trim() === '') {
                console.error('Missing or empty message in continue response:', data);
                showError('Получен некорректный ответ от сервера: отсутствует сообщение');
                return;
            }
            // Убеждаемся, что чат виден, а результат ТЗ скрыт
            chatMessages.style.display = 'block';
            tzResult.style.display = 'none';
            addBotMessage(data.message);
        } else if (responseType === 'tzready') {
            // ТЗ готово
            if (!data.technicalSpec) {
                console.error('Missing technicalSpec in tzReady response:', data);
                showError('Получен некорректный ответ от сервера: отсутствует техническое задание');
                return;
            }
            displayTechnicalSpec(data.technicalSpec);
        } else {
            console.error('Unknown response type:', data.type, 'normalized:', responseType);
            console.error('Full response data:', JSON.stringify(data, null, 2));
            console.error('Data keys:', Object.keys(data));
            showError('Получен некорректный ответ от сервера: неизвестный тип ответа (' + (data.type || 'null') + ')');
        }
        
    } catch (error) {
        console.error('Error in sendMessage:', error);
        console.error('Error stack:', error.stack);
        showError('Ошибка: ' + (error.message || 'Не удалось подключиться к серверу. Убедитесь, что сервер запущен на ' + API_BASE_URL));
    } finally {
        // Разблокируем поле ввода
        messageInput.disabled = false;
        sendButton.disabled = false;
        loadingIndicator.style.display = 'none';
        messageInput.focus();
    }
}

// Функция для очистки истории
async function clearHistory() {
    if (!confirm('Вы уверены, что хотите начать новый диалог? История сообщений будет очищена.')) {
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/chat`, {
            method: 'DELETE',
        });
        
        if (response.ok) {
            // Очищаем UI
            chatMessages.innerHTML = '';
            tzResult.style.display = 'none';
            tzContent.innerHTML = '';
            chatMessages.style.display = 'block';
            
            // Очищаем JSON данные
            lastLlmRequest = null;
            lastLlmResponse = null;
            
            // Добавляем приветственное сообщение
            addBotMessage('Привет! Я помогу вам собрать требования и создать техническое задание. Расскажите, какой проект вы хотите разработать?');
        } else {
            showError('Не удалось очистить историю');
        }
    } catch (error) {
        console.error('Error:', error);
        showError('Не удалось подключиться к серверу');
    }
}

// Обработчик клика на кнопку отправки
if (sendButton) {
    sendButton.addEventListener('click', sendMessage);
} else {
    console.error('Send button not found!');
}

// Обработчик нажатия Enter в поле ввода
if (messageInput) {
    messageInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
} else {
    console.error('Message input not found!');
}

// Обработчик очистки истории
clearHistoryBtn.addEventListener('click', clearHistory);

// Обработчик показа истории переписки
if (showHistoryBtn) {
    showHistoryBtn.addEventListener('click', showHistory);
} else {
    console.error('Show history button not found!');
}

// Функция для отображения истории переписки
async function showHistory() {
    const modal = document.getElementById('historyModal');
    const historyLoading = document.getElementById('historyLoading');
    const historyContent = document.getElementById('historyContent');
    const closeButton = document.getElementById('closeHistoryModal');
    
    // Показываем модальное окно
    modal.style.display = 'flex';
    historyLoading.style.display = 'block';
    historyContent.style.display = 'none';
    
    try {
        // Запрашиваем историю с сервера
        const response = await fetch(`${API_BASE_URL}/chat/history`);
        
        if (!response.ok) {
            throw new Error('Не удалось получить историю');
        }
        
        const data = await response.json();
        
        // Скрываем индикатор загрузки
        historyLoading.style.display = 'none';
        historyContent.style.display = 'block';
        
        // Формируем HTML для истории
        if (!data.entries || data.entries.length === 0) {
            historyContent.innerHTML = '<p style="text-align: center; color: #666;">История переписки пуста</p>';
        } else {
            let html = '';
            data.entries.forEach((entry, index) => {
                // Форматируем JSON
                let requestText = '';
                let responseText = '';
                
                try {
                    if (typeof entry.requestJson === 'string') {
                        const parsed = JSON.parse(entry.requestJson);
                        requestText = JSON.stringify(parsed, null, 2);
                    } else {
                        requestText = JSON.stringify(entry.requestJson, null, 2);
                    }
                } catch (e) {
                    requestText = typeof entry.requestJson === 'string' ? entry.requestJson : String(entry.requestJson);
                }
                
                try {
                    if (typeof entry.responseJson === 'string') {
                        const parsed = JSON.parse(entry.responseJson);
                        responseText = JSON.stringify(parsed, null, 2);
                    } else {
                        responseText = JSON.stringify(entry.responseJson, null, 2);
                    }
                } catch (e) {
                    responseText = typeof entry.responseJson === 'string' ? entry.responseJson : String(entry.responseJson);
                }
                
                html += `
                    <div class="history-entry">
                        <h3 class="history-entry-title">Запрос #${index + 1}</h3>
                        <div class="json-section">
                            <h4>Запрос к LLM</h4>
                            <pre class="json-code">${escapeHtml(requestText)}</pre>
                        </div>
                        <div class="json-section">
                            <h4>Ответ от LLM</h4>
                            <pre class="json-code">${escapeHtml(responseText)}</pre>
                        </div>
                    </div>
                `;
            });
            historyContent.innerHTML = html;
        }
    } catch (error) {
        console.error('Error loading history:', error);
        historyLoading.style.display = 'none';
        historyContent.style.display = 'block';
        historyContent.innerHTML = `<p style="text-align: center; color: #d32f2f;">Ошибка при загрузке истории: ${error.message}</p>`;
    }
    
    // Закрытие по клику на кнопку
    closeButton.onclick = () => {
        modal.style.display = 'none';
    };
    
    // Закрытие по клику вне модального окна
    modal.onclick = (e) => {
        if (e.target === modal) {
            modal.style.display = 'none';
        }
    };
    
    // Закрытие по нажатию Escape
    const escapeHandler = (e) => {
        if (e.key === 'Escape') {
            modal.style.display = 'none';
            document.removeEventListener('keydown', escapeHandler);
        }
    };
    document.addEventListener('keydown', escapeHandler);
}

// Функция для экранирования HTML
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Функция для добавления кнопки просмотра JSON
function addJsonViewButton(messageDiv) {
    if (!lastLlmRequest || !lastLlmResponse) {
        return;
    }
    
    const buttonContainer = document.createElement('div');
    buttonContainer.className = 'json-view-button-container';
    
    const jsonButton = document.createElement('button');
    jsonButton.className = 'json-view-button';
    jsonButton.innerHTML = '📋 Показать JSON';
    jsonButton.onclick = () => showJsonModal(lastLlmRequest, lastLlmResponse);
    
    buttonContainer.appendChild(jsonButton);
    messageDiv.appendChild(buttonContainer);
}

// Функция для отображения модального окна с JSON
function showJsonModal(request, response) {
    const modal = document.getElementById('jsonModal');
    const requestJson = document.getElementById('requestJson');
    const responseJson = document.getElementById('responseJson');
    const closeButton = document.getElementById('closeJsonModal');
    
    // Форматируем JSON с отступами (данные приходят как JSON строки)
    let requestText = '';
    let responseText = '';
    
    // Парсим и форматируем запрос
    try {
        if (typeof request === 'string') {
            const parsed = JSON.parse(request);
            requestText = JSON.stringify(parsed, null, 2);
        } else {
            requestText = JSON.stringify(request, null, 2);
        }
    } catch (e) {
        // Если не удалось распарсить, показываем как есть
        requestText = typeof request === 'string' ? request : String(request);
    }
    
    // Парсим и форматируем ответ
    try {
        if (typeof response === 'string') {
            const parsed = JSON.parse(response);
            responseText = JSON.stringify(parsed, null, 2);
        } else {
            responseText = JSON.stringify(response, null, 2);
        }
    } catch (e) {
        // Если не удалось распарсить, показываем как есть
        responseText = typeof response === 'string' ? response : String(response);
    }
    
    requestJson.textContent = requestText;
    responseJson.textContent = responseText;
    
    // Показываем модальное окно
    modal.style.display = 'flex';
    
    // Закрытие по клику на кнопку
    closeButton.onclick = () => {
        modal.style.display = 'none';
    };
    
    // Закрытие по клику вне модального окна
    modal.onclick = (e) => {
        if (e.target === modal) {
            modal.style.display = 'none';
        }
    };
    
    // Закрытие по нажатию Escape
    const escapeHandler = (e) => {
        if (e.key === 'Escape') {
            modal.style.display = 'none';
            document.removeEventListener('keydown', escapeHandler);
        }
    };
    document.addEventListener('keydown', escapeHandler);
}

// Фокус на поле ввода при загрузке
messageInput.focus();

// Добавляем приветственное сообщение при загрузке
window.addEventListener('load', () => {
    addBotMessage('Привет! Я помогу вам собрать требования и создать техническое задание. Расскажите, какой проект вы хотите разработать?');
});

// Проверка доступности сервера при загрузке
async function checkServerHealth() {
    try {
        const response = await fetch(`${API_BASE_URL}/health`);
        if (!response.ok) {
            showError('Сервер недоступен. Убедитесь, что он запущен.');
        }
    } catch (error) {
        showError('Не удалось подключиться к серверу. Убедитесь, что сервер запущен на ' + API_BASE_URL);
    }
}

// Проверяем здоровье сервера при загрузке страницы
checkServerHealth();
