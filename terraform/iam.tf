# iam.tf — ECS 실행/태스크 역할
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# 실행 역할: ECR pull, 로그, Secrets 읽기
resource "aws_iam_role" "ecs_exec" {
  name               = "${local.name_prefix}-ecs-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}
resource "aws_iam_role_policy_attachment" "ecs_exec_managed" {
  role       = aws_iam_role.ecs_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}
resource "aws_iam_role_policy" "ecs_exec_secrets" {
  name = "read-secrets"
  role = aws_iam_role.ecs_exec.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect   = "Allow",
      Action   = ["secretsmanager:GetSecretValue"],
      Resource = [for s in aws_secretsmanager_secret.s : s.arn]
    }]
  })
}

# 태스크 역할: 앱이 AWS API 호출용 (feed S3 등). Kafka는 EC2 PLAINTEXT라 IAM 불필요.
resource "aws_iam_role" "ecs_task" {
  name               = "${local.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}
resource "aws_iam_role_policy" "ecs_task_app" {
  name = "app-s3"
  role = aws_iam_role.ecs_task.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      { Effect = "Allow", Action = ["s3:*"], Resource = ["*"] }   # feed S3 (데모: 넓게. 운영은 좁히기)
    ]
  })
}