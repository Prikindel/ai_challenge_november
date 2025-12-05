// Будет реализовано в коммите 8

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('analysisForm');
    if (form) {
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            alert('Функционал будет реализован в коммите 8');
        });
    }
});

