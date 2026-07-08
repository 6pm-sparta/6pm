# Notification Service 설계

Notification Service의 책임, 도메인 모델, 알림 생성/발송 파이프라인, 재시도·재조정 정책, 기기 토큰/보관함, 이벤트 연동을 관심사별 문서로 나눈 설계 문서 모음이다.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [architecture.md](architecture.md) | 도메인 모델, Repository 계층 구조, 삭제 정책, 예외 및 응답 정책, 설정 레퍼런스 |
| [flows.md](flows.md) | 알림 생성 흐름, 발송 파이프라인, 재시도 및 재조정, 발송 어댑터(FCM), 회원 탈퇴 처리 |
| [api.md](api.md) | 기기 토큰 및 알림 설정, 보관함(Inbox) |
| [events.md](events.md) | 이벤트 및 토픽, Kafka 운영 계약 |

## 빠른 참조

- **엔드포인트**: 토큰 `POST/DELETE /api/v1/notifications/tokens` · 설정 `GET/PATCH .../{id}/settings` / 보관함 `GET/PATCH/DELETE /api/v1/notifications`
- **Kafka**: 소비 `notification.send`·`notification.push`·`notification.push.failed`·`user.deleted` / 발행 `notification.push`·`notification.push.failed`
- **설정**: `fcm.enabled`, `notification.dispatch.max-attempt`, `notification.reconciler.*`

## 문서 목적

이 문서는 Notification Service의 책임, 도메인 모델, 알림 생성/발송 파이프라인, 재시도 및 재조정 정책, 기기 토큰/알림 설정 관리, 보관함(Inbox), 이벤트 연동, 삭제 정책, repository 계층 구조를 정리한다.

전체 인증과 Gateway 흐름은 공통 인증 문서를 따른다.

## Notification Service 책임 범위

Notification Service는 사용자 알림의 생성·발송·보관을 담당한다.

주요 책임은 다음과 같다.

- 알림 생성(이벤트 소비, 멱등 처리)
- 푸시 발송(FCM) 및 기기별 전달 기록
- 발송 실패 재시도 및 PENDING 재조정
- 기기 토큰 등록/삭제, 알림 수신 설정
- 보관함 조회/읽음 처리/비우기
- 회원 탈퇴 시 알림/토큰 정리

알림 발생 원인(채팅, 피드, 주문)은 다른 서비스가 이벤트로 알려주고, 이 서비스는 알림 도메인 상태를 담당한다.