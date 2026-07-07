package com.fandom.feed.global.aspect;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.global.annotation.RequireRole;
import com.fandom.feed.global.constant.UserRole;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class AuthorizationAspect {
    @Before("@annotation(requireRole)")
    public void validateRole(JoinPoint joinPoint, RequireRole requireRole) {
        UserIdCard idCard = Arrays.stream(joinPoint.getArgs())
                .filter(arg -> arg instanceof UserIdCard)
                .map(arg -> (UserIdCard) arg)
                .findFirst()
                .orElseThrow(() -> new CustomException(CommonErrorCode.UNAUTHORIZED));

        UserRole[] roles = requireRole.value();
        boolean hasPermission = Arrays.stream(roles).anyMatch(role -> switch (role) {
            case MEMBER -> idCard.isMember();
            case CREATOR -> idCard.isCreator();
            case MASTER -> idCard.isMaster();
        });

        if (!hasPermission) throw new CustomException(CommonErrorCode.FORBIDDEN);
    }
}