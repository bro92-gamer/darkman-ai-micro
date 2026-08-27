#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SERVER_DIR="$ROOT/cloud-server"
MODE=${1:-help}
case "$MODE" in
  local)
    cd "$SERVER_DIR"
    docker compose up --build
    ;;
  render)
    echo "Push this repository to GitHub, then create a Render Docker Web Service using cloud-server/render.yaml."
    echo "The Render dashboard will prompt for GROQ_API_KEY, GEMINI_API_KEY, OPENROUTER_API_KEY, and optional GITHUB_TOKEN."
    ;;
  oracle|codespaces|replit)
    echo "For $MODE, copy cloud-server/.env.example to .env, set secrets, then run npm install && npm start inside cloud-server."
    ;;
  *)
    echo "Usage: $0 local|render|oracle|codespaces|replit"
    echo "local     Build and run with Docker Compose."
    echo "render    Print the Render deployment checklist."
    echo "oracle    Print the Oracle VM deployment checklist."
    echo "codespaces Print the Codespaces testing checklist."
    echo "replit   Print the Replit-style testing checklist."
    ;;
esac
