const taskText = document.getElementById('taskText');
const runButton = document.getElementById('runExperimentButton');
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

const API_URL = '/reasoning';

function toggleLoading(isLoading) {
    if (!runButton || !statusLabel) return;
    runButton.disabled = isLoading;
    statusLabel.textContent = isLoading ? 'Запрашиваем ответы у модели...' : '';
}

function showError(message) {
    if (!errorSection || !errorText) return;
    errorText.textContent = message;
    errorSection.classList.remove('hidden');
}

function hideError() {
    if (!errorSection) return;
    errorSection.classList.add('hidden');
    errorText.textContent = '';
}

function fillCard(card, data) {
    if (!card) return;
    const promptEl = card.querySelector('[data-role="prompt"]');
    const answerEl = card.querySelector('[data-role="answer"]');
    if (promptEl) {
        promptEl.textContent = data.prompt || '—';
    }
    if (answerEl) {
        answerEl.textContent = data.answer || '—';
    }
}

function fillPromptCard(card, data) {
    if (!card) return;
    const generatedEl = card.querySelector('[data-role="generatedPrompt"]');
    const answerEl = card.querySelector('[data-role="answer"]');
    if (generatedEl) {
        generatedEl.textContent = data.generatedPrompt || '—';
    }
    if (answerEl) {
        answerEl.textContent = data.answer || '—';
    }
}

function fillExpertsCard(card, expertPanel) {
    if (!card) return;
    const expertsContainer = card.querySelector('[data-role="experts"]');
    const summaryEl = card.querySelector('[data-role="summary"]');

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
            answerEl.textContent = `Ответ: ${expert.answer}`;
            wrapper.appendChild(answerEl);

            expertsContainer.appendChild(wrapper);
        });
    }

    if (summaryEl) {
        summaryEl.textContent = expertPanel.summary || '—';
    }
}

function renderResult(data) {
    if (!data) {
        showError('Получен пустой ответ.');
        return;
    }

    hideError();

    taskText.textContent = data.task;
    fillCard(directCard, data.direct);
    fillCard(stepCard, data.stepByStep);
    fillPromptCard(promptCard, data.promptFromOtherAI);
    fillExpertsCard(expertsCard, data.expertPanel);

    comparisonText.textContent = data.comparison || '—';

    resultsSection.classList.remove('hidden');
    comparisonSection.classList.remove('hidden');
}

async function fetchReasoning() {
    toggleLoading(true);
    hideError();
    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({})
        });

        if (!response.ok) {
            const errorPayload = await response.json().catch(() => null);
            const message = errorPayload?.message || `Ошибка запроса: ${response.status}`;
            throw new Error(message);
        }

        const data = await response.json();
        renderResult(data);
    } catch (error) {
        console.error('Reasoning request failed', error);
        showError(error.message || 'Не удалось выполнить эксперимент.');
    } finally {
        toggleLoading(false);
    }
}

function init() {
    if (!runButton) return;
    runButton.addEventListener('click', () => {
        fetchReasoning();
    });

    fetchReasoning();
}

document.addEventListener('DOMContentLoaded', init);


