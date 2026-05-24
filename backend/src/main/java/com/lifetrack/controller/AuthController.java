package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.dto.LoginResponse;
import com.lifetrack.dto.PhoneLoginRequest;
import com.lifetrack.dto.WechatLoginRequest;
import com.lifetrack.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证管理", description = "用户登录与注册相关接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "微信登录", description = "通过微信 code 获取 token，若用户不存在则自动注册")
    @PostMapping("/login/wechat")
    public Result<LoginResponse> loginWithWechat(@RequestBody WechatLoginRequest request) {
        return Result.success(authService.loginWithWechat(request));
    }

    @Operation(summary = "手机号登录", description = "通过手机号和验证码登录，若用户不存在则自动注册")
    @PostMapping("/login/phone")
    public Result<LoginResponse> loginWithPhone(@RequestBody PhoneLoginRequest request) {
        return Result.success(authService.loginWithPhone(request));
    }

    @Operation(summary = "开发人员一键登录", description = "便捷获取 token，仅限开发环境使用")
    @PostMapping("/login/dev")
    public Result<LoginResponse> loginDev() {
        return Result.success(authService.loginDev());
    }
}
