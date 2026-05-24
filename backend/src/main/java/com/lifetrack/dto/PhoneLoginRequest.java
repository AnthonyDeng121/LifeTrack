package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "手机号登录请求")
public class PhoneLoginRequest {
    @Schema(description = "手机号")
    private String phone;
    
    @Schema(description = "验证码")
    private String verifyCode;
}
