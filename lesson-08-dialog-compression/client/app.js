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
        loading: false
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
        const shouldDisableSend = isLoading || state.mode === 'comparison';
        elements.sendButton.disabled = shouldDisableSend;
        elements.sendButton.classList.toggle('loading', isLoading);
        elements.messageInput.disabled = shouldDisableSend;
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

                const time = document.createElement('span');
                time.className = 'badge';
                time.textContent = formatDate(message.createdAt);
                header.appendChild(time);

                const content = document.createElement('div');
                content.textContent = message.content;

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
                    info.textContent = `Свёрнуто сообщений: ${covered}`;
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
            const payload = {
                message,
                summaryInterval: state.summaryInterval,
                maxSummariesInContext: state.maxSummaries
            };
            const response = await request('/message', {
                method: 'POST',
                body: JSON.stringify(payload)
            });

            state.lastResponse = response;
            state.contextUsed = response.contextUsed;
            elements.summaryIntervalInput.value = response.summaryInterval;
            elements.modeHint.textContent = 'Ответ получен. История обновлена.';

            await refreshState();
            renderMetrics();
            renderSummaries();
            addHistoryEntry({ response, message });
            recomputeTotals();
            renderHistory();
        } catch (error) {
            showToast(error.message);
        } finally {
            setLoading(false);
            elements.modeHint.textContent =
                'В обычном режиме каждое сообщение отправляется в агент и фиксируются метрики сжатия.';
            elements.messageInput.value = '';
            elements.messageInput.focus();
        }
    };

    const addHistoryEntry = ({ response, message }) => {
        const entry = {
            timestamp: new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
            promptTokens: response.tokenUsage.promptTokens,
            completionTokens: response.tokenUsage.completionTokens,
            totalTokens: response.tokenUsage.totalTokens,
            tokensSaved: response.tokenUsage.tokensSavedByCompression,
            summaryCount: response.summaries.length,
            contextRawCount: response.contextUsed.rawMessages.length,
            preview: `${message.slice(0, 120)}${message.length > 120 ? '…' : ''}`
        };
        state.history.unshift(entry);
        if (state.history.length > 25) {
            state.history.pop();
        }
    };

    const handleModeChange = () => {
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

    const handleScenarioChange = () => {
        state.selectedScenario = elements.scenarioSelect.value;
    };

    const runComparison = async () => {
        if (!state.selectedScenario) {
            showToast('Выберите сценарий для сравнения.');
            return;
        }
        elements.runComparisonButton.disabled = true;
        elements.runComparisonButton.textContent = 'Выполняю...';
        try {
            const report = await request('/comparison', {
                method: 'POST',
                body: JSON.stringify({ scenarioId: state.selectedScenario })
            });
            state.comparisonReport = report;
            renderComparison();
            showToast('Контрольный прогон завершён.');
            await refreshState();
        } catch (error) {
            showToast(error.message);
        } finally {
            elements.runComparisonButton.disabled = false;
            elements.runComparisonButton.textContent = 'Запустить контрольный прогон';
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
