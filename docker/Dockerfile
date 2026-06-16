# syntax=docker/dockerfile:1
# =====================================================================
# 6pm 공통 멀티스테이지 Dockerfile (멀티모듈 모노레포용, 단 1개로 9개 서비스 커버)
# - 빌드 컨텍스트는 "레포 루트"여야 함 (:common, 루트 build.gradle 의존 때문)
# - 어떤 모듈을 빌드할지는 --build-arg SERVICE=<모듈명> 으로 지정
#   예) docker build -f docker/Dockerfile --build-arg SERVICE=user-service -t 6pm/user-service .
# =====================================================================

# ---- 1) build stage : gradle wrapper로 해당 모듈 bootJar 생성 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 캐시 최적화: 빌드 스크립트 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon || true

# 전체 소스 복사 후 지정 모듈만 bootJar (테스트는 CI에서 별도, 여기선 -x test)
COPY . .
ARG SERVICE
RUN ./gradlew :${SERVICE}:bootJar -x test --no-daemon

# ---- 2) runtime stage : JRE만 담은 가벼운 실행 이미지 ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 비루트 유저로 실행(보안)
RUN useradd -r -u 1001 spring
USER spring

ARG SERVICE
COPY --from=build /workspace/${SERVICE}/build/libs/*.jar app.jar

# 타임존은 compose env로도 덮어쓸 수 있음
ENV TZ=Asia/Seoul
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
