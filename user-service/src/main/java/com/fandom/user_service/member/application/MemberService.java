package com.fandom.user_service.member.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.domain.entity.Creator;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.member.domain.exception.MemberErrorCode;
import com.fandom.user_service.member.domain.repository.CreatorRepository;
import com.fandom.user_service.member.domain.repository.UserRepository;
import com.fandom.user_service.member.presentation.dto.request.CreatorSignUpRequest;
import com.fandom.user_service.member.presentation.dto.request.CreatorUpdateRequest;
import com.fandom.user_service.member.presentation.dto.request.MemberUpdateRequest;
import com.fandom.user_service.member.presentation.dto.request.SignUpRequest;
import com.fandom.user_service.member.presentation.dto.response.CreatorSignUpResponse;
import com.fandom.user_service.member.presentation.dto.response.CreatorUpdateResponse;
import com.fandom.user_service.member.presentation.dto.response.InternalMemberResponse;
import com.fandom.user_service.member.presentation.dto.response.MemberSignUpResponse;
import com.fandom.user_service.member.presentation.dto.response.MemberUpdateResponse;
import com.fandom.user_service.profile.application.ProfileService;
import com.fandom.user_service.profile.domain.entity.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileService profileService;

    /**
     * 일반회원 가입. role=MEMBER, status는 기본 ACTIVE.
     */
    @Transactional
    public MemberSignUpResponse signUp(SignUpRequest request) {
        validateEmailNotDuplicated(request.email());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = userRepository.save(
                User.builder()
                        .email(request.email())
                        .password(encodedPassword)
                        .role(Role.MEMBER)
                        .zipCode(request.zipCode())
                        .address1(request.address1())
                        .address2(request.address2())
                        .build()
        );
        Profile profile = profileService.createInitialProfile(user, request.nickname());

        return MemberSignUpResponse.from(user, profile);
    }

    /**
     * 크리에이터 가입. User(role=CREATOR) + Creator를 한 트랜잭션으로 생성.
     */
    @Transactional
    public CreatorSignUpResponse signUpCreator(CreatorSignUpRequest request) {
        validateEmailNotDuplicated(request.email());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = userRepository.save(
                User.builder()
                        .email(request.email())
                        .password(encodedPassword)
                        .role(Role.CREATOR)
                        .zipCode(request.zipCode())
                        .address1(request.address1())
                        .address2(request.address2())
                        .build()
        );
        Creator creator = creatorRepository.save(
                Creator.builder()
                        .user(user)
                        .agencyName(request.agencyName())
                        .build()
        );
        Profile profile = profileService.createInitialProfile(user, request.nickname());

        return CreatorSignUpResponse.from(user, profile, creator);
    }

    /**
     * 일반회원 계정 정보 수정. User 공통 계정 필드만 수정한다.
     */
    @Transactional
    public MemberUpdateResponse updateMember(UUID userId, MemberUpdateRequest request) {
        User user = findUserById(userId);
        validateRole(user, Role.MEMBER);

        String email = resolveEmailForUpdate(user, request.email());
        String password = encodePasswordIfPresent(request.password());

        user.updateAccount(email, password, request.zipCode(), request.address1(), request.address2());

        return MemberUpdateResponse.from(user);
    }

    /**
     * 크리에이터 계정 정보 수정. User 공통 계정 필드와 Creator 부가정보를 수정한다.
     */
    @Transactional
    public CreatorUpdateResponse updateCreator(UUID userId, CreatorUpdateRequest request) {
        User user = findUserById(userId);
        validateRole(user, Role.CREATOR);

        Creator creator = creatorRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.CREATOR_NOT_FOUND));

        String email = resolveEmailForUpdate(user, request.email());
        String password = encodePasswordIfPresent(request.password());

        user.updateAccount(email, password, request.zipCode(), request.address1(), request.address2());
        if (request.agencyName() != null) {
            creator.updateAgencyName(request.agencyName());
        }

        return CreatorUpdateResponse.from(user, creator);
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

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateEmailNotDuplicated(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateEmailNotDuplicatedForUpdate(String email, UUID userId) {
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void validateRole(User user, Role role) {
        if (user.getRole() != role) {
            throw new CustomException(MemberErrorCode.FORBIDDEN_MEMBER_ACCESS);
        }
    }

    private String resolveEmailForUpdate(User user, String email) {
        if (email == null) {
            return null;
        }
        if (email.equals(user.getEmail())) {
            return email;
        }
        validateEmailNotDuplicatedForUpdate(email, user.getId());
        return email;
    }

    private String encodePasswordIfPresent(String password) {
        if (password == null) {
            return null;
        }
        return passwordEncoder.encode(password);
    }
}
