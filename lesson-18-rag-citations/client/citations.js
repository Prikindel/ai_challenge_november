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
 * Заменяет ссылки на цитаты в уже обработанном HTML
 * Ищет ссылки, которые соответствуют цитатам, и заменяет их на ссылки с обработчиками
 */
function replaceCitationLinksInHTML(containerElement, citations) {
    if (!citations || citations.length === 0) {
        return;
    }
    
    // Создаём Map для быстрого поиска цитат по пути
    const citationMap = new Map();
    citations.forEach(cit => {
        // Нормализуем путь для сравнения
        const normalizedPath = cit.path.replace(/^\/+|\/+$/g, ''); // Убираем ведущие/конечные слэши
        citationMap.set(normalizedPath, cit);
        
        // Также добавляем вариант с полным путём
        if (cit.path.startsWith('documents/')) {
            citationMap.set(cit.path, cit);
        }
    });
    
    // Находим все ссылки в контейнере
    const allLinks = containerElement.querySelectorAll('a');
    
    allLinks.forEach(link => {
        const href = link.getAttribute('href') || '';
        const linkText = link.textContent.trim();
        
        // Нормализуем href для сравнения
        let normalizedHref = href.replace(/^\/+|\/+$/g, '');
        
        // Ищем соответствующую цитату по пути
        let citation = citationMap.get(normalizedHref);
        
        if (!citation) {
            // Пробуем найти по части пути
            citation = Array.from(citationMap.values()).find(cit => {
                const citPath = cit.path.replace(/^\/+|\/+$/g, '');
                return normalizedHref === citPath || 
                       normalizedHref.endsWith('/' + citPath) ||
                       normalizedHref.endsWith(citPath) ||
                       citPath.endsWith(normalizedHref);
            });
        }
        
        // Если не нашли по пути, проверяем по тексту
        if (!citation) {
            citation = citations.find(cit => {
                const citationText = cit.text.replace(/\[|\]/g, '').trim();
                return linkText.includes(citationText) ||
                       (citationText.includes('Источник') && linkText.includes('Источник')) ||
                       (citationText.includes(cit.title) && cit.title.length > 3);
            });
        }
        
        if (citation) {
            // Заменяем ссылку на нашу ссылку с обработчиком
            link.className = 'citation-link';
            link.href = '#'; // Предотвращаем переход по ссылке
            link.setAttribute('data-document-path', citation.path);
            link.setAttribute('data-document-title', citation.title);
        }
    });
}

/**
 * Инициализирует обработчики кликов на цитаты в элементе
 */
function initializeCitationLinks(containerElement) {
    const citationLinks = containerElement.querySelectorAll('.citation-link');
    citationLinks.forEach(link => {
        // Удаляем старые обработчики, чтобы избежать дублирования
        const newLink = link.cloneNode(true);
        link.parentNode.replaceChild(newLink, link);
        
        const path = newLink.getAttribute('data-document-path');
        const title = newLink.getAttribute('data-document-title');
        
        if (path && title) {
            newLink.addEventListener('click', (e) => {
                handleCitationClick(e, path, title);
            });
        }
    });
}

// Делаем функции глобально доступными
window.replaceCitationLinksInHTML = replaceCitationLinksInHTML;

