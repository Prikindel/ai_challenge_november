const API_BASE_URL = '';

const elements = {
    defaultQuestion: document.getElementById('defaultQuestion'),
    questionInput: document.getElementById('questionInput'),
    questionCounter: document.getElementById('questionCounter'),
    resetQuestionButton: document.getElementById('resetQuestionButton'),
    addTemperatureButton: document.getElementById('addTemperatureButton'),
    temperaturesList: document.getElementById('temperaturesList'),
    runExperimentButton: document.getElementById('runExperimentButton'),
    loadingIndicator: document.getElementById('loadingIndicator'),
    errorMessage: document.getElementById('errorMessage'),
    usedQuestion: document.getElementById('usedQuestion'),
    usedDefaultQuestion: document.getElementById('usedDefaultQuestion'),
    temperatureCards: document.getElementById('temperatureCards'),
    comparisonBlock: document.getElementById('comparisonBlock'),
    comparisonSummary: document.getElementById('comparisonSummary'),
    comparisonDetails: document.getElementById('comparisonDetails'),
    historyList: document.getElementById('historyList'),
    clearHistoryButton: document.getElementById('clearHistoryButton'),
};

const state = {
    defaultQuestion: '',
    defaultTemperatures: [],
    currentTemperatures: [],
    history: [],
};

function init() {
    bindEvents();
    fetchDefaults();
    updateQuestionCounter();
    renderHistory();
}

function bindEvents() {
    elements.addTemperatureButton.addEventListener('click', () => {
        addTemperature(suggestTemperature());
        renderTemperatures();
    });

    elements.resetQuestionButton.addEventListener('click', () => {
        elements.questionInput.value = '';
        updateQuestionCounter();
    });

    elements.questionInput.addEventListener('input', updateQuestionCounter);

    elements.runExperimentButton.addEventListener('click', runExperiment);

    elements.clearHistoryButton.addEventListener('click', () => {
        state.history = [];
        renderHistory();
    });
}

async function fetchDefaults() {
    showError();
    try {
        const response = await fetch(`${API_BASE_URL}/temperature`);
        if (!response.ok) {
            throw new Error('Не удалось получить дефолтные настройки');
        }
        const data = await response.json();
        applyDefaults(data);
    } catch (error) {
        console.error(error);
        showError('Ошибка загрузки настроек. Убедитесь, что сервер запущен.');
    }
}

function applyDefaults(data, options = {}) {
    const { overwriteTemperatures = true } = options;

    state.defaultQuestion = data?.defaultQuestion ?? state.defaultQuestion;

    if (Array.isArray(data?.defaultTemperatures) && data.defaultTemperatures.length > 0) {
        state.defaultTemperatures = data.defaultTemperatures.map((value) =>
            roundTemperature(Number(value) || 0)
        );
    }

    elements.defaultQuestion.textContent = state.defaultQuestion || '—';

    if (overwriteTemperatures) {
        state.currentTemperatures = [...state.defaultTemperatures];
        renderTemperatures();
    }
}

function updateQuestionCounter() {
    const currentLength = elements.questionInput.value.length;
    elements.questionCounter.textContent = `${currentLength} / 2000`;
}

function renderTemperatures() {
    elements.temperaturesList.innerHTML = '';

    if (state.currentTemperatures.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'empty-state';
        empty.innerHTML = `
            <div class="empty-icon">🌡️</div>
            <p>Добавьте хотя бы одну температуру, чтобы запустить эксперимент.</p>
        `;
        elements.temperaturesList.appendChild(empty);
        return;
    }

    state.currentTemperatures.forEach((temperature, index) => {
        const item = document.createElement('div');
        const category = categorizeTemperature(temperature);
        item.className = `temperature-item ${category}`;

        const badge = document.createElement('div');
        badge.className = 'temperature-badge';
        badge.textContent = formatTemperature(temperature);

        const label = document.createElement('label');
        label.innerHTML = `
            <span>Температура #${index + 1}</span>
        `;

        const input = document.createElement('input');
        input.type = 'number';
        input.step = '0.1';
        input.min = '0';
        input.max = '2';
        input.value = temperature.toFixed(2);
        input.addEventListener('change', (event) => {
            const value = parseFloat(event.target.value.replace(',', '.'));
            const normalized = Number.isFinite(value) ? clamp(value, 0, 2) : temperature;
            state.currentTemperatures[index] = roundTemperature(normalized);
            renderTemperatures();
        });
        input.addEventListener('focus', (event) => event.target.select());

        const buttonsWrapper = document.createElement('div');
        buttonsWrapper.style.display = 'flex';
        buttonsWrapper.style.gap = '12px';

        const removeButton = document.createElement('button');
        removeButton.type = 'button';
        removeButton.className = 'remove-temperature';
        removeButton.textContent = 'Удалить';
        removeButton.disabled = state.currentTemperatures.length === 1;
        removeButton.addEventListener('click', () => {
            if (state.currentTemperatures.length === 1) return;
            state.currentTemperatures.splice(index, 1);
            renderTemperatures();
        });

        label.appendChild(input);
        buttonsWrapper.appendChild(removeButton);

        item.appendChild(badge);
        item.appendChild(label);
        item.appendChild(buttonsWrapper);
        elements.temperaturesList.appendChild(item);
    });
}

