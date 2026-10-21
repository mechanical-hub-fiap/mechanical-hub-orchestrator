#!/usr/bin/env bash
# Smoke test do mechanical-hub-orchestrator contra instancias ja no ar.
#
# Uso: ci/smoke-test.sh <base-url> [<base-url>...]
#      (ex.: http://localhost:8080; no pipeline, uma URL por pod via
#      `kubectl port-forward`, porque qualquer replica pode consumir a sonda)
#
# Variavel opcional:
#   EVENTS_QUEUE_URL  URL de mechanical-hub-orchestrator-events.fifo. Com ela, o
#                     teste publica uma sonda pela AWS CLI (no LocalStack, exportar
#                     tambem AWS_ENDPOINT_URL=http://localhost:4566) e confere
#                     que ela foi consumida e gravada no MongoDB.
#
# Confere:
#   1. readiness UP em cada instancia (inclui o MongoDB: application.yml);
#   2. GET /transaction/{orderId} inexistente = 404 (leitura no MongoDB);
#   3. metricas da saga expostas em /actuator/prometheus;
#   4. (com EVENTS_QUEUE_URL) SQS de ponta a ponta: um `stockConsumed` de uma
#      ordem que nao existe e descartado pela guarda de passo — consome da fila,
#      grava a transaction_history (DISCARDED_STEP_GUARD) e apaga a mensagem,
#      sem criar saga nem publicar comando. Fica so a linha de auditoria, com o
#      orderId da sonda.
set -euo pipefail

[ "$#" -ge 1 ] || { echo "informe ao menos uma URL base, ex.: http://localhost:8080" >&2; exit 2; }
BASE_URLS=("$@")
PROBE_TIMEOUT_SECONDS="${PROBE_TIMEOUT_SECONDS:-90}"

fail() {
  echo "FALHOU: $*" >&2
  exit 1
}

status_of() {
  curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@"
}

# Soma, entre todas as instancias, o contador de descartes de `stockConsumed`.
discarded_probes() {
  local total=0 url value
  for url in "${BASE_URLS[@]}"; do
    value="$(curl -fsS --max-time 10 "$url/actuator/prometheus" \
      | grep '^saga_messages_total{' \
      | grep 'type="stockConsumed"' \
      | grep 'outcome="DISCARDED_STEP_GUARD"' \
      | awk '{ sum += $NF } END { printf "%d", sum }')" || value=0
    total=$((total + ${value:-0}))
  done
  echo "$total"
}

uuid() {
  cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())'
}

for url in "${BASE_URLS[@]}"; do
  echo "→ [$url] readiness (aplicacao + MongoDB)"
  readiness="$(curl -fsS --max-time 10 "$url/actuator/health/readiness")" || fail "readiness nao respondeu em $url"
  grep -q '"status":"UP"' <<< "$readiness" || fail "readiness diferente de UP em $url: $readiness"
done

PRIMARY_URL="${BASE_URLS[0]}"

echo "→ GET /transaction/{orderId} inexistente"
[ "$(status_of "$PRIMARY_URL/transaction/$(uuid)")" = "404" ] || fail "ordem inexistente deveria retornar 404"

echo "→ GET /transaction/{orderId} com id invalido"
[ "$(status_of "$PRIMARY_URL/transaction/nao-e-uuid")" = "400" ] || fail "orderId invalido deveria retornar 400"

echo "→ /actuator/prometheus"
metrics="$(curl -fsS --max-time 10 "$PRIMARY_URL/actuator/prometheus")" || fail "prometheus nao respondeu"
for metric in outbox_oldest_pending_age_seconds jvm_memory_used_bytes; do
  grep -q "^${metric}" <<< "$metrics" || fail "metrica ${metric} ausente"
done

if [ -n "${EVENTS_QUEUE_URL:-}" ]; then
  command -v aws > /dev/null || fail "AWS CLI necessaria para a sonda SQS"

  before="$(discarded_probes)"
  order_id="$(uuid)"
  event_id="$(uuid)"
  body="$(printf '{"eventId":"%s","transactionId":"%s","orderId":"%s","type":"stockConsumed","version":1,"timestamp":"%s"}' \
    "$event_id" "$(uuid)" "$order_id" "$(date -u +%Y-%m-%dT%H:%M:%SZ)")"

  echo "→ sonda SQS: stockConsumed da ordem inexistente $order_id"
  aws sqs send-message \
    --queue-url "$EVENTS_QUEUE_URL" \
    --message-body "$body" \
    --message-group-id "$order_id" \
    --message-deduplication-id "$event_id" > /dev/null || fail "nao foi possivel publicar a sonda na fila de eventos"

  deadline=$((SECONDS + PROBE_TIMEOUT_SECONDS))
  until [ "$(discarded_probes)" -gt "$before" ]; do
    [ "$SECONDS" -lt "$deadline" ] || fail "sonda nao consumida em ${PROBE_TIMEOUT_SECONDS}s (confira acesso ao SQS pela LabRole e os logs do pod)"
    sleep 3
  done
  echo "  sonda consumida e descartada pela guarda de passo (DISCARDED_STEP_GUARD)"
fi

echo "Smoke test OK em ${BASE_URLS[*]}"
