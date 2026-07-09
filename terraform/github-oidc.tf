# =====================================================================
# github-oidc.tf — GitHub Actions ↔ AWS OIDC 연동 (장기 액세스키 없이 역할 위임)
# 파일명: terraform/github-oidc.tf
# 적용 후 output "github_actions_role_arn" 값을 GitHub Secret(AWS_DEPLOY_ROLE_ARN)에 넣는다.
# ★ 수정(2026-07): deploy.yml 의 SHA-핀 배포 스크립트가 describe/register-task-definition 을
#    호출하는데 그 권한이 없어 배포 스텝이 조용히 실패(continue-on-error)했었음.
#    → ecs:DescribeTaskDefinition, ecs:RegisterTaskDefinition 추가.
# =====================================================================

# 배포를 허용할 레포 (owner/repo). 예: "6pm-sparta/6pm"
variable "github_repo" {
  description = "GitHub owner/repo (CD 배포 허용 대상)"
  type        = string
  default     = "6pm-sparta/6pm"
}

# GitHub OIDC provider (계정에 하나만 있으면 됨)
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  # GitHub OIDC 인증서 지문(고정값). 최신 provider는 생략 가능하나 명시가 안전.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

# GitHub Actions가 assume 할 역할 (main 브랜치에서만 허용)
resource "aws_iam_role" "github_actions" {
  name = "${local.name_prefix}-github-actions"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn },
      Action    = "sts:AssumeRoleWithWebIdentity",
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" },
        # main 브랜치 push에서만 배포 허용 (원하면 :* 로 전체 허용)
        StringLike = { "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:ref:refs/heads/main" }
      }
    }]
  })
}

# ECR push + ECS 배포 권한 (최소권한)
resource "aws_iam_role_policy" "github_actions" {
  name = "deploy"
  role = aws_iam_role.github_actions.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
      {
        Effect = "Allow",
        Action = [
          "ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer",
        ],
        Resource = [for r in aws_ecr_repository.svc : r.arn]
      },
      # ★ ECS 배포: SHA-핀 스크립트가 쓰는 4개 액션 모두 필요.
      #   Describe/RegisterTaskDefinition 은 리소스 단위 제한이 안 돼 Resource="*".
      {
        Effect = "Allow",
        Action = [
          "ecs:UpdateService",
          "ecs:DescribeServices",
          "ecs:DescribeTaskDefinition",
          "ecs:RegisterTaskDefinition",
        ],
        Resource = "*"
      },
      { Effect = "Allow", Action = ["iam:PassRole"], Resource = [aws_iam_role.ecs_exec.arn, aws_iam_role.ecs_task.arn] },
    ]
  })
}

output "github_actions_role_arn" {
  description = "GitHub Secret(AWS_DEPLOY_ROLE_ARN)에 넣을 값"
  value       = aws_iam_role.github_actions.arn
}