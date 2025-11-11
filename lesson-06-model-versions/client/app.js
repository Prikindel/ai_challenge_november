const API_BASE_URL = '';

const elements = {
    defaultQuestion: document.getElementById('defaultQuestion'),
    questionInput: document.getElementById('questionInput'),
    questionCounter: document.getElementById('questionCounter'),
    resetQuestionButton: document.getElementById('resetQuestionButton'),
    resetModelsButton: document.getElementById('resetModelsButton'),
    selectedModels: document.getElementById('selectedModels'),
    modelsCatalog: document.getElementById('modelsCatalog'),
    runButton: document.getElementById('runExperimentButton'),
    loadingIndicator: document.getElementById('loadingIndicator'),
    errorMessage: document.getElementById('errorMessage'),
    usedQuestion: document.getElementById('usedQuestion'),
    usedModels: document.getElementById('usedModels'),
    modelCards: document.getElementById('modelCards'),
    comparisonBlock: document.getElementById('comparisonBlock'),
    comparisonSummary: document.getElementById('comparisonSummary'),
    comparisonDetails: document.getElementById('comparisonDetails'),
    modelLinksBlock: document.getElementById('modelLinksBlock'),
    modelLinksList: document.getElementById('modelLinksList'),
    historyList: document.getElementById('historyList'),
    clearHistoryButton: document.getElementById('clearHistoryButton'),
};

const state = {
    defaultQuestion: '',
    catalog: [],
    selectedModelIds: [],
    history: [],
};

function init() {
    bindEvents();
    fetchCatalog();
    updateQuestionCounter();
    renderHistory();
}

function bindEvents() {
    elements.questionInput.addEventListener('input', updateQuestionCounter);

    elements.resetQuestionButton.addEventListener('click', () => {
        elements.questionInput.value = '';
        updateQuestionCounter();
    });

    elements.resetModelsButton.addEventListener('click', () => {
        state.selectedModelIds = [...(state.catalogDefaults ?? state.selectedModelIds)];
        renderSelectedModels();
        renderModelsCatalog();
    });

    elements.clearHistoryButton.addEventListener('click', () => {
        state.history = [];
        renderHistory();
    });

    elements.runButton.addEventListener('click', runComparison);
}

async function fetchCatalog() {
    showError();
    try {
        const response = await fetch(`${API_BASE_URL}/api/models`);
        if (!response.ok) {
            throw new Error('Не удалось получить список моделей');
        }
        const data = await response.json();
        applyDefaults(data);
    } catch (error) {
        console.error(error);
        showError('Ошибка загрузки настроек. Убедитесь, что сервер запущен.');
    }
}

function applyDefaults(data) {
    state.defaultQuestion = data?.defaultQuestion ?? state.defaultQuestion;
    const catalog = Array.isArray(data?.models) ? data.models : [];
    state.catalog = catalog;
    state.catalogDefaults = Array.isArray(data?.defaultModelIds) ? data.defaultModelIds : [];
    if (!state.selectedModelIds.length) {
        state.selectedModelIds = [...state.catalogDefaults];
    }

    elements.defaultQuestion.textContent = state.defaultQuestion || '—';

    renderSelectedModels();
    renderModelsCatalog();
}

function updateQuestionCounter() {
    const currentLength = elements.questionInput.value.length;
    elements.questionCounter.textContent = `${currentLength} / 4000`;
}

function renderSelectedModels() {
    elements.selectedModels.innerHTML = '';

    if (!state.selectedModelIds.length) {
        elements.selectedModels.classList.add('empty');
        elements.selectedModels.innerHTML = `
            <div>Не выбрано ни одной модели. Добавьте карточки ниже.</div>
        `;
        return;
    }

    elements.selectedModels.classList.remove('empty');

    state.selectedModelIds.forEach((modelId) => {
        const info = getModelById(modelId);
        const chip = document.createElement('span');
        chip.className = 'model-chip';
        chip.innerHTML = `
            <span>${info?.displayName ?? modelId}</span>
        `;

        const removeButton = document.createElement('button');
        removeButton.type = 'button';
        removeButton.setAttribute('aria-label', `Удалить модель ${info?.displayName ?? modelId}`);
        removeButton.textContent = '×';
        removeButton.addEventListener('click', () => {
            state.selectedModelIds = state.selectedModelIds.filter((id) => id !== modelId);
            renderSelectedModels();
            renderModelsCatalog();
        });

        chip.appendChild(removeButton);
        elements.selectedModels.appendChild(chip);
    });
}

