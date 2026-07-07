# 🚀 6pm 서비스 개발 시작 가이드 (모든 서비스 공통 핸드오프)

> 최종수정: 2026-06-29
> **이 문서대로 따라 하면 본인 서비스 코딩을 바로 시작할 수 있습니다.** 빠진 게 있으면 인프라 담당에게 바로 알려주세요.
> ℹ️ 2026-06-29 재구성(#207): **DB-per-service(서비스별 독립 DB 인스턴스) + public 스키마**, **Redis 2개 분리**로 변경. 자세한 근거는 `db.md`/`decisions.md`(D11).

---

## 0. 한눈에 — 5분 시작
1. `git pull` → `docker compose down -v` (구 볼륨 정리) → `cp .env.example .env` 후 값 채우기
2. `docker compose --profile db-<내서비스> --profile redis-general up -d` (내 DB + Redis-일반)
   - 예) user/auth/gateway: `--profile db-user --profile redis-general`
   - 예) ticketing/order: `--profile db-ticketing --profile db-order --profile redis-ticketing`
   - 전체: `docker compose --profile infra up -d`
3. IntelliJ: **config → eureka → gateway → 본인 서비스** 순서로 실행
4. 본인 `build.gradle`·`application.yml`은 아래 3·4장 표준대로 (이미 와이어링돼 있으면 그대로)
5. 컨트롤러 반환은 `ApiResponse<T>`, 예외는 `CustomException`, 엔티티는 `BaseEntity` 상속 (5장)

---

## 1. 포트 · DB · 인프라 한눈 표
| 서비스 | 서비스 포트 | DB 인스턴스(로컬 포트) / DB명 | profile |
|---|---|---|---|
| gateway-service | 8080 | - (DB 없음) | - |
| user-service | 8081 | 5432 / `user_db` | `db-user` |
| feed-service | 8082 | 5433 / `feed_db` | `db-feed` |
| ticketing-service | 8083 | 5434 / `ticketing_db` | `db-ticketing` |
| order-service | 8084 | 5435 / `order_db` | `db-order` |
| notification-service | 8085 | 5436 / `notification_db` | `db-notification` |
| aiops-service | 8086 | 5437 / `aiops_db` | `db-aiops` |
| auth-service | 8087 | - (DB 없음, Redis만) | - |
| chat-service | 8088 | 5438 / `chat_db` | `db-chat` |
| eureka-server | 8761 | - | - |
| config-server | 8888 | - | - |

| 인프라 | 포트 | profile | 로컬 접속 |
|---|---|---|---|
| Redis-일반 | 6379 | `redis-general` | 비번 `fandom_redis_pw` (auth/gateway/user/feed/chat) |
| Redis-티켓팅 | 6380 | `redis-ticketing` | 비번 `fandom_redis_pw` (ticketing/order) |
| Kafka (KRaft) | 9092 | `infra` | `localhost:9092` |
| Zipkin | 9411 | `infra` | http://localhost:9411 |
| Grafana / Prometheus / Loki / Alertmanager / kafka-ui | 3000 / 9090 / 3100 / 9093 / 8090 | `o11y` | |

> DB명 = 서비스명 일치(public 스키마 사용, 별도 스키마 지정 없음). auth/gateway는 DB 미사용.

---

