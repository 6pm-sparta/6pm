package com.fandom.order_service.payment.application.zombierecovery;

/**
 * APPROVED_SYNCED: 거래조회 결과 APPROVED → CONFIRMING 전이.
 * FAILED_SYNCED: 그 외/조회 결과 없음/orphan → FAILED 전이.
 * SKIPPED: 이미 다른 경로로 처리됨(정상 경합).
 */
public enum ZombiePaymentRecoveryResult {
    APPROVED_SYNCED,
    FAILED_SYNCED,
    SKIPPED
}
