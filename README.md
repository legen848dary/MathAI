# MathAI — IB Math Worksheet Generator

AI-powered, print-ready math worksheets aligned to the IB MYP (Grades 6–10) and IB DP (Grades 11–12) curriculum.
Powered by Google Gemini 2.0 Flash (free tier).

---

## Prerequisites

- Java 21
- Node.js 18+
- Docker + Docker Compose (for production deployment)
- A free Gemini API key from https://aistudio.google.com

---

## Local Development

### 1. Get a Gemini API Key (Free)

1. Go to https://aistudio.google.com
2. Click **"Get API key"** → **"Create API key"**
3. Copy the key

### 2. Set the API Key

```bash
# In the project root:
export GEMINI_API_KEY=your_key_here
```

Or create a `.env` file:
```
GEMINI_API_KEY=your_key_here
```

### 3. Start the Backend (Spring Boot)

```bash
# From project root
./gradlew bootRun
```

Backend starts on http://localhost:8080

### 4. Start the Frontend (React + Vite)

```bash
cd frontend
npm install   # first time only
npm run dev
```

Frontend starts on http://localhost:5173

Open http://localhost:5173 in your browser. Done!

---

## Production Deployment (DigitalOcean SGP Droplet)

### One-time server setup

```bash
# On your droplet (Ubuntu):
apt update && apt install -y docker.io docker-compose-plugin
```

### Deploy

```bash
# On your local machine — copy files to server:
rsync -avz --exclude 'frontend/node_modules' --exclude '.gradle' --exclude 'build' \
  ./ root@YOUR_DROPLET_IP:/opt/mathai/

# On the server:
cd /opt/mathai
echo "GEMINI_API_KEY=your_key_here" > .env
docker compose up -d --build
```

App is live on http://YOUR_DROPLET_IP (port 80)

### Update after code changes

```bash
# On server:
cd /opt/mathai
git pull   # or rsync again
docker compose up -d --build
```

---

## API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/topics?grade=6` | Get IB-aligned topics for a grade |
| POST | `/api/worksheet/generate` | Generate worksheet as JSON |
| POST | `/api/worksheet/pdf` | Generate and download as PDF |

### Example Request

```bash
curl -X POST http://localhost:8080/api/worksheet/generate \
  -H "Content-Type: application/json" \
  -d '{
    "grade": 6,
    "topic": "Fractions, Decimals & Percentages",
    "difficulty": "Medium",
    "questionCount": 10
  }'
```

---

## Project Structure

```
MathAI/
├── src/main/java/com/insoftu/mathai/
│   ├── MathAIApplication.java        # Spring Boot entry point
│   ├── controller/
│   │   └── WorksheetController.java  # REST endpoints
│   ├── service/
│   │   ├── GeminiService.java        # Gemini AI integration
│   │   └── PdfService.java           # PDF generation (OpenPDF)
│   ├── model/
│   │   ├── WorksheetRequest.java     # Request DTO
│   │   └── WorksheetResponse.java    # Response DTO
│   └── config/
│       └── AppConfig.java            # CORS + RestClient config
├── src/main/resources/
│   └── application.yml               # App config
├── frontend/                         # React + Vite + Tailwind
│   ├── src/
│   │   ├── App.tsx                   # Main app component
│   │   ├── components/
│   │   │   ├── WorksheetForm.tsx     # Grade/topic/difficulty selector
│   │   │   └── WorksheetViewer.tsx  # Rendered worksheet + print
│   │   ├── api/
│   │   │   └── worksheetApi.ts      # API calls (Axios)
│   │   └── types/
│   │       └── worksheet.ts         # TypeScript types
│   ├── nginx.conf                   # Nginx config for production
│   └── Dockerfile                   # Frontend Docker build
├── Dockerfile                       # Backend Docker build
├── docker-compose.yml               # Production compose
└── .env.example                     # Environment variable template
```

---

## IB Curriculum Topics by Grade

| Grade | Programme | Sample Topics |
|-------|-----------|---------------|
| 6 | IB MYP | Fractions, Basic Algebra, Ratios, Geometry |
| 7 | IB MYP | Rational Numbers, Linear Equations, Probability |
| 8 | IB MYP | Quadratics, Pythagoras, Functions |
| 9 | IB MYP | Trig, Surds, Simultaneous Equations |
| 10 | IB MYP | Quadratic Functions, Sine/Cosine Rules, Vectors |
| 11 | IB DP | Differentiation, Logarithms, Sequences & Series |
| 12 | IB DP | Calculus, Integration, Matrices, Complex Numbers |

