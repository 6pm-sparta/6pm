# ADR 006 — MockPaymentGateway DIP 구조

> 📌 **상태명 변경 참고**: 이 문서는 (주문 상태 머신 재설계) 이전에 작성되어 `PAYMENT_REQUESTED`/`PAID`/`REFUND_REQUESTED`/`REFUNDED`/`COMPENSATING` 같은 옛 상태명을 그대로 쓴다. 결정 배경을 남긴 역사적 기록이라 원문은 유지하고, 현재 상태명 매핑은 [architecture.md](../architecture.md) 3번 섹션을 참고할 것.

**날짜**: 2026-06  
**상태**: 확정 (2026-07 Mock PG 고도화 반영)

---

## 배경

실제 PG 없이 개발·테스트 환경에서 결제 흐름 전체를 검증해야 한다.

---

## 결정: PaymentGateway 인터페이스 + MockPaymentGateway 구현체 (DIP)

`PaymentGateway` 인터페이스를 두고 `MockPaymentGateway`가 이를 구현한다. 서비스 레이어는 인터페이스에만 의존하므로 실제 PG 구현체로 교체할 때 서비스 코드를 건드리지 않아도 된다.

---

## 패키지 구조

`payment/infra/pg/` 아래에 인터페이스와 공용 컴포넌트(`PaymentGateway`, 거래 결과 enum, 거래조회 결과 DTO, webhook 서명 검증 유틸, HMAC 계산 로직)를 두고, 그 아래 `mock/` 패키지에 Mock 전용 구현체(`MockPaymentGateway`와 그 거래 기록 엔티티/레포지토리, webhook 발송기, webhook 서명 생성기)를 몰아넣었다.

서명 쪽은 검증(`verify()`)만 공용이고 생성(`sign()`)은 Mock 전용이다. 실제 PG라면 PG사가 자기 비밀키로 서명해서 보내므로 우리가 `sign()`을 만들 일이 없다 — `sign()`은 "Mock이 PG인 척 webhook을 만들어 보내는" Mock 전용 책임이기 때문이다.

---

## MockPaymentGateway 시나리오 트리거

`Idempotency-Key` 헤더 접두사로 결정론적 시나리오를 재현한다.

| Idempotency-Key 접두사 | 동작 |
|------------------------|------|
| (없음, 일반) | webhook 콜백 APPROVED |
| `FAIL_` | webhook 콜백 FAILED (failureReason="결제 실패", 영구 실패로 취급 — 재시도 대상 아님) |
| `TRANSIENT_FAIL_` | webhook 콜백 FAILED (failureReason="TRANSIENT:PG 일시적 오류" — 재시도 대상. 재시도는 새 Idempotency-Key로 나가므로 prefix가 안 붙어 정상 승인 처리됨) |
| `TIMEOUT_` | webhook 미발송 (`payments.REQUESTED`에 멈춘 좀비 상태 시뮬레이션) |

환불 시나리오는 `pgTransactionId`에 포함된 마커로 분기한다.

| pgTransactionId 포함 마커 | 동작 |
|---------------------------|------|
| (없음) | webhook 콜백 REFUNDED |
| `REFUND_FAIL_` | webhook 콜백 REFUND_FAILED |
| `REFUND_TIMEOUT_` | webhook 미발송 (환불 webhook 유실 시뮬레이션) |

이 마커 방식은 **기능 검증(결정론적 시나리오 재현)** 전용이며, 마커가 있으면 항상 마커가 우선한다.

**확률적 장애 주입 모드 (부하 테스트용, 구현 완료)**: 마커가 없는 요청에 한해 `MockPgChaosPolicy`(`ChaosProperties.enabled`)가 확률적으로 결과를 흔든다. 실패/유실/지연(SLOW, 지터 추가) 세 종류를 확률적으로 판정하고, 이후 처리(거래 영속화 → webhook 발송)는 마커 로직과 동일한 경로를 탄다. 구체 확률/지터 값은 `architecture.md` 참고.

---

## Mock PG 거래조회 (2026-07 추가)

webhook은 신뢰할 수 없는 채널이라 유실될 수 있다. 유실 시 "PG가 실제로 어떻게 처리했는지"를 알 방법이 없으면 복구 배치가 무조건 재시도에 의존해야 한다.

Mock PG에 자체 거래 기록(`mock_pg_transactions` 테이블)을 추가하고 `PaymentGateway.inquireTransaction()`으로 조회할 수 있게 했다.

**핵심 설계 포인트: webhook 발송 여부와 거래 기록 저장을 분리.**

`TIMEOUT_` 마커일 때 기존 코드는 그냥 아무것도 안 하고 끝났다. 이러면 거래조회를 도입해도 "PG가 진짜 처리했는지"를 알 수 없다. 그래서 PG는 결과와 무관하게 자신의 거래 기록을 항상 먼저 저장하도록 바꾸고, `TIMEOUT_` 마커일 때만 그 저장 뒤에 webhook 발송만 생략하도록 분리했다.

이렇게 하면 TIMEOUT 시나리오가 "webhook 유실 + PG 처리는 성공"을 정확히 재현한다.

---

## 트레이드오프

- 실제 PG의 네트워크 지연, 타임아웃, 부분 실패 등 엣지 케이스는 Mock으로 완전히 재현할 수 없다.
- 실제 PG 연동 시 `mock/` 패키지를 건드리지 않고 `PaymentGateway` 새 구현체만 추가하면 된다.
- 실제 PG사마다 상태 코드 체계가 다르므로 `PgTransactionResult` enum은 교체가 필요할 수 있다.