package com.fandom.ticketing_service.seat.listener;

import com.fandom.ticketing_service.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHoldExpirationListener implements MessageListener {

    private static final Pattern SEAT_KEY_PATTERN = Pattern.compile("^show:(\\d+):seat:(.+)$");

    private final SeatService seatService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody());
        Matcher matcher = SEAT_KEY_PATTERN.matcher(expiredKey);
        if (!matcher.matches()) {
            return;
        }

        Long showId = Long.parseLong(matcher.group(1));
        UUID seatId = UUID.fromString(matcher.group(2));
        seatService.releaseExpiredHold(showId, seatId);
    }
}
