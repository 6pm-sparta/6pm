provider "aws" {
  region = var.aws_region

  # 모든 리소스에 공통 태그 자동 부착(비용추적/관리)
  default_tags {
    tags = {
      Project   = var.project
      Env       = var.env
      ManagedBy = "terraform"
    }
  }
}
