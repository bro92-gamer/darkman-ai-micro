# Deploy Darkman-AI Cloud for free

The cloud server is an ordinary Node.js/Express service. It listens on `0.0.0.0` and uses `PORT` from the hosting platform. Provider and GitHub secrets are environment variables; no domain purchase is required.

## Render: recommended public URL

Render can deploy the included Dockerfile from a GitHub repository and assigns a free `*.onrender.com` hostname. Free services are suitable for hobby or testing workloads but can spin down when idle and may have usage limits.[1]

1. Push the package to a GitHub repository, keeping `cloud-server/` as the service root.
2. In the Render dashboard, create a **New Web Service**, connect the repository, and choose **Docker**.
3. Set the Dockerfile path to `cloud-server/Dockerfile` and the Docker context to `cloud-server` if the dashboard asks for both.
4. Add at least one of `GROQ_API_KEY`, `GEMINI_API_KEY`, or `OPENROUTER_API_KEY` as a secret environment variable. Add `GITHUB_TOKEN` only if GitHub file commits are required.
5. Deploy and copy the assigned URL, for example `https://darkman-ai-cloud.onrender.com`.
6. In the Android app, set `BASE_URL` to that URL without a trailing slash. A prompt beginning with `cloud:` will call `${BASE_URL}/v1/ai/task`.

The included `render.yaml` can also be used through Render Blueprint deployment. Do not put actual secrets in that file.

## Oracle Cloud Always Free: persistent ARM VM

Oracle's Always Free documentation describes Ampere A1 Flex Arm compute as 1,500 OCPU hours and 9,000 GB hours per month, equivalent to 2 OCPUs and 12 GB memory for Always Free tenancies.[2] Availability can vary by home region; an out-of-host-capacity message means that the shape is temporarily unavailable rather than that the project is broken.

Create an Ubuntu or Oracle Linux Ampere A1 VM, open TCP port 3000 in the cloud security list and the VM firewall, then run:

```sh
git clone https://github.com/YOUR_NAME/YOUR_REPO.git
cd YOUR_REPO/cloud-server
cp .env.example .env
nano .env
npm install
npm start
```

For a persistent service, use a process manager such as `systemd` or `pm2`, and put Nginx/Caddy in front of Node.js for HTTPS. If you use a reverse proxy, set the Android `BASE_URL` to the public HTTPS URL rather than port 3000.

## GitHub Codespaces: development and temporary testing

Open the `cloud-server/` directory in a Codespace. The included `.devcontainer/devcontainer.json` installs dependencies and starts the server. Add secrets through Codespaces secrets or a local `.env` file that is not committed. Codespaces can forward port 3000 through the **Ports** panel or the documented forwarding command.[3] The forwarded URL is intended for testing and is not a permanent public production endpoint.

```sh
cd cloud-server
cp .env.example .env
npm install
npm start
```

## Replit-style free development

Import the `cloud-server/` directory into a Node.js Replit workspace, set the Run command to `npm start`, and add secrets in the workspace Secrets panel. Replit may sleep inactive workspaces and its current free offering can change, so use the assigned workspace URL only for development. Render or Oracle is a better choice when the Android client needs a stable `BASE_URL`.

## Provider environment variables

| Variable | Purpose |
| --- | --- |
| `PORT` | Hosting-provided listener port; defaults to 3000 locally. |
| `GROQ_API_KEY` | Server-side Groq key. |
| `GEMINI_API_KEY` | Server-side Google Gemini key. |
| `OPENROUTER_API_KEY` | Server-side OpenRouter key. |
| `GITHUB_TOKEN` | Optional token for repository metadata and content commits. |
| `CORS_ORIGIN` | Allowed browser origin; defaults to `*` for simple mobile access. |
| `APP_URL` | Referer used for OpenRouter requests. |

## Security and free-tier limits

Treat the cloud URL as an API endpoint, not a secret. Add authentication and rate limiting before exposing it to untrusted users. The server limits JSON body size, avoids logging credentials, and refuses GitHub operations when `GITHUB_TOKEN` is absent. Provider quotas, Render sleep behavior, Oracle capacity, and Replit policies are controlled by those providers and are not guaranteed by Darkman-AI.

## References

[1]: https://render.com/docs/free "Render free services"
[2]: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm "Oracle Always Free resources"
[3]: https://docs.github.com/en/codespaces/developing-in-a-codespace/forwarding-ports-in-your-codespace "GitHub Codespaces forwarding ports"
