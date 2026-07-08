# outputs.tf — apply 후 확인용 값
output "alb_dns_name" {
  description = "외부 접속 주소 (http://<이 값>)"
  value       = aws_lb.main.dns_name
}

output "rds_endpoint" {
  description = "RDS 접속 주소 (DB 8개 생성 시 사용)"
  value       = aws_db_instance.postgres.address
}

output "redis_endpoints" {
  value = { for k, c in aws_elasticache_cluster.redis : k => c.cache_nodes[0].address }
}

output "ecr_repository_urls" {
  description = "이미지 push 대상"
  value       = { for k, r in aws_ecr_repository.svc : k => r.repository_url }
}
