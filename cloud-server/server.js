import express from "express";
import cors from "cors";

const app = express();
const port = Number(process.env.PORT || 3000);
const maxBody = process.env.MAX_BODY_BYTES || "1mb";

app.disable("x-powered-by");
app.use(cors({ origin: process.env.CORS_ORIGIN || "*" }));
app.use(express.json({ limit: maxBody }));

const providerNames = ["Groq", "Google Gemini", "OpenRouter"];
const keys = {
  Groq: process.env.GROQ_API_KEY || "",
  "Google Gemini": process.env.GEMINI_API_KEY || "",
  OpenRouter: process.env.OPENROUTER_API_KEY || ""
};

function providerOrDefault(value) {
  return providerNames.includes(value) ? value : "Groq";
}
function requireText(value, name, max = 20000) {
  if (typeof value !== "string" || !value.trim()) throw new Error(`${name} is required`);
  if (value.length > max) throw new Error(`${name} is too long`);
  return value.trim();
}
function jsonError(res, status, message) { return res.status(status).json({ error: message }); }

function isSimplePrompt(prompt) {
  return prompt.length < 180 && !/(why|compare|debug|architect|analy[sz]e|خطة|حلل)/i.test(prompt);
}
async function callProvider(provider, prompt, system = "You are Darkman-AI, a concise and practical assistant.", requestedModel = "auto") {
  const key = keys[provider];
  if (!key) throw new Error(`${provider} is not configured on the cloud server`);
  if (provider === "Google Gemini") {
    const configuredMain = process.env.GEMINI_MODEL || "gemini-2.0-flash";
    const configuredLite = process.env.GEMINI_LITE_MODEL || "gemini-2.0-flash-lite";
    const model = requestedModel === "lite" || (requestedModel === "auto" && isSimplePrompt(prompt)) ? configuredLite : configuredMain;
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`;
    const response = await fetch(url, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ contents: [{ parts: [{ text: `${system}\n\n${prompt}` }] }] })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(`Gemini HTTP ${response.status}: ${JSON.stringify(data).slice(0, 300)}`);
    return data?.candidates?.[0]?.content?.parts?.[0]?.text || JSON.stringify(data);
  }
  const url = provider === "Groq" ? "https://api.groq.com/openai/v1/chat/completions" : "https://openrouter.ai/api/v1/chat/completions";
  const model = provider === "Groq" ? (process.env.GROQ_MODEL || "llama-3.3-70b-versatile") : (process.env.OPENROUTER_MODEL || "openai/gpt-4o-mini");
  const headers = { "content-type": "application/json", authorization: `Bearer ${key}` };
  if (provider === "OpenRouter") { headers["HTTP-Referer"] = process.env.APP_URL || "https://darkman-ai.local"; headers["X-Title"] = "Darkman-AI"; }
  const response = await fetch(url, { method: "POST", headers, body: JSON.stringify({ model, messages: [{ role: "system", content: system }, { role: "user", content: prompt }], temperature: 0.2 }) });
  const data = await response.json();
  if (!response.ok) throw new Error(`${provider} HTTP ${response.status}: ${JSON.stringify(data).slice(0, 300)}`);
  return data?.choices?.[0]?.message?.content || JSON.stringify(data);
}

app.get("/", (_req, res) => res.json({ name: "Darkman-AI Cloud", status: "ok", docs: "/v1/ai/providers" }));
app.get("/health", (_req, res) => res.json({ status: "ok", service: "darkman-ai-cloud", time: new Date().toISOString() }));
app.get("/v1/ai/providers", (_req, res) => res.json({ providers: providerNames.map(name => ({ name, configured: Boolean(keys[name]) })), default: "Groq" }));

app.post("/v1/ai/chat", async (req, res) => {
  try {
    const prompt = requireText(req.body?.prompt, "prompt");
    const provider = providerOrDefault(req.body?.provider);
    const modelMode = typeof req.body?.modelMode === "string" ? req.body.modelMode : "auto";
    const result = await callProvider(provider, prompt, undefined, modelMode);
    return res.json({ provider, modelMode, result });
  } catch (error) { return jsonError(res, 400, error.message); }
});

app.post("/v1/ai/task", async (req, res) => {
  try {
    const task = requireText(req.body?.task, "task", 60000);
    const provider = providerOrDefault(req.body?.provider);
    const taskType = typeof req.body?.taskType === "string" ? req.body.taskType.slice(0, 40) : "advanced_reasoning";
    const system = `You are Darkman-AI Cloud handling a ${taskType} task. Be explicit about assumptions, return useful structured text, and do not claim to have executed actions you did not execute.`;
    const result = await callProvider(provider, task, system);
    return res.json({ provider, taskType, result });
  } catch (error) { return jsonError(res, 400, error.message); }
});

function githubHeaders() {
  if (!process.env.GITHUB_TOKEN) throw new Error("GITHUB_TOKEN is not configured");
  return { accept: "application/vnd.github+json", authorization: `Bearer ${process.env.GITHUB_TOKEN}`, "X-GitHub-Api-Version": "2022-11-28" };
}
async function githubRequest(path, options = {}) {
  const response = await fetch(`https://api.github.com${path}`, { ...options, headers: { ...githubHeaders(), ...(options.headers || {}) } });
  const data = await response.json();
  if (!response.ok) throw new Error(`GitHub HTTP ${response.status}: ${data?.message || JSON.stringify(data).slice(0, 300)}`);
  return data;
}
app.post("/v1/github/operation", async (req, res) => {
  try {
    const action = requireText(req.body?.action, "action", 30);
    const owner = requireText(req.body?.owner, "owner", 100);
    const repo = requireText(req.body?.repo, "repo", 100);
    if (action === "repository") return res.json(await githubRequest(`/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`));
    if (action === "read_file") {
      const path = requireText(req.body?.path, "path", 500);
      const data = await githubRequest(`/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/contents/${path.split("/").map(encodeURIComponent).join("/")}`);
      return res.json({ ...data, decodedContent: data.encoding === "base64" ? Buffer.from(data.content, "base64").toString("utf8") : data.content });
    }
    if (action === "put_file") {
      const path = requireText(req.body?.path, "path", 500);
      const message = requireText(req.body?.message, "message", 200);
      const content = requireText(req.body?.content, "content", 200000);
      const branch = typeof req.body?.branch === "string" ? req.body.branch : undefined;
      let sha;
      try { const current = await githubRequest(`/repos/${owner}/${repo}/contents/${path.split("/").map(encodeURIComponent).join("/")}${branch ? `?ref=${encodeURIComponent(branch)}` : ""}`); sha = current.sha; } catch (error) { if (!String(error.message).includes("404")) throw error; }
      const body = { message, content: Buffer.from(content, "utf8").toString("base64"), ...(sha ? { sha } : {}), ...(branch ? { branch } : {}) };
      return res.json(await githubRequest(`/repos/${owner}/${repo}/contents/${path.split("/").map(encodeURIComponent).join("/")}`, { method: "PUT", headers: { "content-type": "application/json" }, body: JSON.stringify(body) }));
    }
    return jsonError(res, 400, "action must be repository, read_file, or put_file");
  } catch (error) { return jsonError(res, 400, error.message); }
});

app.use((_req, res) => jsonError(res, 404, "Not found"));
app.listen(port, "0.0.0.0", () => console.log(`Darkman-AI Cloud listening on ${port}`));
