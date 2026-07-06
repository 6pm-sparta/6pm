param([string]$Tag = "latest")
$ErrorActionPreference = "Stop"
$REGION = "ap-northeast-2"
$SERVICES = @(
  "config-server", "eureka-server", "gateway-service",
  "user-service", "feed-service", "ticketing-service", "order-service",
  "notification-service", "aiops-service", "auth-service", "chat-service", "cs-service"
)
$ACCOUNT  = (aws sts get-caller-identity --query Account --output text)
$REGISTRY = "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com"
Write-Host "Registry: $REGISTRY  Tag: $Tag"
$pw = aws ecr get-login-password --region $REGION
docker login --username AWS --password $pw $REGISTRY
if ($LASTEXITCODE -ne 0) { throw "ECR login failed" }
foreach ($svc in $SERVICES) {
  $image = "${REGISTRY}/6pm/${svc}:${Tag}"
  Write-Host "==== [$svc] build and push -> $image ===="
  docker build -f docker/Dockerfile --build-arg SERVICE=$svc -t $image .
  if ($LASTEXITCODE -ne 0) { throw "build failed: $svc" }
  docker push $image
  if ($LASTEXITCODE -ne 0) { throw "push failed: $svc" }
}
Write-Host "DONE - 12 images pushed (tag=$Tag)"
