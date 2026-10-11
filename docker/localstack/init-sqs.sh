#!/bin/bash
# Cria as 4 filas FIFO da saga e as DLQs (maxReceiveCount = 6),
# espelhando o Terraform do mechanical-hub-infra (infrastructure.md secao 3).
set -euo pipefail

REGION=us-east-1
ACCOUNT=000000000000

create_fifo_with_dlq() {
  local name="$1"
  local dlq="${name}-dlq.fifo"
  local queue="${name}.fifo"

  awslocal sqs create-queue --region "$REGION" --queue-name "$dlq" \
    --attributes FifoQueue=true,MessageRetentionPeriod=1209600 >/dev/null

  local dlq_arn="arn:aws:sqs:${REGION}:${ACCOUNT}:${dlq}"
  awslocal sqs create-queue --region "$REGION" --queue-name "$queue" \
    --attributes "{\"FifoQueue\":\"true\",\"VisibilityTimeout\":\"30\",\"ReceiveMessageWaitTimeSeconds\":\"20\",\"MessageRetentionPeriod\":\"345600\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${dlq_arn}\\\",\\\"maxReceiveCount\\\":\\\"6\\\"}\"}" >/dev/null

  echo "Fila ${queue} criada (DLQ ${dlq})"
}

create_fifo_with_dlq mechanical-hub-orchestrator-events
create_fifo_with_dlq mechanical-hub-orders-commands
create_fifo_with_dlq mechanical-hub-stock-commands
create_fifo_with_dlq mechanical-hub-billing-commands
