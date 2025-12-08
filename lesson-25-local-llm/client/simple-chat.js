// Простой чат без RAG и MCP
let currentSessionId = null;

// Загрузка статуса LLM
async function loadLLMStatus() {
    try {
        const response = await fetch('/api/llm/status');
        const data = await response.json();
        
        const statusEl = document.getElementById('llmStatus');
        const providerEl = document.getElementById('llmProvider');
        
        if (data.localLLM && data.localLLM.enabled && data.localLLM.available) {
            providerEl.textContent = `Локальная LLM (${data.localLLM.provider}: ${data.localLLM.model})`;
            statusEl.className = 'llm-status';
        } else {
            providerEl.textContent = data.provider;
            statusEl.className = 'llm-status unavailable';
        }
    } catch (error) {
        console.error('Failed to load LLM status:', error);
        document.getElementById('llmProvider').textContent = 'Ошибка загрузки';
    }
}

// Отправка сообщения
async function sendMessage() {
    const input = document.getElementById('messageInput');
    const button = document.getElementById('sendButton');
    const messagesEl = document.getElementById('messages');
    
    const question = input.value.trim();
    if (!question) return;
    
    // Блокируем кнопку
    button.disabled = true;
    input.disabled = true;
    
    // Добавляем сообщение пользователя
    addMessage('user', question);
    input.value = '';
    
    // Добавляем индикатор загрузки
    const loadingId = addMessage('assistant', 'Думаю...');
    
    try {
        const response = await fetch('/api/llm/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                question: question,
                systemPrompt: 'Ты — полезный ассистент. Отвечай на вопросы пользователя дружелюбно и информативно.'
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Удаляем индикатор загрузки и добавляем ответ
        removeMessage(loadingId);
        addMessage('assistant', data.answer);
        
    } catch (error) {
        console.error('Error sending message:', error);
        removeMessage(loadingId);
        addMessage('assistant', `Ошибка: ${error.message}`);
    } finally {
        button.disabled = false;
        input.disabled = false;
        input.focus();
    }
}

// Добавление сообщения
function addMessage(role, content) {
    const messagesEl = document.getElementById('messages');
    const messageId = 'msg-' + Date.now() + '-' + Math.random();
    
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;
    messageDiv.id = messageId;
    
    const headerDiv = document.createElement('div');
    headerDiv.className = 'message-header';
    headerDiv.textContent = role === 'user' ? 'Вы' : 'Ассистент';
    
    const contentDiv = document.createElement('div');
    if (role === 'assistant') {
        // Рендерим Markdown для ответов ассистента
        contentDiv.innerHTML = marked.parse(content);
    } else {
        contentDiv.textContent = content;
    }
    
    messageDiv.appendChild(headerDiv);
    messageDiv.appendChild(contentDiv);
    messagesEl.appendChild(messageDiv);
    
    // Прокрутка вниз
    messagesEl.scrollTop = messagesEl.scrollHeight;
    
    return messageId;
}

// Удаление сообщения
function removeMessage(messageId) {
    const messageEl = document.getElementById(messageId);
    if (messageEl) {
        messageEl.remove();
    }
}

// Обработка Enter в textarea
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('messageInput');
    
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
    
    // Загружаем статус LLM
    loadLLMStatus();
    
    // Фокус на поле ввода
    input.focus();
});

