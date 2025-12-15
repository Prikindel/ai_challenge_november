// Базовый JavaScript для клиента

// Глобальная константа для API базового URL
if (typeof window.API_BASE === 'undefined') {
    window.API_BASE = 'http://localhost:8080/api';
}

// Функция для отображения сообщений
function showMessage(message, type = 'info') {
    const messageDiv = document.createElement('div');
    messageDiv.className = type;
    messageDiv.textContent = message;
    document.body.appendChild(messageDiv);
    
    setTimeout(() => {
        messageDiv.remove();
    }, 5000);
}

