package com.fandom.feed.presentation.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Size(max = 4)
@ReportAsSingleViolation
public @interface ValidImageLimit {
    String message() default "이미지는 최대 4개까지 가능합니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}