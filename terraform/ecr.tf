# =====================================================================
# ECR: 서비스별 컨테이너 이미지 저장소 (services map 순회)
# =====================================================================
resource "aws_ecr_repository" "service" {
  for_each     = var.services
  name         = "${var.project}/${each.key}"
  force_delete = true # 스켈레톤(이미지 있어도 삭제 허용). 운영은 제거.

  image_scanning_configuration {
    scan_on_push = true
  }
  tags = { Name = each.key }
}
