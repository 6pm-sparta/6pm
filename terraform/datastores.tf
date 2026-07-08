# datastores.tf — RDS(1개) · ElastiCache(2) · ECR
# (Kafka는 kafka.tf 에서 EC2로 운영 — 앱 변경 없음)

# ── ECR: 서비스별 이미지 저장소 ──
resource "aws_ecr_repository" "svc" {
  for_each     = local.services
  name         = "6pm/${each.key}"
  force_delete = true # 데모: destroy 시 이미지째 삭제
}

# ── RDS PostgreSQL 1개 (안에 DB 8개) ──
resource "aws_db_subnet_group" "rds" {
  name       = "db-${local.name_prefix}-subnets"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "postgres" {
  identifier             = "db-${local.name_prefix}" # RDS 식별자는 글자로 시작해야 함 → db-6pm-dev
  engine                 = "postgres"
  engine_version         = "17.9" # 리전에서 사용 가능한 버전으로 조정
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  db_name                = "postgres" # 기본 DB. 나머지 8개는 db-init로 생성(README 참고)
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.rds.name
  vpc_security_group_ids = [aws_security_group.data.id]
  publicly_accessible    = false
  skip_final_snapshot    = true
  deletion_protection    = false
  # ⚠️ user_db~cs_db 8개 DATABASE 생성은 README "DB 8개 생성" 단계로.
}

# ── ElastiCache Redis 2개 (general / ticketing) ──
resource "aws_elasticache_subnet_group" "redis" {
  name       = "redis-${local.name_prefix}-subnets"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_cluster" "redis" {
  for_each             = toset(["general", "ticketing"])
  cluster_id           = "redis-${local.name_prefix}-${each.key}" # 글자로 시작 → redis-6pm-dev-general
  engine               = "redis"
  node_type            = "cache.t3.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  subnet_group_name    = aws_elasticache_subnet_group.redis.name
  security_group_ids   = [aws_security_group.data.id]
  # 데모: AUTH 없이 SG로만 보호 → 앱의 REDIS_*_PASSWORD 는 빈 값.
}