package com.fandom.ticketing_service.seat.presentation.dto;

public record PurchaseLimitResponse(int limit, int purchased, int remaining) {
}
