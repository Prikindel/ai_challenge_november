// Конфигурация
const API_BASE_URL = 'http://localhost:8080';

// Элементы DOM
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const errorMessage = document.getElementById('errorMessage');
const loadingIndicator = document.getElementById('loadingIndicator');

// Функция для добавления сообщения в чат
function addMessage(text, isUser = false) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${isUser ? 'user-message' : 'bot-message'}`;
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    
    // Для сообщений от бота используем Markdown, для пользователя - обычный текст
    if (!isUser && typeof marked !== 'undefined') {
        try {
            // Парсим Markdown в HTML с безопасными настройками
            const html = marked.parse(text, {
                breaks: true, // Переносы строк превращать в <br>
                gfm: true, // Поддержка GitHub Flavored Markdown
            });
            contentDiv.innerHTML = html;
        } catch (e) {
            // Если ошибка парсинга, отображаем как обычный текст
            console.error('Ошибка парсинга Markdown:', e);
            const paragraph = document.createElement('p');
            paragraph.textContent = text;
            contentDiv.appendChild(paragraph);
        }
    } else {
        // Для сообщений пользователя используем обычный текст (без форматирования)
        const paragraph = document.createElement('p');
        paragraph.textContent = text;
        contentDiv.appendChild(paragraph);
    }
    
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    
    // Автоскролл вниз
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
    addMessage(message, true);
    
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
        
        // Добавляем ответ от AI
        if (data.response) {
            addMessage(data.response, false);
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

