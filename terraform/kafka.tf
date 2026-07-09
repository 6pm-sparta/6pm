# kafka.tf — EC2 1대에 Kafka(KRaft, PLAINTEXT)를 Docker로 실행
# + SSM 접속 + psql (RDS DB 8개 생성용). 앱 변경 0.

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
}

# ── EC2가 SSM(브라우저 접속)으로 붙을 수 있게 IAM ──
resource "aws_iam_role" "kafka" {
  name = "${local.name_prefix}-kafka-role"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}
resource "aws_iam_role_policy_attachment" "kafka_ssm" {
  role       = aws_iam_role.kafka.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_instance_profile" "kafka" {
  name = "${local.name_prefix}-kafka-profile"
  role = aws_iam_role.kafka.name
}

resource "aws_instance" "kafka" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = "t3.medium"
  subnet_id                   = aws_subnet.private[0].id
  vpc_security_group_ids      = [aws_security_group.data.id]
  associate_public_ip_address = false
  iam_instance_profile        = aws_iam_instance_profile.kafka.name # SSM 접속용

  metadata_options {
    http_tokens = "optional"
  }

  # 부팅: Docker+Kafka 실행, psql(postgresql 클라이언트) 설치
  user_data = <<-EOF
    #!/bin/bash
    set -e
    dnf install -y docker postgresql15    # postgresql15 = psql 클라이언트
    systemctl enable --now docker
    PRIVATE_IP=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)
    docker run -d --restart always --name kafka -p 9092:9092 -p 9093:9093 \
      -e KAFKA_NODE_ID=1 \
      -e KAFKA_PROCESS_ROLES=broker,controller \
      -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
      -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
      -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://$PRIVATE_IP:9092 \
      -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
      -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
      -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
      -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
      -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
      -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
      -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
      apache/kafka:3.7.0
    docker run -d --restart always --name redis -p 6379:6379 redis:7 --requirepass fandom_redis_pw
  EOF

  tags = { Name = "${local.name_prefix}-kafka" }
  lifecycle {
    ignore_changes = [ami]           # ★추가: apply마다 새 AMI로 인스턴스가 통째로 교체되는 것 방지
  }
}
