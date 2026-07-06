# variables.tf (terraform/variables.tf)
variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "env" {
  type    = string
  default = "prod"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

# 이미지 태그 (GitHub Actions가 ECR에 push한 태그). 배포 시 최신 값으로.
variable "image_tag" {
  type    = string
  default = "latest"
}

# RDS
variable "db_username" {
  type    = string
  default = "root"
}
variable "db_password" {
  type      = string
  sensitive = true   # tfvars/환경변수로 주입 (커밋 금지)
}
variable "db_instance_class" {
  type    = string
  default = "db.t3.micro"   # 데모용 최소
}

# config-server가 Private config 레포를 읽기 위한 값
variable "config_git_username" {
  type    = string
}
variable "config_git_token" {
  type      = string
  sensitive = true
}

# 공유 시크릿
variable "jwt_secret" {
  type      = string
  sensitive = true
}
variable "hmac_secret" {
  type      = string
  sensitive = true
}
variable "gemini_api_key" {
  type      = string
  sensitive = true
  default   = ""
}
variable "slack_webhook_url" {
  type      = string
  sensitive = true
  default   = ""
}
