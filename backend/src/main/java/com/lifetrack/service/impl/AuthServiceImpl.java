package com.lifetrack.service.impl;

import com.lifetrack.dto.LoginResponse;
import com.lifetrack.dto.PhoneLoginRequest;
import com.lifetrack.config.DevelopmentAuthPolicy;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.entity.User;
import com.lifetrack.repository.UserRepository;
import com.lifetrack.service.AuthService;
import com.lifetrack.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final DevelopmentAuthPolicy authPolicy;

    @Override
    @Transactional
    public LoginResponse loginWithPhone(PhoneLoginRequest request) {
        authPolicy.requireDemoPhone();
        if (request == null || request.getPhone() == null || !request.getPhone().matches("1[3-9][0-9]{9}")) {
            throw new BusinessException(400, "请输入有效的11位手机号");
        }
        if (request.getVerifyCode() == null || !request.getVerifyCode().matches("[0-9]{6}")) {
            throw new BusinessException(400, "验证码必须为6位数字");
        }
        if (!authPolicy.demoCode().equals(request.getVerifyCode())) {
            throw new BusinessException(400, "验证码错误");
        }

        // 2. 根据手机号查找用户
        User user = userRepository.findByPhone(request.getPhone())
                .orElseGet(() -> {
                    // 3. 如果用户不存在，则注册新用户
                    User newUser = User.builder()
                            .username("demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                            .password("NOPASSWORD")
                            .phone(request.getPhone())
                            .avatar("https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=default+avatar+icon&image_size=square")
                            .build();
                    return userRepository.save(newUser);
                });

        return createLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse loginDev() {
        authPolicy.requireDevLogin();
        log.info("Developer login permitted in development environment");
        String devUsername = "dev_admin";
        
        User user = userRepository.findByUsername(devUsername)
                .orElseGet(() -> {
                    User devUser = User.builder()
                            .username(devUsername)
                            .password("DISABLED_DEVELOPMENT_PASSWORD")
                            .avatar("https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=developer+avatar&image_size=square")
                            .build();
                    return userRepository.save(devUser);
                });

        return createLoginResponse(user);
    }

    private LoginResponse createLoginResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .build();
    }
}
