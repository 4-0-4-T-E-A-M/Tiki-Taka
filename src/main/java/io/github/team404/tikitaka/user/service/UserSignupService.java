package io.github.team404.tikitaka.user.service;

import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.dto.request.OAuthUserSignupRequest;
import io.github.team404.tikitaka.user.dto.response.OAuthUserSignupResponse;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import io.github.team404.tikitaka.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSignupService {
    private final UserRepository userRepository;

    public boolean existsById(Long userId) {
        return userRepository.existsById(userId);
    }

    public void validateExists(Long userId) {
        if (!existsById(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // 요청 정보가 들어왔을때 사용자가 존재하지 않는다면 회원가입한다.
    public OAuthUserSignupResponse signupIfAbsent(
            OAuthUserSignupRequest request
    ){
        User user = userRepository
                .findByProviderAndProviderId(
                OAuthProvider.GOOGLE,
                request.getProviderId()
                )
                .orElseGet(() -> saveNewUser(request));
        return OAuthUserSignupResponse.from(user);
    }


    private User saveNewUser(OAuthUserSignupRequest request){
        User newUser = new User(
                request.getEmail(),
                request.getName(),
                OAuthProvider.GOOGLE,
                request.getProviderId()
        );
        return userRepository.save(newUser);
    }
}
