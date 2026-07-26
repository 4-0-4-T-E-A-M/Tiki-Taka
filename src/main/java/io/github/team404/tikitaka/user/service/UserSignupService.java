package io.github.team404.tikitaka.user.service;

import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.dto.request.OAuthUserSignupRequest;
import io.github.team404.tikitaka.user.dto.response.OAuthUserSignupResponse;
import io.github.team404.tikitaka.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserSignupService {
    private final UserRepository userRepository;

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
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
