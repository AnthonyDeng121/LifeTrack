package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "微信登录请求")
public class WechatLoginRequest {
    @Schema(description = "微信授权码 (code)")
    private String code;
}
