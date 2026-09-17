# NGINX Ingress Controller
resource "helm_release" "nginx_ingress" {
  name       = "nginx-ingress"
  repository = "https://kubernetes.github.io/ingress-nginx"
  chart      = "ingress-nginx"
  version    = "4.11.0"
  namespace  = "ingress-nginx"
  create_namespace = true

  values = [
    <<-EOT
controller:
  replicaCount: 3
  service:
    type: LoadBalancer
    annotations:
      service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
      service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
      service.beta.kubernetes.io/aws-load-balancer-backend-protocol: "tcp"
  resources:
    requests:
      cpu: 100m
      memory: 128Mi
    limits:
      cpu: 500m
      memory: 512Mi
  autoscaling:
    enabled: true
    minReplicas: 3
    maxReplicas: 10
    targetCPUUtilizationPercentage: 70
    targetMemoryUtilizationPercentage: 80
  metrics:
    enabled: true
    serviceMonitor:
      enabled: true
  config:
    use-forwarded-headers: "true"
    compute-full-forwarded-for: "true"
    use-proxy-protocol: "false"
    proxy-body-size: "50m"
    proxy-read-timeout: "300"
    proxy-send-timeout: "300"
  admissionWebhooks:
    enabled: true
  publishService:
    enabled: true
  enableCustomResources: true
defaultBackend:
  enabled: true
  resources:
    requests:
      cpu: 10m
      memory: 20Mi
    limits:
      cpu: 100m
      memory: 128Mi
EOT
  ]

  depends_on = [module.eks]
}

# Cert Manager
resource "helm_release" "cert_manager" {
  name       = "cert-manager"
  repository = "https://charts.jetstack.io"
  chart      = "cert-manager"
  version    = "1.14.0"
  namespace  = "cert-manager"
  create_namespace = true

  values = [
    <<-EOT
installCRDs: true
replicaCount: 2
resources:
  requests:
    cpu: 10m
    memory: 64Mi
  limits:
    cpu: 100m
    memory: 128Mi
prometheus:
  enabled: true
  servicemonitor:
    enabled: true
cainjector:
  resources:
    requests:
      cpu: 10m
      memory: 64Mi
    limits:
      cpu: 100m
      memory: 128Mi
webhook:
  resources:
    requests:
      cpu: 10m
      memory: 64Mi
    limits:
      cpu: 100m
      memory: 128Mi
EOT
  ]

  depends_on = [module.eks]
}

# Cluster Issuer for Let's Encrypt
resource "kubernetes_manifest" "cluster_issuer" {
  manifest = {
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata = {
      name = "letsencrypt-prod"
    }
    spec = {
      acme = {
        server = "https://acme-v02.api.letsencrypt.org/directory"
        email  = "admin@${var.domain_name}"
        privateKeySecretRef = {
          name = "letsencrypt-prod-key"
        }
        solvers = [{
          http01 = {
            ingress = {
              class = "nginx"
            }
          }
        }]
      }
    }
  }

  depends_on = [helm_release.cert_manager]
}

# External DNS
resource "helm_release" "external_dns" {
  name       = "external-dns"
  repository = "https://kubernetes-sigs.github.io/external-dns/"
  chart      = "external-dns"
  version    = "1.14.0"
  namespace  = "external-dns"
  create_namespace = true

  values = [
    <<-EOT
provider: cloudflare
cloudflare:
  apiToken: "${var.cloudflare_api_token}"
sources:
  - ingress
  - service
domainFilters:
  - "${var.domain_name}"
policy: sync
interval: 1m
txtOwnerId: "${var.cluster_name}"
resources:
  requests:
    cpu: 10m
    memory: 64Mi
  limits:
    cpu: 100m
    memory: 128Mi
EOT
  ]

  depends_on = [module.eks]
}

# Prometheus Stack
resource "helm_release" "prometheus" {
  name       = "prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = "61.0.0"
  namespace  = "monitoring"
  create_namespace = true

  values = [
    <<-EOT
prometheus:
  prometheusSpec:
    replicaCount: 2
    retention: 30d
    retentionSize: 50GB
    storageSpec:
      volumeClaimTemplate:
        spec:
          storageClassName: gp3
          resources:
            requests:
              storage: 100Gi
    resources:
      requests:
        cpu: 500m
        memory: 2Gi
      limits:
        cpu: 2000m
        memory: 4Gi
    serviceMonitorSelectorNilUsesHelmValues: false
    podMonitorSelectorNilUsesHelmValues: false
    ruleSelectorNilUsesHelmValues: false
grafana:
  enabled: true
  replicas: 2
  persistence:
    enabled: true
    storageClassName: gp3
    size: 10Gi
  adminPassword: "${random_password.grafana_password.result}"
  sidecar:
    datasources:
      enabled: true
    dashboards:
      enabled: true
  resources:
    requests:
      cpu: 100m
      memory: 128Mi
    limits:
      cpu: 500m
      memory: 512Mi
alertmanager:
  enabled: true
  replicas: 2
  persistence:
    enabled: true
    storageClassName: gp3
    size: 10Gi
  resources:
    requests:
      cpu: 10m
      memory: 64Mi
    limits:
      cpu: 100m
      memory: 128Mi
EOT
  ]

  depends_on = [module.eks]
}

resource "random_password" "grafana_password" {
  length  = 24
  special = false
}

# AWS Load Balancer Controller
resource "helm_release" "aws_lb_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.7.0"
  namespace  = "kube-system"

  values = [
    <<-EOT
clusterName: "${var.cluster_name}"
serviceAccount:
  create: false
  name: aws-load-balancer-controller
region: "${var.aws_region}"
vpcId: "${module.vpc.vpc_id}"
image:
  repository: 602401143452.dkr.ecr.${var.aws_region}.amazonaws.com/amazon/aws-load-balancer-controller
resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 500m
    memory: 256Mi
EOT
  ]

  depends_on = [module.eks, module.irsa]
}

# Metrics Server
resource "helm_release" "metrics_server" {
  name       = "metrics-server"
  repository = "https://kubernetes-sigs.github.io/metrics-server/"
  chart      = "metrics-server"
  version    = "3.12.0"
  namespace  = "kube-system"

  values = [
    <<-EOT
replicaCount: 2
resources:
  requests:
    cpu: 10m
    memory: 64Mi
  limits:
    cpu: 100m
    memory: 128Mi
args:
  - --kubelet-insecure-tls
  - --kubelet-preferred-address-types=InternalIP,ExternalIP,Hostname
  - --metric-resolution=30s
EOT
  ]

  depends_on = [module.eks]
}

# AWS EBS CSI Driver (already added as EKS addon, but ensure IRSA)
resource "kubernetes_service_account" "ebs_csi_driver" {
  metadata {
    name      = "ebs-csi-controller-sa"
    namespace = "kube-system"
    annotations = {
      "eks.amazonaws.com/role-arn" = module.irsa.iam_role_arns["EBS_CSI_Driver"]
    }
  }
  depends_on = [module.irsa]
}