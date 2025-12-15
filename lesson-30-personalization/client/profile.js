// Загрузка текущего профиля
async function loadProfile() {
    try {
        const response = await fetch('/api/profile');
        if (!response.ok) {
            throw new Error('Failed to load profile');
        }
        const profile = await response.json();
        populateForm(profile);
    } catch (error) {
        console.error('Error loading profile:', error);
        showMessage('Ошибка загрузки профиля: ' + error.message, 'error');
    }
}

// Заполнение формы данными профиля
function populateForm(profile) {
    document.getElementById('name').value = profile.name || '';
    
    // Предпочтения
    document.getElementById('language').value = profile.preferences?.language || 'ru';
    document.getElementById('responseFormat').value = profile.preferences?.responseFormat?.toLowerCase() || 'detailed';
    document.getElementById('timezone').value = profile.preferences?.timezone || '';
    document.getElementById('dateFormat').value = profile.preferences?.dateFormat || '';
    
    // Стиль работы
    document.getElementById('preferredWorkingHours').value = profile.workStyle?.preferredWorkingHours || '';
    document.getElementById('focusAreas').value = (profile.workStyle?.focusAreas || []).join(', ');
    document.getElementById('tools').value = (profile.workStyle?.tools || []).join(', ');
    document.getElementById('projects').value = (profile.workStyle?.projects || []).join(', ');
    
    // Стиль общения
    document.getElementById('tone').value = profile.communicationStyle?.tone?.toLowerCase() || 'professional';
    document.getElementById('detailLevel').value = profile.communicationStyle?.detailLevel?.toLowerCase() || 'medium';
    document.getElementById('useExamples').checked = profile.communicationStyle?.useExamples ?? true;
    document.getElementById('useEmojis').checked = profile.communicationStyle?.useEmojis ?? false;
    
    // Контекст
    document.getElementById('currentProject').value = profile.context?.currentProject || '';
    document.getElementById('role').value = profile.context?.role || '';
    document.getElementById('team').value = profile.context?.team || '';
    document.getElementById('goals').value = (profile.context?.goals || []).join('\n');
}

// Сохранение профиля
async function saveProfile(event) {
    event.preventDefault();
    
    const formData = {
        name: document.getElementById('name').value,
        preferences: {
            language: document.getElementById('language').value,
            responseFormat: document.getElementById('responseFormat').value,
            timezone: document.getElementById('timezone').value || null,
            dateFormat: document.getElementById('dateFormat').value || null
        },
        workStyle: {
            preferredWorkingHours: document.getElementById('preferredWorkingHours').value || null,
            focusAreas: document.getElementById('focusAreas').value
                .split(',')
                .map(s => s.trim())
                .filter(s => s.length > 0),
            tools: document.getElementById('tools').value
                .split(',')
                .map(s => s.trim())
                .filter(s => s.length > 0),
            projects: document.getElementById('projects').value
                .split(',')
                .map(s => s.trim())
                .filter(s => s.length > 0)
        },
        communicationStyle: {
            tone: document.getElementById('tone').value,
            detailLevel: document.getElementById('detailLevel').value,
            useExamples: document.getElementById('useExamples').checked,
            useEmojis: document.getElementById('useEmojis').checked
        },
        context: {
            currentProject: document.getElementById('currentProject').value || null,
            role: document.getElementById('role').value || null,
            team: document.getElementById('team').value || null,
            goals: document.getElementById('goals').value
                .split('\n')
                .map(s => s.trim())
                .filter(s => s.length > 0)
        }
    };
    
    try {
        const response = await fetch('/api/profile', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to save profile');
        }
        
        const result = await response.json();
        showMessage('Профиль успешно сохранен! ✅', 'success');
    } catch (error) {
        console.error('Error saving profile:', error);
        showMessage('Ошибка сохранения профиля: ' + error.message, 'error');
    }
}

// Показ сообщения
function showMessage(text, type = 'info') {
    const messageDiv = document.getElementById('message');
    messageDiv.textContent = text;
    messageDiv.className = `message ${type}`;
    messageDiv.style.display = 'block';
    
    setTimeout(() => {
        messageDiv.style.display = 'none';
    }, 5000);
}

// Сброс формы
function resetForm() {
    if (confirm('Вы уверены, что хотите сбросить все изменения?')) {
        loadProfile();
    }
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    loadProfile();
    document.getElementById('profileForm').addEventListener('submit', saveProfile);
    document.getElementById('resetBtn').addEventListener('click', resetForm);
});

