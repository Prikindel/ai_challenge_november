// Базовый JavaScript для работы с API внешней памяти
// TODO: Реализовать на этапе 4 (API и UI)

const API_BASE = '/api/memory';

// Элементы DOM
const messageForm = document.getElementById('messageForm');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const conversationList = document.getElementById('conversationList');
const dialogStatus = document.getElementById('dialogStatus');
const resetMemoryButton = document.getElementById('resetMemoryButton');
const statsContent = document.getElementById('statsContent');

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', async () => {
    await loadHistory();
    await loadStats();
    
    // Обработчики событий
    messageForm.addEventListener('submit', handleSendMessage);
    resetMemoryButton.addEventListener('click', handleResetMemory);
    
    updateDialogStatus('Готов к диалогу');
});

// Загрузка истории из памяти
async function loadHistory() {
    try {
        updateDialogStatus('Загрузка истории...');
        const response = await fetch(`${API_BASE}/history`);
        if (!response.ok) throw new Error('Ошибка загрузки истории');
        
        const data = await response.json();
        renderHistory(data.history);
        updateDialogStatus(`Загружено ${data.history.length} сообщений`);
    } catch (error) {
        console.error('Ошибка загрузки истории:', error);
        showToast('Ошибка загрузки истории');
        updateDialogStatus('Ошибка загрузки');
    }
}

// Загрузка статистики
async function loadStats() {
    try {
        const response = await fetch(`${API_BASE}/stats`);
        if (!response.ok) throw new Error('Ошибка загрузки статистики');
        
        const data = await response.json();
        renderStats(data);
    } catch (error) {
        console.error('Ошибка загрузки статистики:', error);
        statsContent.innerHTML = '<p class="empty">Ошибка загрузки статистики</p>';
    }
}

// Отправка сообщения
async function handleSendMessage(e) {
    e.preventDefault();
    
    const message = messageInput.value.trim();
    if (!message) return;
    
    // Блокируем форму
    sendButton.disabled = true;
    sendButton.classList.add('loading');
    messageInput.disabled = true;
    updateDialogStatus('Отправка сообщения...');
    
    try {
        const response = await fetch(`${API_BASE}/message`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Ошибка отправки сообщения');
        }
        
        const data = await response.json();
        
        // Добавляем сообщения в историю
        addMessage('user', message);
        addMessage('assistant', data.message);
        
        // Очищаем поле ввода
        messageInput.value = '';
        
        // Обновляем статистику
        await loadStats();
        
        updateDialogStatus('Сообщение отправлено');
    } catch (error) {
        console.error('Ошибка отправки сообщения:', error);
        showToast(error.message || 'Ошибка отправки сообщения');
        updateDialogStatus('Ошибка отправки');
    } finally {
        sendButton.disabled = false;
        sendButton.classList.remove('loading');
        messageInput.disabled = false;
        messageInput.focus();
    }
}

// Сброс памяти
async function handleResetMemory() {
    if (!confirm('Вы уверены, что хотите сбросить всю память? Это действие нельзя отменить.')) {
        return;
    }
    
    resetMemoryButton.disabled = true;
    updateDialogStatus('Сброс памяти...');
    
    try {
        const response = await fetch(`${API_BASE}/reset`, {
            method: 'POST'
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Ошибка сброса памяти');
        }
        
        // Очищаем историю на экране
        conversationList.innerHTML = '';
        
        // Обновляем статистику
        await loadStats();
        
        showToast('Память успешно сброшена');
        updateDialogStatus('Память сброшена');
    } catch (error) {
        console.error('Ошибка сброса памяти:', error);
        showToast(error.message || 'Ошибка сброса памяти');
        updateDialogStatus('Ошибка сброса');
    } finally {
        resetMemoryButton.disabled = false;
    }
}

// Рендеринг истории
function renderHistory(history) {
    conversationList.innerHTML = '';
    history.forEach(entry => {
        addMessage(entry.role, entry.content, entry.timestamp);
    });
    scrollToBottom();
}

// Добавление сообщения в список
function addMessage(role, content, timestamp = null) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;
    
    const header = document.createElement('div');
    header.className = 'message-header';
    
    const label = document.createElement('span');
    label.className = 'message-label';
    label.textContent = role === 'user' ? 'Пользователь' : 'Ассистент';
    
    header.appendChild(label);
    
    if (timestamp) {
        const time = document.createElement('span');
        time.className = 'badge muted';
        time.textContent = new Date(timestamp).toLocaleString('ru-RU');
        header.appendChild(time);
    }
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = content;
    
    messageDiv.appendChild(header);
    messageDiv.appendChild(contentDiv);
    
    conversationList.appendChild(messageDiv);
    scrollToBottom();
}

// Рендеринг статистики
function renderStats(stats) {
    if (stats.totalEntries === 0) {
        statsContent.innerHTML = '<p class="empty">Память пуста</p>';
        return;
    }
    
    const html = `
        <div class="stat-row">
            <span>Всего записей:</span>
            <strong>${stats.totalEntries}</strong>
        </div>
        <div class="stat-row">
            <span>Сообщений пользователя:</span>
            <strong>${stats.userMessages}</strong>
        </div>
        <div class="stat-row">
            <span>Ответов ассистента:</span>
            <strong>${stats.assistantMessages}</strong>
        </div>
    `;
    
    statsContent.innerHTML = html;
}

// Обновление статуса диалога
function updateDialogStatus(text) {
    dialogStatus.textContent = text;
}

// Прокрутка вниз
function scrollToBottom() {
    conversationList.scrollTop = conversationList.scrollHeight;
}

// Показ уведомления
function showToast(message) {
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = message;
    
    const container = document.getElementById('toastContainer');
    container.appendChild(toast);
    
    setTimeout(() => {
        toast.remove();
    }, 3000);
}

