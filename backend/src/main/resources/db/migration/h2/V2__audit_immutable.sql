-- H2 (dev/test) has no PL/pgSQL; immutability is enforced in the application layer
-- (AuditLogRepository exposes no update or delete). PostgreSQL adds a trigger.
SELECT 1;
