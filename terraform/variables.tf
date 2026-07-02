variable "project" {
  description = "프로젝트 prefix"
  type        = string
  default     = "6pm"
}

variable "env" {
  description = "환경(dev/prod 등)"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS 리전 (서울)"
  type        = string
  default     = "ap-northeast-2"
}

variable "azs" {
  description = "사용할 가용영역 (멀티 AZ)"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "vpc_cidr" {
  description = "VPC CIDR"
  type        = string
  default     = "10.0.0.0/16"
}

# --- DB 자격증명: 평문 금지. tfvars / 환경변수(TF_VAR_db_password) / Secrets Manager로 주입 ---
variable "db_username" {
  type    = string
  default = "root"
}

variable "db_password" {
  type      = string
  sensitive = true
}

# --- MSA 서비스 목록(컨테이너 포트). ECR/ECS 가 이 map을 for_each로 순회 ---
variable "services" {
  description = "서비스명 => 컨테이너 포트"
  type = map(object({
    port = number
  }))
  default = {
    gateway-service      = { port = 8080 } # 외부 진입(ALB 연결)
    user-service         = { port = 8081 }
    feed-service         = { port = 8082 }
    ticketing-service    = { port = 8083 }
    order-service        = { port = 8084 }
    notification-service = { port = 8085 }
    aiops-service        = { port = 8086 }
    auth-service         = { port = 8087 }
    chat-service         = { port = 8088 }
  }
  # 참고: eureka/config는 운영에서 AWS Cloud Map(서비스디스커버리)/Parameter Store로 대체 가능
}

variable "container_cpu" {
  type    = number
  default = 256 # 0.25 vCPU
}

variable "container_memory" {
  type    = number
  default = 512 # MB
}
