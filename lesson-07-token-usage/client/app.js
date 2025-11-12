const state = {
  templates: [],
  promptTokenLimit: null,
  defaultMaxResponseTokens: null,
  tokenEncoding: null,
  scenarios: new Map(),
};

const selectors = {
  form: document.getElementById("scenariosForm"),
  templatesLoader: document.getElementById("templatesLoader"),
  runButton: document.getElementById("runButton"),
  runLoader: document.getElementById("runLoader"),
  resultsContainer: document.getElementById("resultsContainer"),
  historyContainer: document.getElementById("historyContainer"),
  historyEmptyState: document.getElementById("historyEmptyState"),
  refreshHistoryButton: document.getElementById("refreshHistoryButton"),
  formError: document.getElementById("formError"),
  tokenLimitsInfo: document.getElementById("tokenLimitsInfo"),
};

const templates = {
  scenario: document.getElementById("scenarioTemplate"),
  result: document.getElementById("resultTemplate"),
  history: document.getElementById("historyTemplate"),
};

const numberFormatter = new Intl.NumberFormat("ru-RU");

document.addEventListener("DOMContentLoaded", () => {
  bindEvents();
  loadScenarioTemplates().then(loadHistory);
});

function bindEvents() {
  selectors.runButton.addEventListener("click", handleRunAnalysis);
  selectors.refreshHistoryButton.addEventListener("click", loadHistory);
}

async function loadScenarioTemplates() {
  selectors.form
    .querySelectorAll(".scenario")
    .forEach((node) => node.remove());
  toggleElement(selectors.templatesLoader, true);

  try {
    const response = await fetch("/api/token-usage/scenarios");
    if (!response.ok) {
      throw new Error(`Ошибка загрузки сценариев: ${response.status}`);
    }
    const payload = await response.json();

    state.templates = payload.scenarios ?? [];
    state.promptTokenLimit = payload.promptTokenLimit;
    state.defaultMaxResponseTokens = payload.defaultMaxResponseTokens;
    state.tokenEncoding = payload.tokenEncoding;

    renderScenarios(state.templates);
    updateTokenInfo();
  } catch (error) {
    showFormError(error.message || "Не удалось загрузить сценарии");
  } finally {
    toggleElement(selectors.templatesLoader, false);
  }
}

function renderScenarios(scenarios) {
  if (!Array.isArray(scenarios) || scenarios.length === 0) {
    selectors.form.innerHTML =
      '<p class="empty-state">Сценарии не найдены. Проверьте конфигурацию сервера.</p>';
    return;
  }

  const fragment = document.createDocumentFragment();
  state.scenarios.clear();

  scenarios.forEach((scenario) => {
    const node = templates.scenario.content.cloneNode(true);
    const fieldset = node.querySelector(".scenario");
    const textarea = node.querySelector(".scenario__textarea");
    const title = node.querySelector(".scenario__title");
    const description = node.querySelector(".scenario__description");
    const stats = node.querySelector(".scenario__prompt-tokens strong");

    fieldset.dataset.scenarioId = scenario.scenarioId;
    title.textContent = scenario.scenarioName;
    description.textContent = scenario.description || "Без описания";
    textarea.value = scenario.defaultPrompt || "";
    stats.textContent = formatTokenLabel(estimateTokens(textarea.value));

    textarea.addEventListener("input", () => {
      stats.textContent = formatTokenLabel(estimateTokens(textarea.value));
    });

    state.scenarios.set(scenario.scenarioId, {
      element: fieldset,
      textarea,
    });

    fragment.appendChild(node);
  });

  selectors.form.appendChild(fragment);
}

function updateTokenInfo() {
  const { promptTokenLimit, defaultMaxResponseTokens, tokenEncoding } = state;
  selectors.tokenLimitsInfo.textContent = [
    `Кодировка: ${tokenEncoding}`,
    `Лимит промпта: ${numberFormatter.format(promptTokenLimit)} токенов`,
    `Дефолтный лимит ответа: ${numberFormatter.format(defaultMaxResponseTokens)} токенов`,
  ].join(" • ");
}

async function handleRunAnalysis() {
  clearFormError();
  toggleRunState(true);

  try {
    const overrides = Array.from(state.scenarios.entries()).map(
      ([scenarioId, { textarea }]) => ({
        scenarioId,
        promptText: textarea.value,
      })
    );

    const response = await fetch("/api/token-usage/analyze", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ scenarios: overrides }),
    });

    if (!response.ok) {
      const errorPayload = await safeJson(response);
      const message =
        errorPayload?.error ||
        `Не удалось выполнить анализ (код ${response.status})`;
      throw new Error(message);
    }

    const payload = await response.json();
    renderCurrentRun(payload.currentRun);
    renderHistory(payload.history);
  } catch (error) {
    showFormError(error.message || "Неизвестная ошибка");
  } finally {
    toggleRunState(false);
  }
}

