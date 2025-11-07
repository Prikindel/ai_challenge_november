const API_RUN_URL = '/reasoning';
const API_DEFAULT_URL = '/reasoning/default';

const taskInput = document.getElementById('taskInput');
const statusLabel = document.getElementById('statusLabel');
const resultsSection = document.getElementById('resultsSection');
const comparisonSection = document.getElementById('comparisonSection');
const comparisonText = document.getElementById('comparisonText');
const errorSection = document.getElementById('errorSection');
const errorText = document.getElementById('errorText');

const directCard = document.getElementById('directCard');
const stepCard = document.getElementById('stepCard');
const promptCard = document.getElementById('promptCard');
const expertsCard = document.getElementById('expertsCard');

const runDirectButton = document.getElementById('runDirect');
const runStepButton = document.getElementById('runStep');
const runPromptButton = document.getElementById('runPrompt');
const runExpertsButton = document.getElementById('runExperts');
const runAllButton = document.getElementById('runAll');
const showComparisonButton = document.getElementById('showComparison');
const runLLMComparisonButton = document.getElementById('runLLMComparison');
const resetButton = document.getElementById('resetResults');

const stepIndicators = Array.from(document.querySelectorAll('.step-indicator'));
const allControlButtons = Array.from(document.querySelectorAll('.control-button'));

const MODES = ['direct', 'step', 'prompt', 'experts'];

const state = {
    question: '',
    resultsByMode: {},
    completedModes: new Set()
};

function setStatus(message = '') {
    if (statusLabel) {
        statusLabel.textContent = message;
    }
}

function showError(message) {
    if (!errorSection || !errorText) return;
    errorText.textContent = message;
    errorSection.classList.remove('hidden');
}

function hideError() {
    if (!errorSection || !errorText) return;
    errorText.textContent = '';
    errorSection.classList.add('hidden');
}

function disableControls(disabled) {
    allControlButtons.forEach((btn) => {
        btn.disabled = disabled;
    });
}

function updateStepIndicator(mode, status, label) {
    const indicator = stepIndicators.find((item) => item.dataset.mode === mode);
    if (!indicator) return;
    const statusEl = indicator.querySelector('.step-status');
    if (statusEl) {
        statusEl.dataset.status = status;
        statusEl.textContent = label;
    }
}

function markModeIdle(mode) {
    updateStepIndicator(mode, 'idle', 'ожидает');
}

function markModeLoading(mode) {
    updateStepIndicator(mode, 'loading', 'запрос');
}

function markModeSuccess(mode) {
    updateStepIndicator(mode, 'success', 'готово');
    state.completedModes.add(mode);
}

function markModeError(mode) {
    updateStepIndicator(mode, 'error', 'ошибка');
}

function resetIndicators() {
    MODES.forEach((mode) => {
        markModeIdle(mode);
    });
}

function resetCards() {
    const cards = [directCard, stepCard, promptCard, expertsCard];
    cards.forEach((card) => {
        if (!card) return;
        card.classList.add('hidden');
        const statusEl = card.querySelector('[data-role="status"]');
        const promptEl = card.querySelector('[data-role="prompt"]');
        const answerEl = card.querySelector('[data-role="answer"]');
        const generatedEl = card.querySelector('[data-role="generatedPrompt"]');
        const summaryEl = card.querySelector('[data-role="summary"]');
        const expertsEl = card.querySelector('[data-role="experts"]');
        const notesEl = card.querySelector('[data-role="notes"]');
        if (statusEl) statusEl.textContent = '—';
        if (promptEl) promptEl.textContent = '';
        if (answerEl) answerEl.textContent = '';
        if (generatedEl) generatedEl.textContent = '';
        if (summaryEl) summaryEl.textContent = '';
        if (notesEl) {
            notesEl.textContent = '';
            notesEl.classList.remove('hidden');
        }
        if (expertsEl) expertsEl.innerHTML = '';
    });
    resultsSection.classList.add('hidden');
    comparisonSection.classList.add('hidden');
    comparisonText.textContent = '';
}

function ensureResultsSectionVisible() {
    resultsSection.classList.remove('hidden');
}

function setCardStatus(card, text) {
    const statusEl = card?.querySelector('[data-role="status"]');
    if (statusEl) {
        statusEl.textContent = text;
    }
}

function fillDirectCard(data) {
    if (!directCard || !data) return;
    directCard.querySelector('[data-role="prompt"]').textContent = data.prompt || '—';
    directCard.querySelector('[data-role="answer"]').textContent = data.answer || '—';
    setCardStatus(directCard, 'Готово');
    directCard.classList.remove('hidden');
    ensureResultsSectionVisible();
}

function fillStepCard(data) {
    if (!stepCard || !data) return;
    stepCard.querySelector('[data-role="prompt"]').textContent = data.prompt || '—';
    stepCard.querySelector('[data-role="answer"]').textContent = data.answer || '—';
    setCardStatus(stepCard, 'Готово');
    stepCard.classList.remove('hidden');
    ensureResultsSectionVisible();
}