function renderModelsCatalog() {
    elements.modelsCatalog.innerHTML = '';

    if (!state.catalog.length) {
        const placeholder = document.createElement('div');
        placeholder.className = 'empty-state';
        placeholder.innerHTML = `
            <div class="empty-icon">🧩</div>
            <p>Список моделей пуст. Проверьте конфигурацию.</p>
        `;
        elements.modelsCatalog.appendChild(placeholder);
        return;
    }

    state.catalog.forEach((model) => {
        const card = document.createElement('article');
        card.className = 'catalog-card';
        if (state.selectedModelIds.includes(model.id)) {
            card.classList.add('selected');
        }

        const title = document.createElement('h4');
        title.textContent = model.displayName;

        const description = document.createElement('p');
        description.textContent = model.huggingFaceUrl.replace('https://huggingface.co/', '');

        const meta = document.createElement('div');
        meta.className = 'catalog-meta';
        if (typeof model.pricePer1kTokensUsd === 'number') {
            meta.innerHTML += `<span>💰 $${model.pricePer1kTokensUsd.toFixed(2)} за 1k токенов</span>`;
        } else {
            meta.innerHTML += '<span>💰 цена не указана</span>';
        }
        meta.innerHTML += `<span>🔁 endpoint: ${model.endpoint.split('//')[1] ?? model.endpoint}</span>`;

        const params = formatDefaultParams(model.defaultParams);
        if (params) {
            meta.innerHTML += `<span>⚙️ ${params}</span>`;
        }

        const toggleButton = document.createElement('button');
        toggleButton.type = 'button';
        toggleButton.textContent = state.selectedModelIds.includes(model.id)
            ? 'Убрать'
            : 'Добавить';
        toggleButton.addEventListener('click', () => toggleModelSelection(model.id));

        card.appendChild(title);
        card.appendChild(description);
        card.appendChild(meta);
        card.appendChild(toggleButton);
        elements.modelsCatalog.appendChild(card);
    });
}

function toggleModelSelection(modelId) {
    if (state.selectedModelIds.includes(modelId)) {
        state.selectedModelIds = state.selectedModelIds.filter((id) => id !== modelId);
    } else {
        state.selectedModelIds = [...state.selectedModelIds, modelId];
    }
    renderSelectedModels();
    renderModelsCatalog();
}

function formatDefaultParams(params = {}) {
    const entries = Object.entries(params).filter(([, value]) => value != null);
    if (!entries.length) {
        return '';
    }
    return entries
        .map(([key, value]) => `${key}=${Array.isArray(value) ? value.join(',') : value}`)
        .join(' • ');
}

