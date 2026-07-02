package com.fandom.order_service.payment.infra.pg.mock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MockPgTransactionRepository extends JpaRepository<MockPgTransaction, UUID> {

    /** 거래조회용 조회. pgTransactionId는 UNIQUE 컬럼. */
    Optional<MockPgTransaction> findByPgTransactionId(String pgTransactionId);

    /** 비관적 락(SELECT FOR UPDATE) 조회. 환불 상태 갱신 시 race condition 방지용. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from MockPgTransaction t where t.pgTransactionId = :pgTransactionId")
    Optional<MockPgTransaction> findByPgTransactionIdForUpdate(@Param("pgTransactionId") String pgTransactionId);
}
