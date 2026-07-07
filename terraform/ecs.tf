# ecs.tf — 클러스터 + 서비스 디스커버리(Cloud Map) + 태스크정의/서비스(12개 for_each)

# ── 서비스 간 이름으로 찾기 (localhost → <service>.6pm.local) ──
resource "aws_service_discovery_private_dns_namespace" "ns" {
  name = "6pm.local"
  vpc  = aws_vpc.main.id
}
resource "aws_service_discovery_service" "svc" {
  for_each = local.services
  name     = each.key
  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.ns.id
    routing_policy = "MULTIVALUE"
    dns_records {
      type = "A"
      ttl  = 10
    }
  }
  health_check_custom_config { failure_threshold = 1 }
}

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"
}

resource "aws_cloudwatch_log_group" "svc" {
  for_each          = local.services
  name              = "/ecs/6pm/${each.key}"
  retention_in_days = 7
}

# ── 각 서비스에 주입할 환경변수(env)를 동적으로 구성 ──
locals {
  redis_host = { for k, c in aws_elasticache_cluster.redis : k => c.cache_nodes[0].address }
  # Kafka(EC2)의 private IP:9092 — 앱은 PLAINTEXT로 그대로 연결(코드 변경 없음)
  msk_bootstrap = "${aws_instance.kafka.private_ip}:9092"

  svc_env = {
    for name, s in local.services : name => merge(
      {
        SPRING_PROFILES_ACTIVE  = var.env
        EUREKA_URI              = "http://eureka-server.6pm.local:8761/eureka/"
        KAFKA_BOOTSTRAP_SERVERS = local.msk_bootstrap
        DB_USERNAME             = var.db_username
        CONFIG_GIT_USERNAME     = var.config_git_username
        ZIPKIN_URI              = "http://zipkin.6pm.local:9411/api/v2/spans"
        REDIS_PASSWORD          = ""

        EUREKA_INSTANCE_HOSTNAME          = "${name}.6pm.local"
        EUREKA_INSTANCE_PREFER_IP_ADDRESS = "false"
      },
      # config-server 자신은 import 안 함
        name == "config-server" ? {} : { SPRING_CONFIG_IMPORT = "optional:configserver:http://config-server.6pm.local:8888" },
      # DB URL (db 있는 서비스만) — 예: user-service → USER_DB_URL
        s.db == null ? {} : { "${upper(replace(name, "-service", ""))}_DB_URL" = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${s.db}" },
      # Redis
        s.redis == "general"   ? { REDIS_GENERAL_HOST = local.redis_host["general"], REDIS_GENERAL_PORT = "6379", REDIS_GENERAL_PASSWORD = "" } : {},
        s.redis == "ticketing" ? { REDIS_TICKETING_HOST = aws_instance.kafka.private_ip, REDIS_TICKETING_PORT = "6379", REDIS_TICKETING_PASSWORD = "fandom_redis_pw" } : {},
    )
  }

  # 모든 태스크에 주입할 시크릿(Secrets Manager 참조)
  secret_refs = [
    { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.s["DB_PASSWORD"].arn },
    { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.s["JWT_SECRET"].arn },
    { name = "HMAC_SECRET_KEY", valueFrom = aws_secretsmanager_secret.s["HMAC_SECRET_KEY"].arn },
    { name = "GEMINI_API_KEY", valueFrom = aws_secretsmanager_secret.s["GEMINI_API_KEY"].arn },
    { name = "SLACK_WEBHOOK_URL", valueFrom = aws_secretsmanager_secret.s["SLACK_WEBHOOK_URL"].arn },
    { name = "CONFIG_GIT_TOKEN", valueFrom = aws_secretsmanager_secret.s["CONFIG_GIT_TOKEN"].arn },
  ]
}

resource "aws_ecs_task_definition" "svc" {
  for_each                 = local.services
  family                   = "${local.name_prefix}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.mem
  execution_role_arn       = aws_iam_role.ecs_exec.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name         = each.key
    image        = "${aws_ecr_repository.svc[each.key].repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = each.value.port }]
    environment  = [for k, v in local.svc_env[each.key] : { name = k, value = tostring(v) }]
    secrets      = local.secret_refs
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.svc[each.key].name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "svc" {
  for_each        = local.services
  name            = each.key
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.svc[each.key].arn
  desired_count   = each.value.desired
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = aws_service_discovery_service.svc[each.key].arn
  }

  # gateway만 ALB에 연결
  dynamic "load_balancer" {
    for_each = each.value.alb ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.gateway.arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }
  health_check_grace_period_seconds = each.value.alb ? 120 : null # 스프링 느린 기동

  # ⚠️ ECS는 서비스 간 기동 순서를 강제 못함. config-server/eureka가 늦으면
  #    다른 서비스가 잠깐 실패→재시작 반복하다 안정화됨(정상). 급하면 config/eureka만 먼저 apply.
}