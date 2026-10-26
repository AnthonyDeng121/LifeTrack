package com.lifetrack.dto;

public record AuthOptionsResponse(boolean devLoginEnabled, boolean demoPhoneEnabled, String demoVerifyCode) {
    @Override public String toString() {
        return "AuthOptionsResponse[devLoginEnabled=" + devLoginEnabled + ", demoPhoneEnabled=" + demoPhoneEnabled + "]";
    }
}
