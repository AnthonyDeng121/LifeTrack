package com.lifetrack.service;

import com.lifetrack.config.DevelopmentAuthPolicy;
import com.lifetrack.dto.PhoneLoginRequest;
import com.lifetrack.entity.User;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.UserRepository;
import com.lifetrack.service.impl.AuthServiceImpl;
import com.lifetrack.util.JwtUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.env.MockEnvironment;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    UserRepository users;
    JwtUtil jwt;
    MockEnvironment environment;
    AuthServiceImpl service;
    @BeforeEach void setup() {
        users = mock(UserRepository.class); jwt = mock(JwtUtil.class);
        environment = new MockEnvironment(); environment.setActiveProfiles("dev");
        service = new AuthServiceImpl(users, jwt, new DevelopmentAuthPolicy(environment, true, true, "654321"));
    }
    PhoneLoginRequest phone(String value, String code) {
        var request = new PhoneLoginRequest(); request.setPhone(value); request.setVerifyCode(code); return request;
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"1", "abc", "12345678901", "138000000000"})
    void rejectsInvalidPhoneWithoutTouchingDatabase(String number) {
        assertEquals(400, assertThrows(BusinessException.class, () -> service.loginWithPhone(phone(number, "654321"))).getCode());
        verifyNoInteractions(users, jwt);
    }
    @Test void incorrectCodeIsClientError() {
        assertEquals(400, assertThrows(BusinessException.class, () -> service.loginWithPhone(phone("13800001234", "111111"))).getCode());
        verifyNoInteractions(users, jwt);
    }
    @Test void usesConfiguredDemoCodeAndPreservesExistingUser() {
        var existing = User.builder().id(9L).username("existing").build();
        when(users.findByPhone("13800001234")).thenReturn(Optional.of(existing)); when(jwt.generateToken(9L,"existing")).thenReturn("test-token");
        assertEquals(9L, service.loginWithPhone(phone("13800001234", "654321")).getUserId());
        verify(users, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"prod", "test", "staging", "dev,prod", "dev,test", ""})
    void environmentCannotBeBypassedByEnablingBothSwitches(String profile) {
        environment.setActiveProfiles(profile.isEmpty() ? new String[0] : profile.split(","));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.loginDev()).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> service.loginWithPhone(phone("13800001234","654321"))).getCode());
        verifyNoInteractions(users, jwt);
    }
    @Test void developmentSwitchesAreIndependentAndCanBeTurnedOff() {
        var off = new DevelopmentAuthPolicy(environment,false,false,"654321");
        assertFalse(off.devLoginEnabled()); assertFalse(off.demoPhoneEnabled()); assertNull(off.demoCode());
        var onlyPhone = new DevelopmentAuthPolicy(environment,false,true,"654321");
        assertFalse(onlyPhone.devLoginEnabled()); assertTrue(onlyPhone.demoPhoneEnabled());
        environment.setActiveProfiles("prod"); assertNull(onlyPhone.demoCode());
    }
    @Test void diagnosticTextDoesNotExposePhoneCodeOrToken() {
        String request = phone("13800001234", "654321").toString();
        assertFalse(request.contains("13800001234")); assertFalse(request.contains("654321"));
        assertFalse(com.lifetrack.dto.LoginResponse.builder().token("secret-test-token").build().toString().contains("secret-test-token"));
        assertFalse(new com.lifetrack.dto.AuthOptionsResponse(true,true,"654321").toString().contains("654321"));
    }
}