function fillPromptCard(data) {
    if (!promptCard || !data) return;
    promptCard.querySelector('[data-role="generatedPrompt"]').textContent = data.generatedPrompt || '—';
    promptCard.querySelector('[data-role="answer"]').textContent = data.answer || '—';
    const notesEl = promptCard.querySelector('[data-role="notes"]');
    if (notesEl) {
        notesEl.textContent = data.notes || '';
        notesEl.classList.toggle('hidden', !data.notes);
    }
    setCardStatus(promptCard, data.usedFallback ? 'Готово (fallback)' : 'Готово');
    promptCard.classList.remove('hidden');
    ensureResultsSectionVisible();
}

function fillExpertsCard(expertPanel) {
    if (!expertsCard || !expertPanel) return;
    const expertsContainer = expertsCard.querySelector('[data-role="experts"]');
    const summaryEl = expertsCard.querySelector('[data-role="summary"]');
    if (expertsContainer) {
        expertsContainer.innerHTML = '';
        expertPanel.experts.forEach((expert) => {
            const wrapper = document.createElement('div');
            wrapper.classList.add('expert-item');

            const title = document.createElement('h4');
            title.textContent = expert.name;
            wrapper.appendChild(title);

            const styleEl = document.createElement('p');
            styleEl.classList.add('expert-style');
            styleEl.textContent = expert.style;
            wrapper.appendChild(styleEl);

            const reasoningEl = document.createElement('p');
            reasoningEl.classList.add('expert-reasoning');
            reasoningEl.textContent = `Обоснование: ${expert.reasoning}`;
            wrapper.appendChild(reasoningEl);

            const answerEl = document.createElement('p');
            answerEl.classList.add('expert-reasoning');
            answerEl.textContent = `Итог: ${expert.answer}`;
            wrapper.appendChild(answerEl);

            expertsContainer.appendChild(wrapper);
        });
    }
    if (summaryEl) {
        summaryEl.textContent = expertPanel.summary || '—';
    }
    setCardStatus(expertsCard, 'Готово');
    expertsCard.classList.remove('hidden');
    ensureResultsSectionVisible();
}

function renderComparison(comparison) {
    if (!comparison) return;
    comparisonText.textContent = comparison;
    comparisonSection.classList.remove('hidden');
}

function applyModeResults(mode, data) {
    if (!data) return;
    switch (mode) {
        case 'direct':
            fillDirectCard(data.direct);
            break;
        case 'step':
            fillStepCard(data.stepByStep);
            break;
        case 'prompt':
            fillPromptCard(data.promptFromOtherAI);
            break;
        case 'experts':
            fillExpertsCard(data.expertPanel);
            break;
        case 'all':
            fillDirectCard(data.direct);
            fillStepCard(data.stepByStep);
            fillPromptCard(data.promptFromOtherAI);
            fillExpertsCard(data.expertPanel);
            renderComparison(data.comparison);
            break;
        default:
            break;
    }
}

function maybeRenderComparison() {
    const comparisonData = state.resultsByMode['comparison'];
    if (comparisonData?.comparison) {
        renderComparison(comparisonData.comparison);
        return;
    }

    const allData = state.resultsByMode['all'];
    if (allData?.comparison) {
        renderComparison(allData.comparison);
    }
}

function normalizeAnswerText(text) {
    return text ? text.replace(/\s+/g, ' ').trim().toLowerCase() : '';
}

function buildClientComparison() {
    const directData = state.resultsByMode.direct?.direct;
    const stepData = state.resultsByMode.step?.stepByStep;
    const promptData = state.resultsByMode.prompt?.promptFromOtherAI;
    const expertsData = state.resultsByMode.experts?.expertPanel;

    if (!directData || !stepData || !promptData || !expertsData) {
        return null;
    }

    const variants = [
        { key: 'direct', label: 'Direct', value: directData.answer || '' },
        { key: 'step', label: 'Step-by-step', value: stepData.answer || '' },
        { key: 'prompt', label: 'Prompt from other AI', value: promptData.answer || '' },
        { key: 'experts', label: 'Expert panel', value: expertsData.summary || '' }
    ];

    const groups = variants.reduce((acc, entry) => {
        const normalized = normalizeAnswerText(entry.value);
        const key = normalized || `__empty_${entry.key}`;
        if (!acc[key]) {
            acc[key] = { entries: [], normalized };
        }
        acc[key].entries.push(entry);
        return acc;
    }, {});

    const groupList = Object.values(groups);
    groupList.sort((a, b) => b.entries.length - a.entries.length);

    let summaryLines = [];

    if (groupList.length === 1 && groupList[0].normalized) {
        summaryLines.push('Все подходы сошлись на одном ответе.');
    } else {
        const consensusGroup = groupList.find((group) => group.normalized && group.entries.length > 1);
        if (consensusGroup) {
            const labels = consensusGroup.entries.map((entry) => entry.label).join(', ');
            summaryLines.push(`Наиболее согласованный ответ (${labels}):`);
            summaryLines.push(consensusGroup.entries[0].value || '—');
        } else {
            summaryLines.push('Ответы различаются между подходами.');
        }
    }

    summaryLines.push('');
    summaryLines.push(`Direct: ${variants[0].value || '—'}`);
    summaryLines.push(`Step-by-step: ${variants[1].value || '—'}`);
    summaryLines.push(`Prompt from other AI: ${variants[2].value || '—'}`);
    summaryLines.push(`Expert panel: ${variants[3].value || '—'}`);

    return summaryLines.join('\n');
}

