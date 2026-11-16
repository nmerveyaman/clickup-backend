package org.example.services;

import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.example.client.ClickupClient;
import org.example.dto.FolderResponseDto;
import org.example.entity.Folder;
import org.example.entity.Space;
import org.example.mapper.FolderMapper;
import org.example.repository.FolderRepository;
import org.example.repository.SpaceRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final SpaceRepository spaceRepository;
    private final ClickupClient client;
    private final FolderMapper folderMapper;
    private final ListService listService;
    private final SyncEventRecorder syncEventRecorder;

    @Transactional
    public Set<String> syncFolders(String spaceId, String userAccessToken) {

        FolderResponseDto response = client.getFolders(spaceId, userAccessToken);

        if (response == null || response.folders() == null) {
            throw new IllegalStateException("ClickUp folders response was empty for space " + spaceId);
        }

        Space space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new RuntimeException("Space bulunamadı: " + spaceId));

        Set<String> currentFolderIds = new HashSet<>();
        Set<String> currentListIds = new HashSet<>();
        for (FolderResponseDto.FolderDto folderDto : response.folders()) {
            currentFolderIds.add(folderDto.id());
            if (folderDto.lists() == null) {
                throw new IllegalStateException("ClickUp folder lists were missing for folder " + folderDto.id());
            }
            Folder folder = folderRepository.findById(folderDto.id()).orElseGet(Folder::new);
            String oldName = folder.getName();
            String oldSpaceId = folder.getSpace() == null ? null : folder.getSpace().getId();
            Boolean oldHidden = folder.getHidden();
            Boolean oldArchived = folder.getArchived();

            folderMapper.updateEntityFromDto(folderDto, folder);
            folder.setSpace(space);

            folderRepository.save(folder);
            syncEventRecorder.recordHierarchyChange("FOLDER", folderDto.id(), "name", oldName, folder.getName());
            syncEventRecorder.recordHierarchyChange(
                    "FOLDER", folderDto.id(), "space_id", oldSpaceId, folder.getSpace().getId());
            syncEventRecorder.recordHierarchyChange(
                    "FOLDER", folderDto.id(), "hidden", oldHidden, folder.getHidden());
            syncEventRecorder.recordHierarchyChange(
                    "FOLDER", folderDto.id(), "archived", oldArchived, folder.getArchived());

            for (FolderResponseDto.ListDto listDto : folderDto.lists()) {
                currentListIds.add(listDto.id());
                listService.saveFolderList(listDto, folder, space);
            }
        }

        for (Folder existingFolder : folderRepository.findBySpaceId(spaceId)) {
            if (!currentFolderIds.contains(existingFolder.getId())
                    && !Boolean.TRUE.equals(existingFolder.getArchived())) {
                Boolean oldArchived = existingFolder.getArchived();
                existingFolder.setArchived(true);
                folderRepository.save(existingFolder);
                syncEventRecorder.recordHierarchyChange(
                        "FOLDER", existingFolder.getId(), "archived", oldArchived, true);
            }
        }
        return currentListIds;
    }
}
