package com.fandom.ticketing_service.seat.listener;

import com.fandom.ticketing_service.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHoldExpirationListener implements MessageListener {

    // show:{showId}:seat:{seatId}:owner 키는 매칭에서 제외 (owner 키는 :owner 접미사로 끝나므로 $ 앵커에서 자연히 배제됨)
    private static final Pattern SEAT_KEY_PATTERN = Pattern.compile("^show:([0-9a-fA-F-]+):seat:([0-9a-fA-F-]+)$");

    private final SeatService seatService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        Matcher matcher = SEAT_KEY_PATTERN.matcher(expiredKey);
        if (!matcher.matches()) {
            return;
        }

        UUID showId = UUID.fromString(matcher.group(1));
        UUID seatId = UUID.fromString(matcher.group(2));
        seatService.releaseExpiredHold(showId, seatId);
    }
}
