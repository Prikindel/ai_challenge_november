# VPS Setup — День 27. Локальная LLM на VPS

## 0. Подключение к VPS

**Подключение по SSH:**
```bash
ssh root@185.31.165.227
```

**Или с указанием пользователя (если не root):**
```bash
ssh username@185.31.165.227
```

**Если используется SSH-ключ:**
```bash
ssh -i ~/.ssh/your_key root@185.31.165.227
```

**Примечания:**
- IP адрес VPS: `185.31.165.227`
- При первом подключении подтвердите fingerprint сервера
- Если порт SSH нестандартный (не 22), используйте: `ssh -p PORT root@185.31.165.227`

---

## 1. Требования
- VPS: Ubuntu 22.04 LTS, 4–8 vCPU, 8–16 ГБ RAM (лучше с GPU).
- Диск: +10–20 ГБ под модели.
- IP адрес: `185.31.165.227` (или домен, если настроен).
- Порты: 22 (SSH), 80/443 (HTTP/HTTPS).

## 2. Обновление системы
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl git ufw nginx
```

## 3. UFW (firewall)
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

## 4. Установка Ollama
```bash
curl -fsSL https://ollama.com/install.sh | sh
sudo systemctl enable --now ollama
```

### Модель
```bash
ollama pull llama3.2
ollama run llama3.2 "hello"
```

## 5. Nginx как реверс-прокси (HTTPS + basic auth)

### Сертификаты (Let's Encrypt)

**Вариант 1: Certbot через apt (рекомендуется)**
```bash
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d 185.31.165.227
```

**Вариант 2: Certbot через snap (если snap установлен)**
```bash
sudo snap install core; sudo snap refresh core
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/bin/certbot
sudo certbot certonly --nginx -d 185.31.165.227
```

**Вариант 3: Certbot standalone (без nginx плагина)**
```bash
sudo apt update
sudo apt install -y certbot
sudo certbot certonly --standalone -d 185.31.165.227
```

**Примечание:** 
- Для Let's Encrypt нужен домен. Если используете IP адрес, можно использовать самоподписанный сертификат
- IP адрес сервера: `185.31.165.227`
- После получения сертификатов, certbot автоматически обновит конфиг nginx (если использовали `--nginx`)

### Basic auth
```bash
sudo apt install -y apache2-utils
sudo htpasswd -c /etc/nginx/.htpasswd user   # задайте пароль
```

### Конфиг nginx

**Создание файла конфигурации:**

**Вариант 1: Через nano (рекомендуется)**
```bash
sudo nano /etc/nginx/sites-available/llm
```

Затем вставьте следующую конфигурацию (без команд bash!):
```
server {
  listen 80;
  server_name 185.31.165.227;
  return 301 https://$host$request_uri;
}

server {
  listen 443 ssl;
  server_name 185.31.165.227;

  ssl_certificate     /etc/letsencrypt/live/185.31.165.227/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/185.31.165.227/privkey.pem;

  auth_basic "LLM";
  auth_basic_user_file /etc/nginx/.htpasswd;

  location /api/ {
    proxy_pass http://127.0.0.1:11434/;
    proxy_set_header Host $host;
    proxy_read_timeout 300;
    proxy_send_timeout 300;
    client_max_body_size 16m;
  }
}
```

Сохраните файл: `Ctrl+O`, затем `Enter`, затем `Ctrl+X`

**Вариант 2: Через cat (одной командой)**
```bash
sudo tee /etc/nginx/sites-available/llm > /dev/null <<'EOF'
server {
  listen 80;
  server_name 185.31.165.227;
  return 301 https://$host$request_uri;
}

server {
  listen 443 ssl;
  server_name 185.31.165.227;

  ssl_certificate     /etc/letsencrypt/live/185.31.165.227/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/185.31.165.227/privkey.pem;

  auth_basic "LLM";
  auth_basic_user_file /etc/nginx/.htpasswd;

  location /api/ {
    proxy_pass http://127.0.0.1:11434/;
    proxy_set_header Host $host;
    proxy_read_timeout 300;
    proxy_send_timeout 300;
    client_max_body_size 16m;
  }
}
EOF
```

**⚠️ ВАЖНО:** В файле конфигурации nginx должны быть ТОЛЬКО директивы nginx, без команд bash (sudo, ls и т.д.)!

**Содержимое файла `/etc/nginx/sites-available/llm`:**
```
server {
  listen 80;
  server_name 185.31.165.227;
  return 301 https://$host$request_uri;
}

server {
  listen 443 ssl;
  server_name 185.31.165.227;

  ssl_certificate     /etc/letsencrypt/live/185.31.165.227/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/185.31.165.227/privkey.pem;

  auth_basic "LLM";
  auth_basic_user_file /etc/nginx/.htpasswd;

  location /api/ {
    proxy_pass http://127.0.0.1:11434/;
    proxy_set_header Host $host;
    proxy_read_timeout 300;
    proxy_send_timeout 300;
    client_max_body_size 16m;
  }
}
```

**Активируем конфигурацию:**
```bash
# Проверяем, существует ли уже ссылка
ls -la /etc/nginx/sites-enabled/llm

# Если ссылка уже существует и правильная, можно пропустить создание
# Если нужно пересоздать, удалите старую ссылку:
sudo rm /etc/nginx/sites-enabled/llm

# Создаём символическую ссылку
sudo ln -s /etc/nginx/sites-available/llm /etc/nginx/sites-enabled/llm

# Проверяем конфигурацию на ошибки
sudo nginx -t

# Если проверка прошла успешно, перезагружаем nginx
sudo systemctl reload nginx
```

**Если ссылка уже существует:**
- Если файл `/etc/nginx/sites-available/llm` существует и ссылка правильная, можно сразу перейти к проверке: `sudo nginx -t`
- Если нужно пересоздать ссылку, сначала удалите старую: `sudo rm /etc/nginx/sites-enabled/llm`

**Примечание:** 
- IP адрес сервера: `185.31.165.227`
- Если используете IP адрес вместо домена, для Let's Encrypt нужен домен. Можно использовать самоподписанный сертификат или работать без HTTPS (не рекомендуется для production)

## 6. Проверка извне
```bash
curl https://185.31.165.227/api/generate \
  -u user:pass \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3.2","prompt":"Привет!","stream":false}'
```

Ожидаем корректный ответ. При 401 — проверить auth; при 502/504 — таймауты/доступ к 127.0.0.1:11434.

## 7. Настройки в клиенте
```yaml
localLLM:
  enabled: true
  provider: "ollama"
  baseUrl: "https://185.31.165.227"
  model: "llama3.2"
  apiPath: "/api/generate"
  timeout: 120000
  auth:
    type: "basic"
    user: "user"
    password: "pass"
```

## 8. Рекомендации
- Не открывать порт 11434 наружу.
- Следить за ресурсами (CPU/RAM/GPU); ограничивать параллельные запросы.
- Логи и ротация (nginx, systemd).
- Таймауты 200–300s на прокси для длинных ответов.
- Для OpenAI-совместимого API можно использовать llama.cpp server с `--api` или text-generation-inference.

