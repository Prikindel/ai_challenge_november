// JavaScript для страницы сравнения RAG с фильтрацией и реранкингом

const API_BASE = 'http://localhost:8080/api';

// Инициализация: показываем/скрываем поля в зависимости от стратегии
document.addEventListener('DOMContentLoaded', () => {
    const strategySelect = document.getElementById('filterStrategy');
    if (strategySelect) {
        strategySelect.addEventListener('change', updateFilterFields);
        updateFilterFields();
    }
});

// Обновление видимости полей фильтрации
function updateFilterFields() {
    const strategy = document.getElementById('filterStrategy').value;
    const thresholdGroup = document.getElementById('thresholdGroup');
    const keepTopGroup = document.getElementById('keepTopGroup');
    
    if (strategy === 'none') {
        thresholdGroup.style.display = 'none';
        keepTopGroup.style.display = 'none';
    } else if (strategy === 'reranker') {
        thresholdGroup.style.display = 'none';
        keepTopGroup.style.display = 'none';
    } else {
        thresholdGroup.style.display = 'block';
        keepTopGroup.style.display = 'block';
    }
}

// Выполнение сравнения
async function performComparison() {
    const question = document.getElementById('questionInput').value.trim();
    if (!question) {
        showMessage('Введите вопрос', 'error');
        return;
    }
    
    const topK = parseInt(document.getElementById('topK').value) || 5;
    const minSimilarity = parseFloat(document.getElementById('minSimilarity').value) || 0.4;
    const strategy = document.getElementById('filterStrategy').value;
    const thresholdMinSimilarity = parseFloat(document.getElementById('thresholdMinSimilarity').value) || 0.6;
    const keepTop = document.getElementById('keepTop').value ? parseInt(document.getElementById('keepTop').value) : null;
    
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
    clearResults();
    
    try {
        // Сравниваем baseline (без фильтра) и filtered (с фильтром)
        const response = await fetch(`${API_BASE}/rag/compare`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                question: question,
                topK: topK,
                minSimilarity: minSimilarity,
                applyFilter: strategy !== 'none',
                strategy: strategy
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ message: 'Unknown error' }));
            throw new Error(errorData.message || `HTTP ${response.status}`);
        }
        
        const data = await response.json();
        
        // Отображаем результаты
        displayComparisonResults(data, strategy);
        
        statusDiv.className = 'status success';
        statusDiv.textContent = 'Сравнение завершено успешно!';
        resultsDiv.style.display = 'block';
        
    } catch (error) {
        statusDiv.className = 'status error';
        statusDiv.textContent = `Ошибка: ${error.message}`;
        resultsDiv.style.display = 'none';
        
        document.getElementById('filteredAnswer').innerHTML = '<p class="error-text">Ошибка загрузки ответа</p>';
    } finally {
        compareButton.disabled = false;
        compareButton.textContent = 'Сравнить';
    }
}

// Очистка результатов
function clearResults() {
    document.getElementById('baselineAnswer').innerHTML = '<p class="placeholder">Загрузка...</p>';
    document.getElementById('filteredAnswer').innerHTML = '<p class="placeholder">Загрузка...</p>';
    document.getElementById('baselineChunksListContent').innerHTML = '';
    document.getElementById('filteredChunksListContent').innerHTML = '';
    document.getElementById('filterStats').style.display = 'none';
    document.getElementById('rerankInsights').style.display = 'none';
    document.getElementById('metricsPanel').style.display = 'none';
    document.getElementById('baselineCard').style.display = 'none';
}

// Отображение результатов сравнения
function displayComparisonResults(data, strategy) {
    // Показываем baseline, если есть
    if (data.baseline) {
        displayResponse(data.baseline, 'baseline');
        document.getElementById('baselineCard').style.display = 'block';
    }
    
    // Показываем filtered
    if (data.filtered) {
        displayResponse(data.filtered, 'filtered');
    } else if (data.ragResponse) {
        // Обратная совместимость
        displayResponse(data.ragResponse, 'filtered');
    }
    
    // Показываем стандартный ответ, если есть
    if (data.standardResponse) {
        displayStandardResponse(data.standardResponse);
    }
    
    // Показываем метрики
    if (data.metrics) {
        displayMetrics(data.metrics);
    }
    
    // Показываем статистику фильтрации
    if (data.filtered && data.filtered.filterStats) {
        displayFilterStats(data.filtered.filterStats);
    }
    
    // Показываем решения реранкера
    if (data.filtered && data.filtered.rerankInsights) {
        displayRerankInsights(data.filtered.rerankInsights);
    }
}