function renderCurrentRun(run) {
  selectors.resultsContainer.innerHTML = "";
  if (!run || !Array.isArray(run.results)) {
    selectors.resultsContainer.innerHTML =
      '<p class="empty-state">Результаты не найдены. Попробуйте повторить запрос.</p>';
    return;
  }

  const fragment = document.createDocumentFragment();

  run.results.forEach((result) => {
    const node = templates.result.content.cloneNode(true);
    const root = node.querySelector(".result");
    const icon = node.querySelector(".result__icon");
    const title = node.querySelector("h3");
    const status = node.querySelector(".result__status");
    const prompt = node.querySelector(".result__prompt");
    const completion = node.querySelector(".result__completion");
    const total = node.querySelector(".result__total");
    const duration = node.querySelector(".result__duration");
    const message = node.querySelector(".result__message");

    title.textContent = result.scenarioName;
    status.textContent = formatStatusLabel(result.status);
    prompt.textContent = formatTokens(result.promptTokens);
    completion.textContent = formatTokens(result.responseTokens);
    total.textContent = formatTokens(result.totalTokens);
    duration.textContent = formatDuration(result.durationMs);
    message.textContent =
      result.responseText || result.errorMessage || "Ответ отсутствует.";

    const statusClass = getStatusClass(result.status);
    root.classList.add(statusClass);
    icon.style.background = getStatusColor(result.status);

    fragment.appendChild(node);
  });

  selectors.resultsContainer.appendChild(fragment);
}

async function loadHistory() {
  try {
    const response = await fetch("/api/token-usage/history");
    if (!response.ok) {
      throw new Error(`Ошибка загрузки истории (код ${response.status})`);
    }
    const payload = await response.json();
    renderHistory(payload.history);
  } catch (error) {
    console.warn("[history] ", error);
  }
}

function renderHistory(history) {
  selectors.historyContainer.innerHTML = "";
  const items = Array.isArray(history) ? history : [];
  toggleElement(selectors.historyEmptyState, items.length === 0);

  if (items.length === 0) {
    return;
  }

  const fragment = document.createDocumentFragment();

  items.forEach((run) => {
    const node = templates.history.content.cloneNode(true);
    const headerTitle = node.querySelector("h3");
    const meta = node.querySelector(".history-item__meta");
    const badge = node.querySelector(".history-item__badge");
    const list = node.querySelector(".history-item__list");

    const startDate = formatDate(run.startedAt);
    const endDate = formatDate(run.finishedAt);
    const successCount = run.results.filter(
      (item) => item.status === "success"
    ).length;

    headerTitle.textContent = `Запуск ${shortId(run.runId)}`;
    meta.textContent = `${startDate} → ${endDate}`;
    badge.textContent = `Успешно: ${successCount}/${run.results.length}`;

    run.results.forEach((result) => {
      const li = document.createElement("li");
      const statusColor = getStatusColor(result.status);

      li.innerHTML = `
        <strong style="color:${statusColor}">${formatStatusLabel(
          result.status
        )}</strong>
        <span>${result.scenarioName}</span>
        <span>Промпт: ${formatTokens(result.promptTokens)}, ответ: ${formatTokens(
        result.responseTokens
      )}</span>
      `;

      list.appendChild(li);
    });

    fragment.appendChild(node);
  });

  selectors.historyContainer.appendChild(fragment);
}

function formatTokens(value) {
  if (value === null || value === undefined) {
    return "—";
  }
  return `${numberFormatter.format(value)} ток.`;
}

function formatDuration(ms) {
  if (typeof ms !== "number" || Number.isNaN(ms)) {
    return "—";
  }
  if (ms < 1000) {
    return `${ms} мс`;
  }
  return `${(ms / 1000).toFixed(1)} с`;
}

function estimateTokens(text) {
  if (!text) return 0;
  const charsPerToken = 4;
  return Math.max(1, Math.ceil(text.length / charsPerToken));
}

function formatTokenLabel(tokens) {
  return `${numberFormatter.format(tokens)} токенов`;
}

function formatStatusLabel(status) {
  switch ((status || "").toLowerCase()) {
    case "success":
      return "успех";
    case "truncated":
      return "усечено";
    case "error":
      return "ошибка";
    default:
      return status || "неизвестно";
  }
}

function getStatusClass(status) {
  switch ((status || "").toLowerCase()) {
    case "success":
      return "result--success";
    case "truncated":
      return "result--truncated";
    case "error":
      return "result--error";
    default:
      return "result";
  }
}

function getStatusColor(status) {
  switch ((status || "").toLowerCase()) {
    case "success":
      return "var(--success)";
    case "truncated":
      return "var(--warning)";
    case "error":
      return "var(--error)";
    default:
      return "rgba(255,255,255,0.4)";
  }
}

function shortId(value = "") {
  return value.slice(0, 8).toUpperCase();
}

function formatDate(value) {
  if (!value) return "неизвестно";
  try {
    const date = new Date(value);
    return new Intl.DateTimeFormat("ru-RU", {
      dateStyle: "short",
      timeStyle: "medium",
    }).format(date);
  } catch (error) {
    return value;
  }
}

function toggleRunState(isRunning) {
  selectors.runButton.disabled = isRunning;
  toggleElement(selectors.runLoader, isRunning);
}

function toggleElement(element, visible) {
  if (!element) return;
  element.classList.toggle("hidden", !visible);
}

function showFormError(message) {
  selectors.formError.textContent = message;
  toggleElement(selectors.formError, true);
}

function clearFormError() {
  selectors.formError.textContent = "";
  toggleElement(selectors.formError, false);
}

async function safeJson(response) {
  try {
    return await response.json();
  } catch (error) {
    return null;
  }
}

