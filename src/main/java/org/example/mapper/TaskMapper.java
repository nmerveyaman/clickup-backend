package org.example.mapper;

import org.example.dto.TaskResponseDto;
import org.example.entity.Task;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "status.status", target = "statusName")
    @Mapping(source = "status.type", target = "statusType")
    @Mapping(source = "priority.priority", target = "priority")
    @Mapping(target = "list", ignore = true)
    @Mapping(target = "workspace", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "topLevelParent", ignore = true)
    @Mapping(target = "blockedByTask", ignore = true)
    @Mapping(target = "assignees", ignore = true)
    @Mapping(target = "tags", expression = "java(dto.tags() != null ? dto.tags().stream().map(t -> t.name()).toList() : null)")
    void updateEntityFromDto(TaskResponseDto.TaskDto dto, @MappingTarget Task entity);
}