package com.lifetrack.service.impl;

import com.lifetrack.dto.LoginResponse;
import com.lifetrack.dto.PhoneLoginRequest;
import com.lifetrack.dto.WechatLoginRequest;
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

    @Override
    @Transactional
    public LoginResponse loginWithWechat(WechatLoginRequest request) {
        // 1. 调用微信接口获取 openid (这里 mock)
        log.info("Wechat login with code: {}", request.getCode());
        String openid = "mock_openid_" + request.getCode(); // 实际应调用微信 API

        // 2. 根据 openid 查找用户
        User user = userRepository.findByOpenid(openid)
                .orElseGet(() -> {
                    // 3. 如果用户不存在，则注册新用户
                    User newUser = User.builder()
                            .username("微信用户_" + UUID.randomUUID().toString().substring(0, 8))
                            .password("NOPASSWORD") // 第三方登录不设密码
                            .openid(openid)
                            .avatar("https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=default+avatar+icon&image_size=square")
                            .build();
                    return userRepository.save(newUser);
                });

        return createLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse loginWithPhone(PhoneLoginRequest request) {
        // 1. 验证手机验证码 (这里 mock)
        log.info("Phone login with phone: {}, code: {}", request.getPhone(), request.getVerifyCode());
        if (!"123456".equals(request.getVerifyCode())) {
            throw new RuntimeException("验证码错误");
        }

        // 2. 根据手机号查找用户
        User user = userRepository.findByPhone(request.getPhone())
                .orElseGet(() -> {
                    // 3. 如果用户不存在，则注册新用户
                    User newUser = User.builder()
                            .username("手机用户_" + request.getPhone().substring(7))
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
        log.info("Developer one-click login triggered");
        String devUsername = "dev_admin";
        
        User user = userRepository.findByUsername(devUsername)
                .orElseGet(() -> {
                    User devUser = User.builder()
                            .username(devUsername)
                            .password("dev_password")
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
