---
name: "🚀 기능 개발 (Feature)"
about: "새로운 API 개발, 비즈니스 로직 추가, DB 스키마 설계 등의 작업을 요청합니다."
title: "[FEAT] "
labels: "enhancement"
assignees: ''
---

## 🎯 목적
(예: 사용자가 티켓을 예매하기 위해 대기열에 진입하는 API가 필요합니다.)

## 📝 상세 작업 내용
- [ ] `TicketingController` 에 POST 엔드포인트 생성
- [ ] Redis Redisson을 이용한 분산 락 로직 구현
- [ ] `ticketing_history` 테이블 엔티티(JPA) 설계

## 🔗 연관된 API 및 데이터 통신
- Request: `POST /api/v1/tickets/reserve`
- Feign Client: `user-service`에서 사용자 권한 확인 필요
- Kafka: 예매 성공 시 `order-service`로 이벤트 발행

## 📌 참고 자료