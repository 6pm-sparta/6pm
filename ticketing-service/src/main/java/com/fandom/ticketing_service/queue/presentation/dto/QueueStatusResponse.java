package com.fandom.ticketing_service.queue.presentation.dto;

public record QueueStatusResponse(
        Long rank,
        boolean isReady
) {
    public static QueueStatusResponse of(Long rank) {
        return new QueueStatusResponse(rank, rank == null);
    }
}
