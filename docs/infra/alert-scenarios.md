# 🚨 6pm 위험 시나리오 · 감지/알림 기준 (서비스별 취합)

> 취합 담당: 하준영(관측/AIOps) · **제출기한: 6/29(월)**
> 회의(6/23) 결정: **각 서비스 담당이 자기 서비스의 위험 시나리오를 채운다.** 부하테스트 기반 실제 구현은 **W3~4**.
> ⚠️ 이건 `logging-standard.md`(로그를 어떻게 쓰나)와 **별개 레이어** — 표준대로 남긴 로그·메트릭에서 **무엇을 위험으로 보고 어떻게 알릴지** 정의.

---

## 작성법 (담당자용)
각 행에 다음을 채워주세요:
- **위험 시나리오**: 운영 중 반드시 즉시 알아야 하는 상황 (정합성 위반·장애·매출 직결 등)
- **심각도**: `critical`(즉시 대응) / `warning`(주의)
- **감지 기준**: 가능하면 **메트릭(Prometheus)** 우선, 보조로 **로그 패턴(Loki)**
- **알림 방식**: 공통은 `Alertmanager → AIOps → Slack`. 심각도만 정하면 됨.
- **비고**: 부하테스트 필요 여부 등

> 팁: "이게 터지면 운영자가 자다가도 깨야 하나?" → 그렇다 = critical. "지표가 나빠지는 신호" = warning.

---

## 서비스별 표

### 🎟️ ticketing (Inventory & Ticketing) — 담당: 조아영
| 위험 시나리오 | 심각도 | 감지 기준 | 알림 | 비고 |
|---|---|---|---|---|
| **오버부킹**(2명+ 같은 좌석 확정) | critical | 좌석별 확정수 > 1 (메트릭 카운터) **또는** 예매수 > 재고. 로그: 동일 seatId "confirmed" 중복 | AIOps→Slack 즉시 | 정합성 위반. W3~4 부하테스트로 실제 발생률 확인(회의 안건) |
| 분산락 타임아웃 급증 | warning | lock_timeout 카운트/분 > N (메트릭) 또는 WARN "lock timeout" 급증 | Slack | 선착순 경합 과부하 신호 |
| 예매 실패율 급증 | warning | 예매실패/요청 비율 > X% (or 5xx율) | Slack | |
| 대기열 적체 | warning | 대기열 길이 > N / 처리지연 | Slack | (대기열 사용 시) |

### 👤 user (User) — 담당: (이재범?)
| 로그인 실패율 급증(공격 의심) | warning | 로그인 실패/분 > N | Slack | brute-force 신호 |
| (담당이 추가) | | | | |

### 💳 order (Order & Payment) — 담당: ?
| 결제 실패율 급증 | critical | 결제실패/시도 > X% | Slack | 매출 직결 |
| 결제-주문 상태 불일치 | critical | 정합성 체크 위반 | Slack | |

### 📰 feed (Feed) — 담당: ?
| 타임라인 조회 지연 | warning | p99 레이턴시 > N ms | Slack | 이탈 직결 |

### 🔔 notification — 담당: ?
| 발송 실패율 급증 | warning | 발송실패/시도 > X% (or Kafka consumer lag) | Slack | |

### 🌐 gateway / 🔐 auth — 담당: ?
| (담당이 추가) | | | | |

---

## 🛠️ 공통 인프라 시나리오 — 담당: 하준영 (이미 #44에 구현)
| 위험 시나리오 | 심각도 | 감지 기준 | 비고 |
|---|---|---|---|
| 서비스 다운 | critical | `up == 0` | #44 ServiceDown |
| CPU/Heap 임계 | warning | `process_cpu_usage>0.8` / heap>90% | #44 |
| DB 커넥션풀 고갈 | critical | `hikaricp_connections_pending>0` | #44 |
| Kafka consumer lag | warning | lag > 1000 | #44 |

---

## 진행 메모
- 채워진 시나리오 → 부하테스트(W3) 후 **Prometheus alert-rules + AIOps 웹훅**으로 구현.
- 1차 목표(6/29까지): **각 서비스 1~2개 핵심 시나리오 채우기**(완벽 X, 모으기 O).
- 오버부킹은 조아영님이 제기한 1번 안건 — 정합성 알림 대표 사례.
