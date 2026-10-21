# =============================================================================
# Leitura dos states remotos — somente leitura
# =============================================================================
#
# Nao provisiona nada: resolve, em um `terraform init` + `terraform apply` sem
# recursos, os valores que os repositorios de infraestrutura publicam como
# contrato para o pipeline do orchestrator:
#
#   mechanical-hub-infra     -> eks_cluster_name, ecr_repository_urls["orchestrator"],
#                               sqs_queue_urls / sqs_dlq_urls, otlp_http_endpoint
#   mechanical-hub-database  -> mongodb_host, mongodb_port, mongodb_replica_set,
#                               mongodb_database, mongodb_secret_name,
#                               mongodb_namespace
#
# Sem bloco `backend` de proposito: o state deste diretorio e efemero e local
# ao runner (so contem os data sources).
#
# Uso:
#   terraform init -input=false
#   terraform apply -auto-approve -input=false \
#     -var="state_bucket=mechanical-hub-tfstate-<conta>"
#   terraform output -raw eks_cluster_name

terraform {
  required_version = ">= 1.7.0"
}

variable "state_bucket" {
  description = "Bucket S3 que hospeda os states dos repositorios de infraestrutura."
  type        = string

  validation {
    condition     = length(var.state_bucket) > 0
    error_message = "state_bucket e obrigatorio (ex.: mechanical-hub-tfstate-<conta>)."
  }
}

variable "state_region" {
  description = "Regiao do bucket de state."
  type        = string
  default     = "us-east-1"
}

variable "infra_state_key" {
  description = "Chave do state do mechanical-hub-infra dentro do bucket."
  type        = string
  default     = "mechanical-hub-infra/terraform.tfstate"
}

variable "database_state_key" {
  description = "Chave do state do mechanical-hub-database dentro do bucket."
  type        = string
  default     = "mechanical-hub-database/terraform.tfstate"
}

data "terraform_remote_state" "infra" {
  backend = "s3"

  config = {
    bucket = var.state_bucket
    key    = var.infra_state_key
    region = var.state_region
  }
}

data "terraform_remote_state" "database" {
  backend = "s3"

  config = {
    bucket = var.state_bucket
    key    = var.database_state_key
    region = var.state_region
  }
}

locals {
  service  = "orchestrator"
  infra    = data.terraform_remote_state.infra.outputs
  database = data.terraform_remote_state.database.outputs
  queues   = local.infra.sqs_queue_urls
  dlqs     = local.infra.sqs_dlq_urls
}

# Se uma chave sumir do repositorio produtor, o apply falha aqui com o nome do
# valor ausente, antes de qualquer deploy contra um contrato quebrado.

output "eks_cluster_name" {
  value = local.infra.eks_cluster_name
}

output "ecr_repository_url" {
  value = local.infra.ecr_repository_urls[local.service]
}

# ── Filas (o orchestrator consome os eventos e publica os 3 comandos) ────────

output "orchestrator_events_queue_url" {
  value = local.queues["orchestrator-events"]
}

output "orchestrator_events_dlq_url" {
  value = local.dlqs["orchestrator-events"]
}

output "orders_commands_queue_url" {
  value = local.queues["orders-commands"]
}

output "stock_commands_queue_url" {
  value = local.queues["stock-commands"]
}

output "billing_commands_queue_url" {
  value = local.queues["billing-commands"]
}

# ── MongoDB (usuario e senha ficam so no Secret do Kubernetes) ───────────────

output "mongodb_host" {
  value = local.database.mongodb_host
}

output "mongodb_port" {
  value = local.database.mongodb_port
}

output "mongodb_replica_set" {
  value = local.database.mongodb_replica_set
}

output "mongodb_database" {
  value = local.database.mongodb_database
}

output "mongodb_secret_name" {
  value = local.database.mongodb_secret_name
}

output "mongodb_namespace" {
  value = local.database.mongodb_namespace
}

# Opcional: null com a observabilidade desligada no mechanical-hub-infra. Ler
# com `terraform output -json` e tratar a ausencia.
output "otlp_http_endpoint" {
  value = try(local.infra.otlp_http_endpoint, null)
}
