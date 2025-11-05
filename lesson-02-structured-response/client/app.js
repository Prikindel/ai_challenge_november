// Конфигурация
const API_BASE_URL = 'http://localhost:8080';

// Элементы DOM
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const errorMessage = document.getElementById('errorMessage');
const loadingIndicator = document.getElementById('loadingIndicator');

// Функция для добавления сообщения пользователя в чат
function addUserMessage(text) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message user-message';
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    
    const paragraph = document.createElement('p');
    paragraph.textContent = text;
    contentDiv.appendChild(paragraph);
    
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    
    scrollToBottom();
}

// Функция для отображения карточки животного
function addAnimalCard(animalInfo) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message bot-message';
    
    const cardDiv = document.createElement('div');
    cardDiv.className = 'animal-card';
    
    // Заголовок с названием животного
    const title = document.createElement('h3');
    title.textContent = animalInfo.name;
    cardDiv.appendChild(title);
    
    // Описание
    const descriptionItem = createInfoItem('Описание', animalInfo.description);
    cardDiv.appendChild(descriptionItem);
    
    // Питание
    const dietItem = createInfoItem('Питание', animalInfo.diet);
    cardDiv.appendChild(dietItem);
    
    // Продолжительность жизни
    const lifespanItem = createInfoItem('Продолжительность жизни', animalInfo.lifespan);
    cardDiv.appendChild(lifespanItem);
    
    // Среда обитания
    const habitatItem = createInfoItem('Среда обитания', animalInfo.habitat);
    cardDiv.appendChild(habitatItem);
    
    messageDiv.appendChild(cardDiv);
    chatMessages.appendChild(messageDiv);
    
    scrollToBottom();
}

// Функция для создания элемента информации
function createInfoItem(label, value) {
    const itemDiv = document.createElement('div');
    itemDiv.className = 'animal-info-item';
    
    const labelSpan = document.createElement('div');
    labelSpan.className = 'animal-info-label';
    labelSpan.textContent = label;
    
    const valueDiv = document.createElement('div');
    valueDiv.className = 'animal-info-value';
    valueDiv.textContent = value;
    
    itemDiv.appendChild(labelSpan);
    itemDiv.appendChild(valueDiv);
    
    return itemDiv;
}

// Функция для отображения ошибки валидации темы
function addTopicError(errorMessage) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message bot-message';
    
    const errorDiv = document.createElement('div');
    errorDiv.className = 'topic-error';
    
    const icon = document.createElement('div');
    icon.className = 'topic-error-icon';
    icon.textContent = '⚠️';
    
    const message = document.createElement('div');
    message.className = 'topic-error-message';
    message.textContent = errorMessage;
    
    errorDiv.appendChild(icon);
    errorDiv.appendChild(message);
    
    messageDiv.appendChild(errorDiv);
    chatMessages.appendChild(messageDiv);
    
    scrollToBottom();
}

// Функция для отображения ошибки
function showError(message) {
    errorMessage.textContent = message;
    errorMessage.classList.add('show');
    
    // Скрыть ошибку через 5 секунд
    setTimeout(() => {
        errorMessage.classList.remove('show');
    }, 5000);
}

// Функция для скролла вниз
function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
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
        
        const data = await response.json();
        
        if (!response.ok) {
            // Обработка ошибок от сервера
            const errorText = data.error || 'Произошла ошибка при отправке сообщения';
            showError(errorText);
            return;
        }
        
        // Обрабатываем структурированный ответ
        if (data.response) {
            const responseData = data.response;
            
            // Проверяем тип ответа (success или error)
            if (responseData.type === 'success' && responseData.data) {
                // Отображаем карточку животного
                addAnimalCard(responseData.data);
            } else if (responseData.type === 'error' && responseData.error) {
                // Отображаем ошибку валидации темы
                addTopicError(responseData.error.message || 'Ошибка валидации темы');
            } else {
                showError('Получен некорректный ответ от сервера');
            }
        } else {
            showError('Получен некорректный ответ от сервера');
        }
        
    } catch (error) {
        console.error('Error:', error);
        showError('Не удалось подключиться к серверу. Убедитесь, что сервер запущен на ' + API_BASE_URL);
    } finally {
        // Разблокируем поле ввода
        messageInput.disabled = false;
        sendButton.disabled = false;
        loadingIndicator.style.display = 'none';
        messageInput.focus();
    }
}

// Обработчик клика на кнопку отправки
sendButton.addEventListener('click', sendMessage);

// Обработчик нажатия Enter в поле ввода
messageInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

// Фокус на поле ввода при загрузке
messageInput.focus();

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
