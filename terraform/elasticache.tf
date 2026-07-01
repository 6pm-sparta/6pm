# =====================================================================
# ElastiCache Redis — A안: 2개(일반 / 티켓팅) 분리
# =====================================================================
resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project}-redis-subnet"
  subnet_ids = [for s in aws_subnet.private : s.id]
}

locals {
  redis_clusters = {
    general   = "일반: 캐시/세션/refresh-token"
    ticketing = "티켓팅: 대기열/분산락/카운터"
  }
}

resource "aws_elasticache_replication_group" "redis" {
  for_each                   = local.redis_clusters
  replication_group_id       = "${var.project}-redis-${each.key}"
  description                = each.value
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = "cache.t4g.micro"
  num_cache_clusters         = 2 # primary + replica
  automatic_failover_enabled = true
  multi_az_enabled           = true
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = false # 스켈레톤(앱 TLS 적용 시 true)
  tags                       = { Name = "${var.project}-redis-${each.key}" }
}
