package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应结果")
public class PageResponse<T> {
    @Schema(description = "总条数")
    private Long total;

    @Schema(description = "当前页数据")
    private List<T> list;

    public static <T> PageResponse<T> of(Long total, List<T> list) {
        return new PageResponse<>(total, list);
    }
}
