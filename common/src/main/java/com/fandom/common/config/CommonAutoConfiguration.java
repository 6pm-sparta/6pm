package com.fandom.common.config;

import com.fandom.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET) // TODO: 임시 설정으로 추후 삭제 예정
@Import(GlobalExceptionHandler.class)
public class CommonAutoConfiguration {
}
