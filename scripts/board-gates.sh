#!/usr/bin/env bash
# Board gates for a cleanup close-out PR (working rule 55): rule 53 tracker size, rule 38 both files,
# rule 37 no added inline comments, ASCII under src, then the counts the board stamps. Usage: scripts/board-gates.sh [BASE]
set -u
BASE=${1:-origin/main}
T=ai_docs/reviews/2026-08-22-backend-cleanup-spike.md
H=ai_docs/reviews/2026-08-22-backend-cleanup-history.md
cd "$(git rev-parse --show-toplevel)" || exit 1
echo "base $BASE ($(git rev-parse --short "$BASE"))"
[ "$(wc -l < "$T")" -le "$(git show "$BASE:$T" | wc -l)" ] || echo "FAIL rule 53: tracker grew"
if ! git diff --quiet "$BASE" -- "$T"; then
  git diff --quiet "$BASE" -- "$H" && echo "FAIL rule 38: tracker changed, history did not"
fi
git diff "$BASE" -- src | grep -E '^\+[[:space:]]*//' && echo "FAIL rule 37: inline comment added"
git diff "$BASE" -- src | LC_ALL=C grep '^+.*[^ -~]' && echo "FAIL ASCII under src"
for g in '\*\*S-' '\*\*U-' '\*\*Bug #' '\*\*#[0-9]' '\*\*FE-' ''; do
  printf 'boxes %-12s %s\n' "$g" "$(grep -c "^- \[ \] $g" "$T")"
done
git grep -c '^[[:space:]]*//' -- src/main/java | awk -F: '{s+=$NF} END {print "comments main", s}'
git grep -c '^[[:space:]]*//' -- src/test/java | awk -F: '{s+=$NF} END {print "comments test", s}'
wc -l "$T" "$H"
printf 'numstat tracker (added deleted): '
git diff --numstat "$BASE" -- "$T" | awk '{print $1, $2}'
