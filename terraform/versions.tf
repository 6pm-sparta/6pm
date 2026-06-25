# =====================================================================
# 6pm 운영 인프라 IaC (스켈레톤) — Terraform + AWS
# 목표 아키텍처: ALB → ECS Fargate(MSA) / RDS PostgreSQL(Writer+ReadReplica)
#               / ElastiCache(Redis) / MSK(Kafka) / ECR  (서울 ap-northeast-2)
# ⚠️ "작성(IaC 설계)"용 스켈레톤. 실제 배포(apply) 전 보강 필요(상태백엔드/시크릿/도메인 등).
#    검증:  terraform init  →  terraform validate
# =====================================================================
terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # 실제 운영 시: S3 + DynamoDB 원격 상태 백엔드 사용(스켈레톤이라 주석)
  # backend "s3" {
  #   bucket         = "6pm-terraform-state"
  #   key            = "prod/terraform.tfstate"
  #   region         = "ap-northeast-2"
  #   dynamodb_table = "6pm-terraform-lock"
  #   encrypt        = true
  # }
}
