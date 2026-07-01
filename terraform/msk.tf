# =====================================================================
# MSK (Managed Kafka) — 이벤트(ticketing/order/notification/feed)
# =====================================================================
resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${var.project}-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = length(var.azs) # AZ당 1개 = 2 (서브넷 수의 배수여야 함)

  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = [for s in aws_subnet.private : s.id]
    security_groups = [aws_security_group.kafka.id]
    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }
  tags = { Name = "${var.project}-kafka" }
}
