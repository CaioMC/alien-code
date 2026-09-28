#!/usr/bin/env bash
# Sobe o opencode em modo servidor. A senha (OPENCODE_SERVER_PASSWORD) é gerada
# pelo Alien Server para cada Toca; sem ela o servidor não deve ser exposto.
set -euo pipefail

if [[ -z "${OPENCODE_SERVER_PASSWORD:-}" ]]; then
  echo "toca: OPENCODE_SERVER_PASSWORD não definida; recusando subir o opencode sem senha" >&2
  exit 1
fi

git config --global user.name "Alien Code"
git config --global user.email "toca@alien-code.local"
git config --global init.defaultBranch main

exec opencode serve --hostname 0.0.0.0 --port "${OPENCODE_PORT}"
