package com.lifetrack.entity;

import com.lifetrack.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum PlanStatus {
    IN_PROGRESS(0, "进行中"),
    COMPLETED(1, "已完成"),
    NOT_STARTED(2, "未开始"),
    CANCELLED(3, "已取消"),
    EXPIRED(4, "已过期");

    private final int code;
    private final String description;

    public boolean canTransitionTo(PlanStatus target) {
        if (this == target) {
            return true;
        }
        Set<PlanStatus> allowed = switch (this) {
            case NOT_STARTED -> EnumSet.of(IN_PROGRESS, CANCELLED, EXPIRED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CANCELLED, EXPIRED);
            case COMPLETED, CANCELLED, EXPIRED -> EnumSet.noneOf(PlanStatus.class);
        };
        return allowed.contains(target);
    }

    public static PlanStatus fromCode(Integer code) {
        if (code == null) {
            return IN_PROGRESS;
        }
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "非法的计划状态编码: " + code));
    }

    public static PlanStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value) || status.description.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "非法的计划状态: " + value));
    }
}
