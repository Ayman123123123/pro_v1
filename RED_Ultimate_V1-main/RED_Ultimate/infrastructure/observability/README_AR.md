# مراقبة RED الاحترافية

تشغل فقط بملف profile منفصل:

```bash
docker compose -f docker-compose.yml -f infrastructure/observability/docker-compose.observability.yml --profile observability up -d
```

لا تُعرض Prometheus/Grafana للإنترنت من Compose. ضع Grafana خلف VPN أو reverse proxy مع SSO. غيّر `GRAFANA_ADMIN_PASSWORD` و`POSTGRES_EXPORTER_PASSWORD` في `.env`، واضبط receiver حقيقي في Alertmanager قبل الاعتماد على التنبيهات.

المقاييس: Backend/Actuator، PostgreSQL، Redis، MongoDB، MinIO، SFU. قواعد التنبيه تشمل unavailable dependencies وp95 latency و5xx وPostgreSQL connections. Backup failure وشهادات TLS يحتاجان job تشغيلي يكتب metric حقيقياً بعد backup/renewal، ولا يُسمح بتوليد status وهمي.
