package com.fandom.feed.presentation.dto.request.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ImageKeyValidator implements ConstraintValidator<ValidImageKey, String> {
    private static final String REGEX = "^posts/\\d{8}/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[a-zA-Z]{3,4}$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // null 허용
        return value.matches(REGEX);
    }
}