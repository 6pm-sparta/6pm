# ADR 010 — Kafka DLQ 도입 (Consumer + Outbox)

**날짜**: 2026-07
**상태**: ✅ 구현 완료

---

## 배경

기존에 도입한 Transactional Outbox 패턴과 기본 Kafka 컨슈머 재시도만으로는 두 지점에서 메시지가 조용히 유실될 수 있었다.

**Consumer 측**: `ticketing.seat.booked`, `ticketing.seat.book.failed` 리스너가 처리 중 예외를 던지면 기본 재시도 정책(고정 횟수)을 소진한 뒤 메시지가 그냥 버려졌다. 특히 `ticketing.seat.book.failed`는 SAGA 보상 트랜잭션의 유일한 트리거인데, 이게 유실되면 결제는 승인됐지만 좌석 확정도 환불도 되지 않는 주문이 조용히 방치된다.

**Outbox 측**: `OutboxRecordPublisher`가 발행에 실패하면 레코드가 `PENDING`으로 남아 다음 폴링에서 다시 시도한다(의도된 재시도). 하지만 재시도 횟수 상한이 없어서, 영구적으로 발행에 실패하는 레코드(예: 잘못된 페이로드, 브로커 설정 문제)가 있으면 매 폴링마다 계속 같은 실패를 반복하는 무한 루프가 된다.

---

## 결정 1: Consumer 측 — DeadLetterPublishingRecoverer

`DefaultErrorHandler` + `DeadLetterPublishingRecoverer` 조합으로, 재시도 소진 시 원본 메시지를 `{topic}.DLQ` 토픽으로 옮긴다.

- DLQ 토픽명은 `record.topic() + ".DLQ"` 동적 생성 방식 채택. 초기엔 `KafkaTopics.SEAT_BOOKED_DLQ`/`SEAT_BOOK_FAILED_DLQ` 상수로 명시적 매핑했으나, 코드 리뷰 피드백을 받아 확장성을 위해 동적 생성으로 리팩토링 — 이 과정에서 두 상수는 미사용이 되어 제거됨.
- DLQ 발행 전용 `dlqKafkaTemplate`을 별도로 둔다. 주 발행용 `kafkaTemplate`과 달리 idempotence를 비활성화 — DLQ는 "일단 남긴다"가 목적이라 exactly-once 보장이 불필요하고, 원본 메시지 발행 경로와 완전히 독립시켜 장애 전파를 막기 위함.

## 결정 2: Outbox 측 — retryCount + MAX_RETRY_COUNT

`OrderOutbox`에 `retryCount` 컬럼을 추가하고, `OutboxRecordPublisher`에서 발행 실패 시마다 증가시킨다. `MAX_RETRY_COUNT`(클래스 상수, 5)를 초과하면 `OutboxStatus.FAILED`로 전환해 폴링 대상에서 제외한다.

`MAX_RETRY_COUNT`를 `OrderProperties`가 아니라 클래스 레벨 상수로 둔 이유: `OrderProperties`는 record라 필드를 추가할 때마다 기존 테스트의 positional 생성자 호출이 전부 깨지는 문제가 반복됐다(Timeout, RefundRecovery, 이후 zombie-payment 때도 동일). 이 값은 운영 중 튜닝 필요성이 낮다고 판단해 설정화 비용을 감수하지 않기로 했다.

## 검토했으나 반려한 안

**Consumer/Outbox 실패 원인 통합 모니터링**: DLQ와 Outbox FAILED를 하나의 대시보드/알림 채널로 묶는 안을 검토했으나, 관측 인프라(AIOps 서비스) 쪽 작업과 맞물려 있어 이번 스코프에서는 제외. DLQ 토픽/`OutboxStatus.FAILED` 각각을 운영자가 별도로 확인하는 것을 MVP 기준으로 삼는다.

---

## 남은 갭

- DLQ에 쌓인 메시지의 재처리 절차가 수동이다(운영자가 직접 확인 후 재발행/폐기). 자동 재처리 배치는 범위 밖.
- Outbox `FAILED` 레코드도 마찬가지로 수동 개입 전제. 알림 연동은 미구현.

## 관련 문서

- `architecture.md` 4번 섹션: Kafka 이벤트 및 Outbox 패턴