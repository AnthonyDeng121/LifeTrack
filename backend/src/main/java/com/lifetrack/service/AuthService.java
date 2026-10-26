package com.lifetrack.service;

import com.lifetrack.dto.LoginResponse;
import com.lifetrack.dto.PhoneLoginRequest;

public interface AuthService {
    LoginResponse loginWithPhone(PhoneLoginRequest request);
    LoginResponse loginDev();
}
