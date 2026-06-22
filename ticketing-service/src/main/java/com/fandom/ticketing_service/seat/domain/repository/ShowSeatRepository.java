package com.fandom.ticketing_service.seat.domain.repository;

import com.fandom.ticketing_service.seat.domain.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {

    List<ShowSeat> findAllByShowId(Long showId);

    Optional<ShowSeat> findByOrderId(UUID orderId);
}
