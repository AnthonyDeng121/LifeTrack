package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Schema(description = "手机号登录请求")
public class PhoneLoginRequest {
    @Schema(description = "手机号")
    @lombok.ToString.Exclude
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "1[3-9][0-9]{9}", message = "请输入有效的11位手机号")
    private String phone;
    
    @Schema(description = "验证码")
    @lombok.ToString.Exclude
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "[0-9]{6}", message = "验证码必须为6位数字")
    private String verifyCode;
}
