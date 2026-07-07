# 📐 6pm 로그 표준 (모든 서비스 공통)

> 담당: 하준영(관측) · 최종수정: 2026-06-23
> 목적: 모든 서비스가 **같은 형식**으로 로그를 남겨 → Loki에서 통합 검색 + AIOps가 분석 가능하게.
> ⚠️ 이건 "로그를 어떻게 쓰는가" 표준. "무엇을 위험으로 보고 알릴까"는 별도 문서 `alert-scenarios.md`.

---

## 1. 형식 (모든 서비스 동일)
- **파일**: ECS JSON 1줄/이벤트 (Spring Boot 3.4+ 내장)
- **콘솔**: 평문(개발 가독성 유지)
- **수집**: Promtail → Loki (인프라가 제공. 서비스는 `./logs/<svc>.log`에 남기기만)
- 각 서비스 `application.yml`:
```yaml
logging:
  file:
    name: ./logs/ticketing-service.log   # 서비스명만 변경
  structured:
    format:
      file: ecs
  level:
    root: INFO
```

## 2. 로그 레벨 가이드 — 언제 무엇을
| 레벨 | 언제 쓰나 | 예시 |
|---|---|---|
| **ERROR** | 처리 못한 예외/장애. **반드시 스택과 함께** | 예매 처리 실패, DB 연결 끊김 |
| **WARN** | 비정상이지만 처리됨/재시도/거절 | 분산락 타임아웃, 외부호출 실패, 검증 실패 |
| **INFO** | 비즈니스 핵심 이벤트/상태 전이 | 예매 성공, 주문 생성, 결제 완료 |
| **DEBUG** | 개발 진단(운영 OFF) | 상세 변수값 |

> 핵심: **ERROR/WARN은 AIOps가 읽는 1순위 재료.** 남발 금지, 대신 맥락을 충분히.

## 3. 운영 로그 3종 + 공통 필드
- **System Error**(ERROR) · **User Access**(INFO 접근로그) · **Slow Query**(WARN)
- 자동 포함 필드(공통): `@timestamp`, `log.level`, `service.name`, `message`, **`trace.id`/`span.id`**
- **권장 컨텍스트(MDC)**: `userId`, 도메인키(`seatId`/`orderId` 등), `result`
  ```java
  MDC.put("seatId", seatId);   // → ECS JSON에 자동 포함 → Loki에서 필터 가능
  try { ... } finally { MDC.clear(); }
  ```

## 4. DO / DON'T
**DO**
- 에러는 스택 + 맥락: `log.error("좌석 선점 실패 seatId={}", seatId, e);`
- "무엇을 하다 / 어떤 키로 / 왜" 실패했는지 (AI 분석 품질 ↑)
- 외부호출 결과·지연, 상태 전이, 검증 실패

**DON'T**
- 비밀번호·토큰·주민번호·카드번호 등 **민감정보 평문 금지**
- 루프 안 과도 로그 / 정상흐름 INFO 남발
- `e.printStackTrace()` (반드시 로거 사용)

## 5. 접근 로그(User Access) 표준
- 공통 필터(가능하면 `common`)에서 한 줄(INFO): `method`, `path`, `status`, `latencyMs`, `userId`
- 게이트웨이·각 서비스 공통 적용

## 6. Slow Query
- 임계(예: 500ms) 초과 쿼리만 **WARN**. Hibernate 통계/`logging.level` 또는 p6spy 사용.

## 7. 상관관계 (로그 ↔ 추적 ↔ 메트릭)
- `trace.id`로 **Loki 로그 ↔ Zipkin trace ↔ 메트릭 스파이크**를 하나의 사건으로 연결해 원인 추적.

## 8. AIOps 연계
- 알림 발생 시 AIOps가 **해당 서비스의 알림 시각 ±N분 ERROR/WARN 로그**를 Loki에서 긁어 LLM에 전달.
- → 그래서 에러 로그에 **맥락(작업·키·원인)** 이 충분해야 진단이 정확해짐.

---
*관련: `alert-scenarios.md`(감지/알림 기준), 관측 인프라(Loki/Promtail/Grafana)*