## 2. 로컬 환경 실행
**(1) 인프라 기동** — 처음이면 구 볼륨 정리 후 기동
```powershell
docker compose down -v                        # 최초 1회: 구 단일 postgres 볼륨 정리
docker compose --profile db-user --profile redis-general up -d   # 내 서비스만 (예: user)
docker compose --profile infra up -d          # 또는 전체: DB 7 + Redis 2 + kafka + zipkin
# (관측까지) docker compose --profile o11y up -d
```
> 내게 필요한 profile만 띄우면 된다. 예: 티켓팅 계열 `--profile db-ticketing --profile db-order --profile redis-ticketing`.
**(2) `.env` 설정** — 레포 루트 `.env.example` 복사 후 값 채움 (`.env`는 커밋 금지)
```
# 공용 계정(docker 컨테이너 생성용) — 현재 계정 미분리 단계
DB_USERNAME=root
DB_PASSWORD=password
REDIS_PASSWORD=fandom_redis_pw
# 서비스별 접속 변수 (본인 것으로! currentSchema 없음, DB명=서비스명)
USER_DB_URL=jdbc:postgresql://localhost:5432/user_db
USER_DB_USERNAME=root
USER_DB_PASSWORD=password
# Redis는 본인 용도 선택: 일반=6379(REDIS_GENERAL_*) / 티켓팅=6380(REDIS_TICKETING_*)
REDIS_GENERAL_HOST=localhost
REDIS_GENERAL_PORT=6379
REDIS_GENERAL_PASSWORD=fandom_redis_pw
```
**(3) IntelliJ가 `.env`를 읽게 하기** — **EnvFile 플러그인** 설치 → 각 서비스 Run/Debug Configuration → "EnvFile" 탭 → `.env` 추가 ✅
**(4) 실행 순서**: `config-server`(8888) → `eureka-server`(8761) → `gateway-service`(8080) → 본인 서비스
> **config-first**: 다른 서비스가 config-server(8888 직접 URL)에서 공유 설정(hmac 등)을 받아와야 하므로 config를 먼저. (config·eureka는 서로 독립이라 순서 강제는 아니지만 관례상 config 먼저)
> 본인 서비스만 개발/테스트할 땐 eureka·config·gateway 없이 단독 실행도 가능(단 서비스 간 호출은 안 됨).

---

## 3. build.gradle 표준 (본인 서비스)
> 루트가 plugin/Java21/Spring Cloud BOM/lombok/test 관리. 각 서비스는 **의존성만**. 본인 도메인에 필요한 것만 주석 해제.
```gradle
dependencies {
    implementation project(':common')                                                   // ApiResponse/BaseEntity/예외
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly    'org.postgresql:postgresql'

    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    implementation 'io.micrometer:micrometer-tracing-bridge-brave'                       // 분산추적
    implementation 'io.zipkin.reporter2:zipkin-reporter-brave'
    implementation 'io.micrometer:micrometer-registry-prometheus'                        // 메트릭

    // === 본인 도메인에 필요한 것만 주석 해제 ===
    // implementation 'org.springframework.boot:spring-boot-starter-data-redis'          // 캐시/세션
    // implementation 'org.redisson:redisson-spring-boot-starter:3.35.0'                 // 분산락/대기열 (ticketing)
    // implementation 'org.springframework.kafka:spring-kafka'                           // 이벤트
    // implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'         // 서비스 간 호출
    // implementation 'org.springframework.boot:spring-boot-starter-validation'          // @Valid
}
```

