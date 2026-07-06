# Auth/User/Gateway 테스트 가이드

## 1. 문서 목적

이 문서는 로컬 환경에서 Gateway, Auth Service, User Service, Redis, Kafka 연동을 빠르게 확인하기 위한 테스트 절차를 정리한다.

설계 배경은 [auth-user-architecture.md](./auth-user-architecture.md)를 따른다.

## 2. 로컬 실행 전제

필요 구성:

- Docker infrastructure profile
- Config Server
- Eureka Server
- Gateway Service
- Auth Service
- User Service
- Redis
- Kafka
- Kafka UI

권장 실행 순서:

1. Docker infra
2. Config Server
3. Eureka Server
4. User Service
5. Auth Service
6. Gateway Service

Gateway 경유 테스트 기준 base URL:

```text
http://localhost:8080
```

## 3. 공통 변수 준비

```powershell
$suffix = Get-Date -Format "yyyyMMddHHmmss"

$memberEmail = "test-member-$suffix@example.com"
$creatorEmail = "test-creator-$suffix@example.com"
$password = "password123!"
```

## 4. 회원가입 테스트

### 4.1 일반 회원가입

```powershell
$memberSignupBody = @{
  email = $memberEmail
  password = $password
  nickname = "테스트멤버$suffix"
  zipCode = "06978"
  address1 = "서울특별시 동작구 상도로 369"
  address2 = "단독"
} | ConvertTo-Json

$memberSignupResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/members" `
  -ContentType "application/json; charset=utf-8" `
  -Body $memberSignupBody

$memberId = $memberSignupResponse.data.userId
```

기대 결과:

- HTTP 201
- response data에 `userId`, `email`, `nickname` 포함

### 4.2 크리에이터 회원가입

```powershell
$creatorSignupBody = @{
  email = $creatorEmail
  password = $password
  nickname = "테스트크리에이터$suffix"
  agencyName = "테스트소속사"
  zipCode = "12028"
  address1 = "경기도 남양주시 오남읍 진건오남로667번길 64-33"
  address2 = "본관"
} | ConvertTo-Json

$creatorSignupResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/creators" `
  -ContentType "application/json; charset=utf-8" `
  -Body $creatorSignupBody

$creatorId = $creatorSignupResponse.data.userId
```

기대 결과:

- HTTP 201
- Kafka UI에서 `user.creator-created` 확인

## 5. 로그인 테스트

```powershell
$loginBody = @{
  email = $memberEmail
  password = $password
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/login" `
  -ContentType "application/json; charset=utf-8" `
  -Body $loginBody

$accessToken = $loginResponse.data.accessToken
$refreshToken = $loginResponse.data.refreshToken
```

기대 결과:

- HTTP 200
- `accessToken` 존재
- `refreshToken` 존재

## 6. 인증 API 테스트

회원 정보 수정:

```powershell
$memberUpdateBody = @{
  address2 = "수정주소"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Patch `
  -Uri "http://localhost:8080/api/v1/members/me" `
  -Headers @{ Authorization = "Bearer $accessToken" } `
  -ContentType "application/json; charset=utf-8" `
  -Body $memberUpdateBody
```

기대 결과:

- HTTP 200
- Gateway가 Access Token 검증
- User Service에서 `@CurrentIdCard` 주입 성공

## 7. 프로필 테스트

