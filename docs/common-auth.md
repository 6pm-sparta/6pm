# common-auth 문서

## 0. 요약 및 문서 읽는 법

**한 줄 요약:**
> 우리 프로젝트는 Gateway가 발급한 `UserIdCard`로 신원을 전파한다.

| 목적 | 이동 |
|---|---|
| 빠르게 적용하고 싶다 | → [5. 사용 방법](#5-사용-방법)으로 바로 이동 |
| 왜 이 방식을 택했는지 이해하고 싶다 | → 1번부터 순서대로 읽기 |
| Zero Trust가 뭔지 모르겠다 | → [7. Zero Trust 원칙](#7-zero-trust-원칙) 먼저 읽기 |

---

## 1. 개요

### 왜 Identity Object를 쓰는가

일반적인 MSA에서 각 서비스가 직접 JWT를 검증하면 다음 문제가 생긴다.

- 모든 서비스가 Auth-Service에 매 요청마다 토큰 검증 요청을 보낸다.
- Auth-Service가 병목이 되고, 장애 시 전체 서비스가 인증 불가 상태가 된다.
- 서비스마다 JWT 검증 로직이 중복된다.

이를 해결하기 위해 **Gateway가 토큰을 한 번 검증하고, 검증된 사용자 신원을 Identity Object(`UserIdCard`)로 변환하여 downstream으로 전파**한다.
각 서비스는 Auth-Service를 호출할 필요 없이 `UserIdCard`만 신뢰하면 된다.

### Netflix Passport 패턴과의 유사점

Netflix는 내부적으로 `Passport`라는 Identity Object를 사용한다.
Gateway에서 인증 후 사용자 컨텍스트를 Passport에 담아 내부 서비스로 전달하는 구조로,
우리 프로젝트의 `UserIdCard`는 이 패턴을 참고하여 설계하였다.

### 기존 JWT 직접 검증 방식과의 차이

| 구분 | JWT 직접 검증 | UserIdCard (우리 방식) |
|---|---|---|
| 검증 주체 | 각 서비스가 직접 | Gateway |
| Auth-Service 의존 | 매 요청마다 | 로그인 시점만 |
| 서비스 간 결합도 | 높음 | 낮음 |
| 장애 전파 | Auth-Service 장애 시 전체 영향 | 영향 없음 |

---

## 2. 신원 전파 흐름

```
Client
→ Authorization: Bearer {AccessToken}
→ Gateway
   → Access Token 검증
   → 클라이언트 발 X-Id-Card 헤더 제거 (위조 방지)
   → UserIdCard 생성 (userId, role)
   → HMAC 서명
   → X-Id-Card / X-Id-Card-Signature 헤더로 전달
→ Domain Service
   → COMMON-auth Filter: X-Id-Card-Signature HMAC 검증
   → 검증된 UserIdCard를 Request Context에 저장
   → Controller: @CurrentIdCard로 UserIdCard 주입
   → 비즈니스 로직 수행
```

### Access Token 검증 vs UserIdCard HMAC 검증

두 검증은 **대상이 다르다.**

| 구분 | 검증 주체 | 검증 대상 | 목적 |
|---|---|---|---|
| Access Token 검증 | Gateway | 외부 클라이언트가 보낸 JWT | 클라이언트 인증 |
| UserIdCard HMAC 검증 | Domain Service | Gateway가 생성한 IdCard | 내부 전파 위변조 방지 |

### 각 구간별 책임

| 구간 | 책임 |
|---|---|
| Client → Gateway | Access Token 제출 |
| Gateway | Access Token 검증, X-Id-Card 헤더 제거, UserIdCard 생성 및 HMAC 서명 |
| Gateway → Domain Service | X-Id-Card / X-Id-Card-Signature 헤더 전달 |
| Domain Service | HMAC 검증, UserIdCard Request Context 저장 |

---

## 3. UserIdCard

### 구조

```java
public class UserIdCard {
    private UUID userId;
    private String role; // MEMBER, CREATOR, MASTER
}
```

### 왜 최소 정보만 담는가

`UserIdCard`는 **매 요청마다 헤더에 실려 전송**된다.
담기는 정보가 많을수록 헤더 크기가 커지고, 민감 정보 노출 위험도 높아진다.

Domain Service에서 추가 정보(이메일, 배송지 등)가 필요한 경우 `userId`로 User-Service를 직접 조회한다.
단, `userId`와 `role`만으로 처리 가능한 경우(권한 체크, 논리참조 저장 등)는 별도 조회 없이 사용한다.

---

## 4. HMAC 서명/검증

### 서명 키 관리

HMAC 서명 키(`hmac.secret-key`)는 Gateway와 모든 Domain Service가 공유한다.
이 키가 노출되면 `UserIdCard` 위조가 가능하므로 반드시 안전하게 관리해야 한다.

| 환경 | 관리 방법 |
|---|---|
| 로컬 | 각 서비스 `application.yml`에 직접 입력 |
| 개발/운영 | Config Server를 통해 중앙 관리 |

### Gateway에서 서명, Domain Service에서 검증

```
Gateway
→ UserIdCard를 JSON 직렬화
→ HMAC-SHA256(JSON, secretKey) → 서명값 생성
→ X-Id-Card: {JSON}
→ X-Id-Card-Signature: {서명값}

Domain Service
→ X-Id-Card JSON + secretKey로 HMAC-SHA256 직접 계산
→ X-Id-Card-Signature와 비교
→ 일치 → 신뢰 / 불일치 → 401 거부
```

---

## 5. 사용 방법

### 의존성 추가

각 서비스의 `build.gradle`에 추가:

```gradle
implementation project(':common')
```

### application.yml 설정

```yaml
hmac:
  secret-key: local-dev-secret-key  # 로컬 개발용. 운영은 Config Server에서 관리.
```

### 컨트롤러에서 @CurrentIdCard 사용

```java
@GetMapping("/me")
public ResponseEntity<?> getMe(@CurrentIdCard UserIdCard idCard) {
    UUID userId = idCard.getUserId();   // 사용자 식별
    String role = idCard.getRole();     // 권한 체크

    // role 체크 편의 메서드
    idCard.isMember();   // MEMBER 여부
    idCard.isCreator();  // CREATOR 여부
    idCard.isMaster();   // MASTER 여부
}
```

### 주의사항

- `@CurrentIdCard`는 인증이 필요한 API에서만 사용한다.
- 인증이 불필요한 API(회원가입, 로그인 등)는 파라미터에 `@CurrentIdCard`를 붙이지 않는다.
- `X-Id-Card` 헤더가 없으면 필터가 통과시키므로, 인증 필요 여부는 `SecurityConfig`에서 경로별로 제어한다.

---

## 6. 인증 필요 여부 판단 기준

### 헤더 없으면 통과 원칙

`IdCardVerificationFilter`는 `X-Id-Card` 헤더가 없으면 요청을 통과시킨다.
이는 필터가 **검증만 담당**하고, **경로별 접근 제어는 SecurityConfig의 책임**으로 분리하기 위함이다.

### 인증/IdCard 판단 기준

| 값 | 기준 | 예시 |
|---|---|---|
| 인증 필요 | Access Token이 있어야만 호출 가능 | 프로필 수정, 팔로우, 게시글 작성 |
| 인증 불필요 | 로그인 없이 호출 가능 | 회원가입, 로그인, 공개 프로필 조회 |
| 인증 선택 | 비로그인도 가능하지만 로그인 여부에 따라 응답이 달라짐 | 크리에이터 상세 조회 시 `isFollowing` 포함 여부 |

| 값 | 기준 |
|---|---|
| IdCard 사용 | Domain Service에서 현재 사용자 정보가 필요한 API |
| IdCard 미사용 | 현재 사용자 정보 없이 처리 가능한 API |
| IdCard 선택 | 로그인한 경우에만 사용자 컨텍스트를 참고하는 API |

> 인증이 `필요`인 API는 대부분 IdCard도 `사용`이다.
> `X-Id-Card`, `X-Id-Card-Signature` Header는 개별 API 문서에 작성하지 않고, 본 문서를 따른다.

---

## 7. Zero Trust 원칙

### 내부망도 신뢰하지 않는 이유

전통적인 보안 모델은 "내부망은 안전하다"는 가정을 전제로 한다.
그러나 MSA 환경에서는 다음과 같은 위협이 존재한다.

- 내부 네트워크 침해 시 서비스 간 직접 호출로 권한 우회 가능
- 잘못된 Gateway 설정으로 내부 헤더가 외부에 노출될 수 있음
- 서비스 간 직접 호출(테스트, 배치 등) 시 사용자 컨텍스트 위조 가능

### Gateway 통과했어도 Domain Service가 재검증하는 이유

```
Gateway 검증 = "외부 클라이언트가 보낸 Access Token이 유효한가"
IdCard HMAC 검증 = "Gateway가 생성한 UserIdCard가 위변조되지 않았는가"
```

두 검증은 대상이 다르다.
Gateway를 통과했다고 해서 이후 전파된 `UserIdCard`가 안전하다는 보장이 없다.
따라서 각 Domain Service는 `UserIdCard`를 독립적으로 검증한다.

이를 **Defense in Depth (심층 방어)** 라고 하며, 단일 실패 지점을 없애는 것이 목적이다.
