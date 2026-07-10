# Auth Service 설계

## 1. 문서 목적

이 문서는 Auth Service의 책임, 토큰 발급 정책, Redis 저장 구조, 로그아웃 및 토큰 무효화 흐름을 정리한다.

전체 인증 흐름은 [auth-user-architecture.md](./auth-user-architecture.md)를 따른다.

## 2. Auth Service 책임 범위

Auth Service는 인증 토큰의 생성과 폐기를 담당한다.

주요 책임은 다음과 같다.

- 로그인
- Access Token 발급
- Refresh Token 발급 및 저장
- Access Token 재발급
- 로그아웃
- Access Token blacklist 등록
- 사용자 단위 토큰 무효화
- User Service를 통한 사용자 인증 정보 조회
- `user.deleted` 이벤트 소비

Auth Service는 User, Profile, Follow 같은 사용자 도메인 데이터를 직접 소유하지 않는다.

## 3. 로그인 흐름

```text
Client
  -> Gateway
  -> Auth Service
  -> User Service
  -> Auth Service
  -> Client
```

1. 클라이언트가 이메일과 비밀번호를 제출한다.
2. Gateway는 로그인 경로를 인증 제외 경로로 통과시킨다.
3. Auth Service는 User Service에서 이메일 기준 사용자 정보를 조회한다.
4. 비밀번호를 검증한다.
5. 사용자 상태를 확인한다.
6. Access Token과 Refresh Token을 발급한다.
7. Refresh Token을 Redis에 저장한다.

## 4. Access Token 발급 정책

Access Token은 외부 클라이언트가 Gateway에 제출하는 인증 토큰이다.

Access Token에는 최소한 다음 claim을 포함한다.

| Claim | 설명 |
| --- | --- |
| `sub` | 사용자 식별자 |
| `role` | 사용자 역할 |
| `status` | 사용자 상태 |
| `jti` | 토큰 식별자 |
| `iat` | 발급 시간 |
| `exp` | 만료 시간 |

Gateway는 Access Token을 검증한 뒤 내부 서비스용 `UserIdCard`를 생성한다.

## 5. Refresh Token 발급 및 저장 정책

Refresh Token은 Access Token 재발급에 사용한다.

Auth Service는 Refresh Token을 Redis에 저장한다.

예시 key:

```text
refresh:{userId}:{tokenId}
```

Refresh Token 저장 시 TTL은 Refresh Token 만료 시간과 맞춘다.

Refresh Token 원문은 외부 응답으로 전달되지만, 로그에는 남기지 않는다.

## 6. Access Token 재발급 흐름

```text
Client
  -> Gateway
  -> Auth Service
  -> Redis
  -> Auth Service
  -> Client
```

1. 클라이언트가 Refresh Token으로 재발급을 요청한다.
2. Auth Service는 Refresh Token의 서명과 만료 시간을 검증한다.
3. Redis에 저장된 Refresh Token과 요청 토큰을 비교한다.
4. 사용자 단위 blacklist 여부를 확인한다.
5. 유효하면 새 Access Token을 발급한다.

사용자 단위 blacklist가 존재하면 Refresh Token이 유효해도 재발급을 거부한다.

현재 Refresh Token rotation은 적용하지 않는다. 동일 Refresh Token으로 동시에 재발급을 요청하면 각각 새 Access Token이 발급될 수 있다(현재 정책, 후속 보안 강화 대상).

## 7. 로그아웃 흐름

로그아웃은 현재 세션의 토큰을 사용할 수 없게 만드는 작업이다.

처리 흐름:

1. 현재 Access Token의 남은 TTL 계산
2. Access Token의 `jti`를 blacklist에 등록
3. 요청 Refresh Token 또는 현재 사용자 Refresh Token 삭제
4. 성공 응답 반환

로그아웃 응답은 data 없이 `ApiResponse` 성공 응답을 사용한다.

## 8. 토큰 무효화 정책

토큰 무효화는 두 수준으로 나뉜다.

| 수준 | 용도 |
| --- | --- |
| Access Token 단건 blacklist | 로그아웃처럼 특정 토큰만 차단 |
| 사용자 단위 blacklist | 회원 탈퇴, 계정 정지처럼 사용자 전체 토큰 차단 |

사용자 단위 blacklist가 등록되면 다음 동작이 차단된다.

- 기존 Access Token 사용
- 기존 Refresh Token 재발급

## 9. Redis key 설계

| 용도 | 예시 key | 설명 |
| --- | --- | --- |
| Refresh Token | `refresh:{userId}:{tokenId}` | 사용자별 Refresh Token |
| Access Token blacklist | `blacklist:access:{jti}` | 로그아웃된 Access Token |
| 사용자 단위 blacklist | `blacklist:user:{userId}` | 탈퇴/정지 사용자 토큰 전체 차단 |

TTL 정책:

- Refresh Token key: Refresh Token 만료 시각까지 유지
- Access Token blacklist: Access Token 남은 만료 시간만큼 유지
- 사용자 단위 blacklist: 정책상 필요한 기간만큼 유지

## 10. User Service 연동

Auth Service는 로그인 시 User Service에서 사용자 정보를 조회한다.

조회 정보 예시:

- userId
- email
- password
- role
- status

Auth Service는 사용자의 source of truth가 아니다. 사용자 상태가 바뀌면 User Service 또는 사용자 이벤트를 통해 반영한다.

## 11. user.deleted 이벤트 소비

회원 탈퇴는 User Service에서 발생한다.

User Service는 회원 탈퇴 성공 후 `user.deleted` 이벤트를 발행한다.

Auth Service는 이 이벤트를 소비해 다음 작업을 수행한다.

1. 사용자 단위 blacklist 등록
2. 해당 사용자 Refresh Token 삭제

이벤트 처리 실패에 대비해 consumer는 재시도와 멱등성을 고려해야 한다.

## 12. 예외 및 응답 정책

| 상황 | 권장 상태 |
| --- | --- |
| 로그인 실패 | 401 |
| 존재하지 않는 사용자 | 401 또는 팀 정책에 따른 404 |
| 비밀번호 불일치 | 401 |
| 만료된 Refresh Token | 401 |
| Redis에 없는 Refresh Token | 401 |
| blacklist 사용자 | 401 |
| 요청 검증 실패 | 400 |

인증 실패 응답은 보안상 상세 원인을 과도하게 노출하지 않는다.

## 13. 테스트 방법

단위 테스트:

- 로그인 성공
- 비밀번호 불일치
- Access Token 발급
- Refresh Token 저장
- 재발급 성공
- blacklist 사용자 재발급 실패
- 로그아웃 시 blacklist 등록
- `user.deleted` 이벤트 소비 시 Refresh Token 삭제

API 스모크 테스트:

- 회원가입
- 로그인
- 인증 API 호출
- 로그아웃
- 로그아웃 후 기존 Access Token 차단
- 회원 탈퇴 후 Refresh Token 재발급 차단

Redis 확인 예시:

```powershell
docker exec 6pm-redis-general redis-cli -a fandom_redis_pw --scan --pattern "refresh:${userId}:*"
docker exec 6pm-redis-general redis-cli -a fandom_redis_pw EXISTS "blacklist:user:${userId}"
```

## 14. 후속 과제

- Refresh Token rotation 정책 검토
- 다중 기기 로그인 정책 정리
- 권한 변경 시 기존 토큰 무효화
- 계정 정지 이벤트 연동
- Redis 장애 시 인증 정책 고도화 (현재 Gateway는 인증 상태 저장소 조회 실패 시 fail-closed로 503을 반환 — #228 구현 완료)
- Consumer DLQ 및 재처리 정책 구현

