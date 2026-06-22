package com.fandom.user_service.member.presentation.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreatorUpdateRequest 검증 테스트")
class CreatorUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("모든 필드가 null이면 검증에 실패한다")
    void allFieldsNull_invalid() {
        // given
        CreatorUpdateRequest request = new CreatorUpdateRequest(null, null, null, null, null, null);

        // when & then
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("빈 문자열이 있으면 검증에 실패한다")
    void blankField_invalid() {
        // given
        CreatorUpdateRequest request = new CreatorUpdateRequest(null, null, "", null, null, null);

        // when & then
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("하나 이상의 유효한 필드가 있으면 검증에 성공한다")
    void valid() {
        // given
        CreatorUpdateRequest request = new CreatorUpdateRequest(null, null, "소속사", null, null, null);

        // when & then
        assertThat(validator.validate(request)).isEmpty();
    }
}
