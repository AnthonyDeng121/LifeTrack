package com.lifetrack.service;

import com.lifetrack.dto.LoginResponse;
import com.lifetrack.dto.PhoneLoginRequest;
import com.lifetrack.dto.WechatLoginRequest;

public interface AuthService {
    LoginResponse loginWithWechat(WechatLoginRequest request);
    LoginResponse loginWithPhone(PhoneLoginRequest request);
    LoginResponse loginDev();
}
