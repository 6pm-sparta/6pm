# ADR 007 — Redis 백업 전략: RDB + PostgreSQL 재적재

**날짜**: 2026-06-18
**상태**: 확정

---

## 배경

좌석 상태의 단일 진실 공급원(SoT)인 Redis가 장애로 데이터를 잃을 경우의 복구 전략이 필요했다. AOF와 RDB를 검토했다.

---

## 결정: RDB 영속성 + 장애 시 PostgreSQL 기준 재적재

---

## 이유

AOF는 모든 쓰기 명령마다 I/O 비용이 발생한다. `BOOKED`(확정) 상태의 원본 데이터는 이미 PostgreSQL `orders` 테이블에 있으므로, 장애 시 CONFIRMED 주문을 기준으로 Redis를 재적재하는 방식이 AOF보다 단순하면서도 데이터 유실 없이 복구 가능하다.
