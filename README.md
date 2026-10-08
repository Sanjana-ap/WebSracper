# Page Summarizer (AI Web Scraper)

Paste a URL, click **Summarize**, and get a short AI-written summary of the page.

- **Live app:** (https://web-sracper-puce.vercel.app/)

## How it works

1. The React frontend (`frontend/`) sends the URL to `POST /api/summarize`.
2. The Spring Boot backend (`backend/`) fetches the page and extracts the main text with Jsoup.
3. The text goes to the free Groq API (Llama 3.3 70B), which returns a summary.
4. The frontend shows the summary. A "Loading..." state shows while the backend works.

Safety details: only http/https URLs are accepted, private/internal addresses are blocked (SSRF guard), redirects are re-checked on every hop, and requests time out.

## Tech stack

- Backend: Java 17, Spring Boot 3.3, Jsoup, Spring `RestClient`
- Frontend: React 18 + Vite
- AI: Groq free tier (OpenAI-compatible API)

## Project structure

```
backend/    Spring Boot API (port 8080)
frontend/   React app (port 5173)
```

## Run locally

Prerequisites: Java 17+, Maven 3.9+, Node.js 18+, and a free Groq API key from https://console.groq.com/keys.

### 1. Backend

```bash
cd backend
cp .env.example .env
```

Open `backend/.env` and set your key:

```
GROQ_API_KEY=your_real_key_here
```

**The `.env` file must be inside the `backend/` folder** and you must start the backend from that folder. (You can also set `GROQ_API_KEY` as a normal environment variable instead.)

```bash
mvn spring-boot:run
```

The API runs at http://localhost:8080.

### 2. Frontend (in a second terminal)

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Open http://localhost:5173. `frontend/.env` only holds `VITE_API_URL=http://localhost:8080` (no secrets).

### Tests

```bash
cd backend && mvn test
```

## API

`POST /api/summarize`

```json
{ "url": "https://example.com/article" }
```

Success: `{ "url": "...", "title": "...", "summary": "..." }`
Error: `{ "error": "Readable message" }` with a 4xx/5xx status.

## Deploy

### Backend on Render

1. New > **Web Service**, connect this repo.
2. Set **Root Directory** to `backend` and **Runtime** to **Docker** (it uses `backend/Dockerfile`).
3. Add environment variables:
   - `GROQ_API_KEY` = your key
   - `CORS_ALLOWED_ORIGINS` = your Vercel URL (for example `https://your-app.vercel.app`, no trailing slash)
4. Deploy and copy the service URL.

### Frontend on Vercel

1. New Project > import this repo.
2. Set **Root Directory** to `frontend` (framework Vite is detected).
3. Add environment variable `VITE_API_URL` = your Render URL (no trailing slash).
4. Deploy, then add the Vercel URL to `CORS_ALLOWED_ORIGINS` on Render if you haven't yet.

Note: Render's free tier sleeps after inactivity, so the first request can take around a minute.

## Limitations

- Reads server-rendered HTML only. Pages that need JavaScript to show content, or that block bots, won't work.
- Long pages are truncated to about 12,000 characters before summarizing.
