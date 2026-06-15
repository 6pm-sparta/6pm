package com.fandom.common.auth.resolver;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @CurrentIdCard 어노테이션이 붙은 컨트롤러 파라미터에 UserIdCard를 주입하는 ArgumentResolver.
 * IdCardVerificationFilter에서 저장한 request attribute를 꺼내 주입한다.
 */
public class CurrentIdCardArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentIdCard.class)
                && parameter.getParameterType().equals(UserIdCard.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        UserIdCard idCard = (UserIdCard) request.getAttribute(IdCardVerificationFilter.ID_CARD_ATTRIBUTE);

        if (idCard == null) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }

        return idCard;
    }
}
