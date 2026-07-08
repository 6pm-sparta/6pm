# secrets.tf — Secrets Manager (비밀값을 ECS에 안전하게 주입)
locals {
  secret_map = {
    DB_PASSWORD       = var.db_password
    JWT_SECRET        = var.jwt_secret
    HMAC_SECRET_KEY   = var.hmac_secret
    GEMINI_API_KEY    = var.gemini_api_key
    SLACK_WEBHOOK_URL = var.slack_webhook_url
    CONFIG_GIT_TOKEN  = var.config_git_token
  }
}

resource "aws_secretsmanager_secret" "s" {
  for_each                = local.secret_map
  name                    = "${local.name_prefix}/${each.key}"
  recovery_window_in_days = 0 # 데모: 즉시 삭제 가능
}

resource "aws_secretsmanager_secret_version" "v" {
  for_each      = local.secret_map
  secret_id     = aws_secretsmanager_secret.s[each.key].id
  secret_string = each.value
}
