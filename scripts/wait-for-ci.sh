#!/bin/bash
# Wait until the CI workflow for this commit is green. A release must not publish first.
set -euo pipefail
SHA="${GITHUB_SHA:-}"
if [ -z "$SHA" ]; then
  echo "GITHUB_SHA is missing."
  exit 1
fi
echo "Waiting for CI on $SHA"
for _try in $(seq 1 45); do
  line=$(gh run list --repo "$GITHUB_REPOSITORY" --commit "$SHA" --workflow ci.yml --limit 10 \
    --json status,conclusion,databaseId,event \
    --jq '[.[] | select(.event=="push" or .event=="workflow_dispatch")] | .[0] | "\(.status) \(.conclusion) \(.databaseId)"')
  if [ -z "$line" ] || [ "$line" = "null null null" ] || [ "$line" = "  " ]; then
    echo "CI has not started yet."
    sleep 30
    continue
  fi
  status=${line%% *}
  rest=${line#* }
  conclusion=${rest%% *}
  if [ "$status" = "completed" ] && [ "$conclusion" = "success" ]; then
    echo "CI is green ($line)."
    exit 0
  fi
  if [ "$status" = "completed" ] && [ "$conclusion" != "success" ] && [ "$conclusion" != "null" ]; then
    echo "CI finished as $conclusion ($line)."
    exit 1
  fi
  echo "CI is $status."
  sleep 30
done
echo "CI did not go green in time."
exit 1
