#!/usr/bin/env bash
# Pede ao Codex uma revisão somente-leitura do projeto ou das mudanças desde um commit.
# Uso: scripts/codex-review.sh            -> revisa o app inteiro
#      scripts/codex-review.sh HEAD~1     -> revisa só o diff desde HEAD~1
set -euo pipefail
cd "$(dirname "$0")/.."

if [ $# -gt 0 ]; then
  scope="Revise APENAS as mudanças de \`git diff $1\` (rode esse comando para ver), olhando o resto do código só para contexto."
else
  scope="Revise o app inteiro em app/src/main."
fi

codex exec --sandbox read-only --skip-git-repo-check \
  "Siga as instruções de AGENTS.md. $scope" \
  | tee "${CODEX_REVIEW_OUT:-/dev/null}"
