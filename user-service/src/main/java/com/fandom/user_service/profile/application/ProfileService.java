package com.fandom.user_service.profile.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.profile.domain.entity.Profile;
import com.fandom.user_service.profile.domain.exception.ProfileErrorCode;
import com.fandom.user_service.profile.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ProfileRepository profileRepository;

    @Transactional
    public Profile createInitialProfile(User user, String nickname) {
        validateNicknameNotDuplicated(nickname);

        return profileRepository.save(
                Profile.builder()
                        .user(user)
                        .nickname(nickname)
                        .build()
        );
    }

    private void validateNicknameNotDuplicated(String nickname) {
        if (profileRepository.existsByNickname(nickname)) {
            throw new CustomException(ProfileErrorCode.DUPLICATE_NICKNAME);
        }
    }
}
