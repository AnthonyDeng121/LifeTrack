package com.lifetrack.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class TaskBatchDeleteRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@NotNull @Positive Long> taskIds;
}
