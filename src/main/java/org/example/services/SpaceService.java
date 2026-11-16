package org.example.services;

import jakarta.transaction.Transactional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.client.ClickupClient;
import org.example.dto.SpaceResponseDto;
import org.example.entity.Space;
import org.example.entity.Workspace;
import org.example.mapper.SpaceMapper;
import org.example.repository.SpaceRepository;
import org.example.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpaceService {
    private final SpaceRepository spaceRepository;
    private final ClickupClient client;
    private final WorkspaceRepository workspaceRepository;
    private final SpaceMapper spaceMapper;
    private final SyncEventRecorder syncEventRecorder;

    @Transactional
    public void syncSpaces(String workspaceId, String userAccessToken) {

        SpaceResponseDto response = client.getSpaces(workspaceId, userAccessToken);

        if (response == null || response.spaces() == null) {
            throw new IllegalStateException("ClickUp spaces response was empty for workspace " + workspaceId);
        }

        Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace bulunamadı: " + workspaceId));

        for (SpaceResponseDto.SpaceDto spaceDto : response.spaces()) {

            Space space = spaceRepository.findById(spaceDto.id()).orElseGet(Space::new);
            String oldName = space.getName();
            Boolean oldPrivate = space.getIsPrivate();
            Boolean oldArchived = space.getArchived();
            Object oldStatuses = space.getStatuses();

            spaceMapper.updateEntityFromDto(spaceDto, space);
            if (spaceDto.statuses() != null) {
                space.setStatuses(spaceDto.statuses());
            }
            space.setWorkspace(workspace);
            spaceRepository.save(space);

            syncEventRecorder.recordHierarchyChange("SPACE", spaceDto.id(), "name", oldName, space.getName());
            syncEventRecorder.recordHierarchyChange(
                    "SPACE", spaceDto.id(), "is_private", oldPrivate, space.getIsPrivate());
            syncEventRecorder.recordHierarchyChange(
                    "SPACE", spaceDto.id(), "archived", oldArchived, space.getArchived());
            syncEventRecorder.recordHierarchyChange(
                    "SPACE", spaceDto.id(), "statuses", oldStatuses, space.getStatuses());
        }

        Set<String> currentSpaceIds =
                response.spaces().stream().map(SpaceResponseDto.SpaceDto::id).collect(Collectors.toSet());
        for (Space existingSpace : spaceRepository.findByWorkspaceId(workspaceId)) {
            if (!currentSpaceIds.contains(existingSpace.getId()) && !Boolean.TRUE.equals(existingSpace.getArchived())) {
                Boolean oldArchived = existingSpace.getArchived();
                existingSpace.setArchived(true);
                spaceRepository.save(existingSpace);
                syncEventRecorder.recordHierarchyChange(
                        "SPACE", existingSpace.getId(), "archived", oldArchived, true);
            }
        }
    }
}
