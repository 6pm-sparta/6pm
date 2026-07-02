-- =====================================================================
-- orders — 부분 UNIQUE 인덱스
-- =====================================================================
-- Hibernate ddl-auto=update는 컬럼/테이블만 생성하고, WHERE 조건이 붙은
-- "부분(partial) UNIQUE 인덱스"는 만들어주지 않는다. JPA 표준 @Index/@UniqueConstraint
-- 애노테이션도 WHERE절을 지원하지 않으므로, 이 인덱스는 별도 SQL로 정의해야 한다.
--
-- 목적: 동일 seatId로 "진행중"(PENDING/PAYMENT_REQUESTED/PAID) 주문이 동시에 2건 이상
-- 생성되는 것을 DB 레벨에서 최종적으로 막는다 (Redis 멱등성 1차 방어가 뚫렸을 때의 최후 방어선).
-- seatId 단독 UNIQUE는 불가능하다 — 취소/환불 후 같은 좌석을 다시 파는 흐름과 충돌하기 때문.
--
-- 적용 방법: application.yml의 spring.sql.init.mode=always 설정에 따라
-- 앱 기동 시 자동 실행된다 (별도 수동 실행 불필요).
--
-- 주의: OrderStatus.ACTIVE(Java enum)에 정의된 "진행중" 상태 집합과 이 IN절은
-- 반드시 동일해야 한다. 둘 중 하나만 바뀌면 애플리케이션 레벨 멱등 체크와
-- DB 레벨 최종 방어선의 기준이 어긋난다.
-- =====================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_seat_active
    ON orders (seat_id)
    WHERE status IN ('PENDING', 'PAYMENT_REQUESTED', 'PAID');