// JavaScript для страницы сравнения RAG и обычного режима

const API_BASE = 'http://localhost:8080/api';

// Выполнение сравнения
async function performComparison() {
    const question = document.getElementById('questionInput').value.trim();
    if (!question) {
        showMessage('Введите вопрос', 'error');
        return;
    }
    
    const topK = parseInt(document.getElementById('topK').value) || 3;
    const minSimilarity = parseFloat(document.getElementById('minSimilarity').value) || 0.7;
    
    const statusDiv = document.getElementById('compareStatus');
    const resultsDiv = document.getElementById('comparisonResults');
    const compareButton = document.getElementById('compareButton');
    
    // Показываем статус загрузки
    statusDiv.className = 'status';
    statusDiv.textContent = 'Выполняется сравнение... Это может занять некоторое время.';
    resultsDiv.style.display = 'none';
    compareButton.disabled = true;
    compareButton.textContent = 'Сравнение...';
    
    // Очищаем предыдущие результаты
    document.getElementById('ragAnswer').innerHTML = '<p class="placeholder">Загрузка...</p>';
    document.getElementById('standardAnswer').innerHTML = '<p class="placeholder">Загрузка...</p>';
    document.getElementById('ragChunksList').innerHTML = '';
    
    try {
        const response = await fetch(`${API_BASE}/rag/compare`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                question: question,
                topK: topK,
                minSimilarity: minSimilarity
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ message: 'Unknown error' }));
            throw new Error(errorData.message || `HTTP ${response.status}`);
        }
        
        const data = await response.json();
        
        // Отображаем результаты
        displayComparisonResults(data);
        
        statusDiv.className = 'status success';
        statusDiv.textContent = 'Сравнение завершено успешно!';
        resultsDiv.style.display = 'block';
        
    } catch (error) {
        statusDiv.className = 'status error';
        statusDiv.textContent = `Ошибка: ${error.message}`;
        resultsDiv.style.display = 'none';
        
        document.getElementById('ragAnswer').innerHTML = '<p class="error-text">Ошибка загрузки ответа</p>';
        document.getElementById('standardAnswer').innerHTML = '<p class="error-text">Ошибка загрузки ответа</p>';
    } finally {
        compareButton.disabled = false;
        compareButton.textContent = 'Сравнить';
    }
}

// Отображение результатов сравнения
function displayComparisonResults(data) {
    // RAG ответ
    const ragAnswerDiv = document.getElementById('ragAnswer');
    const ragTokensBadge = document.getElementById('ragTokens');
    const ragChunksList = document.getElementById('ragChunksList');
    
    if (data.ragResponse) {
        // Рендерим Markdown
        const ragMarkdownHtml = marked.parse(data.ragResponse.answer);
        ragAnswerDiv.innerHTML = ragMarkdownHtml;
        
        // Токены
        if (data.ragResponse.tokensUsed !== null && data.ragResponse.tokensUsed !== undefined) {
            ragTokensBadge.textContent = `Токены: ${data.ragResponse.tokensUsed}`;
        } else {
            ragTokensBadge.textContent = 'Токены: -';
        }
        
        // Чанки
        if (data.ragResponse.contextChunks && data.ragResponse.contextChunks.length > 0) {
            ragChunksList.innerHTML = data.ragResponse.contextChunks.map((chunk, index) => {
                const similarityPercent = (chunk.similarity * 100).toFixed(1);
                const documentName = chunk.documentTitle || chunk.documentPath || 'Неизвестный документ';
                
                return `
                    <div class="chunk-item">
                        <div class="chunk-header">
                            <span class="chunk-number">Чанк #${chunk.chunkIndex}</span>
                            <span class="chunk-similarity">Сходство: ${similarityPercent}%</span>
                        </div>
                        <div class="chunk-source">${escapeHtml(documentName)}</div>
                        <div class="chunk-preview">${escapeHtml(chunk.content.substring(0, 150))}${chunk.content.length > 150 ? '...' : ''}</div>
                    </div>
                `;
            }).join('');
        } else {
            ragChunksList.innerHTML = '<p class="no-chunks">Чанки не найдены</p>';
        }
    } else {
        ragAnswerDiv.innerHTML = '<p class="error-text">Ответ не получен</p>';
    }
    
    // Обычный ответ
    const standardAnswerDiv = document.getElementById('standardAnswer');
    const standardTokensBadge = document.getElementById('standardTokens');
    
    if (data.standardResponse) {
        // Рендерим Markdown
        const standardMarkdownHtml = marked.parse(data.standardResponse.answer);
        standardAnswerDiv.innerHTML = standardMarkdownHtml;
        
        // Токены
        if (data.standardResponse.tokensUsed !== null && data.standardResponse.tokensUsed !== undefined) {
            standardTokensBadge.textContent = `Токены: ${data.standardResponse.tokensUsed}`;
        } else {
            standardTokensBadge.textContent = 'Токены: -';
        }
    } else {
        standardAnswerDiv.innerHTML = '<p class="error-text">Ответ не получен</p>';
    }
}

// Экранирование HTML для безопасности
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Поиск по Enter
document.getElementById('questionInput')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        performComparison();
    }
});

