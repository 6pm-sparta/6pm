# =====================================================================
# ALB — 외부 진입점. gateway-service로 포워딩
# =====================================================================
resource "aws_lb" "main" {
  name_prefix        = "6pm-"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [for s in aws_subnet.public : s.id]
  tags               = { Name = "${var.project}-alb" }
}

resource "aws_lb_target_group" "gateway" {
  name_prefix = "6pmgw" # name_prefix 최대 6자
  port        = var.services["gateway-service"].port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip" # Fargate awsvpc

  health_check {
    path     = "/actuator/health"
    matcher  = "200"
    interval = 30
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }
  # 운영: 443(ACM 인증서) 리스너 추가 + 80→443 리다이렉트
}
