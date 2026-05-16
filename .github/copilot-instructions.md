# MathAI Copilot Instructions

## Build & Test Commands

### Backend (Spring Boot + Gradle)

```bash
# Start backend (dev mode, port 8080)
./gradlew bootRun

# Build JAR
./gradlew bootJar

# Run all tests (no test files exist yet)
./gradlew test

# Run a single test class
./gradlew test --tests com.insoftu.mathai.FullyQualifiedClassName

# Run a single test method
./gradlew test --tests "com.insoftu.mathai.MyTest.myTestMethod"
```

### Frontend (React + Vite)

```bash
cd frontend
npm install              # first time only
npm run dev              # start dev server on port 5173, proxies /api to localhost:8080
npm run build            # type-check + Vite production build
npm run lint             # ESLint
```

### Docker (production-like)

```bash
# Local Docker Compose (requires .env with GEMINI_API_KEY)
./syncAndDeployLocally.sh           # build & start (frontend:8081, backend:8080)
./syncAndDeployLocally.sh logs      # tail logs
./syncAndDeployLocally.sh stop      # stop containers
./syncAndDeployLocally.sh restart   # stop then start
```

The `GEMINI_API_KEY` environment variable is **required** for all runtime modes (local dev, Docker, production).

## High-Level Architecture

**MathAI** is a stateless, two-tier web app that generates IB MYP/DP math worksheets using Google Gemini.

### Backend (Spring Boot 3.4, Java 21, no database)

Three REST endpoints — all under `/api`:

| Endpoint | Purpose |
|---|---|
| `GET /api/topics?grade=6` | Returns hardcoded IB topic list (7 topics per grade, grades 6–12) |
| `POST /api/worksheet/generate` | Calls Gemini → returns structured JSON worksheet |
| `POST /api/worksheet/pdf` | Calls Gemini → renders PDF via OpenPDF → returns binary |

**Request/response flow:**
`WorksheetRequest` (grade, topic, difficulty, questionCount, optional context) → `GeminiService` builds a prompt instructing Gemini to return a specific JSON schema → Gemini returns raw text → `GeminiService` cleans markdown fences, sanitizes control characters inside JSON strings, parses to `WorksheetResponse` → returns as JSON (or pipes through `PdfService` for PDF).

**GeminiService key behaviors:**
- Detects `MAX_TOKENS` truncation and retries once with fewer questions (down to min 3)
- Teaches Gemini to include inline SVG diagrams for geometry questions (single-line `<svg>` strings)
- The sanitizer (`sanitiseJsonControlChars`) walks the raw response character-by-character, escaping literal newlines/tabs/CR that Gemini occasionally emits inside JSON string values

**PdfService** uses OpenPDF to render printer-friendly A4 worksheets with blue-tinted headers, numbered question bubbles, hint callouts, ruled answer spaces, and a green answer key on a separate page.

**Config:**
- `AppConfig`: defines `RestClient` bean pointing at `https://generativelanguage.googleapis.com` and enables open CORS on `/api/**`
- `GlobalExceptionHandler`: returns structured `{status, error, message, timestamp}` JSON for 404s and 5xx errors

### Frontend (React 19, Vite 7, Tailwind CSS 4, single page)

- **No routing** — the entire app is a single page
- `App.tsx` manages state: form vs. viewer toggle
- `WorksheetForm` fetches topics from backend on grade change, collects parameters, fires generation
- `WorksheetViewer` renders the worksheet with inline SVG diagrams via `dangerouslySetInnerHTML`, plus CSS overrides for dark-mode SVG color inversion
- `GeneratingProgress` shows a 6-stage animated progress bar while waiting for the LLM response (pure frontend animation, no backend status)
- **Dark mode** via Tailwind `dark:` variants, toggled by `useTheme` hook (reads/writes `mathai-theme` in localStorage, supports `system` following OS preference)
- **Print mode**: components marked `no-print` are hidden during `window.print()` — the viewer renders printer-friendly worksheets

### Production Deployment

- Docker Compose: backend + frontend containers
- Frontend is served by Nginx (SPA routing with `/api` proxy to backend)
- Scripted deploy to DigitalOcean droplet via `./syncAndDeploy.sh` (git push → rsync code → remote `docker compose up -d --build`)

## Key Conventions

### Java
- **All DTOs are Java records** with compact constructors for validation
- Constructor injection only (no `@Autowired` field injection)
- The `WorksheetRequest` compact constructor clamps `questionCount` to 1–20, defaulting to 10
- Logging uses SLF4J at `com.insoftu.mathai: DEBUG`, `org.springframework.web: INFO`
- Jackson configured with `default-property-inclusion: non_null` — null fields are omitted from JSON responses

### TypeScript/React
- **Enable `verbatimModuleSyntax`** — use `import type` for type-only imports
- All API calls go through `api/worksheetApi.ts` (Axios, extracts error messages from `e.response.data.message`)
- SVG diagrams stored as plain strings, rendered via `dangerouslySetInnerHTML`

### Gemini Integration
- The prompt explicitly instructs Gemini to return **raw JSON only — no markdown fences** — but `parseResponse` strips code fences anyway as a safety net
- Model is configurable via `GEMINI_MODEL` env var (defaults to `gemini-2.5-flash`)
- Quota exhaustion (HTTP 429) and bad API key (401/403) produce user-friendly error messages with links to rate-limit docs

### Environment Variables
- `GEMINI_API_KEY` — required, used at runtime only
- `GEMINI_MODEL` — optional, defaults to `gemini-2.5-flash`
- `.env` is gitignored; `.env.example` is the template
