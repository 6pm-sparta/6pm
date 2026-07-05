# ADR 010 — Kafka DLQ 처리 (Consumer + Outbox)

**날짜**: 2026-07
**상태**: 구현 완료

---

## 배경

DLQ 도입 전 두 쪽 다 "재시도 소진 = 유실"이었다.

**Consumer 쪽**: 기존 `DefaultErrorHandler`는 `FixedBackOff(1000ms, 2회)` 재시도 후에도 실패하면 `log.error`만 남기고 오프셋을 커밋해버렸다. 문제는 이 컨슈머가 받는 이벤트가 `ticketing.seat.book.failed`라는 점이다 — 이건 SAGA 보상(자동 환불) 트리거다. 처리 중 예외가 재시도까지 다 소진되면 보상 트랜잭션 자체가 시작되지 않은 채 메시지가 조용히 사라진다. 결제는 이미 승인됐는데 환불도 안 되고 좌석도 안 풀리는, 로그 한 줄 말고는 흔적도 안 남는 정합성 사고로 이어질 수 있었다.

**Outbox 쪽**: 발행 실패 시 `markPublished()`를 호출하지 않고 `PENDING`을 유지해 다음 폴링이 재시도하는 구조였는데, 실패 횟수에 상한이 없었다. 즉 직렬화가 깨졌거나 토픽 설정이 잘못된 것처럼 **영원히 실패할 수밖에 없는 레코드**가 하나 생기면, 그 레코드가 폴링 배치 슬롯을 매 주기 계속 차지하면서 무한 재시도만 반복하고, 운영자 입장에서는 "얘가 고장났다"는 신호를 받을 방법이 로그를 직접 뒤지는 것 말고는 없었다.

두 경우 모두 "실패가 조용히 반복되거나 조용히 사라진다"는 같은 뿌리의 문제였지만, 실패 지점이 다르기 때문에(하나는 메시지가 이미 브로커에 있고, 하나는 우리 쪽 발행 자체가 안 된 것) 처리 방식은 분리했다.

---

## 결정 1: Consumer는 `DeadLetterPublishingRecoverer`로 `{topic}.DLQ` 이동

`ticketing.seat.booked`, `ticketing.seat.book.failed` 컨슈머에 `DefaultErrorHandler(recoverer, FixedBackOff(1000ms, 2회))`를 적용했다. 재시도 소진 시 `record.topic() + ".DLQ"`로 토픽명을 동적 조합해 이동시킨다.

역직렬화 실패가 리스너 스레드를 죽이지 않도록 `ErrorHandlingDeserializer`로 감쌌다 — 역직렬화 실패도 동일하게 재시도 → DLQ 경로를 탄다.

---

## 결정 2: DLQ 전용 `dlqKafkaTemplate` 분리 (idempotence 비활성화)

메인 `kafkaTemplate`(`@Primary`, 비즈니스 이벤트 발행용)은 `enable.idempotence=true`로 설정돼 있다. `DeadLetterPublishingRecoverer`는 원본 토픽이 무엇이든 임의의 `{topic}.DLQ`에 써야 하는데, idempotence가 켜진 프로듀서로 이런 범용 쓰기를 하는 게 부적절하다고 판단해 `dlqKafkaTemplate`을 idempotence 비활성화 상태로 별도 생성했다.

---

## 결정 3: Outbox는 별도 DLQ 토픽 대신 `OutboxStatus.FAILED`로 종결

`OutboxRecordPublisher`가 발행 실패 시 `retryCount`를 증가시키고, `MAX_RETRY_COUNT(5)`를 넘으면 `OutboxStatus.FAILED`로 전이한다. Consumer처럼 별도 `.DLQ` 토픽으로 보내지 않는다.

**이유**: Outbox 발행 실패는 "우리 쪽에서 Kafka로 내보내는 것 자체"가 안 된 상황이다. 컨슈머가 이미 받은 메시지를 처리하다 실패한 게 아니라, 애초에 메시지가 브로커에 도달하지 못했다. 이런 상황을 별도 DLQ 토픽으로 보내봐야 소비할 대상이 없다 — 그냥 DB 상태(`FAILED`)로 남겨 운영자가 직접 원인(브로커 장애, 직렬화 오류 등)을 확인하는 편이 더 명확하다.

`FAILED`는 폴링 대상에서 제외되므로 무한 재시도로 `PENDING`이 쌓이는 것도 함께 방지한다.

`MAX_RETRY_COUNT`는 `OutboxRecordPublisher` 내부 클래스 상수로 뒀다(`OrderProperties`가 아니라) — 테스트 생성자 호출부 전체를 건드리는 cascading 변경을 피하기 위함(`OrderProperties`는 record라 필드 추가 시 모든 테스트 생성자를 고쳐야 한다).

---

## 관련 문서

- SA_2차: 공통 이벤트 정책(재시도/DLQ 정책)
- adr/005: Writer 빈 분리(Outbox 단건 트랜잭션 분리와 같은 맥락 — self-invocation 프록시 문제)