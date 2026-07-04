# Model Forge REST Wrapper

A thin REST host around the **embedded** Model Forge facade
(`de.civitascore.modelforge.facade.ModelForge`), re-exposing the small pre-pivot
HTTP surface the marketplace add-on prototype consumes.

**Why this exists:** Model Forge removed its standalone REST service in the pivot
to an embedded Java library, and the planned host (`portal-backend`) has not
adopted it yet — so the `:8000` REST target the marketplace calls exists in no
current repo. This wrapper fills that gap.

It also acts as an **anti-corruption layer**: the marketplace keeps speaking the
pre-ADR-14 vocabulary (`datastructure` URNs, deterministic dataset ids, labels),
and the wrapper translates to the current embedded model (`element` URNs, minted
ids, labels stored inside the dataset manifest).

> Development aid only — **not** a deployment artifact.

## Prerequisites

- **JDK 25** (the model-forge modules enforce 25+). On macOS/Homebrew:
  `/opt/homebrew/opt/openjdk`.
- **Docker** (for the throwaway Postgres).
- The **model-forge modules built into the local Maven repo** (they are
  `0.1.0-SNAPSHOT`, not published):

  ```sh
  cd ../civitas-v2/model-forge
  JAVA_HOME=/opt/homebrew/opt/openjdk ./mvnw install -DskipTests \
    -pl model-forge-contract,model-forge-runtime,model-forge-spring-boot-starter -am
  ```

## Start

```sh
# 1. Postgres (throwaway) on :5433
docker run -d --name mf-wrapper-postgres -p 5433:5432 \
  -e POSTGRES_DB=model_forge -e POSTGRES_USER=model_forge -e POSTGRES_PASSWORD=model_forge \
  postgres:16

# 2. The wrapper on :8001 (Flyway migrates the model_forge schema on first run)
export JAVA_HOME=/opt/homebrew/opt/openjdk
mvn spring-boot:run
```

Point the marketplace at it (in `marketplace-addon/app/.env.local`):

```
MODELFORGE_BASE_URL="http://localhost:8001"
```

Health probe (expects `404`, i.e. server up + auth passed):

```sh
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-API-Key: your-secret-key-here" \
  "http://localhost:8001/api/v1/datasets?id=probe"
```

## Stop

```sh
# wrapper: Ctrl+C in its terminal (or kill the `mvn spring-boot:run` process)
docker stop mf-wrapper-postgres && docker rm mf-wrapper-postgres   # drops all data
```

To wipe just the data and keep the container:

```sh
docker exec mf-wrapper-postgres psql -U model_forge -d model_forge \
  -c 'DROP SCHEMA IF EXISTS model_forge CASCADE;'
```

## Configuration

Everything has a working default; override via environment variables.

| Variable | Default | Purpose |
|---|---|---|
| `MODELFORGE_HTTP_PORT` | `8001` | wrapper port (`:8000` is usually the legacy MF container) |
| `MODELFORGE_API_KEY` | `your-secret-key-here` | `X-API-Key` header the marketplace must send |
| `MODELFORGE_DB_URL` | `jdbc:postgresql://localhost:5433/model_forge` | registry database |
| `MODELFORGE_DB_USERNAME` / `MODELFORGE_DB_PASSWORD` | `model_forge` | DB credentials |
| `MODELFORGE_URN_SCOPE` / `_OWNER` / `_DOMAIN` / `_VERSION` | `platform` / `civitas` / `common` / `1.0.0` | namespace for deterministic dataset URNs |

## Endpoints

All require the `X-API-Key` header.

| Method | Path | Maps to |
|---|---|---|
| `POST` | `/api/v1/datastructures` `{schema}` | `importSchema` → `{resourceId}` |
| `GET` | `/api/v1/datastructures?id=<urn>` | `getArtifact` (existence probe) |
| `DELETE` | `/api/v1/datastructures?id=<urn>&force=true` | `deleteArtifact` |
| `POST` | `/api/v1/datasets` `{title}` | `createArtifact(DATA_SET)` → shell |
| `PUT` | `/api/v1/datasets?id=<urn>` | `saveArtifact` (labels + refs) |
| `GET` | `/api/v1/datasets?id=<urn>` | resolve + return manifest |
| `DELETE` | `/api/v1/datasets?id=<urn>` | `deleteArtifact` |
| `GET` | `/api/v1/artifacts/search?type=dataset&label=<key>=<value>` | label filter over datasets |

## Notes / limitations (prototype scope)

- Label search and alias resolution scan the (small) dataset population — fine at
  prototype scale, not built for large registries.
- Dataset labels are stored inside the manifest document, since the embedded
  facade has no native label concept.
- Auth is a single static `X-API-Key` (no Spring Security), matching the
  pre-pivot service.
