# Server and admin panel

## Request flow

```text
Android/browser
    -> Nginx HTTP/WebSocket ingress
    -> backend API and WebSocket handlers
    -> PostgreSQL / MongoDB / Redis / MinIO
```

The admin dashboard is served by the `admin-panel` container and uses relative API paths. It does not contain a separate production database or mock authority.

## Identity and authorization

Registration creates a pending RED account and device record. An administrator approves the account and device before normal use. JWT access/refresh tokens, device certificates and WebSocket tickets protect authenticated operations.

Administrative routes are declared in `SecurityConfig.kt`. The dashboard role check compares routes called by the canonical Android app with the backend authorization rules.

## Operational endpoints

- `/health` — backend health.
- `/sfu-health` — media SFU health through Nginx.
- `/api/admin/audit` — administrative audit data.
- `/api/admin/users` — account administration.
- `/api/master/admin/system/stats` — protected system summary.

See `API_REFERENCE.md` for the current route inventory. Run `npm run check` in `admin_dashboard` after changing dashboard routes.
