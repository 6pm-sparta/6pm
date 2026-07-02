output "alb_dns_name" {
  description = "외부 진입 주소(게이트웨이)"
  value       = aws_lb.main.dns_name
}

output "rds_writer_endpoint" {
  value = aws_db_instance.writer.address
}

output "rds_reader_endpoint" {
  value = aws_db_instance.reader.address
}

output "redis_endpoints" {
  value = { for k, r in aws_elasticache_replication_group.redis : k => r.primary_endpoint_address }
}

output "kafka_bootstrap_brokers" {
  value = aws_msk_cluster.kafka.bootstrap_brokers
}

output "ecr_repository_urls" {
  value = { for k, r in aws_ecr_repository.service : k => r.repository_url }
}
