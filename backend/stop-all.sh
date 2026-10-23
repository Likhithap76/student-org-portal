#!/usr/bin/env bash
# Stops the services started by run-all.sh.
set -uo pipefail
cd "$(dirname "$0")"

shopt -s nullglob
pid_files=(logs/*.pid)
if [[ ${#pid_files[@]} -eq 0 ]]; then
  echo "Nothing to stop."
  exit 0
fi

for pid_file in "${pid_files[@]}"; do
  svc=$(basename "$pid_file" .pid)
  pid=$(cat "$pid_file")
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    for _ in $(seq 1 20); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.5
    done
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
    echo "Stopped $svc (pid $pid)"
  else
    echo "$svc was not running"
  fi
  rm -f "$pid_file"
done
