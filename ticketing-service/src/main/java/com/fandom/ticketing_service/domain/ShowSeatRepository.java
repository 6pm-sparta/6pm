package com.fandom.ticketing_service.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {

    List<ShowSeat> findAllByShowId(UUID showId);

    Optional<ShowSeat> findByOrderId(UUID orderId);
}
