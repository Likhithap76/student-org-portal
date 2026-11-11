#!/usr/bin/env bash
# Builds once, then starts every service in the background.
# Logs: backend/logs/<service>.log   PIDs: backend/logs/<service>.pid   Stop with ./stop-all.sh
set -euo pipefail
cd "$(dirname "$0")"

SERVICES=(user-service event-service task-service content-service notification-service reporting-service ai-service gateway)
ENV_FILE="${ENV_FILE:-.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Create it with: cp .env.example .env  (then fill in the values)" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

required=(JWT_SECRET INTERNAL_SERVICE_TOKEN)
if [[ "${SPRING_PROFILES_ACTIVE:-}" == *supabase* ]]; then
  required+=(DB_URL DB_USER DB_PASSWORD)
else
  # Default "local" profile: shared file-based H2 in backend/data/
  export ORGPORTAL_DATA_DIR="${ORGPORTAL_DATA_DIR:-$PWD/data}"
  echo "Profile: local (H2 database in $ORGPORTAL_DATA_DIR)"
fi
for var in "${required[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "$var is not set in $ENV_FILE" >&2
    exit 1
  fi
done

mkdir -p logs
echo "Building (tests skipped)..."
./mvnw -q -B -DskipTests package


# Start services one at a time and wait for each to become healthy.
PORTS=(8081 8082 8083 8084 8085 8086 8087 8080)

for i in "${!SERVICES[@]}"; do
  svc="${SERVICES[$i]}"
  port="${PORTS[$i]}"
  pid_file="logs/$svc.pid"

  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    pid=$(cat "$pid_file")
    echo "$svc already running (pid $pid)"
  else
    jar=$(ls "$svc"/target/"$svc"-*.jar | head -n 1)
    nohup java -jar "$jar" > "logs/$svc.log" 2>&1 &
    pid=$!
    echo "$pid" > "$pid_file"
    echo "Starting $svc on port $port (pid $pid)..."
  fi

  ready=false

  for ((attempt=1; attempt<=90; attempt++)); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "ERROR: $svc exited. Check logs/$svc.log" >&2
      exit 1
    fi

    if curl -fsS --max-time 2 \
      "http://localhost:$port/actuator/health" 2>/dev/null \
      | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      ready=true
      break
    fi

    sleep 2
  done

  if [[ "$ready" != true ]]; then
    echo "ERROR: $svc did not become healthy. Check logs/$svc.log" >&2
    exit 1
  fi

  echo "$svc is healthy."
done

echo
echo "Health:  curl http://localhost:8080/actuator/health  (services: ports 8081-8087)"
echo "Swagger: http://localhost:8080/swagger-ui.html"
echo "Stop:    ./stop-all.sh"
