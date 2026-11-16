package org.example.mapper;

import org.example.dto.WebhookResponseDto;
import org.example.entity.Webhook;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WebhookMapper {
    WebhookResponseDto toDto(Webhook entity);

    Webhook toEntity(WebhookResponseDto dto);
}
