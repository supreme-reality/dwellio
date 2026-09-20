# Dwellio module: aurora
# Aurora PostgreSQL Serverless v2 in private DB subnets + Secrets Manager master user.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-aurora"
  subnet_ids = var.db_subnet_ids
  tags       = merge(var.tags, { Name = "${var.name_prefix}-aurora-subnets" })
}

resource "aws_rds_cluster" "this" {
  cluster_identifier = "${var.name_prefix}-aurora"
  engine             = "aurora-postgresql"
  engine_mode        = "provisioned"
  engine_version     = var.engine_version
  database_name      = var.database_name
  master_username    = var.master_username

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.db_security_group_id]

  storage_encrypted         = true
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = !var.deletion_protection
  final_snapshot_identifier = var.deletion_protection ? "${var.name_prefix}-final" : null

  serverlessv2_scaling_configuration {
    min_capacity = var.min_acu
    max_capacity = var.max_acu
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-aurora" })
}

resource "aws_rds_cluster_instance" "this" {
  identifier         = "${var.name_prefix}-aurora-1"
  cluster_identifier = aws_rds_cluster.this.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.this.engine
  engine_version     = aws_rds_cluster.this.engine_version

  tags = merge(var.tags, { Name = "${var.name_prefix}-aurora-1" })
}

# App-facing secret stub (connection hints). Values filled out-of-band / linked later.
resource "aws_secretsmanager_secret" "app_db" {
  name                    = "${var.name_prefix}/db"
  description             = "Dwellio DB connection stub (host/user/db); password via RDS-managed secret"
  recovery_window_in_days = 0

  tags = merge(var.tags, { Name = "${var.name_prefix}-db-secret" })
}

resource "aws_secretsmanager_secret_version" "app_db" {
  secret_id = aws_secretsmanager_secret.app_db.id
  secret_string = jsonencode({
    username = var.master_username
    dbname   = var.database_name
    host     = aws_rds_cluster.this.endpoint
    port     = 5432
    engine   = "postgres"
    note     = "Use RDS master user secret ARN for password; rotate out-of-band as needed"
  })
}
