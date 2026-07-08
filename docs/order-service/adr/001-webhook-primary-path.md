# ADR 001 — PG 웹훅을 Primary Completion Path로 채택

> 📌 **상태명 변경 참고**: 이 문서는 (주문 상태 머신 재설계) 이전에 작성되어 `PAYMENT_REQUESTED`/`PAID`/`REFUND_REQUESTED`/`REFUNDED`/`COMPENSATING` 같은 옛 상태명을 그대로 쓴다. 결정 배경을 남긴 역사적 기록이라 원문은 유지하고, 현재 상태명 매핑은 [architecture.md](../architecture.md) 3번 섹션을 참고할 것.

**날짜**: 2026-06  
**상태**: 확정

---

## 배경

PG 결제 승인 흐름을 어떻게 처리할지 두 가지 모델을 검토했다.

**Model A — 동기 승인**
```
Client → order-service → PG API (동기 승인 응답 대기) → 결과 반환
```

**Model B — 웹훅 Primary Path**
```
Client → order-service → PG API (접수만)
                          PG → order-service (웹훅으로 결과 전달)
```

---

## 결정: Model B 채택

---

## 이유

**Model A의 문제점**

1. **PG API 응답 지연이 곧 클라이언트 응답 지연이 된다.** PG API는 외부 시스템이라 응답 시간을 보장할 수 없다. 타임아웃 설정 없이 스레드가 무한 대기할 수 있다.

2. **PG 승인 후 DB 저장 실패 시 복구 경로가 없다.** PG는 승인했는데 서버가 죽으면 PAYMENT_REQUESTED 상태로 영구 stuck. 이 케이스를 복구하려면 결국 웹훅이 필요하다.

3. **PG 재시도 시 멱등성 관리가 복잡해진다.** 동기 타임아웃 후 재시도 시 동일 요청인지 PG가 보장하지 않으면 중복 결제가 발생할 수 있다.

**Model B의 이점**

- 클라이언트는 PG 응답 대기 없이 즉시 응답을 받는다 (PAYMENT_REQUESTED).
- PG 처리 중 서버 장애가 나도 웹훅이 복구 경로가 된다.
- 실제 현업 PG(토스페이먼츠 등)가 동일한 방식으로 동작한다. PG가 거래 ID를 즉시 발급하고 최종 결과를 웹훅으로 비동기 전달한다.

---

## 트레이드오프

- 클라이언트가 결제 완료 시점을 동기 응답으로 알 수 없다. 알림(`notification.send`)으로 대체한다.
- 웹훅 미수신 시 PAYMENT_REQUESTED 상태에서 stuck될 수 있다. 타임아웃 스케줄러(현재 미구현 상태)로 처리한다.
- 웹훅 중복 수신에 대한 멱등성 처리가 필요하다. PG 트랜잭션 ID 기반 Redis SETNX 1차 차단 + 상태 전이 성공 여부 반환으로 Kafka 이벤트 중복 발행을 방지한다.

> **현재 상태 (2026-07)**: 위 두 트레이드오프 모두 이후 구현됐다. 다만 타임아웃 스케줄러(`OrderTimeoutScheduler`)는 결제 시도 자체가 없는 `PENDING` 주문만 대상으로 하고, 웹훅이 안 온 채로 `payments.REQUESTED`에 멈춘 경우는 별도 배치(좀비 결제 정리, `flows.md` 8번 참고)가 담당한다 — "타임아웃 스케줄러 하나로 다 처리한다"는 원래 가정보다 실제로는 두 스케줄러로 역할이 나뉘었다.