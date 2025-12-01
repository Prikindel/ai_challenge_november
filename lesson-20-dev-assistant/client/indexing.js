// JavaScript для страницы индексации

// Используем глобальную константу из app.js
var API_BASE = window.API_BASE || 'http://localhost:8080/api';

// Индексация файла
async function indexFile() {
    const filePathInput = document.getElementById('filePath');
    const filePath = filePathInput?.value.trim();
    
    if (!filePath) {
        showMessage('Введите путь к файлу', 'error');
        filePathInput?.focus();
        return;
    }
    
    const statusDiv = document.getElementById('indexingStatus');
    const progressDiv = document.getElementById('indexingProgress');
    
    statusDiv.className = 'status';
    statusDiv.textContent = 'Индексация...';
    progressDiv.style.display = 'block';
    
    try {
        const response = await fetch(`${API_BASE}/indexing/index`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ filePath })
        });
        
        const result = await response.json();
        
        if (result.success) {
            statusDiv.className = 'status success';
            statusDiv.textContent = `Успешно! Индексировано чанков: ${result.chunksCount}`;
            loadStatistics();
            loadDocuments();
        } else {
            statusDiv.className = 'status error';
            statusDiv.textContent = `Ошибка: ${result.error || 'Неизвестная ошибка'}`;
        }
    } catch (error) {
        statusDiv.className = 'status error';
        statusDiv.textContent = `Ошибка: ${error.message}`;
    } finally {
        progressDiv.style.display = 'none';
    }
}

// Индексация директории
async function indexDirectory() {
    const directoryPathInput = document.getElementById('directoryPath');
    const directoryPath = directoryPathInput?.value.trim();
    
    if (!directoryPath) {
        showMessage('Введите путь к директории', 'error');
        directoryPathInput?.focus();
        return;
    }
    
    const statusDiv = document.getElementById('indexingStatus');
    const progressDiv = document.getElementById('indexingProgress');
    
    statusDiv.className = 'status';
    statusDiv.textContent = 'Индексация директории...';
    progressDiv.style.display = 'block';
    
    try {
        const response = await fetch(`${API_BASE}/indexing/index-directory`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ directoryPath })
        });
        
        const result = await response.json();
        
        if (result.success) {
            statusDiv.className = 'status success';
            statusDiv.textContent = `Успешно! Обработано документов: ${result.documentsProcessed}, чанков: ${result.totalChunks}`;
            loadStatistics();
            loadDocuments();
        } else {
            statusDiv.className = 'status error';
            statusDiv.textContent = `Ошибка: ${result.error || 'Неизвестная ошибка'}`;
        }
    } catch (error) {
        statusDiv.className = 'status error';
        statusDiv.textContent = `Ошибка: ${error.message}`;
    } finally {
        progressDiv.style.display = 'none';
    }
}

// Загрузка статистики
async function loadStatistics() {
    try {
        const response = await fetch(`${API_BASE}/indexing/status`);
        const stats = await response.json();
        
        document.getElementById('documentsCount').textContent = stats.documentsCount || 0;
        document.getElementById('chunksCount').textContent = stats.chunksCount || 0;
    } catch (error) {
        console.error('Ошибка загрузки статистики:', error);
    }
}

// Загрузка списка документов
async function loadDocuments() {
    const listDiv = document.getElementById('documentsList');
    listDiv.innerHTML = '<p>Загрузка...</p>';
    
    try {
        const response = await fetch(`${API_BASE}/indexing/documents`);
        const data = await response.json();
        
        if (data.documents && data.documents.length > 0) {
            listDiv.innerHTML = data.documents.map(doc => `
                <div class="document-card">
                    <h3>${doc.title || doc.filePath}</h3>
                    <p><strong>Путь:</strong> ${doc.filePath}</p>
                    <p><strong>Чанков:</strong> ${doc.chunkCount}</p>
                    <p><strong>Индексирован:</strong> ${new Date(doc.indexedAt).toLocaleString('ru-RU')}</p>
                </div>
            `).join('');
        } else {
            listDiv.innerHTML = '<p>Нет индексированных документов</p>';
        }
    } catch (error) {
        listDiv.innerHTML = `<p class="error">Ошибка загрузки: ${error.message}</p>`;
    }
}

// Загрузка статистики при загрузке страницы
if (document.getElementById('statistics')) {
    loadStatistics();
    loadDocuments();
}

