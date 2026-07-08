# ADR 002 — SAGA Choreography 패턴 채택

> 📌 **상태명 변경 참고**: 이 문서는 주문 상태 머신 재설계 이전에 작성되어 옛 상태명(`REFUNDED`)을 쓴다. 현재는 `CANCELLED`로 통합됐다. 매핑은 [architecture.md](../architecture.md) 3번 섹션 참고.

**날짜**: 2026-06  
**상태**: 확정

---

## 배경

결제 성공 후 좌석 예매 실패 시 결제를 자동 환불해야 한다. 분산 트랜잭션 처리 방식으로 두 가지를 검토했다.

**Choreography**: 각 서비스가 이벤트를 직접 발행/구독하며 자율적으로 흐름을 진행한다.

**Orchestration**: 중앙 오케스트레이터가 각 서비스에 명령을 보내며 전체 흐름을 제어한다.

---

## 결정: Choreography 채택

---

## 이유

| 항목 | 내용 |
|------|------|
| 참여 서비스 | Order, Ticketing, Notification (3개) |
| 흐름 구조 | 선형적. 성공/실패 두 가지 분기만 존재 |
| Orchestration 필요성 | 없음. 별도 오케스트레이터 컴포넌트 추가 비용 대비 이점이 없음 |

참여 서비스가 3개이고 흐름이 단순 선형이라, 각 서비스가 이벤트를 직접 구독해 처리하는 것으로 충분하다.

---

## 흐름 요약

```
order-service: order.payment.completed 발행
  → ticketing-service: 좌석 확정 시도
      성공: ticketing.seat.booked 발행 → order-service CONFIRMED
      실패: ticketing.seat.book.failed 발행 → order-service 보상 시작
              → PG 환불 → order-service REFUNDED
```

---

## 트레이드오프

- 전체 흐름이 중앙에서 보이지 않는다. 각 서비스 로그 + `order_status_histories`로 추적한다.
- 참여 서비스가 5개 이상으로 늘거나 보상 흐름에 복잡한 조건 분기가 생기면 Orchestration으로 전환을 검토한다.