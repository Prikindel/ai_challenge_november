// Просмотрщик документов - модальное окно

// Используем глобальную константу из app.js
var API_BASE = window.API_BASE || 'http://localhost:8080/api';

/**
 * Открывает модальное окно просмотра документа (глобальная функция)
 */
window.openDocumentViewer = async function(documentPath, documentTitle) {
    // Создаём модальное окно, если его ещё нет
    let modal = document.getElementById('documentViewerModal');
    if (!modal) {
        modal = createModalElement();
        document.body.appendChild(modal);
    }
    
    // Показываем модальное окно
    modal.style.display = 'block';
    document.body.style.overflow = 'hidden';
    
    // Загружаем документ
    await loadDocument(documentPath, documentTitle);
}

/**
 * Создаёт элемент модального окна
 */
function createModalElement() {
    const modal = document.createElement('div');
    modal.id = 'documentViewerModal';
    modal.className = 'document-modal';
    modal.innerHTML = `
        <div class="modal-overlay" onclick="closeDocumentViewer()"></div>
        <div class="modal-content">
            <div class="modal-header">
                <h2 id="modalDocumentTitle">Загрузка документа...</h2>
                <button class="modal-close" onclick="closeDocumentViewer()" aria-label="Закрыть">×</button>
            </div>
            <div class="modal-body">
                <div id="modalLoading" class="modal-loading">
                    <p>Загрузка документа...</p>
                </div>
                <div id="modalContent" class="modal-document-content" style="display: none;">
                    <div id="modalDocumentInfo" class="document-info"></div>
                    <div id="modalDocumentText" class="document-text"></div>
                </div>
                <div id="modalError" class="modal-error" style="display: none;"></div>
            </div>
        </div>
    `;
    
    // Закрытие по Escape
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && modal.style.display === 'block') {
            closeDocumentViewer();
        }
    });
    
    return modal;
}

/**
 * Загружает документ через API
 */
async function loadDocument(documentPath, documentTitle) {
    const loadingDiv = document.getElementById('modalLoading');
    const contentDiv = document.getElementById('modalContent');
    const errorDiv = document.getElementById('modalError');
    const titleDiv = document.getElementById('modalDocumentTitle');
    const infoDiv = document.getElementById('modalDocumentInfo');
    const textDiv = document.getElementById('modalDocumentText');
    
    // Показываем загрузку
    loadingDiv.style.display = 'block';
    contentDiv.style.display = 'none';
    errorDiv.style.display = 'none';
    titleDiv.textContent = documentTitle || 'Загрузка документа...';
    
    try {
        // Кодируем каждый сегмент пути отдельно, сохраняя разделители /
        // Для пути "documents/01-mcp-server-creation.md" получим "documents/01-mcp-server-creation.md"
        const pathSegments = documentPath.split('/').map(segment => encodeURIComponent(segment));
        const encodedPath = pathSegments.join('/');
        const url = `${API_BASE}/documents/${encodedPath}`;
        const response = await fetch(url);
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ message: 'Unknown error' }));
            throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
        }
        
        const document = await response.json();
        
        // Отображаем документ
        titleDiv.textContent = document.documentTitle || document.documentPath;
        
        // Информация о документе
        const indexedDate = new Date(document.indexedAt * 1000).toLocaleString('ru-RU');
        infoDiv.innerHTML = `
            <div class="info-item">
                <strong>Путь:</strong> <code>${escapeHtml(document.documentPath)}</code>
            </div>
            <div class="info-item">
                <strong>Индексирован:</strong> ${indexedDate}
            </div>
            <div class="info-item">
                <strong>Количество чанков:</strong> ${document.chunksCount}
            </div>
        `;
        
        // Содержимое документа (рендерим как Markdown, если есть marked)
        if (typeof marked !== 'undefined') {
            textDiv.innerHTML = marked.parse(document.content);
        } else {
            // Простое форматирование, если marked недоступен
            textDiv.innerHTML = formatPlainText(document.content);
        }
        
        loadingDiv.style.display = 'none';
        contentDiv.style.display = 'block';
        
    } catch (error) {
        loadingDiv.style.display = 'none';
        errorDiv.style.display = 'block';
        errorDiv.innerHTML = `
            <p class="error-message">Ошибка загрузки документа:</p>
            <p class="error-details">${escapeHtml(error.message)}</p>
            <button onclick="closeDocumentViewer()" class="error-button">Закрыть</button>
        `;
    }
}

/**
 * Форматирует обычный текст (если marked недоступен)
 */
function formatPlainText(text) {
    return text
        .split('\n')
        .map(line => {
            // Заголовки
            if (line.startsWith('# ')) {
                return `<h1>${escapeHtml(line.substring(2))}</h1>`;
            } else if (line.startsWith('## ')) {
                return `<h2>${escapeHtml(line.substring(3))}</h2>`;
            } else if (line.startsWith('### ')) {
                return `<h3>${escapeHtml(line.substring(4))}</h3>`;
            } else if (line.trim() === '') {
                return '<br>';
            } else {
                return `<p>${escapeHtml(line)}</p>`;
            }
        })
        .join('');
}

/**
 * Закрывает модальное окно (глобальная функция)
 */
window.closeDocumentViewer = function() {
    const modal = document.getElementById('documentViewerModal');
    if (modal) {
        modal.style.display = 'none';
        document.body.style.overflow = '';
    }
}

/**
 * Экранирование HTML
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