async function runComparison() {
    const question = elements.questionInput.value.trim();
    const modelIds = state.selectedModelIds.filter(Boolean);

    showError();
    setLoading(true);

    const payload = {};
    if (question.length > 0) {
        payload.question = question;
    }
    if (modelIds.length > 0) {
        payload.modelIds = modelIds;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/api/models/compare`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });

        const data = await response.json();
        if (!response.ok) {
            const message = data?.error ?? 'Не удалось выполнить сравнение моделей';
            throw new Error(message);
        }

        renderResults(data);
        pushHistoryEntry({
            timestamp: Date.now(),
            question: data.question,
            modelIds: data.modelResults?.map((item) => item.modelId) ?? modelIds,
            summary: data.comparisonSummary,
            payload: data,
        });
    } catch (error) {
        console.error(error);
        showError(error.message || 'Во время запроса произошла ошибка');
    } finally {
        setLoading(false);
    }
}

function renderResults(data, options = { addToHistory: false }) {
    elements.usedQuestion.textContent = data.question || '—';
    const modelNames = (data.modelResults || []).map((item) => item.displayName || item.modelId);
    const modelIds = (data.modelResults || []).map((item) => item.modelId);
    state.selectedModelIds = modelIds.length ? [...modelIds] : state.selectedModelIds;
    renderSelectedModels();
    renderModelsCatalog();
    elements.usedModels.textContent = modelNames.length ? modelNames.join(', ') : '—';

    elements.modelCards.innerHTML = '';
    (data.modelResults || []).forEach((result, index) => {
        elements.modelCards.appendChild(renderModelCard(result, index));
    });

    if (data.comparisonSummary) {
        elements.comparisonBlock.hidden = false;
        elements.comparisonSummary.textContent = data.comparisonSummary;
        elements.comparisonDetails.innerHTML = '';
        (data.modelResults || []).forEach((result) => {
            elements.comparisonDetails.appendChild(renderComparisonRow(result));
        });
    } else {
        elements.comparisonBlock.hidden = true;
    }

    if (Array.isArray(data.modelLinks) && data.modelLinks.length) {
        elements.modelLinksBlock.hidden = false;
        elements.modelLinksList.innerHTML = '';
        data.modelLinks.forEach((link) => {
            const li = document.createElement('li');
            const anchor = document.createElement('a');
            anchor.href = link.huggingFaceUrl;
            anchor.target = '_blank';
            anchor.rel = 'noopener noreferrer';
            anchor.textContent = `${link.modelId}`;
            li.appendChild(anchor);
            elements.modelLinksList.appendChild(li);
        });
    } else {
        elements.modelLinksBlock.hidden = true;
    }

    if (options.addToHistory) {
        pushHistoryEntry({
            timestamp: Date.now(),
            question: data.question,
            modelIds: data.modelResults?.map((item) => item.modelId) ?? [],
            summary: data.comparisonSummary,
            payload: data,
        });
    }
}

function renderModelCard(result, index) {
    const card = document.createElement('article');
    const variants = ['variant-a', 'variant-b', 'variant-c'];
    card.className = `model-card ${variants[index % variants.length]}`;
    if (result.isError) {
        card.classList.add('error');
    }

    const header = document.createElement('div');
    header.className = 'model-header';

    const name = document.createElement('div');
    name.className = 'model-name';
    name.textContent = result.displayName ?? result.modelId;

    const link = document.createElement('a');
    link.className = 'model-link';
    link.href = result.huggingFaceUrl;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.textContent = 'Открыть на HF';

    header.appendChild(name);
    header.appendChild(link);

    const answer = document.createElement('div');
    answer.className = 'model-answer';
    if (result.isError) {
        answer.textContent = result.answer || 'Не удалось получить ответ от модели.';
    } else if (result.answer) {
        const html = typeof marked !== 'undefined' ? marked.parse(result.answer) : escapeHtml(result.answer);
        answer.innerHTML = html;
    } else {
        answer.textContent = 'Ответ отсутствует 😢';
    }

    const meta = document.createElement('div');
    meta.className = 'model-meta';
    if (result.isError) {
        meta.innerHTML = '<span class="meta-chip">⚠️ Модель не вернула ответ</span>';
    } else {
        const chips = buildMetaChips(result.meta);
        if (!chips.length) {
            meta.innerHTML = '<span class="meta-chip">Метрики недоступны</span>';
        } else {
            chips.forEach((chip) => meta.appendChild(chip));
        }
    }

    card.appendChild(header);
    card.appendChild(answer);
    card.appendChild(meta);
    return card;
}

function renderComparisonRow(result) {
    const row = document.createElement('article');
    row.className = 'comparison-card';
    if (result.isError) {
        row.classList.add('error');
    }

    const title = document.createElement('strong');
    title.textContent = result.displayName ?? result.modelId;

    const metrics = document.createElement('div');
    metrics.className = 'comparison-metrics';
    if (result.isError) {
        metrics.innerHTML = `<span>⚠️ ${escapeHtml(result.answer || 'Модель не ответила')}</span>`;
    } else {
        metrics.innerHTML = `
        <span>⏱ ${formatDuration(result.meta?.durationMs)}</span>
        <span>🔤 ${formatTokens(result.meta)}</span>
        <span>💰 ${formatCost(result.meta?.costUsd)}</span>
    `;
    }

    row.appendChild(title);
    row.appendChild(metrics);
    return row;
}

function buildMetaChips(meta = {}) {
    const chips = [];
    if (meta.durationMs != null) {
        const chip = document.createElement('span');
        chip.className = 'meta-chip';
        chip.textContent = `⏱ ${formatDuration(meta.durationMs)}`;
        chips.push(chip);
    }

    if (meta.totalTokens != null) {
        const chip = document.createElement('span');
        chip.className = 'meta-chip';
        const prompt = meta.promptTokens ?? '—';
        const completion = meta.completionTokens ?? '—';
        chip.textContent = `🔤 ${meta.totalTokens} токенов (prompt: ${prompt}, completion: ${completion})`;
        chips.push(chip);
    }

    if (meta.costUsd != null) {
        const chip = document.createElement('span');
        chip.className = 'meta-chip';
        chip.textContent = `💰 ${formatCost(meta.costUsd)}`;
        chips.push(chip);
    }

    return chips;
}

function formatDuration(value) {
    if (value == null) {
        return 'недоступно';
    }
    if (value < 1000) {
        return `${value} мс`;
    }
    return `${(value / 1000).toFixed(2)} с`;
}

function formatTokens(meta = {}) {
    if (meta.totalTokens == null) {
        return 'недоступно';
    }
    return `${meta.totalTokens} токенов`;
}

function formatCost(value) {
    if (value == null) {
        return 'недоступно';
    }
    return `$${value.toFixed(2)}`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function setLoading(isLoading) {
    elements.runButton.disabled = isLoading;
    elements.loadingIndicator.hidden = !isLoading;
}

function showError(message = '') {
    if (!message) {
        elements.errorMessage.hidden = true;
        elements.errorMessage.textContent = '';
        return;
    }
    elements.errorMessage.hidden = false;
    elements.errorMessage.textContent = message;
}

function pushHistoryEntry(entry) {
    state.history.unshift(entry);
    if (state.history.length > 10) {
        state.history.pop();
    }
    renderHistory();
}

function renderHistory() {
    if (!state.history.length) {
        elements.historyList.classList.add('empty');
        elements.historyList.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📜</div>
                <p>Пока ничего нет. Проведите сравнение и истории появятся здесь.</p>
            </div>
        `;
        return;
    }

    elements.historyList.classList.remove('empty');
    elements.historyList.innerHTML = '';

    state.history.forEach((entry) => {
        const item = document.createElement('article');
        item.className = 'history-entry';

        const time = document.createElement('time');
        time.dateTime = new Date(entry.timestamp).toISOString();
        time.textContent = new Date(entry.timestamp).toLocaleString();

        const question = document.createElement('div');
        question.innerHTML = `<strong>Вопрос:</strong> ${entry.question || 'дефолтный'}`;

        const models = document.createElement('ul');
        (entry.modelIds || []).forEach((modelId) => {
            const li = document.createElement('li');
            li.textContent = getModelById(modelId)?.displayName || modelId;
            models.appendChild(li);
        });

        const summary = document.createElement('p');
        summary.textContent = entry.summary || 'Без сравнительного комментария';

        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = 'Открыть результаты';
        button.addEventListener('click', () => {
            renderResults(entry.payload, { addToHistory: false });
        });

        item.appendChild(time);
        item.appendChild(question);
        item.appendChild(models);
        item.appendChild(summary);
        item.appendChild(button);

        elements.historyList.appendChild(item);
    });
}

function getModelById(modelId) {
    return state.catalog.find((model) => model.id === modelId);
}

init();

