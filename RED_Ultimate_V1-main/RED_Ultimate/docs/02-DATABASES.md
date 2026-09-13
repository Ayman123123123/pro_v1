# Databases and storage

## Stores

| Store | Data | Principle |
|---|---|---|
| PostgreSQL 16 | accounts, devices, refresh sessions, recovery, audit and relational content | transactions and constraints |
| MongoDB 8 | social content, message metadata, media metadata and application documents | document access patterns |
| Redis 7 | rate limits, short-lived sessions and transient coordination | never the only durable source |
| MinIO | encrypted media objects and backups | object storage behind signed URLs |

## Schema ownership

PostgreSQL schema changes are Flyway migrations in `backend-server/src/main/resources/db/migration/`. JPA entities and migration files must be checked together with `scripts/check-schema-consistency.py`.

MongoDB indexes and collection contracts are owned by the backend document models and their initialization code. Redis keys must have explicit TTL and bounded access patterns.

## Operational rules

- Do not commit populated `.env` files, credentials or private keys.
- Run backups before destructive maintenance.
- Use `docker compose config --quiet` before deployment.
- Treat migrations as append-only in deployed environments; removing a migration requires a controlled database rollout plan.
