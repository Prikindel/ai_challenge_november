// Базовый JavaScript для клиента

console.log('Document Indexing System loaded');

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

