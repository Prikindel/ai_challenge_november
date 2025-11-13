(() => {
    const API_BASE = '/api/dialog-compression';

    const elements = {
        conversationList: document.getElementById('conversationList'),
        messageForm: document.getElementById('messageForm'),
        messageInput: document.getElementById('messageInput'),
        summaryIntervalInput: document.getElementById('summaryIntervalInput'),
        maxSummariesInput: document.getElementById('maxSummariesInput'),
        sendButton: document.getElementById('sendButton'),
        modeSelect: document.getElementById('modeSelect'),
        modeHint: document.getElementById('modeHint'),
        dialogStatus: document.getElementById('dialogStatus'),
        metricsContent: document.getElementById('metricsContent'),
        historyList: document.getElementById('historyList'),
        tokensSavedCounter: document.getElementById('tokensSavedCounter'),
        openSummaryModal: document.getElementById('openSummaryModal'),
        summaryModal: document.getElementById('summaryModal'),
        summaryList: document.getElementById('summaryList'),
        resetDialogButton: document.getElementById('resetDialogButton'),
        scenarioSelect: document.getElementById('scenarioSelect'),
        runComparisonButton: document.getElementById('runComparisonButton'),
        comparisonResults: document.getElementById('comparisonResults'),
        toastContainer: document.getElementById('toastContainer')
    };

    const state = {
        summaryInterval: Number(elements.summaryIntervalInput.value) || 10,
        maxSummaries: Number(elements.maxSummariesInput.value) || 3,
        mode: elements.modeSelect.value,
        messages: [],
        summaries: [],
        lastResponse: null,
        contextUsed: null,
        history: [],
        totalTokensSaved: 0,
        scenarios: [],
        selectedScenario: null,
        comparisonReport: null,
    loading: false,
    isScenarioRunning: false
    };

    const request = async (path, options = {}) => {
        const response = await fetch(`${API_BASE}${path}`, {
            headers: {
                'Content-Type': 'application/json',
                ...(options.headers || {})
            },
            ...options
        });

        const contentType = response.headers.get('content-type') || '';
        let payload = null;
        try {
            if (contentType.includes('application/json')) {
                payload = await response.json();
            } else {
                const text = await response.text();
                payload = text ? { message: text } : null;
            }
        } catch (error) {
            payload = null;
        }

        if (!response.ok) {
            const message = payload?.error || payload?.message || `Ошибка ${response.status}`;
            throw new Error(message);
        }

        return payload;
    };

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

    const showToast = (message) => {
        if (!message) return;
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.textContent = message;
        elements.toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.classList.add('hide');
            toast.addEventListener('transitionend', () => toast.remove(), { once: true });
            toast.style.opacity = '0';
        }, 4200);
        if (elements.toastContainer.children.length > 4) {
            elements.toastContainer.firstChild.remove();
        }
    };

    const setLoading = (isLoading) => {
        state.loading = isLoading;
    const shouldDisableSend = isLoading || state.mode === 'comparison' || state.isScenarioRunning;
        elements.sendButton.disabled = shouldDisableSend;
        elements.sendButton.classList.toggle('loading', isLoading);
        elements.messageInput.disabled = shouldDisableSend;
    };