// Отображение ответа (baseline или filtered)
function displayResponse(responseData, type) {
    const answerDiv = document.getElementById(`${type}Answer`);
    const tokensBadge = document.getElementById(`${type}Tokens`);
    const chunksList = document.getElementById(`${type}ChunksListContent`);
    
    if (responseData) {
        // Рендерим Markdown
        const markdownHtml = marked.parse(responseData.answer);
        answerDiv.innerHTML = markdownHtml;
        
        // Токены
        if (responseData.tokensUsed !== null && responseData.tokensUsed !== undefined) {
            tokensBadge.textContent = `Токены: ${responseData.tokensUsed}`;
        } else {
            tokensBadge.textContent = 'Токены: -';
        }
        
        // Чанки
        if (responseData.contextChunks && responseData.contextChunks.length > 0) {
            chunksList.innerHTML = responseData.contextChunks.map((chunk, index) => {
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
            chunksList.innerHTML = '<p class="no-chunks">Чанки не найдены</p>';
        }
    } else {
        answerDiv.innerHTML = '<p class="error-text">Ответ не получен</p>';
    }
}

// Отображение стандартного ответа
function displayStandardResponse(standardData) {
    // Можно добавить отдельный блок для стандартного ответа, если нужно
}

// Отображение метрик
function displayMetrics(metrics) {
    document.getElementById('metricsPanel').style.display = 'block';
    
    if (metrics.baselineChunks !== null && metrics.baselineChunks !== undefined) {
        document.getElementById('baselineChunks').textContent = metrics.baselineChunks;
    }
    
    if (metrics.filteredChunks !== null && metrics.filteredChunks !== undefined) {
        document.getElementById('filteredChunks').textContent = metrics.filteredChunks;
    }
    
    if (metrics.avgSimilarityBefore !== null && metrics.avgSimilarityBefore !== undefined) {
        document.getElementById('avgSimilarityBefore').textContent = (metrics.avgSimilarityBefore * 100).toFixed(1) + '%';
    }
    
    if (metrics.avgSimilarityAfter !== null && metrics.avgSimilarityAfter !== undefined) {
        document.getElementById('avgSimilarityAfter').textContent = (metrics.avgSimilarityAfter * 100).toFixed(1) + '%';
    }
    
    if (metrics.tokensSaved !== null && metrics.tokensSaved !== undefined) {
        const saved = metrics.tokensSaved;
        document.getElementById('tokensSaved').textContent = saved > 0 ? `+${saved}` : saved.toString();
    }
}

// Отображение статистики фильтрации
function displayFilterStats(stats) {
    const statsDiv = document.getElementById('filterStats');
    statsDiv.style.display = 'block';
    
    document.getElementById('statsRetrieved').textContent = stats.retrieved;
    document.getElementById('statsKept').textContent = stats.kept;
    document.getElementById('statsDropped').textContent = stats.dropped ? stats.dropped.length : 0;
    
    // Показываем отброшенные чанки
    const droppedList = document.getElementById('droppedChunksList');
    if (stats.dropped && stats.dropped.length > 0) {
        droppedList.innerHTML = stats.dropped.map(dropped => {
            const similarityPercent = (dropped.similarity * 100).toFixed(1);
            return `
                <div class="dropped-chunk-item">
                    <div><strong>Чанк:</strong> ${escapeHtml(dropped.chunkId.substring(0, 20))}...</div>
                    <div><strong>Сходство:</strong> ${similarityPercent}%</div>
                    <div class="reason"><strong>Причина:</strong> ${escapeHtml(dropped.reason)}</div>
                </div>
            `;
        }).join('');
    } else {
        droppedList.innerHTML = '<p class="no-chunks">Нет отброшенных чанков</p>';
    }
}

// Отображение решений реранкера
function displayRerankInsights(insights) {
    const insightsDiv = document.getElementById('rerankInsights');
    insightsDiv.style.display = 'block';
    
    const decisionsList = document.getElementById('rerankDecisionsList');
    if (insights && insights.length > 0) {
        decisionsList.innerHTML = insights.map(decision => {
            const scorePercent = (decision.rerankScore * 100).toFixed(1);
            const shouldUseClass = decision.shouldUse ? 'yes' : 'no';
            const shouldUseText = decision.shouldUse ? '✓ Использовать' : '✗ Не использовать';
            
            return `
                <div class="rerank-decision">
                    <div class="rerank-decision-header">
                        <span><strong>Чанк:</strong> ${escapeHtml(decision.chunkId.substring(0, 20))}...</span>
                        <span class="rerank-score">${scorePercent}%</span>
                    </div>
                    <div class="rerank-reason">${escapeHtml(decision.reason)}</div>
                    <div class="rerank-should-use ${shouldUseClass}">${shouldUseText}</div>
                </div>
            `;
        }).join('');
    } else {
        decisionsList.innerHTML = '<p class="no-chunks">Нет решений реранкера</p>';
    }
}

// Экранирование HTML для безопасности
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Показ сообщения
function showMessage(message, type) {
    const statusDiv = document.getElementById('compareStatus');
    statusDiv.className = `status ${type}`;
    statusDiv.textContent = message;
}

// Поиск по Enter
document.getElementById('questionInput')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        performComparison();
    }
});
