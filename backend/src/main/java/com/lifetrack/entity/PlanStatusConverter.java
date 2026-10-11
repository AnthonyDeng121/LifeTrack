package com.lifetrack.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PlanStatusConverter implements AttributeConverter<PlanStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(PlanStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public PlanStatus convertToEntityAttribute(Integer dbData) {
        return PlanStatus.fromCode(dbData);
    }
}
