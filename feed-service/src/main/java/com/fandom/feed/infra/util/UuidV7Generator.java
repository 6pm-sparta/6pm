package com.fandom.feed.infra.util;

import com.fandom.feed.domain.util.IdGenerator;
import com.fasterxml.uuid.Generators;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidV7Generator implements IdGenerator {
    @Override
    public UUID generate() {
        return Generators.timeBasedEpochGenerator().generate();
    }
}