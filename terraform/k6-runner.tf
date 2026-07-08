# =====================================================================
# k6-runner.tf — VPC 내부 부하 러너 (private subnet, SSM 접속)
# 목적: order-service 같은 "내부 서비스"를 gateway 우회로 직접 부하테스트.
#       (내부 서비스는 private라 노트북에서 못 붙음 → k6도 VPC 안에서 실행해야 함)
# 사용 후: 비용 아끼려면 이 리소스만 destroy 하거나 count=0 으로 꺼둘 것.
# ※ data.aws_ami.al2023 은 kafka.tf 에 이미 선언돼 있으므로 여기서 재선언하지 않고 재사용.
# =====================================================================

# k6 러너 전용 SG (아웃바운드만; 인바운드 불필요 — SSM은 아웃바운드로 동작)
resource "aws_security_group" "k6" {
  name_prefix = "${local.name_prefix}-k6-"
  vpc_id      = aws_vpc.main.id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "${local.name_prefix}-k6" }
}

# ★ 핵심: ECS 서비스들이 k6 러너의 요청을 받도록 인바운드 허용 (앱 포트 범위만)
resource "aws_security_group_rule" "ecs_from_k6" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8089
  protocol                 = "tcp"
  security_group_id        = aws_security_group.ecs.id       # 대상: 서비스들이 속한 SG
  source_security_group_id = aws_security_group.k6.id        # 출처: k6 러너
  description              = "k6 부하 러너 → 내부 서비스 직접 호출 허용(부하테스트용)"
}

# SSM 접속용 IAM
resource "aws_iam_role" "k6" {
  name = "${local.name_prefix}-k6-role"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}
resource "aws_iam_role_policy_attachment" "k6_ssm" {
  role       = aws_iam_role.k6.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_instance_profile" "k6" {
  name = "${local.name_prefix}-k6-profile"
  role = aws_iam_role.k6.name
}

resource "aws_instance" "k6" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = "t3.medium"   # k6는 CPU를 쓰므로 medium
  subnet_id                   = aws_subnet.private[0].id
  vpc_security_group_ids      = [aws_security_group.k6.id]
  associate_public_ip_address = false
  iam_instance_profile        = aws_iam_instance_profile.k6.name

  metadata_options { http_tokens = "optional" }

  # 부팅 시 k6 설치 (공식 바이너리, 버전 고정)
  user_data = <<-EOF
    #!/bin/bash
    set -e
    dnf install -y git tar
    K6_VER=v0.52.0
    curl -sL https://github.com/grafana/k6/releases/download/$${K6_VER}/k6-$${K6_VER}-linux-amd64.tar.gz -o /tmp/k6.tgz
    tar xzf /tmp/k6.tgz -C /tmp
    install -m 0755 /tmp/k6-$${K6_VER}-linux-amd64/k6 /usr/local/bin/k6
  EOF

  lifecycle { ignore_changes = [ami] }  # apply마다 AMI 교체로 인한 재생성 방지
  tags = { Name = "${local.name_prefix}-k6-runner" }
}

output "k6_runner_instance_id" {
  description = "SSM으로 접속할 k6 러너 인스턴스 ID"
  value       = aws_instance.k6.id
}