### 7.1 공개 프로필 조회

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/members/$memberId/profile"

Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/creators/$creatorId/profile"
```

기대 결과:

- HTTP 200
- Access Token 없이 조회 가능

### 7.2 내 프로필 수정

```powershell
$profileUpdateBody = @{
  nickname = "수정멤버$suffix"
  profileMessage = "프로필 소개 수정"
  profileImage = "https://cdn.example.com/profile-$suffix.png"
  bannerImage = "https://cdn.example.com/banner-$suffix.png"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Patch `
  -Uri "http://localhost:8080/api/v1/members/me/profile" `
  -Headers @{ Authorization = "Bearer $accessToken" } `
  -ContentType "application/json; charset=utf-8" `
  -Body $profileUpdateBody
```

기대 결과:

- HTTP 200
- 변경된 프로필 정보 반환

## 8. 팔로우 테스트

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/follows/$creatorId" `
  -Headers @{ Authorization = "Bearer $accessToken" }
```

기대 결과:

- HTTP 201
- Kafka UI에서 `user.followed` 확인

언팔로우:

```powershell
Invoke-RestMethod `
  -Method Delete `
  -Uri "http://localhost:8080/api/v1/follows/$creatorId" `
  -Headers @{ Authorization = "Bearer $accessToken" }
```

기대 결과:

- HTTP 200
- Kafka UI에서 `user.unfollowed` 확인

## 9. 로그아웃 테스트

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/logout" `
  -Headers @{ Authorization = "Bearer $accessToken" }
```

기대 결과:

- HTTP 200
- 기존 Access Token 사용 불가

기존 토큰 차단 확인:

```powershell
try {
  Invoke-RestMethod `
    -Method Patch `
    -Uri "http://localhost:8080/api/v1/members/me" `
    -Headers @{ Authorization = "Bearer $accessToken" } `
    -ContentType "application/json; charset=utf-8" `
    -Body (@{ address2 = "로그아웃 후 접근 테스트" } | ConvertTo-Json)
} catch {
  "access token blocked: $([int]$_.Exception.Response.StatusCode)"
}
```

기대 결과:

- 401

## 10. 회원 탈퇴 및 토큰 무효화 테스트

회원 탈퇴 테스트는 새 사용자로 진행하는 것이 좋다.

```powershell
Invoke-RestMethod `
  -Method Delete `
  -Uri "http://localhost:8080/api/v1/members/me" `
  -Headers @{ Authorization = "Bearer $accessToken" }
```

기대 결과:

- HTTP 200
- Kafka UI에서 `user.deleted` 확인
- 기존 Access Token 401
- 기존 Refresh Token 재발급 실패

Refresh Token 재발급 차단 확인:

```powershell
try {
  Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/v1/auth/reissue" `
    -ContentType "application/json; charset=utf-8" `
    -Body (@{ refreshToken = $refreshToken } | ConvertTo-Json)
} catch {
  "refresh token revoked: $([int]$_.Exception.Response.StatusCode)"
}
```

기대 결과:

- 401

## 11. Redis 확인 명령어

사용자 단위 blacklist 확인:

```powershell
docker exec 6pm-redis redis-cli -a fandom_redis_pw EXISTS "blacklist:user:${memberId}"
```

Refresh Token 삭제 확인:

```powershell
docker exec 6pm-redis redis-cli -a fandom_redis_pw --scan --pattern "refresh:${memberId}:*"
```

TTL 확인:

```powershell
docker exec 6pm-redis redis-cli -a fandom_redis_pw TTL "blacklist:user:${memberId}"
```

PowerShell에서 `:`가 들어간 문자열에 변수를 붙일 때는 `${memberId}`처럼 중괄호를 사용한다.

## 12. Kafka UI 확인 방법

Kafka UI에서 다음 topic을 확인한다.

- `user.creator-created`
- `user.followed`
- `user.unfollowed`
- `user.deleted`

확인할 값:

- key
- payload
- timestamp
- partition

## 13. 자주 발생한 문제

### 13.1 Gateway가 USER-SERVICE를 찾지 못함

증상:

```text
No servers available for service: USER-SERVICE
```

확인:

- User Service 실행 여부
- Eureka 등록 여부
- Gateway 재시작 필요 여부
- Config Server 정상 여부

### 13.2 Gateway 8080 포트 충돌

증상:

```text
Port 8080 was already in use
```

확인:

- 기존 Gateway 프로세스 종료
- IntelliJ 실행 프로세스 중복 확인

### 13.3 한글 응답 깨짐

PowerShell 출력에서 한글이 깨져 보일 수 있다.

API 자체 문제인지 확인하려면 DB 값 또는 JSON raw 응답 인코딩을 별도로 확인한다.

### 13.4 Eureka 서버 미실행 경고

단위 테스트 중 Eureka 등록/해제 경고가 발생할 수 있다.

테스트 결과가 성공이면 기능 실패로 보지 않는다.

