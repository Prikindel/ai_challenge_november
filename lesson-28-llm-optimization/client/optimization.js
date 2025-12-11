// Страница оптимизации параметров LLM

const STORAGE_KEY = 'llm_optimization_settings';

// Загрузка шаблонов промптов
async function loadTemplates() {
    try {
        const response = await fetch('/api/prompt-templates');
        const templates = await response.json();
        
        const select = document.getElementById('templateId');
        select.innerHTML = '';
        
        templates.forEach(template => {
            const option = document.createElement('option');
            option.value = template.id;
            option.textContent = template.name;
            option.dataset.description = template.description;
            select.appendChild(option);
        });
        
        // Загружаем сохранённые настройки
        loadSavedSettings();
        
        // Обновляем описание при изменении шаблона
        select.addEventListener('change', updateTemplateDescription);
        updateTemplateDescription();
    } catch (error) {
        console.error('Failed to load templates:', error);
        document.getElementById('templateId').innerHTML = '<option value="">Ошибка загрузки</option>';
    }
}

// Обновление описания шаблона
function updateTemplateDescription() {
    const select = document.getElementById('templateId');
    const selectedOption = select.options[select.selectedIndex];
    const descriptionEl = document.getElementById('templateDescription');
    
    if (selectedOption && selectedOption.dataset.description) {
        descriptionEl.textContent = selectedOption.dataset.description;
    } else {
        descriptionEl.textContent = '';
    }
}

// Сохранение настроек в localStorage
function saveSettings() {
    const settings = {
        temperature: parseFloat(document.getElementById('temperature').value) || 0.7,
        maxTokens: parseInt(document.getElementById('maxTokens').value) || 2048,
        topP: parseFloat(document.getElementById('topP').value) || 0.9,
        topK: parseInt(document.getElementById('topK').value) || 40,
        repeatPenalty: parseFloat(document.getElementById('repeatPenalty').value) || 1.1,
        contextWindow: parseInt(document.getElementById('contextWindow').value) || 4096,
        seed: document.getElementById('seed').value ? parseInt(document.getElementById('seed').value) : null,
        templateId: document.getElementById('templateId').value || 'default'
    };
    
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    updateCurrentSettings();
    
    // Показываем уведомление
    alert('Настройки сохранены! Они будут использоваться при следующих запросах к LLM.');
}

// Загрузка сохранённых настроек
function loadSavedSettings() {
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved) {
            const settings = JSON.parse(saved);
            
            document.getElementById('temperature').value = settings.temperature || 0.7;
            document.getElementById('maxTokens').value = settings.maxTokens || 2048;
            document.getElementById('topP').value = settings.topP || 0.9;
            document.getElementById('topK').value = settings.topK || 40;
            document.getElementById('repeatPenalty').value = settings.repeatPenalty || 1.1;
            document.getElementById('contextWindow').value = settings.contextWindow || 4096;
            document.getElementById('seed').value = settings.seed || '';
            document.getElementById('templateId').value = settings.templateId || 'default';
            
            updateTemplateDescription();
        }
    } catch (error) {
        console.error('Failed to load saved settings:', error);
    }
    
    updateCurrentSettings();
}

// Сброс настроек к умолчанию
function resetSettings() {
    if (confirm('Сбросить все настройки к значениям по умолчанию?')) {
        document.getElementById('temperature').value = 0.7;
        document.getElementById('maxTokens').value = 2048;
        document.getElementById('topP').value = 0.9;
        document.getElementById('topK').value = 40;
        document.getElementById('repeatPenalty').value = 1.1;
        document.getElementById('contextWindow').value = 4096;
        document.getElementById('seed').value = '';
        document.getElementById('templateId').value = 'default';
        
        localStorage.removeItem(STORAGE_KEY);
        updateCurrentSettings();
        updateTemplateDescription();
    }
}

// Обновление отображения текущих настроек
function updateCurrentSettings() {
    const settings = {
        temperature: parseFloat(document.getElementById('temperature').value) || 0.7,
        maxTokens: parseInt(document.getElementById('maxTokens').value) || 2048,
        topP: parseFloat(document.getElementById('topP').value) || 0.9,
        topK: parseInt(document.getElementById('topK').value) || 40,
        repeatPenalty: parseFloat(document.getElementById('repeatPenalty').value) || 1.1,
        contextWindow: parseInt(document.getElementById('contextWindow').value) || 4096,
        seed: document.getElementById('seed').value ? parseInt(document.getElementById('seed').value) : null,
        templateId: document.getElementById('templateId').value || 'default'
    };
    
    const content = document.getElementById('currentSettingsContent');
    content.innerHTML = `
        <div><strong>Temperature:</strong> <code>${settings.temperature}</code></div>
        <div><strong>Max Tokens:</strong> <code>${settings.maxTokens}</code></div>
        <div><strong>Context Window:</strong> <code>${settings.contextWindow}</code></div>
        <div><strong>Top-P:</strong> <code>${settings.topP}</code></div>
        <div><strong>Top-K:</strong> <code>${settings.topK}</code></div>
        <div><strong>Repeat Penalty:</strong> <code>${settings.repeatPenalty}</code></div>
        <div><strong>Seed:</strong> <code>${settings.seed || 'Не указан'}</code></div>
        <div><strong>Шаблон:</strong> <code>${settings.templateId}</code></div>
    `;
}

// Функция для получения сохранённых настроек (для использования в других скриптах)
function getSavedSettings() {
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        return saved ? JSON.parse(saved) : null;
    } catch (error) {
        console.error('Failed to get saved settings:', error);
        return null;
    }
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    loadTemplates();
    
    // Обновляем текущие настройки при изменении значений
    const inputs = document.querySelectorAll('input, select');
    inputs.forEach(input => {
        input.addEventListener('change', updateCurrentSettings);
        input.addEventListener('input', updateCurrentSettings);
    });
});

