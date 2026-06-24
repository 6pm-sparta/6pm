package com.fandom.aiops_service.domain.repository;

import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IncidentAlertHistoryRepository extends JpaRepository<IncidentAlertHistory, UUID> {

    Optional<IncidentAlertHistory> findFirstByAlertNameAndSourceServiceAndResolvedAtIsNullOrderByFiredAtDesc(
            String alertName, String sourceService);
}
