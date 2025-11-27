// Утилиты для работы с цитатами в ответах

/**
 * Извлекает ссылки на документы из текста ответа
 * Поддерживает форматы:
 * - [Источник: название](путь)
 * - [1] название (путь)
 * - Источник: путь
 */
function extractCitations(text) {
    const citations = [];
    
    // Markdown формат: [Источник: название](путь)
    const markdownRegex = /\[Источник:\s*([^\]]+)\]\(([^)]+)\)/gi;
    let match;
    while ((match = markdownRegex.exec(text)) !== null) {
        citations.push({
            text: match[0],
            title: match[1].trim(),
            path: match[2].trim(),
            start: match.index,
            end: match.index + match[0].length
        });
    }
    
    // Нумерованный формат: [1] название (путь) или [1] путь
    const numberedRegex = /\[\d+\]\s*(?:([^(]+)\s*)?\(([^)]+)\)|\[\d+\]\s+([^\s,;.]+)/gi;
    while ((match = numberedRegex.exec(text)) !== null) {
        const title = match[1]?.trim();
        const path = match[2] || match[3];
        if (path) {
            citations.push({
                text: match[0],
                title: title || extractTitleFromPath(path.trim()),
                path: path.trim(),
                start: match.index,
                end: match.index + match[0].length
            });
        }
    }
    
    // Простой формат: Источник: путь
    const simpleRegex = /(?:Источник|Source):\s*(?:([^:\-]+)(?:[-–]|:))?\s*([^\s,;.]+)/gi;
    while ((match = simpleRegex.exec(text)) !== null) {
        const title = match[1]?.trim();
        const path = match[2]?.trim();
        if (path) {
            citations.push({
                text: match[0],
                title: title || extractTitleFromPath(path),
                path: path,
                start: match.index,
                end: match.index + match[0].length
            });
        }
    }
    
    // Удаляем дубликаты по пути
    const uniqueCitations = [];
    const seenPaths = new Set();
    for (const citation of citations) {
        if (!seenPaths.has(citation.path)) {
            seenPaths.add(citation.path);
            uniqueCitations.push(citation);
        }
    }
    
    return uniqueCitations;
}

/**
 * Извлекает название из пути документа
 */
function extractTitleFromPath(path) {
    const fileName = path.split('/').pop() || path;
    const nameWithoutExt = fileName.split('.').slice(0, -1).join('.') || fileName;
    return nameWithoutExt
        .replace(/[-_]/g, ' ')
        .split(' ')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
        .join(' ');
}

/**
 * Заменяет цитаты в тексте на кликабельные ссылки
 */
function replaceCitationsWithLinks(text, citations) {
    if (!citations || citations.length === 0) {
        return text;
    }
    
    // Сортируем цитаты по позиции (от конца к началу), чтобы не сломать индексы
    const sortedCitations = [...citations].sort((a, b) => b.start - a.start);
    
    let result = text;
    for (const citation of sortedCitations) {
        const link = `<a href="#" class="citation-link" data-document-path="${escapeHtml(citation.path)}" data-document-title="${escapeHtml(citation.title)}">${escapeHtml(citation.text)}</a>`;
        result = result.substring(0, citation.start) + link + result.substring(citation.end);
    }
    
    return result;
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

/**
 * Обработчик клика на цитату - открывает просмотр документа
 */
function handleCitationClick(event, documentPath, documentTitle) {
    event.preventDefault();
    if (typeof window.openDocumentViewer === 'function') {
        window.openDocumentViewer(documentPath, documentTitle);
    } else {
        console.error('openDocumentViewer is not available');
    }
}

/**
 * Инициализирует обработчики кликов на цитаты в элементе
 */
function initializeCitationLinks(containerElement) {
    const citationLinks = containerElement.querySelectorAll('.citation-link');
    citationLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            const path = link.getAttribute('data-document-path');
            const title = link.getAttribute('data-document-title');
            handleCitationClick(e, path, title);
        });
    });
}

