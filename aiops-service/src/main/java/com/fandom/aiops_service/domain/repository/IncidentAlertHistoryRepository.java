package com.fandom.aiops_service.domain.repository;

import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IncidentAlertHistoryRepository extends JpaRepository<IncidentAlertHistory, UUID> {

    /**
     * fingerprint(알림 시리즈 고유키)로 진행 중(active) 사건 조회 — 중복/해소 매칭의 1순위.
     * Alertmanager는 동일 알림 시리즈에 안정적인 fingerprint를 부여하므로
     * alert_name+job 보다 정확하게 firing↔resolved 를 짝지을 수 있다.
     */
    Optional<IncidentAlertHistory> findFirstByFingerprintAndResolvedAtIsNullOrderByFiredAtDesc(
            String fingerprint);

    /**
     * fingerprint 가 비어있는 경우(구버전 알림 등)의 폴백 — alert_name + source_service 기준.
     */
    Optional<IncidentAlertHistory> findFirstByAlertNameAndSourceServiceAndResolvedAtIsNullOrderByFiredAtDesc(
            String alertName, String sourceService);
}
