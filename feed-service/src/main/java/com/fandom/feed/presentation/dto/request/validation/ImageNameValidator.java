package com.fandom.feed.presentation.dto.request.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ImageNameValidator implements ConstraintValidator<ValidImageName, String> {
    private static final String REGEX = "^.+\\.(jpg|jpeg|png|gif|webp)$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return false;
        return value.toLowerCase().matches(REGEX);
    }
}