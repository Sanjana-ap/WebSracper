import { useState } from "react";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

function Summary({ text }) {
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  const intro = lines.filter((l) => !l.startsWith("- "));
  const points = lines.filter((l) => l.startsWith("- ")).map((l) => l.slice(2));
  return (
    <>
      {intro.map((p, i) => (
        <p key={i} className="lede">{p}</p>
      ))}
      {points.length > 0 && (
        <ul>
          {points.map((p, i) => (
            <li key={i}>{p}</li>
          ))}
        </ul>
      )}
    </>
  );
}

export default function App() {
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setError("");
    setResult(null);
    try {
      const res = await fetch(`${API_URL}/api/summarize`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || "Request failed.");
      setResult(data);
    } catch (err) {
      setError(err instanceof TypeError ? "Couldn't reach the server. Is the backend running?" : err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main>
      <h1>Page Summarizer</h1>
      <p className="sub">Paste a link to an article or web page and get the short version.</p>

      <form onSubmit={handleSubmit}>
        <input
          type="url"
          required
          placeholder="https://example.com/article"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          aria-label="Web page URL"
        />
        <button type="submit" disabled={loading}>
          {loading ? "Loading..." : "Summarize"}
        </button>
      </form>

      {loading && (
        <div className="status" role="status">
          <span className="spinner" aria-hidden="true" />
          Loading... reading the page and writing your summary. The first request can take up to a minute while the free server wakes up.
        </div>
      )}

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}

      {result && (
        <article className="card">
          <h2>{result.title}</h2>
          <a href={result.url} target="_blank" rel="noopener noreferrer">
            {result.url}
          </a>
          <Summary text={result.summary} />
        </article>
      )}
    </main>
  );
}
