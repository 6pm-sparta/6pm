package com.fandom.feed.infra.s3;

import com.fandom.feed.application.event.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class S3ImageDeleteEventListener {
    private final S3Service s3Service;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleS3ImageDelete(Event.S3ImageDelete event) {
        s3Service.deleteAll(event.imageKeys());
    }
}