const setScenarioRunning = (running) => {
    state.isScenarioRunning = running;
    elements.runComparisonButton.disabled = running;
    elements.runComparisonButton.textContent = running ? 'Выполняю...' : 'Запустить контрольный прогон';
    elements.summaryIntervalInput.disabled = running;
    elements.maxSummariesInput.disabled = running;
    elements.modeSelect.disabled = running;
    elements.resetDialogButton.disabled = running;
    setLoading(state.loading);
};

    const formatDate = (isoString) => {
        if (!isoString) return '';
        try {
            return new Date(isoString).toLocaleTimeString('ru-RU', {
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (error) {
            return isoString;
        }
    };

    const formatMarkdown = (text) => {
        if (!text) return '';
        
        // Экранируем HTML для безопасности
        const escapeHtml = (str) => {
            const div = document.createElement('div');
            div.textContent = str;
            return div.innerHTML;
        };
        
        let html = escapeHtml(text);
        
        // Разбиваем на строки для обработки
        const lines = html.split('\n');
        const result = [];
        let inList = false;
        let listItems = [];
        
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();
            
            if (!line) {
                if (inList && listItems.length > 0) {
                    result.push(`<ul>${listItems.join('')}</ul>`);
                    listItems = [];
                    inList = false;
                }
                if (i < lines.length - 1) {
                    result.push('<br>');
                }
                continue;
            }
            
            // Заголовки
            if (line.startsWith('### ')) {
                if (inList) {
                    result.push(`<ul>${listItems.join('')}</ul>`);
                    listItems = [];
                    inList = false;
                }
                result.push(`<h3>${line.substring(4)}</h3>`);
            } else if (line.startsWith('## ')) {
                if (inList) {
                    result.push(`<ul>${listItems.join('')}</ul>`);
                    listItems = [];
                    inList = false;
                }
                result.push(`<h2>${line.substring(3)}</h2>`);
            } else if (line.startsWith('# ')) {
                if (inList) {
                    result.push(`<ul>${listItems.join('')}</ul>`);
                    listItems = [];
                    inList = false;
                }
                result.push(`<h1>${line.substring(2)}</h1>`);
            }
            // Маркированные списки
            else if (/^[-*] (.+)$/.test(line)) {
                const match = line.match(/^[-*] (.+)$/);
                if (match) {
                    inList = true;
                    listItems.push(`<li>${match[1]}</li>`);
                }
            }
            // Нумерованные списки
            else if (/^\d+\. (.+)$/.test(line)) {
                const match = line.match(/^\d+\. (.+)$/);
                if (match) {
                    if (inList && listItems.length > 0) {
                        result.push(`<ul>${listItems.join('')}</ul>`);
                        listItems = [];
                    }
                    inList = true;
                    listItems.push(`<li>${match[1]}</li>`);
                }
            }
            // Обычный текст
            else {
                if (inList && listItems.length > 0) {
                    result.push(`<ul>${listItems.join('')}</ul>`);
                    listItems = [];
                    inList = false;
                }
                result.push(`<p>${line}</p>`);
            }
        }
        
        // Закрываем последний список
        if (inList && listItems.length > 0) {
            result.push(`<ul>${listItems.join('')}</ul>`);
        }
        
        html = result.join('');
        
        // Жирный текст **text**
        html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        
        // Курсив *text* (но не внутри **)
        html = html.replace(/(?<!\*)\*([^*\n]+?)\*(?!\*)/g, '<em>$1</em>');
        
        return html;
    };

    const refreshState = async () => {
        try {
            const previousSummaryIds = new Set(state.summaries.map((summary) => summary.id));
            const data = await request('/state', { method: 'GET' });
            state.messages = (data?.messages || []).map((message) => ({
                ...message,
                createdAt: message.createdAt
            }));
            state.summaries = (data?.summaries || []).map((summary) => ({
                ...summary,
                createdAt: summary.createdAt
            }));

            const newSummaryIds = state.summaries
                .map((summary) => summary.id)
                .filter((id) => !previousSummaryIds.has(id));
            if (newSummaryIds.length > 0) {
                const latestSummaryId = newSummaryIds.at(-1);
                showToast(
                    latestSummaryId
                        ? `Сформирован summary #${latestSummaryId.slice(0, 6)}`
                        : 'Сформирован новый summary'
                );
            }

            renderConversation();
            renderSummaries();
            updateDialogStatus();
        } catch (error) {
            showToast(error.message);
        }
    };

    const updateDialogStatus = () => {
        const totalMessages = state.messages.length;
        const summarizedMessages = state.messages.filter((message) => message.summarized).length;
        const activeMessages = totalMessages - summarizedMessages;
        const summaries = state.summaries.length;
        if (state.mode === 'comparison') {
            elements.dialogStatus.textContent = 'Режим: контрольный прогон';
        } else {
            elements.dialogStatus.textContent = `Активных: ${activeMessages} • Summary: ${summaries}`;
        }
    };

    const renderConversation = () => {
        const container = elements.conversationList;
        container.innerHTML = '';

        if (!state.messages.length) {
            const placeholder = document.createElement('p');
            placeholder.className = 'empty';
            placeholder.textContent = 'История пуста. Напишите первое сообщение или запустите сценарий.';
            container.appendChild(placeholder);
            return;
        }

        const contextIds = new Set((state.contextUsed?.rawMessages || []).map((message) => message.id));
        const summaryMarkers = new Map();
        state.summaries.forEach((summary) => {
            const sourceIds = summary.sourceMessageIds || [];
            const anchorId = summary.anchorMessageId || sourceIds.at(-1);
            if (anchorId) {
                summaryMarkers.set(anchorId, summary);
            }
        });

        state.messages
            .slice()
            .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
            .forEach((message) => {
                const item = document.createElement('article');
                const messageClasses = ['message', message.role];
                if (message.summarized) {
                    messageClasses.push('summarized');
                }
                item.className = messageClasses.join(' ');

                const header = document.createElement('div');
                header.className = 'message-header';

                const label = document.createElement('span');
                label.className = 'message-label';
                const roleLabel =
                    message.role === 'user'
                        ? 'Пользователь'
                        : message.role === 'assistant'
                        ? 'Агент'
                        : 'Система';
                label.textContent = roleLabel;
                header.appendChild(label);

                if (contextIds.has(message.id)) {
                    const badge = document.createElement('span');
                    badge.className = 'badge';
                    badge.textContent = 'В контексте';
                    header.appendChild(badge);
                }

                if (message.summarized) {
                    const badge = document.createElement('span');
                    badge.className = 'badge muted';
                    badge.textContent = 'В summary';
                    header.appendChild(badge);
                }

                if (message.pending) {
                    const badge = document.createElement('span');
                    badge.className = 'badge';
                    badge.textContent = 'Отправляется...';
                    badge.style.opacity = '0.7';
                    header.appendChild(badge);
                }

                const time = document.createElement('span');
                time.className = 'badge';
                time.textContent = formatDate(message.createdAt);
                header.appendChild(time);

                const content = document.createElement('div');
                content.className = 'message-content';
                if (message.role === 'assistant') {
                    content.innerHTML = formatMarkdown(message.content);
                } else {
                    content.textContent = message.content;
                }

                item.appendChild(header);
                item.appendChild(content);
                container.appendChild(item);

                const summary = summaryMarkers.get(message.id);
                if (summary) {
                    const marker = document.createElement('article');
                    marker.className = 'summary-marker';

                    const markerHeader = document.createElement('div');
                    markerHeader.className = 'summary-marker__header';

                    const markerTitle = document.createElement('strong');
                    markerTitle.textContent = `Сформирован summary #${summary.id.slice(0, 6)}`;
                    markerHeader.appendChild(markerTitle);

                    const markerTime = document.createElement('span');
                    markerTime.className = 'badge';
                    markerTime.textContent = formatDate(summary.createdAt);
                    markerHeader.appendChild(markerTime);

                    marker.appendChild(markerHeader);

                    const info = document.createElement('p');
                    info.className = 'summary-marker__info';
                    const covered = summary.sourceMessageIds?.length ?? 0;
                    const infoParts = [`Свёрнуто сообщений: ${covered}`];
                    if (typeof summary.tokensSaved === 'number') {
                        infoParts.push(`Экономия: ${summary.tokensSaved} ток.`);
                    } else if (
                        typeof summary.rawTokens === 'number' &&
                        typeof summary.summaryTokens === 'number'
                    ) {
                        infoParts.push(`raw ${summary.rawTokens} → summary ${summary.summaryTokens}`);
                    }
                    info.textContent = infoParts.join(' • ');
                    marker.appendChild(info);

                    container.appendChild(marker);
                }
            });

        requestAnimationFrame(() => {
            container.scrollTop = container.scrollHeight;
        });
    };

    const renderSummaries = () => {
        elements.summaryList.innerHTML = '';
        if (!state.summaries.length) {
            const empty = document.createElement('p');
            empty.className = 'empty';
            empty.textContent = 'Summary пока нет — диалог ещё не достиг интервала сжатия.';
            elements.summaryList.appendChild(empty);
            return;
        }

        state.summaries
            .slice()
            .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
            .forEach((summary) => {
                const block = document.createElement('article');
                block.className = 'summary-item';

                const title = document.createElement('h4');
                title.textContent = `Summary #${summary.id.slice(0, 6)} • ${formatDate(summary.createdAt)}`;
                block.appendChild(title);

                const metaParts = [];
                if (typeof summary.rawTokens === 'number') {
                    metaParts.push(`raw: ${summary.rawTokens}`);
                }
                if (typeof summary.summaryTokens === 'number') {
                    metaParts.push(`summary: ${summary.summaryTokens}`);
                }
                if (typeof summary.tokensSaved === 'number') {
                    metaParts.push(`экономия: ${summary.tokensSaved}`);
                }
                if (metaParts.length) {
                    const meta = document.createElement('p');
                    meta.className = 'summary-meta';
                    meta.textContent = `Токены (${metaParts.join(' • ')})`;
                    block.appendChild(meta);
                }

                const summaryText = document.createElement('p');
                summaryText.textContent = summary.summary;
                block.appendChild(summaryText);

                if (summary.facts?.length) {
                    const factsTitle = document.createElement('strong');
                    factsTitle.textContent = 'Факты:';
                    block.appendChild(factsTitle);

                    const list = document.createElement('ul');
                    summary.facts.forEach((fact) => {
                        const li = document.createElement('li');
                        li.textContent = fact;
                        list.appendChild(li);
                    });
                    block.appendChild(list);
                }

                const questions = summary.openQuestions?.filter((item) => item.toLowerCase() !== 'none') || [];
                if (questions.length) {
                    const questionsTitle = document.createElement('strong');
                    questionsTitle.textContent = 'Открытые вопросы:';
                    block.appendChild(questionsTitle);

                    const list = document.createElement('ul');
                    questions.forEach((question) => {
                        const li = document.createElement('li');
                        li.textContent = question;
                        list.appendChild(li);
                    });
                    block.appendChild(list);
                }

                elements.summaryList.appendChild(block);
            });
    };

    const renderMetrics = () => {
        const container = elements.metricsContent;
        container.innerHTML = '';
        const response = state.lastResponse;
        if (!response) {
            const empty = document.createElement('p');
            empty.className = 'empty';
            empty.textContent = 'Отправьте сообщение, чтобы увидеть расход токенов и экономию.';
            container.appendChild(empty);
            return;
        }

        const addRow = (label, value) => {
            const row = document.createElement('div');
            row.className = 'metric-row';
            const labelElement = document.createElement('span');
            labelElement.textContent = label;
            const valueElement = document.createElement('strong');
            valueElement.textContent = value ?? '—';
            row.append(labelElement, valueElement);
            container.appendChild(row);
        };

        addRow('Prompt tokens', response.tokenUsage.promptTokens ?? 'нет данных');
        addRow('Completion tokens', response.tokenUsage.completionTokens ?? 'нет данных');
        addRow('Total tokens', response.tokenUsage.totalTokens ?? 'нет данных');
        addRow('Гипотетический промпт (без summary)', response.tokenUsage.hypotheticalPromptTokens ?? 'нет данных');
        addRow('Экономия на запросе', response.tokenUsage.tokensSavedByCompression ?? 'нет данных');
        addRow('Summary в запросе', response.contextUsed.summaryIds.length);
        addRow('Raw сообщений в запросе', response.contextUsed.rawMessages.length);
    };

    const recomputeTotals = () => {
        state.totalTokensSaved = state.history.reduce((acc, item) => acc + (item.tokensSaved || 0), 0);
        elements.tokensSavedCounter.textContent = `Экономия: ${state.totalTokensSaved} токенов`;
    };

    const renderHistory = () => {
        const container = elements.historyList;
        container.innerHTML = '';
        if (!state.history.length) {
            const empty = document.createElement('p');
            empty.className = 'empty';
            empty.textContent = 'Пока нет запусков — начните диалог.';
            container.appendChild(empty);
            return;
        }

        state.history.forEach((entry) => {
            const block = document.createElement('div');
            block.className = 'history-entry';

            const title = document.createElement('strong');
            title.textContent = `${entry.timestamp} • ${entry.promptTokens ?? '—'} → ${entry.totalTokens ?? '—'} токенов`;
            block.appendChild(title);

            if (entry.source) {
                const sourceBadge = document.createElement('span');
                sourceBadge.className = 'chip-inline';
                sourceBadge.textContent = entry.source;
                block.appendChild(sourceBadge);
            }

            const contextRow = document.createElement('span');
            contextRow.className = 'chip-inline';
            contextRow.textContent = `context: ${entry.contextRawCount} raw / summary: ${entry.summaryCount}`;
            block.appendChild(contextRow);

            if (entry.tokensSaved != null) {
                const saved = document.createElement('span');
                saved.className = 'chip-inline';
                saved.textContent = `экономия: ${entry.tokensSaved}`;
                block.appendChild(saved);
            }

            const preview = document.createElement('p');
            preview.className = 'history-preview';
            preview.textContent = entry.preview;
            block.appendChild(preview);

            container.appendChild(block);
        });
    };

    const renderComparison = () => {
        const container = elements.comparisonResults;
        container.innerHTML = '';
        if (!state.comparisonReport) {
            container.classList.add('empty');
            const empty = document.createElement('p');
            empty.className = 'empty';
            empty.textContent = 'Запустите контрольный прогон, чтобы сравнить режимы.';
            container.appendChild(empty);
            return;
        }
        container.classList.remove('empty');

        const header = document.createElement('p');
        header.textContent = state.comparisonReport.analysisText;
        container.appendChild(header);

        const sections = [
            {
                title: 'Без сжатия',
                metrics: state.comparisonReport.withoutCompressionMetrics
            },
            {
                title: 'Со сжатием',
                metrics: state.comparisonReport.withCompressionMetrics
            }
        ];

        sections.forEach(({ title, metrics }) => {
            const block = document.createElement('div');
            block.className = 'result-section';

            const heading = document.createElement('h4');
            heading.textContent = title;
            block.appendChild(heading);

            const list = document.createElement('ul');
            list.style.listStyle = 'none';
            list.style.padding = '0';
            list.style.margin = '0';

            const items = [
                ['Prompt tokens', metrics.totalPromptTokens ?? '—'],
                ['Completion tokens', metrics.totalCompletionTokens ?? '—'],
                ['Всего токенов', metrics.totalTokens ?? '—'],
                ['Сообщений', metrics.messagesProcessed],
                ['Summary', metrics.summariesGenerated],
                ['Экономия', metrics.tokensSaved ?? '—'],
                ['Длительность, мс', metrics.durationMs],
                ['Комментарий', metrics.qualityNotes ?? '—']
            ];

            items.forEach(([label, value]) => {
                const item = document.createElement('li');
                item.innerHTML = `<strong>${label}:</strong> <span>${value}</span>`;
                list.appendChild(item);
            });

            block.appendChild(list);
            container.appendChild(block);
        });
    };

    const addPendingUserMessage = (content) => {
        const pendingMessage = {
            id: `pending-${Date.now()}-${Math.random()}`,
            role: 'user',
            content: content,
            createdAt: new Date().toISOString(),
            summarized: false,
            pending: true
        };
        state.messages.push(pendingMessage);
        renderConversation();
        updateDialogStatus();
    };

    const sendChatMessage = async ({
        message,
        summaryInterval,
        maxSummariesInContext,
        historyLabel,
        clearInput = false,
        focusInput = false,
        showPendingMessage = false
    }) => {
        if (showPendingMessage) {
            addPendingUserMessage(message);
            await delay(300);
        }

        const payload = { message };
        if (typeof summaryInterval === 'number') {
            payload.summaryInterval = summaryInterval;
        }
        if (typeof maxSummariesInContext === 'number') {
            payload.maxSummariesInContext = maxSummariesInContext;
        }

        const response = await request('/message', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        state.lastResponse = response;
        state.contextUsed = response.contextUsed;

        await refreshState();
        renderMetrics();
        renderSummaries();

        if (historyLabel) {
            addHistoryEntry({ response, message, source: historyLabel });
            recomputeTotals();
            renderHistory();
        }

        if (clearInput) {
            elements.messageInput.value = '';
        }
        if (focusInput) {
            elements.messageInput.focus();
        }

        return response;
    };

    const handleMessageSubmit = async (event) => {
        event.preventDefault();
        if (state.mode === 'comparison') {
            showToast('Режим сравнения: используйте кнопку "Запустить контрольный прогон".');
            return;
        }
        const message = elements.messageInput.value.trim();
        if (!message) {
            showToast('Введите сообщение.');
            return;
        }

        state.summaryInterval = Math.max(1, Number(elements.summaryIntervalInput.value) || state.summaryInterval);
        state.maxSummaries = Math.max(0, Number(elements.maxSummariesInput.value) || state.maxSummaries);

        setLoading(true);
        elements.modeHint.textContent = 'Отправка сообщения...';
        try {
            await sendChatMessage({
                message,
                summaryInterval: state.summaryInterval,
                maxSummariesInContext: state.maxSummaries,
                historyLabel: 'Ручной ввод',
                clearInput: true,
                focusInput: true
            });
            elements.summaryIntervalInput.value = state.summaryInterval;
            elements.modeHint.textContent = 'Ответ получен. История обновлена.';
        } catch (error) {
            showToast(error.message);
        } finally {
            setLoading(false);
            elements.modeHint.textContent =
                'В обычном режиме каждое сообщение отправляется в агент и фиксируются метрики сжатия.';
        }
    };

    const addHistoryEntry = ({ response, message, source }) => {
        const entry = {
            timestamp: new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
            promptTokens: response.tokenUsage.promptTokens,
            completionTokens: response.tokenUsage.completionTokens,
            totalTokens: response.tokenUsage.totalTokens,
            tokensSaved: response.tokenUsage.tokensSavedByCompression,
            summaryCount: response.summaries.length,
            contextRawCount: response.contextUsed.rawMessages.length,
            source,
            preview: `${source ? `[${source}] ` : ''}${message.slice(0, 120)}${message.length > 120 ? '…' : ''}`
        };
        state.history.unshift(entry);
        if (state.history.length > 25) {
            state.history.pop();
        }
    };

    const handleModeChange = () => {
        if (state.isScenarioRunning) {
            showToast('Дождитесь завершения контрольного прогона.');
            elements.modeSelect.value = state.mode;
            return;
        }
        state.mode = elements.modeSelect.value;
        const hint =
            state.mode === 'comparison'
                ? 'В режиме сравнения сообщениями управляет сценарий. Выберите сценарий и запустите контрольный прогон.'
                : 'В обычном режиме каждое сообщение отправляется в агент и фиксируются метрики сжатия.';
        elements.modeHint.textContent = hint;
        updateDialogStatus();
        setLoading(false);
    };

    const openModal = () => {
        if (!elements.summaryModal) return;
        elements.summaryModal.classList.add('active');
        renderSummaries();
    };

    const closeModal = () => {
        if (!elements.summaryModal) return;
        elements.summaryModal.classList.remove('active');
    };

    const handleResetDialog = async () => {
        if (state.isScenarioRunning) {
            showToast('Нельзя сбрасывать историю во время контрольного прогона.');
            return;
        }
        setLoading(true);
        try {
            await request('/reset', { method: 'POST', body: JSON.stringify({}) });
            state.messages = [];
            state.summaries = [];
            state.contextUsed = null;
            state.lastResponse = null;
            state.history = [];
            state.totalTokensSaved = 0;
            renderConversation();
            renderSummaries();
            renderMetrics();
            renderHistory();
            recomputeTotals();
            updateDialogStatus();
            elements.comparisonResults.innerHTML = '';
            renderComparison();
        } catch (error) {
            showToast(error.message);
        } finally {
            setLoading(false);
        }
    };

    const loadScenarios = async () => {
        try {
            const data = await request('/scenarios', { method: 'GET' });
            state.scenarios = data?.scenarios || [];
            elements.scenarioSelect.innerHTML = '';
            if (!state.scenarios.length) {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = 'Сценарии не найдены';
                elements.scenarioSelect.appendChild(option);
                elements.scenarioSelect.disabled = true;
                elements.runComparisonButton.disabled = true;
                return;
            }

            state.scenarios.forEach((scenario, index) => {
                const option = document.createElement('option');
                option.value = scenario.id;
                option.textContent = `${scenario.id} • ${scenario.description}`;
                elements.scenarioSelect.appendChild(option);
                if (index === 0) {
                    state.selectedScenario = scenario.id;
                }
            });

            elements.scenarioSelect.disabled = false;
            elements.runComparisonButton.disabled = false;
            elements.scenarioSelect.value = state.selectedScenario;
        } catch (error) {
            showToast(error.message);
            elements.scenarioSelect.disabled = true;
            elements.runComparisonButton.disabled = true;
        }
    };

    const loadScenarioDetails = async (scenarioId) => {
        try {
            const data = await request(`/scenario/${scenarioId}`, { method: 'GET' });
            return data;
        } catch (error) {
            throw new Error(`Не удалось загрузить сценарий: ${error.message}`);
        }
    };

    const handleScenarioChange = () => {
        state.selectedScenario = elements.scenarioSelect.value;
    };

    const runScenarioOnce = async ({ scenarioId, label, summaryInterval, maxSummariesInContext }) => {
        const scenario = await loadScenarioDetails(scenarioId);
        if (!scenario || !scenario.messages || scenario.messages.length === 0) {
            throw new Error('Сценарий пуст или не найден.');
        }

        const userMessages = scenario.messages.filter(
            (msg) => (msg.role || '').toLowerCase() === 'user'
        );
        if (userMessages.length === 0) {
            throw new Error('В сценарии нет пользовательских сообщений для запуска.');
        }

        const startedAt = performance.now();
        await request('/reset', { method: 'POST', body: JSON.stringify({}) });
        await refreshState();

        const totals = {
            promptTokens: 0,
            completionTokens: 0,
            totalTokens: 0,
            messagesProcessed: 0
        };

        for (const message of scenario.messages) {
            if (!state.isScenarioRunning) {
                throw new Error('cancelled');
            }
            
            const role = (message.role || '').toLowerCase();
            if (role !== 'user') {
                continue;
            }

            const response = await sendChatMessage({
                message: message.content,
                summaryInterval,
                maxSummariesInContext,
                historyLabel: label,
                showPendingMessage: true
            });

            totals.promptTokens += response.tokenUsage.promptTokens ?? 0;
            totals.completionTokens += response.tokenUsage.completionTokens ?? 0;
            totals.totalTokens += response.tokenUsage.totalTokens ?? 0;
            totals.messagesProcessed += 1;

            await delay(600);
        }

        await refreshState();

        totals.durationMs = performance.now() - startedAt;
        totals.summariesGenerated = state.summaries.length;
        totals.tokensSaved = state.summaries.reduce(
            (acc, summary) => acc + (summary.tokensSaved || 0),
            0
        );

        return totals;
    };

    const buildComparisonReport = (scenarioId, scenarioDescription, baselineMetrics, compressedMetrics) => {
        const toDto = (metrics, note) => ({
            totalPromptTokens: metrics.promptTokens || null,
            totalCompletionTokens: metrics.completionTokens || null,
            totalTokens: metrics.totalTokens || null,
            durationMs: Math.round(metrics.durationMs ?? 0),
            messagesProcessed: metrics.messagesProcessed ?? 0,
            summariesGenerated: metrics.summariesGenerated ?? 0,
            tokensSaved: metrics.tokensSaved ?? null,
            qualityNotes: note
        });

        const promptDiff =
            (baselineMetrics.promptTokens ?? 0) - (compressedMetrics.promptTokens ?? 0);

        const analysisLines = [
            `Промпт без сжатия: ${baselineMetrics.promptTokens ?? '—'}`,
            `Промпт со сжатием: ${compressedMetrics.promptTokens ?? '—'}`,
            `Экономия токенов: ${promptDiff > 0 ? promptDiff : 0}`,
            `Summary узлов: ${compressedMetrics.summariesGenerated ?? 0}`,
            `Суммарное сокращение (summary): ${compressedMetrics.tokensSaved ?? 0}`
        ];

        return {
            scenarioId: scenarioId,
            description: scenarioDescription,
            withoutCompressionMetrics: toDto(baselineMetrics, 'Без сжатия'),
            withCompressionMetrics: toDto(compressedMetrics, 'Со сжатием'),
            analysisText: analysisLines.join('\n')
        };
    };

    const runComparison = async () => {
        if (!state.selectedScenario) {
            showToast('Выберите сценарий для сравнения.');
            return;
        }
        if (state.isScenarioRunning) {
            showToast('Контрольный прогон уже выполняется.');
            return;
        }

        const scenarioInfo = state.scenarios.find((item) => item.id === state.selectedScenario);
        if (!scenarioInfo) {
            showToast('Сценарий не найден.');
            return;
        }

        state.summaryInterval = Math.max(
            1,
            Number(elements.summaryIntervalInput.value) || state.summaryInterval
        );
        state.maxSummaries = Math.max(
            0,
            Number(elements.maxSummariesInput.value) || state.maxSummaries
        );
        elements.summaryIntervalInput.value = state.summaryInterval;
        elements.maxSummariesInput.value = state.maxSummaries;

        setScenarioRunning(true);
        elements.modeHint.textContent = 'Выполняю контрольный прогон...';

        try {
            const scenarioDetails = await loadScenarioDetails(state.selectedScenario);
            
            const withoutCompression = await runScenarioOnce({
                scenarioId: state.selectedScenario,
                label: 'Демо (без сжатия)',
                summaryInterval: 9_999,
                maxSummariesInContext: 0
            });

            const withCompression = await runScenarioOnce({
                scenarioId: state.selectedScenario,
                label: 'Демо (со сжатием)',
                summaryInterval: state.summaryInterval,
                maxSummariesInContext: state.maxSummaries
            });

            state.comparisonReport = buildComparisonReport(
                state.selectedScenario,
                scenarioDetails.description,
                withoutCompression,
                withCompression
            );
            renderComparison();
            showToast('Контрольный прогон завершён.');
        } catch (error) {
            if (error.message !== 'cancelled') {
                showToast(error.message);
            }
        } finally {
            setScenarioRunning(false);
            const hint =
                state.mode === 'comparison'
                    ? 'В режиме сравнения сообщениями управляет сценарий. Выберите сценарий и запустите контрольный прогон.'
                    : 'В обычном режиме каждое сообщение отправляется в агент и фиксируются метрики сжатия.';
            elements.modeHint.textContent = hint;
            await refreshState();
        }
    };

    const bindEvents = () => {
        elements.messageForm.addEventListener('submit', handleMessageSubmit);
        elements.modeSelect.addEventListener('change', handleModeChange);
        elements.openSummaryModal.addEventListener('click', openModal);
        elements.resetDialogButton.addEventListener('click', handleResetDialog);
        elements.scenarioSelect.addEventListener('change', handleScenarioChange);
        elements.runComparisonButton.addEventListener('click', runComparison);
        document.addEventListener('click', (event) => {
            const target = event.target;
            if (target instanceof HTMLElement && target.dataset.close === 'summaryModal') {
                closeModal();
            }
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                closeModal();
            }
        });
    };

    const init = async () => {
        bindEvents();
        await loadScenarios();
        await refreshState();
        renderMetrics();
        renderHistory();
        renderComparison();
        handleModeChange();
    };

    init().catch((error) => {
        showToast(error.message || 'Не удалось инициализировать интерфейс.');
    });
})();
