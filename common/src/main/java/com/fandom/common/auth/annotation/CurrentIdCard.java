package com.fandom.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 컨트롤러 파라미터에 붙이는 커스텀 어노테이션.
 * IdCardVerificationFilter에서 저장한 UserIdCard를 주입받는다.
 *
 * 사용 예시:
 * public ResponseEntity<?> getProfile(@CurrentIdCard UserIdCard idCard) { ... }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentIdCard {
}
