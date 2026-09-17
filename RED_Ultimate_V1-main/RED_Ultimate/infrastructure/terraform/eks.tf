# VPC for EKS Cluster
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${var.cluster_name}-vpc"
  cidr = var.vpc_cidr

  azs             = slice(data.aws_availability_zones.available.names, 0, 3)
  private_subnets = var.private_subnet_cidrs
  public_subnets  = var.public_subnet_cidrs

  enable_nat_gateway     = true
  single_nat_gateway     = false
  enable_dns_hostnames   = true
  enable_dns_support     = true

  tags = {
    Environment = var.environment
    Project     = "RED Ultimate"
  }
}

# EKS Cluster
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = var.cluster_name
  cluster_version = "1.29"

  vpc_id                         = module.vpc.vpc_id
  subnet_ids                     = module.vpc.private_subnets
  cluster_endpoint_private_access = true
  cluster_endpoint_public_access  = true
  cluster_endpoint_public_access_cidrs = ["0.0.0.0/0"]

  cluster_addons = {
    vpc-cni = {
      most_recent = true
    }
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    aws-ebs-csi-driver = {
      most_recent = true
    }
  }

  eks_managed_node_groups = {
    general = {
      name           = "general"
      instance_types = ["m6i.xlarge"]
      capacity_type  = "ON_DEMAND"

      min_size     = 3
      max_size     = 20
      desired_size = 5

      labels = {
        Environment = var.environment
        NodeGroup   = "general"
      }

      tags = {
        Environment = var.environment
      }
    }

    compute = {
      name           = "compute"
      instance_types = ["c6i.2xlarge"]
      capacity_type  = "SPOT"

      min_size     = 0
      max_size     = 10
      desired_size = 2

      labels = {
        Environment = var.environment
        NodeGroup   = "compute"
        Workload    = "batch"
      }

      taints = [{
        key    = "workload"
        value  = "batch"
        effect = "NO_SCHEDULE"
      }]

      tags = {
        Environment = var.environment
      }
    }

    memory = {
      name           = "memory"
      instance_types = ["r6i.xlarge"]
      capacity_type  = "ON_DEMAND"

      min_size     = 2
      max_size     = 8
      desired_size = 3

      labels = {
        Environment = var.environment
        NodeGroup   = "memory"
        Workload    = "database"
      }

      tags = {
        Environment = var.environment
      }
    }
  }

  tags = {
    Environment = var.environment
    Project     = "RED Ultimate"
  }
}

# IAM Roles for Service Accounts (IRSA)
module "irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name_prefix = "${var.cluster_name}-"

  role_policies = {
    EBS_CSI_Driver = {
      policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
    }
    CloudWatch_Metrics = {
      policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
    }
    XRay_Daemon = {
      policy_arn = "arn:aws:iam::aws:policy/AWSXRayDaemonWriteAccess"
    }
  }

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = {
        red-backend = ["red-sovereign:red-backend"]
        monitoring  = ["monitoring:prometheus", "monitoring:grafana"]
      }
    }
  }
}

# RDS PostgreSQL
resource "aws_db_instance" "postgres" {
  identifier             = "${var.cluster_name}-postgres"
  engine                 = "postgres"
  engine_version         = "16.4"
  instance_class         = var.db_instance_class
  allocated_storage      = var.db_allocated_storage
  max_allocated_storage  = 500
  storage_encrypted      = true
  storage_type           = "gp3"
  db_name                = "red_prod"
  username               = "red_user"
  password               = random_password.db_password.result
  parameter_group_name   = "default.postgres16"
  backup_retention_period = 30
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:04:00-sun:05:00"
  skip_final_snapshot    = false
  deletion_protection    = true
  publicly_accessible    = false
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name

  tags = {
    Environment = var.environment
    Project     = "RED Ultimate"
  }
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.cluster_name}-db-subnet"
  subnet_ids = module.vpc.private_subnets
  tags = {
    Environment = var.environment
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.cluster_name}-rds-sg"
  description = "Security group for RDS"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    security_groups = [module.eks.cluster_security_group_id]
  }

  tags = {
    Environment = var.environment
  }
}

# ElastiCache Redis
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${var.cluster_name}-redis"
  engine                     = "redis"
  engine_version             = "7.2"
  node_type                  = var.redis_node_type
  number_cache_clusters      = var.redis_num_cache_nodes
  port                       = 6379
  parameter_group_name       = "default.redis7"
  automatic_failover_enabled = true
  multi_az_enabled           = true
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_password.result
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.redis.id]

  tags = {
    Environment = var.environment
    Project     = "RED Ultimate"
  }
}

resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.cluster_name}-redis-subnet"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "redis" {
  name        = "${var.cluster_name}-redis-sg"
  description = "Security group for ElastiCache"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [module.eks.cluster_security_group_id]
  }

  tags = {
    Environment = var.environment
  }
}

# DocumentDB (MongoDB compatible)
resource "aws_docdb_cluster" "mongodb" {
  cluster_identifier      = "${var.cluster_name}-mongodb"
  engine                  = "docdb"
  engine_version          = "5.0.0"
  master_username         = "red_user"
  master_password         = random_password.mongodb_password.result
  backup_retention_period = 30
  preferred_backup_window = "03:00-04:00"
  preferred_maintenance_window = "sun:04:00-sun:05:00"
  skip_final_snapshot     = false
  deletion_protection     = true
  storage_encrypted       = true
  vpc_security_group_ids  = [aws_security_group.docdb.id]
  db_subnet_group_name    = aws_docdb_subnet_group.main.name

  tags = {
    Environment = var.environment
    Project     = "RED Ultimate"
  }
}

resource "aws_docdb_cluster_instance" "mongodb" {
  count              = 2
  identifier         = "${var.cluster_name}-mongodb-${count.index}"
  cluster_identifier = aws_docdb_cluster.mongodb.id
  instance_class     = var.mongodb_instance_class
  engine             = aws_docdb_cluster.mongodb.engine
  engine_version     = aws_docdb_cluster.mongodb.engine_version
}

resource "aws_docdb_subnet_group" "main" {
  name       = "${var.cluster_name}-docdb-subnet"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "docdb" {
  name        = "${var.cluster_name}-docdb-sg"
  description = "Security group for DocumentDB"
  vpc_id      = module.vpc.vpc_id

  ingress {
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [module.eks.cluster_security_group_id]
  }

  tags = {
    Environment = var.environment
  }
}

# Random passwords
resource "random_password" "db_password" {
  length  = 32
  special = false
}

resource "random_password" "redis_password" {
  length  = 32
  special = false
}

resource "random_password" "mongodb_password" {
  length  = 32
  special = false
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}