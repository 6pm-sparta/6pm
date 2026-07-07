# =====================================================================
# main.tf  (파일명 그대로: terraform/main.tf)
# terraform 버전/백엔드/프로바이더 + 서비스 목록(locals)
# =====================================================================
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.60" }
  }

  # state를 S3 + DynamoDB(lock)에 저장 (AWS 초기셋업에서 만든 것)
  # ⚠️ bucket/dynamodb_table 이름은 본인이 만든 실제 이름으로.
  backend "s3" {
    bucket         = "6pm-tfstate-jun19-2026"
    key            = "6pm/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "6pm-terraform-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Project = "6pm-fandom-sns"
      Env     = var.env
      Managed = "terraform"
    }
  }
}

# ── 12개 서비스 정의 (한 곳에서 관리 → ECR/ECS가 이 map을 for_each로 반복) ──
# db     : 이 서비스가 쓸 DB명 (없으면 null)
# redis  : "general" | "ticketing" | null
# alb    : ALB로 외부 노출 여부 (gateway만 true)
# desired: 실행 태스크 수
locals {
  services = {
    config-server        = { port = 8888, cpu = 256, mem = 512, desired = 1, db = null, redis = null, alb = false }
    eureka-server        = { port = 8761, cpu = 256, mem = 512, desired = 1, db = null, redis = null, alb = false }
    gateway-service      = { port = 8080, cpu = 512, mem = 1024, desired = 2, db = null, redis = "general", alb = true }
    user-service         = { port = 8081, cpu = 256, mem = 512, desired = 1, db = "user_db", redis = null, alb = false }
    feed-service         = { port = 8082, cpu = 256, mem = 512, desired = 1, db = "feed_db", redis = "general", alb = false }
    ticketing-service    = { port = 8083, cpu = 512, mem = 1024, desired = 1, db = "ticketing_db", redis = "ticketing", alb = false }
    order-service        = { port = 8084, cpu = 256, mem = 512, desired = 1, db = "order_db", redis = "ticketing", alb = false }
    notification-service = { port = 8085, cpu = 256, mem = 512, desired = 1, db = "notification_db", redis = null, alb = false }
    aiops-service        = { port = 8086, cpu = 256, mem = 512, desired = 1, db = "aiops_db", redis = null, alb = false }
    auth-service         = { port = 8087, cpu = 256, mem = 512, desired = 1, db = null, redis = "general", alb = false }
    chat-service         = { port = 8088, cpu = 256, mem = 512, desired = 1, db = "chat_db", redis = "general", alb = false }
    cs-service           = { port = 8089, cpu = 256, mem = 512, desired = 1, db = "cs_db", redis = null, alb = false }
  }

  # RDS 안에 만들 데이터베이스 8개 (db != null 인 서비스들)
  databases = [for s in local.services : s.db if s.db != null]

  name_prefix = "6pm-${var.env}"
}