## 4. application.yml 표준 (본인 서비스)
```yaml
server:
  port: 8083                                   # ← 본인 포트(1장 표)

spring:
  application:
    name: ticketing-service                    # ← 본인 서비스명
  config:
    import: "optional:configserver:http://localhost:8888"
  datasource:
    driver-class-name: org.postgresql.Driver
    url: ${TICKETING_DB_URL}                  # .env (서비스별 변수, currentSchema 없음)
    username: ${TICKETING_DB_USERNAME}
    password: ${TICKETING_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true                       # default_schema 없음 (public 사용)
    show-sql: true

logging:                                       # 로그 표준(ECS JSON) — logging-standard.md
  file:
    name: ./logs/ticketing-service.log
  structured:
    format:
      file: ecs

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${random.uuid}

management:                                    # 관측(메트릭/추적) 자동
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  metrics:
    tags:
      application: ${spring.application.name}
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

---

## 5. 공통 규약 (★ 반드시 지킬 것 — 코드리뷰 체크 항목)
**패키지 구조** (feed-service 기준 레이어드, base `com.fandom.<service>`):
```
application/   (서비스 로직, event)
domain/        (entity, repository, exception)
global/        (config, annotation, aspect, constant)
infra/         (client, redis, kafka, util)
presentation/  (controller, dto)
```

**① 응답은 `ApiResponse<T>`**
```java
@PostMapping
public ApiResponse<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest req) {
    return ApiResponse.created(ticketService.reserve(req));   // 생성=created, 조회=success(data), 빈성공=success()
}
```

**② 예외는 `CustomException` + 도메인 `ErrorCode` enum** (일반 Exception 금지)
```java
// domain/exception/TicketErrorCode.java
@Getter
@RequiredArgsConstructor
public enum TicketErrorCode implements com.fandom.common.exception.ErrorCode {
    SEAT_ALREADY_TAKEN(HttpStatus.CONFLICT, "이미 선점된 좌석입니다."),
    SOLD_OUT(HttpStatus.CONFLICT, "매진되었습니다."),
    LOCK_TIMEOUT(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요.");
    private final HttpStatus status;
    private final String message;
}
// 사용
throw new CustomException(TicketErrorCode.SEAT_ALREADY_TAKEN);
```
> 던진 CustomException은 **공통 GlobalExceptionHandler**가 잡아 `ApiResponse.error(status, message)`로 변환합니다. (common에 없으면 인프라 담당이 추가)

**③ 엔티티는 `BaseEntity` 상속** (id=UUIDv7 자동, createdAt 등 자동)
```java
@Entity @Getter @Table(name = "reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends com.fandom.common.entity.BaseEntity {
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private Long seatId;
    @Column(nullable = false) private UUID concertId;
    @Builder
    private Reservation(UUID userId, Long seatId, UUID concertId) { ... }
}
```
> ℹ️ **JPA Auditing(`@EnableJpaAuditing`)은 common에서 중앙 활성화**돼 있어요. **각 서비스가 추가로 선언하면 `jpaAuditingHandler` 빈 중복으로 기동 실패**합니다 — 서비스는 아무것도 안 해도 createdAt/updatedAt이 자동 동작.

---

## 6. 데이터 · 메시징 규약
### DB
- 서비스별 **독립 DB 인스턴스** + **public 스키마**. `currentSchema`/`default_schema` 안 씀. 다른 서비스 DB 직접 접근 금지(서비스 간은 API/이벤트로).
- 테이블은 `ddl-auto=update`가 엔티티 기준 public에 자동 생성.

### Redis (A안: 일반/티켓팅 2개 — 로컬·운영 모두 분리)
- 일반: `localhost:6379` (auth/gateway/user/feed/chat) / 티켓팅: `localhost:6380` (ticketing/order). 비번 둘 다 `fandom_redis_pw`
- application.yml(필요 서비스) — 본인 용도에 맞는 변수 사용:
```yaml
spring:
  data:
    redis:
      host: ${REDIS_GENERAL_HOST}        # 티켓팅은 ${REDIS_TICKETING_HOST}
      port: ${REDIS_GENERAL_PORT}        # 일반 6379 / 티켓팅 6380
      password: ${REDIS_GENERAL_PASSWORD}
```

### Kafka (이벤트) — 토픽/이벤트 규약 ★ (서비스 간 비동기 통신)
- 로컬 부트스트랩: `localhost:9092`
- **토픽 네이밍**: `<도메인>.<리소스>.<과거형이벤트>`
  예) `ticketing.reservation.completed`, `order.payment.completed`, `notification.send`
- **이벤트 공통 봉투(envelope)** — 멱등성/추적 위해 항상 포함:
```json
{ "eventId": "uuid", "eventType": "ticketing.reservation.completed",
  "occurredAt": "2026-06-24T15:00:00Z", "payload": { ... } }
```
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    consumer:
      group-id: ticketing-service
      auto-offset-reset: latest
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```
- 발행: `kafkaTemplate.send("ticketing.reservation.completed", event);`
- 구독: `@KafkaListener(topics="...", groupId="본인서비스")`
> 토픽/이벤트 추가 시 이 문서에 한 줄 등록 → 팀 공유.

---

## 7. 서비스 간 호출 · 현재 사용자
**동기 호출 = OpenFeign(eureka lb)**
```java
@FeignClient(name = "user-service")   // 서비스명으로 자동 로드밸런싱
public interface UserClient {
    @GetMapping("/api/v1/users/{id}")
    ApiResponse<UserResponse> getUser(@PathVariable("id") UUID id);
}
```
+ 메인에 `@EnableFeignClients`.

**현재 로그인 사용자(userId)** — 게이트웨이가 JWT 검증 후 헤더로 전달하는 패턴 권장:
```java
@RequestHeader("X-User-Id") UUID userId   // gateway가 주입
```
> ⚠️ 정확한 방식(헤더명/UserIdCard)은 **auth·gateway 담당과 규약 확정** 필요. 확정 전엔 위 패턴으로 가정.

---

## 8. 🎟️ ticketing 특화 (조아영님)
**동시성(선착순) = Redis 분산락(Redisson)**
```java
@RequiredArgsConstructor
@Service
public class SeatReservationService {
    private final RedissonClient redissonClient;

    public void reserve(UUID userId, Long seatId) {
        RLock lock = redissonClient.getLock("lock:seat:" + seatId);
        boolean acquired;
        try {
            acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);  // 대기3s, 점유5s
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(TicketErrorCode.LOCK_TIMEOUT);
        }
        if (!acquired) throw new CustomException(TicketErrorCode.LOCK_TIMEOUT);  // 락 실패=WARN 로그 남기기
        try {
            // 1) 좌석 상태 확인(이미 선점이면 SEAT_ALREADY_TAKEN)
            // 2) 선점/예약 저장
            // 3) 이벤트 발행: ticketing.reservation.completed
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
```
- 락 키 컨벤션: `lock:seat:{seatId}` (좌석 단위)
- 로그: 락 획득 실패/타임아웃 시 `log.warn("lock timeout seatId={}", seatId)` (관측/알림 재료 — logging-standard.md)
- 알림 시나리오: **오버부킹/락 타임아웃 급증** 은 `alert-scenarios.md`에 등록됨

---

## 9. 자주 막히는 것 (FAQ)
- **서비스 기동 시 DB 연결 실패** → 본인 DB profile 안 켰거나(`--profile db-<서비스>`) `.env` 미설정. 1·2장 확인.
- **`database "fandom_db" does not exist`** → 구 설정. URL이 본인 DB(`user_db` 등)인지 확인. (더이상 fandom_db 아님)
- **`${USER_DB_URL}` 안 읽힘** → IntelliJ EnvFile 플러그인 미설정. 2장(3).
- **구 컨테이너(6pm-postgres) orphan 경고** → `docker compose down --remove-orphans` 한 번.
- **eureka 등록 에러 로그** → eureka-server 미실행. 단독 개발이면 무시 가능.
- **createdAt이 null** → common JPA Auditing 확인(각 서비스가 `@EnableJpaAuditing` 중복 선언하면 오히려 기동 실패).
- **Redis/Kafka 연결 실패** → 해당 의존성 주석 해제 + 해당 profile(`redis-general`/`redis-ticketing`/`infra`) 기동 확인.

## 10. 관측(자동) — 별도 작업 불필요
3·4장 표준(actuator+tracing+logging)을 넣으면, 메트릭(Grafana)·로그(Loki)·추적(Zipkin)이 **자동 수집**됩니다. 로그 작성 규칙은 `logging-standard.md` 참고.

---
*관련 문서: `db.md`(DB 설계·서비스별 인스턴스), `decisions.md`(D11), `.env.example`(서비스별 변수), `logging-standard.md`, `alert-scenarios.md`. 빠진 결정/값은 인프라 담당에게 → 바로 채웁니다.*