async function fetchDefaultTask() {
    try {
        const response = await fetch(API_DEFAULT_URL);
        if (!response.ok) {
            throw new Error(`Не удалось получить задачу: ${response.status}`);
        }
        const data = await response.json();
        if (taskInput && typeof data.defaultTask === 'string') {
            taskInput.value = data.defaultTask;
        }
    } catch (error) {
        console.error('Ошибка загрузки задачи по умолчанию', error);
        setStatus('Не удалось загрузить задачу по умолчанию.');
    }
}

async function requestReasoning(question, mode) {
    const payload = { question };
    if (mode) {
        payload.mode = mode;
    }

    const response = await fetch(API_RUN_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });

    if (!response.ok) {
        const errorPayload = await response.json().catch(() => null);
        const message = errorPayload?.message || `Ошибка запроса: ${response.status}`;
        throw new Error(message);
    }

    return response.json();
}

function storeResult(mode, data) {
    state.resultsByMode[mode] = data;
    if (mode === 'all') {
        MODES.forEach((item) => {
            state.resultsByMode[item] = data;
        });
    }
}

function handleSuccess(mode, data) {
    if (mode === 'comparison') {
        if (data.comparison) {
            renderComparison(data.comparison);
        }
        setStatus('Сравнение получено от LLM.');
        return;
    }

    if (mode === 'all') {
        MODES.forEach((item) => markModeSuccess(item));
    } else if (MODES.includes(mode)) {
        markModeSuccess(mode);
    }
    applyModeResults(mode, data);
    maybeRenderComparison();
}

function resetStateForQuestion(question) {
    state.question = question;
    state.resultsByMode = {};
    state.completedModes.clear();
    resetIndicators();
    resetCards();
    hideError();
}

async function runMode(mode) {
    hideError();
    const question = (taskInput?.value || '').trim();
    if (!question) {
        showError('Сначала введите задачу.');
        return;
    }

    const questionChanged = question !== state.question;
    if (questionChanged) {
        resetStateForQuestion(question);
    }

    if (mode !== 'all' && state.resultsByMode['all']) {
        handleSuccess(mode, state.resultsByMode['all']);
        return;
    }

    const cached = state.resultsByMode[mode];
    if (cached && !questionChanged) {
        handleSuccess(mode, cached);
        return;
    }

    if (mode === 'all') {
        MODES.forEach(markModeLoading);
    } else if (MODES.includes(mode)) {
        markModeLoading(mode);
    }

    disableControls(true);
    setStatus('Запрашиваем ответы у модели...');

    try {
        const data = await requestReasoning(question, mode);
        storeResult(mode, data);
        handleSuccess(mode, data);
    } catch (error) {
        console.error('Ошибка выполнения режима', error);
        showError(error.message || 'Не удалось выполнить запрос.');
        if (mode === 'all') {
            MODES.forEach((item) => markModeError(item));
        } else if (MODES.includes(mode)) {
            markModeError(mode);
        }
    } finally {
        disableControls(false);
        setStatus('');
    }
}

function handleRunDirect() {
    runMode('direct');
}

function handleRunStep() {
    runMode('step');
}

function handleRunPrompt() {
    runMode('prompt');
}

function handleRunExperts() {
    runMode('experts');
}

function handleRunAll() {
    runMode('all');
}

function handleRunLLMComparison() {
    runMode('comparison');
}

function handleShowComparison() {
    const allData = state.resultsByMode['all'];
    if (allData?.comparison) {
        renderComparison(allData.comparison);
        return;
    }

    const summary = buildClientComparison();
    if (!summary) {
        showError('Сначала выполните все четыре режима, чтобы построить сравнение.');
        return;
    }

    hideError();
    comparisonText.textContent = summary;
    comparisonSection.classList.remove('hidden');
    setStatus('Сравнение сформировано на основе уже полученных результатов.');
}

function handleReset() {
    state.question = '';
    state.resultsByMode = {};
    state.completedModes.clear();
    resetIndicators();
    resetCards();
    hideError();
    setStatus('Результаты очищены.');
}

function initEventListeners() {
    runDirectButton?.addEventListener('click', handleRunDirect);
    runStepButton?.addEventListener('click', handleRunStep);
    runPromptButton?.addEventListener('click', handleRunPrompt);
    runExpertsButton?.addEventListener('click', handleRunExperts);
    runAllButton?.addEventListener('click', handleRunAll);
    runLLMComparisonButton?.addEventListener('click', handleRunLLMComparison);
    showComparisonButton?.addEventListener('click', handleShowComparison);
    resetButton?.addEventListener('click', handleReset);
}

async function init() {
    await fetchDefaultTask();
    resetIndicators();
    hideError();
    initEventListeners();
}

document.addEventListener('DOMContentLoaded', init);


