# =====================================================================
# 네트워크: VPC + public/private 서브넷(멀티 AZ) + IGW + NAT
# =====================================================================
locals {
  az_index = { for idx, az in var.azs : az => idx }
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "${var.project}-vpc" }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project}-igw" }
}

# public: ALB / NAT 용
resource "aws_subnet" "public" {
  for_each                = local.az_index
  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, each.value)     # 10.0.0.0/20, 10.0.16.0/20
  map_public_ip_on_launch = true
  tags                    = { Name = "${var.project}-public-${each.key}", Tier = "public" }
}

# private: ECS / RDS / Redis / Kafka 용
resource "aws_subnet" "private" {
  for_each          = local.az_index
  vpc_id            = aws_vpc.main.id
  availability_zone = each.key
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, each.value + 8)       # 10.0.128.0/20, 10.0.144.0/20
  tags              = { Name = "${var.project}-private-${each.key}", Tier = "private" }
}

# NAT 1개(스켈레톤/비용절감). 운영 고가용은 AZ별 NAT 권장.
resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = { Name = "${var.project}-nat-eip" }
}

resource "aws_nat_gateway" "nat" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[var.azs[0]].id
  depends_on    = [aws_internet_gateway.igw]
  tags          = { Name = "${var.project}-nat" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }
  tags = { Name = "${var.project}-public-rt" }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.nat.id
  }
  tags = { Name = "${var.project}-private-rt" }
}

resource "aws_route_table_association" "public" {
  for_each       = aws_subnet.public
  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  for_each       = aws_subnet.private
  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}
