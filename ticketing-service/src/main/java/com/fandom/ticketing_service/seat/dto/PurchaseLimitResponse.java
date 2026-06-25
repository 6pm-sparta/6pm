package com.fandom.ticketing_service.seat.dto;

public record PurchaseLimitResponse(int limit, int purchased, int remaining) {
}
