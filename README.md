# HTTPS Client

Учебный диагностический HTTP/HTTPS-клиент из [`assignment.md`](assignment.md).
Реализованы этапы A, B и этап C уровня 1: ручной HTTP/1.1 framing,
восстановление представления, TLS с заданным CA и отдельная ручная проверка
имени хоста.

## Сборка и запуск

```bash
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java')
```

HTTP:

```bash
java -cp out netdiag.Main http://127.0.0.1:8090/plain --timeout 5000
```

HTTPS со стендом задания:

```bash
java -cp out netdiag.Main https://localhost:8543/ \
  --ca tests/tls_fixture/out/ca.crt --timeout 5000
```

Для `https` параметр `--ca` обязателен. Имя хоста проверяется собственным
кодом после успешного TLS handshake; проверку цепочки и срока выполняет Java.

## Структура

```text
src/netdiag/
├── Main.java
├── cli/            # аргументы командной строки
├── url/            # разбор URL и модель адреса
├── dns/            # DNS-резолвинг
├── connect/        # установка TCP-соединения
├── decode/         # content-encoding и charset
├── http/           # формирование и разбор HTTP-сообщений
├── json/           # проверка JSON
├── tls/            # TLS-контекст, handshake и проверка имени
└── diagnostic/     # модель и печать результата
```
