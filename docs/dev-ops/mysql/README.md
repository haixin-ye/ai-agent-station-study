# MySQL SQL Layout

This directory separates first-time initialization scripts from migration patches.

## First-time initialization

Use `init/` only for a fresh database or a rebuilt Docker volume.

Execution order:

1. `init/ai-agent-station-study.sql`
2. `init/auto-agent-main-loop-harness.sql`
3. `init/auto-agent-model-runtime.sql`

The Docker Compose environment mounts `./mysql/init` to `/docker-entrypoint-initdb.d`, so these files run automatically only when the MySQL data directory is empty.

## Existing database patches

Use `patches/` for existing databases. These scripts are intended to be run manually and should not be mounted into Docker first-time initialization.

- `patches/auto-agent-main-loop-harness-patch-20260518.sql`
- `patches/auto-agent-memory-model-runtime-migration.sql`
- `patches/auto-agent-runtime-length-upgrade.sql`
- `patches/auto-agent-rag-asset-migration.sql`
- `patches/auto-agent-latest-prompts-only.sql`

Example:

```powershell
docker cp docs/dev-ops/mysql/patches/auto-agent-rag-asset-migration.sql mysql:/tmp/auto-agent-rag-asset-migration.sql
docker exec mysql mysql -uroot -p123456 ai-agent-station-study -e "source /tmp/auto-agent-rag-asset-migration.sql"
```
