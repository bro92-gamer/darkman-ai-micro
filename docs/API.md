# Darkman-AI Cloud API

The server returns JSON and is intended for the Android client or a trusted operator. It does not require a domain. Replace `BASE_URL` with the free host URL assigned by Render, Oracle, Codespaces, or another provider.

## Health

`GET /health`

```json
{ "status": "ok", "service": "darkman-ai-cloud", "time": "2026-08-25T00:00:00.000Z" }
```

`GET /v1/ai/providers` lists the supported providers and whether a server-side key is configured. It never returns a key.

## AI chat

`POST /v1/ai/chat` is for a normal provider-backed prompt.

```json
{
  "provider": "Groq",
  "prompt": "Explain this compiler error in three sentences."
}
```

`provider` may be `Groq`, `Google Gemini`, or `OpenRouter`; an unknown or omitted value defaults to Groq. The server reads the matching environment variable and returns:

```json
{
  "provider": "Groq",
  "result": "..."
}
```

## Complex AI task

`POST /v1/ai/task` is the endpoint used by Android prompts beginning with `cloud:`. It accepts a larger task body and a descriptive `taskType`.

```json
{
  "provider": "Groq",
  "taskType": "code_analysis",
  "task": "Review this Kotlin function for memory and concurrency issues: ..."
}
```

The server wraps the task in a careful system instruction and returns:

```json
{
  "provider": "Groq",
  "taskType": "code_analysis",
  "result": "..."
}
```

## GitHub operations

`POST /v1/github/operation` requires the server-side `GITHUB_TOKEN`. The Android app does not send this token. Supported actions are `repository`, `read_file`, and `put_file`.

Repository metadata:

```json
{ "action": "repository", "owner": "example", "repo": "project" }
```

Read a file:

```json
{ "action": "read_file", "owner": "example", "repo": "project", "path": "README.md" }
```

Create or update a file, which creates a Git commit through the GitHub Contents API:

```json
{
  "action": "put_file",
  "owner": "example",
  "repo": "project",
  "path": "notes/darkman.txt",
  "message": "Add Darkman-AI notes",
  "content": "Generated content",
  "branch": "main"
}
```

The server retrieves the existing SHA when updating a file and base64-encodes the new content. Keep this endpoint private or add authentication/rate limiting before public exposure.

## Error format

Errors use a non-sensitive JSON message:

```json
{ "error": "Groq is not configured on the cloud server" }
```

A `404` indicates an unknown route. A `400` indicates validation failure, missing configuration, an upstream provider error, or a GitHub API error. Upstream detail is truncated to avoid returning large payloads.

## Curl smoke tests

```sh
curl "$BASE_URL/health"
curl "$BASE_URL/v1/ai/providers"
curl -X POST "$BASE_URL/v1/ai/chat" \
  -H 'content-type: application/json' \
  -d '{"provider":"Groq","prompt":"Say hello from Darkman-AI"}'
```

[1]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/POST "HTTP POST method"
[2]: https://docs.github.com/en/rest/repos/contents "GitHub repository contents API"

## References

[1]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/POST "HTTP POST method"
[2]: https://docs.github.com/en/rest/repos/contents "GitHub repository contents API"
