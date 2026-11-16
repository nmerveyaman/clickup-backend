package org.example.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Lists;
import org.example.entity.Space;
import org.example.repository.ListRepository;
import org.example.repository.SpaceRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickUpFullSyncService {

    private final SpaceService spaceService;
    private final FolderService folderService;
    private final ListService listService;
    private final TaskService taskService;

    private final SpaceRepository spaceRepository;
    private final ListRepository listRepository;

    public void syncWorkspaceData(String userId, String workspaceId, String accessToken) {
        log.info("User {} için workspace {} ClickUp senkronizasyonu başlatılıyor.", userId, workspaceId);

        try {
            spaceService.syncSpaces(workspaceId, accessToken);
            List<Space> workspaceSpaces = spaceRepository.findByWorkspaceId(workspaceId);

            for (Space space : workspaceSpaces) {
                if (Boolean.TRUE.equals(space.getArchived())) {
                    continue;
                }
                Set<String> currentListIds = new HashSet<>(folderService.syncFolders(space.getId(), accessToken));
                currentListIds.addAll(listService.syncSpaceLists(space.getId(), accessToken));
                listService.archiveMissingLists(space.getId(), currentListIds);
            }
            log.info("Workspace {} için klasör ve listeler senkronize edildi.", workspaceId);

            for (Space space : workspaceSpaces) {
                if (Boolean.TRUE.equals(space.getArchived())) {
                    continue;
                }
                for (Lists list : listRepository.findBySpaceId(space.getId())) {
                    if (Boolean.TRUE.equals(list.getArchived())) {
                        continue;
                    }
                    taskService.syncListTasks(list.getId(), accessToken);
                }
            }

            log.info("Workspace {} ClickUp verileri başarıyla senkronize edildi.", workspaceId);

        } catch (Exception e) {
            log.error("Workspace {} ClickUp senkronizasyonu başarısız oldu.", workspaceId, e);
            throw new RuntimeException("ClickUp workspace senkronizasyonu tamamlanamadı: " + workspaceId, e);
        }
    }
}
