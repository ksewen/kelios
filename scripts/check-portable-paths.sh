#!/bin/sh
# Active source, examples and procedures must not require a developer workstation.
set -eu
cd "$(dirname "$0")/.."
if git grep -n -I -E '/Users/[^/[:space:]]+/|/home/[^/[:space:]]+/(IdeaProjects|Projects|workspace|Documents)/|/opt/homebrew/|IdeaProjects|[A-Za-z]:\\Users\\' -- \
  ':!docs/history/**' ':!docs/plans/**' ':!scripts/check-portable-paths.sh'; then
  echo 'Developer-local path in active source or documentation; use a portable path.' >&2
  exit 1
else
  result=$?
  [ "$result" -eq 1 ] || exit "$result"
fi
echo 'No developer-local paths in tracked active source or documentation.'
