# Требования к аудио для Vosk

- Формат: WAV, 16-bit PCM
- Частота: 16 kHz
- Каналы: Mono
- Размер: до ~10 МБ

Конвертация (делает сервер автоматически):
- Вход: `webm` (MediaRecorder, браузер)
- ffmpeg: `-ar 16000 -ac 1 -sample_fmt s16`

Проверка ffmpeg:
```bash
ffmpeg -version
```

