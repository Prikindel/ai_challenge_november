// Конфигурация
const API_BASE_URL = 'http://localhost:8080';

// Элементы DOM
const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const voiceButton = document.getElementById('voiceButton');
const micHint = document.getElementById('micHint');
const micHintText = document.getElementById('micHintText');
const micRequestButton = document.getElementById('micRequestButton');
const errorMessage = document.getElementById('errorMessage');
const voiceStatus = document.getElementById('voiceStatus');
const loadingIndicator = document.getElementById('loadingIndicator');

let mediaRecorder = null;
let audioChunks = [];
let isRecording = false;
let micAllowed = false;
let mediaDevicesAvailable = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);

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

voiceButton.addEventListener('click', () => {
    if (isRecording) {
        stopVoiceRecording();
    } else {
        startVoiceRecording();
    }
});

micRequestButton?.addEventListener('click', requestMicAccess);

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

// Проверяем здоровье сервера и доступ к микрофону при загрузке страницы
checkServerHealth();
initMicPermission();

async function startVoiceRecording() {
    if (!mediaDevicesAvailable) {
        voiceStatus.textContent = 'Браузер не дает доступ к микрофону (нет mediaDevices)';
        micHint.style.display = 'flex';
        micHintText.textContent = 'Ваш браузер не поддерживает getUserMedia. Обновите браузер или включите доступ.';
        return;
    }
    if (!micAllowed) {
        await requestMicAccess();
        if (!micAllowed) {
            voiceStatus.textContent = 'Разрешите доступ к микрофону';
            return;
        }
    }
    if (isRecording) return;
    try {
        const stream = await getUserMediaSafe({ audio: true });
        mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' });
        audioChunks = [];

        mediaRecorder.ondataavailable = (event) => {
            if (event.data.size > 0) {
                audioChunks.push(event.data);
            }
        };

        mediaRecorder.onstop = async () => {
            const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
            stream.getTracks().forEach((track) => track.stop());
            voiceStatus.textContent = '⏳ Распознавание...';
            await sendAudioToServer(audioBlob);
        };

        mediaRecorder.start();
        voiceButton.classList.add('recording');
        voiceStatus.textContent = '🎤 Запись...';
        isRecording = true;
    } catch (error) {
        console.error('Mic error:', error);
        voiceStatus.textContent = 'Ошибка доступа к микрофону';
    }
}

function stopVoiceRecording() {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
    }
    isRecording = false;
    voiceButton.classList.remove('recording');
}

async function sendAudioToServer(audioBlob) {
    const formData = new FormData();
    formData.append('audio', audioBlob, 'recording.webm');

    try {
        const response = await fetch(`${API_BASE_URL}/api/voice/process`, {
            method: 'POST',
            body: formData,
        });

        const result = await response.json();

        if (result.status === 'success') {
            const recognized = result.recognizedText || '(не распознано)';
            addMessage(recognized, true);
            addMessage(result.response || '(пустой ответ)', false);
            voiceStatus.textContent = '✅ Готово';
        } else {
            voiceStatus.textContent = '❌ ' + (result.error || 'Ошибка обработки');
        }
    } catch (error) {
        console.error('Audio send error:', error);
        voiceStatus.textContent = '❌ Ошибка отправки';
    } finally {
        voiceButton.classList.remove('recording');
        isRecording = false;
    }
}

async function initMicPermission() {
    if (!navigator.permissions || !navigator.permissions.query) {
        await requestMicAccess();
        return;
    }
    try {
        const status = await navigator.permissions.query({ name: 'microphone' });
        updateMicUi(status.state);
        status.onchange = () => updateMicUi(status.state);
    } catch {
        await requestMicAccess();
    }
}

async function requestMicAccess() {
    try {
        if (!mediaDevicesAvailable) {
            throw new Error('mediaDevices unavailable');
        }
        const stream = await getUserMediaSafe({ audio: true });
        stream.getTracks().forEach((t) => t.stop());
        micAllowed = true;
        updateMicUi('granted');
    } catch (err) {
        micAllowed = false;
        console.error('Mic permission error:', err);
        updateMicUi('denied');
    }
}

function updateMicUi(state) {
    if (state === 'granted') {
        micAllowed = true;
        micHint.style.display = 'none';
        voiceButton.disabled = false;
        voiceStatus.textContent = '';
    } else if (state === 'prompt') {
        micAllowed = false;
        micHint.style.display = 'flex';
        micHintText.textContent = 'Разрешите доступ к микрофону, чтобы записывать голос.';
        voiceButton.disabled = false;
    } else {
        micAllowed = false;
        micHint.style.display = 'flex';
        micHintText.textContent = 'Микрофон заблокирован в настройках браузера. Разрешите доступ.';
        voiceButton.disabled = true;
        voiceStatus.textContent = 'Разрешите микрофон, чтобы записывать.';
    }
}

async function getUserMediaSafe(constraints) {
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        return navigator.mediaDevices.getUserMedia(constraints);
    }
    const legacyGetUserMedia =
        navigator.getUserMedia ||
        navigator.webkitGetUserMedia ||
        navigator.mozGetUserMedia ||
        navigator.msGetUserMedia;
    if (legacyGetUserMedia) {
        return new Promise((resolve, reject) => {
            legacyGetUserMedia.call(navigator, constraints, resolve, reject);
        });
    }
    throw new Error('getUserMedia is not available');
}

