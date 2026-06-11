# pgvector SQL Layout

This directory separates first-time initialization scripts from migration patches.

## First-time initialization

Use `init/init.sql` only for a fresh pgvector database or a rebuilt Docker volume.

The Docker Compose environment mounts `./pgvector/init/init.sql` to `/docker-entrypoint-initdb.d/init.sql`, so it runs automatically only when the PostgreSQL data directory is empty.

## Existing database patches

Use `patches/` for existing pgvector databases. These scripts are intended to be run manually and should not be used as a replacement for `init/init.sql`.

- `patches/auto-agent-rag-vector-migration.sql`

Example:

```powershell
docker cp docs/dev-ops/pgvector/patches/auto-agent-rag-vector-migration.sql vector_db:/tmp/auto-agent-rag-vector-migration.sql
docker exec vector_db psql -U postgres -d ai-rag-knowledge -f /tmp/auto-agent-rag-vector-migration.sql
```