function addTemperature(value) {
    const normalized = roundTemperature(clamp(value, 0, 2));
    state.currentTemperatures.push(normalized);
}

function suggestTemperature() {
    if (state.currentTemperatures.length === 0) return 0.7;
    const last = state.currentTemperatures[state.currentTemperatures.length - 1];
    return Math.min(last + 0.3, 2);
}

function roundTemperature(value) {
    return Math.round(value * 100) / 100;
}

function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
}

function categorizeTemperature(value) {
    if (value < 0.35) return 'cool';
    if (value < 0.85) return 'warm';
    return 'hot';
}

function formatTemperature(value) {
    return value.toFixed(2).replace(/\.?0+$/, '');
}

async function runExperiment() {
    const question = elements.questionInput.value.trim();
    const temperatures = state.currentTemperatures.filter((value) => Number.isFinite(value));

    if (temperatures.length === 0) {
        showError('Добавьте хотя бы одно корректное значение температуры (0.0 — 2.0).');
        return;
    }

    showError();
    setLoading(true);

    const payload = {
        temperatures,
    };

    if (question.length > 0) {
        payload.question = question;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/temperature`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(payload),
        });

        const data = await response.json();

        if (!response.ok) {
            const message = data?.error ?? 'Не удалось выполнить эксперимент';
            throw new Error(message);
        }

        applyDefaults(data, { overwriteTemperatures: false });

        const temperaturesFromResult = Array.isArray(data.results)
            ? data.results
                  .map((result) => roundTemperature(Number(result.temperature) || 0))
                  .filter((value) => Number.isFinite(value))
            : [];

        if (temperaturesFromResult.length > 0) {
            state.currentTemperatures = temperaturesFromResult;
        }

        renderTemperatures();
        renderResults(data);
    } catch (error) {
        console.error(error);
        showError(error.message || 'Во время запроса произошла ошибка');
    } finally {
        setLoading(false);
    }
}

function setLoading(isLoading) {
    elements.runExperimentButton.disabled = isLoading;
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

function renderResults(data, options = { addToHistory: true }) {
    const { defaultQuestion, question, results, comparison } = data;

    elements.usedQuestion.textContent = question || '—';
    elements.usedDefaultQuestion.textContent = defaultQuestion || '—';

    elements.temperatureCards.innerHTML = '';
    results.forEach((result) => {
        elements.temperatureCards.appendChild(renderResultCard(result));
    });

    if (comparison?.summary) {
        elements.comparisonBlock.hidden = false;
        elements.comparisonSummary.textContent = comparison.summary;
        elements.comparisonDetails.innerHTML = '';

        comparison.perTemperature.forEach((item) => {
            const card = document.createElement('article');
            card.className = 'comparison-card';

            const title = document.createElement('strong');
            title.textContent = `${item.mode} (${formatTemperature(item.temperature)})`;

            const metrics = document.createElement('div');
            metrics.className = 'comparison-metrics';
            metrics.innerHTML = `
                <span>Точность: ${item.accuracy}</span>
                <span>Креативность: ${item.creativity}</span>
                <span>Разнообразие: ${item.diversity}</span>
            `;

            const recommendation = document.createElement('p');
            recommendation.textContent = item.recommendation;

            card.appendChild(title);
            card.appendChild(metrics);
            card.appendChild(recommendation);
            elements.comparisonDetails.appendChild(card);
        });
    } else {
        elements.comparisonBlock.hidden = true;
    }

    if (options.addToHistory) {
        pushHistoryEntry({
            timestamp: Date.now(),
            question,
            defaultQuestion,
            temperatures: results.map((result) => result.temperature),
            payload: data,
        });
    }
}

function renderResultCard(result) {
    const card = document.createElement('article');
    const category = categorizeTemperature(result.temperature);
    const highlightMap = {
        cool: 'highlight-low',
        warm: 'highlight-medium',
        hot: 'highlight-high',
    };
    card.className = `temperature-card ${highlightMap[category] ?? ''}`;

    const header = document.createElement('div');
    header.className = 'temperature-header';
    header.innerHTML = `
        <div class="temperature-mode">${result.mode}</div>
        <div class="temperature-value">t = ${formatTemperature(result.temperature)}</div>
    `;

    const answer = document.createElement('div');
    answer.className = 'temperature-answer';
    if (result.answer) {
        const html = typeof marked !== 'undefined' ? marked.parse(result.answer) : escapeHtml(result.answer);
        answer.innerHTML = html;
    } else {
        answer.textContent = 'Ответ отсутствует 😢';
    }

    const meta = document.createElement('div');
    meta.className = 'temperature-meta';
    const chips = buildMetaChips(result.meta);
    if (chips.length === 0) {
        meta.innerHTML = '<span class="meta-chip">Метрики недоступны</span>';
    } else {
        chips.forEach((chip) => meta.appendChild(chip));
    }

    card.appendChild(header);
    card.appendChild(answer);
    card.appendChild(meta);
    return card;
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

    if (meta.requestJson) {
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'meta-chip';
        chip.style.cursor = 'pointer';
        chip.textContent = '📋 Посмотреть JSON';
        chip.addEventListener('click', () => {
            openJsonModal(meta.requestJson, meta.responseJson);
        });
        chips.push(chip);
    }

    return chips;
}

function formatDuration(ms) {
    if (ms < 1000) {
        return `${ms} мс`;
    }
    return `${(ms / 1000).toFixed(2)} с`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function openJsonModal(requestJson, responseJson) {
    const modal = document.createElement('dialog');
    modal.className = 'json-modal';
    modal.innerHTML = `
        <div class="json-modal-content">
            <header class="json-modal-header">
                <h3>Запрос и ответ LLM</h3>
                <button class="json-modal-close" type="button">×</button>
            </header>
            <div class="json-modal-body">
                <section>
                    <h4>Запрос</h4>
                    <pre>${formatJsonForDisplay(requestJson)}</pre>
                </section>
                <section>
                    <h4>Ответ</h4>
                    <pre>${formatJsonForDisplay(responseJson)}</pre>
                </section>
            </div>
        </div>
    `;

    modal.querySelector('.json-modal-close').addEventListener('click', () => modal.close());
    modal.addEventListener('click', (event) => {
        if (event.target === modal) {
            modal.close();
        }
    });
    modal.addEventListener('close', () => modal.remove());

    document.body.appendChild(modal);
    modal.showModal();
}

function formatJsonForDisplay(value) {
    if (!value) return '—';
    try {
        const parsed = typeof value === 'string' ? JSON.parse(value) : value;
        return escapeHtml(JSON.stringify(parsed, null, 2));
    } catch {
        return escapeHtml(String(value));
    }
}

function pushHistoryEntry(entry) {
    state.history.unshift(entry);
    if (state.history.length > 10) {
        state.history.pop();
    }
    renderHistory();
}

function renderHistory() {
    if (state.history.length === 0) {
        elements.historyList.classList.add('empty');
        elements.historyList.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📜</div>
                <p>Пока ничего нет. Проведите эксперимент и история появится здесь.</p>
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
        question.innerHTML = `<strong>Вопрос:</strong> ${entry.question || 'дефолтный пример'}`;

        const temps = document.createElement('ul');
        entry.temperatures.forEach((temp) => {
            const li = document.createElement('li');
            li.textContent = `t = ${formatTemperature(temp)}`;
            temps.appendChild(li);
        });

        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = 'Открыть результаты';
        button.addEventListener('click', () => {
            renderResults(entry.payload, { addToHistory: false });
        });

        item.appendChild(time);
        item.appendChild(question);
        item.appendChild(temps);
        item.appendChild(button);

        elements.historyList.appendChild(item);
    });
}

init();

