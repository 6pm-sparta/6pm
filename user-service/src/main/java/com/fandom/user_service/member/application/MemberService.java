package com.fandom.user_service.member.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.domain.entity.Creator;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.member.domain.exception.MemberErrorCode;
import com.fandom.user_service.member.domain.repository.CreatorRepository;
import com.fandom.user_service.member.domain.repository.UserRepository;
import com.fandom.user_service.member.presentation.dto.request.CreatorSignUpRequest;
import com.fandom.user_service.member.presentation.dto.request.SignUpRequest;
import com.fandom.user_service.member.presentation.dto.response.InternalMemberResponse;
import com.fandom.user_service.member.presentation.dto.response.SignUpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 일반회원 가입. role=MEMBER, status는 기본 ACTIVE.
     */
    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        validateEmailNotDuplicated(request.email());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = userRepository.save(
                User.builder()
                        .email(request.email())
                        .password(encodedPassword)
                        .role(Role.MEMBER)
                        .build()
        );

        return SignUpResponse.from(user);
    }

    /**
     * 크리에이터 가입. User(role=CREATOR) + Creator를 한 트랜잭션으로 생성.
     */
    @Transactional
    public SignUpResponse signUpCreator(CreatorSignUpRequest request) {
        validateEmailNotDuplicated(request.email());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = userRepository.save(
                User.builder()
                        .email(request.email())
                        .password(encodedPassword)
                        .role(Role.CREATOR)
                        .build()
        );
        creatorRepository.save(
                Creator.builder()
                        .user(user)
                        .agencyName(request.agencyName())
                        .build()
        );

        return SignUpResponse.from(user);
    }

    /**
     * 내부 회원 조회. (Auth Service의 로그인 검증용)
     * 비밀번호 해시를 포함하므로 내부 통신 전용이다.
     * 조회 전용 - 클래스 기본 @Transactional(readOnly = true) 적용.
     */
    public InternalMemberResponse findByEmailForInternal(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

        return InternalMemberResponse.from(user);
    }

    private void validateEmailNotDuplicated(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }
    }
}
