resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = var.vpc_name
  }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${var.vpc_name}-igw"
  }
}

# Public Subnets - used by ALB and Jenkins EC2
resource "aws_subnet" "public" {
  count                   = length(var.public_subnets)
  cidr_block              = var.public_subnets[count.index]
  vpc_id                  = aws_vpc.this.id
  map_public_ip_on_launch = true
  availability_zone       = var.azs[count.index]

  tags = {
    Name                                        = "${var.vpc_name}-public-subnet-${count.index + 1}"
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

# Private Subnets - used by EKS nodes, RDS, SonarQube EC2
resource "aws_subnet" "private" {
  count             = length(var.private_subnets)
  cidr_block        = var.private_subnets[count.index]
  vpc_id            = aws_vpc.this.id
  availability_zone = var.azs[count.index]

  tags = {
    Name                                        = "${var.vpc_name}-private-subnet-${count.index + 1}"
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

# NAT Gateway
resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "${var.vpc_name}-nat-eip"
  }
}

resource "aws_nat_gateway" "natgw" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  # PROD NOTE: This is a single NAT Gateway (cost-effective for dev).
  # In production use one NAT GW per AZ so a single AZ failure doesn't cut outbound traffic for all private subnets. Add count = length(var.azs) and reference aws_subnet.public[count.index].id.

  tags = {
    Name = "${var.vpc_name}-natgw"
  }

  depends_on = [aws_internet_gateway.igw]
}

# Route tables for public subnets
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${var.vpc_name}-public-rt"
  }
}

resource "aws_route" "public" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.igw.id
}

resource "aws_route_table_association" "public" {
  count          = length(var.public_subnets)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Route tables for private subnets
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id
  tags = {
    Name = "${var.vpc_name}-private-rt"
  }
}

resource "aws_route" "private" {
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.natgw.id
  depends_on             = [aws_nat_gateway.natgw]
}

resource "aws_route_table_association" "private" {
  count          = length(var.private_subnets)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}