# =====================================================================
# RDS PostgreSQL — Writer + Read Replica (DB_DESIGN B안: 조회 분산)
# =====================================================================
resource "aws_db_subnet_group" "main" {
  name_prefix = "${var.project}-db-"
  subnet_ids  = [for s in aws_subnet.private : s.id]
  tags        = { Name = "${var.project}-db-subnet-group" }
}

resource "aws_db_instance" "writer" {
  identifier_prefix      = "${var.project}-pg-writer-"
  engine                 = "postgres"
  engine_version         = "17.2"
  instance_class         = "db.t4g.micro" # 스켈레톤 사이즈(운영은 부하 따라 상향)
  allocated_storage      = 20
  storage_encrypted      = true
  db_name                = "fandom_db"
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  multi_az               = true  # 쓰기 가용성
  skip_final_snapshot    = true  # 스켈레톤. 운영은 false + final_snapshot_identifier
  deletion_protection    = false # 운영은 true
  tags                   = { Name = "${var.project}-pg-writer" }
}

# 읽기 전용 복제본 (feed 타임라인 / ticketing 조회 분산)
resource "aws_db_instance" "reader" {
  identifier_prefix      = "${var.project}-pg-reader-"
  replicate_source_db    = aws_db_instance.writer.identifier
  instance_class         = "db.t4g.micro"
  vpc_security_group_ids = [aws_security_group.rds.id]
  skip_final_snapshot    = true
  tags                   = { Name = "${var.project}-pg-reader" }
}
