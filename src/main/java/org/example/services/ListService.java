package org.example.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.client.ClickupClient;
import org.example.dto.FolderResponseDto;
import org.example.dto.ListResponseDto;
import org.example.dto.StatusDto;
import org.example.entity.Folder;
import org.example.entity.Lists;
import org.example.entity.Space;
import org.example.mapper.ListMapper;
import org.example.repository.ListRepository;
import org.example.repository.SpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListService {

    private final ClickupClient client;
    private final ListRepository listRepository;
    private final SpaceRepository spaceRepository;
    private final ListMapper listMapper;
    private final SyncEventRecorder syncEventRecorder;

    @Transactional
    public Set<String> syncSpaceLists(String spaceId, String userAccessToken) {
        ListResponseDto response;

        try {
            log.info("Space ID: {} için klasörsüz listeler ClickUp API'den isteniyor...", spaceId);
            response = client.getFolderlessLists(spaceId, userAccessToken);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn(
                    "Space ID: {} listeleri için yetki bulunamadı (401 Unauthorized / OAUTH_192). Hata: {}",
                    spaceId,
                    e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Space ID: {} listeleri çekilirken hata oluştu.", spaceId, e);
            throw new IllegalStateException("ClickUp listeleri senkronize edilemedi: " + spaceId, e);
        }

        if (response == null || response.lists() == null) {
            throw new IllegalStateException("ClickUp lists response was empty for space " + spaceId);
        }

        Space space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new RuntimeException("Space veritabanında bulunamadı: " + spaceId));

        Set<String> currentListIds = new HashSet<>();
        for (ListResponseDto.ListDto listDto : response.lists()) {
            currentListIds.add(listDto.id());
            Lists list = listRepository.findById(listDto.id()).orElseGet(Lists::new);
            String oldName = list.getName();
            String oldSpaceId = list.getSpace() == null ? null : list.getSpace().getId();
            String oldFolderId = list.getFolder() == null ? null : list.getFolder().getId();
            String oldStartDate = list.getStartDate();
            String oldDueDate = list.getDueDate();
            Boolean oldArchived = list.getArchived();
            Object oldStatuses = list.getStatuses();

            listMapper.updateEntityFromDto(listDto, list);

            list.setSpace(space);
            list.setFolder(null); // Klasörsüz liste
            list.setStatuses(resolveStatuses(
                    listDto.id(), listDto.overrideStatuses(), listDto.statuses(), space, userAccessToken));

            listRepository.save(list);
            recordListChanges(
                    list,
                    oldName,
                    oldSpaceId,
                    oldFolderId,
                    oldStartDate,
                    oldDueDate,
                    oldArchived,
                    oldStatuses);
        }

        log.info(
                "Space ID: {} için {} adet klasörsüz liste başarıyla senkronize edildi.",
                spaceId,
                response.lists().size());
        return currentListIds;
    }

    public void saveFolderList(FolderResponseDto.ListDto listDto, Folder folder, Space space) {
        Lists list = listRepository.findById(listDto.id()).orElseGet(Lists::new);
        String oldName = list.getName();
        String oldSpaceId = list.getSpace() == null ? null : list.getSpace().getId();
        String oldFolderId = list.getFolder() == null ? null : list.getFolder().getId();
        String oldStartDate = list.getStartDate();
        String oldDueDate = list.getDueDate();
        Boolean oldArchived = list.getArchived();
        Object oldStatuses = list.getStatuses();
        listMapper.updateEntityFromDto(listDto, list);
        list.setFolder(folder);
        list.setSpace(space);
        list.setStatuses(listDto.statuses() == null ? List.of() : listDto.statuses());
        listRepository.save(list);
        recordListChanges(list, oldName, oldSpaceId, oldFolderId, oldStartDate, oldDueDate, oldArchived, oldStatuses);
    }

    private void recordListChanges(
            Lists list,
            String oldName,
            String oldSpaceId,
            String oldFolderId,
            String oldStartDate,
            String oldDueDate,
            Boolean oldArchived,
            Object oldStatuses) {
        syncEventRecorder.recordHierarchyChange("LIST", list.getId(), "name", oldName, list.getName());
        syncEventRecorder.recordHierarchyChange(
                "LIST",
                list.getId(),
                "space_id",
                oldSpaceId,
                list.getSpace() == null ? null : list.getSpace().getId());
        syncEventRecorder.recordHierarchyChange(
                "LIST",
                list.getId(),
                "folder_id",
                oldFolderId,
                list.getFolder() == null ? null : list.getFolder().getId());
        syncEventRecorder.recordHierarchyChange("LIST", list.getId(), "start_date", oldStartDate, list.getStartDate());
        syncEventRecorder.recordHierarchyChange("LIST", list.getId(), "due_date", oldDueDate, list.getDueDate());
        syncEventRecorder.recordHierarchyChange("LIST", list.getId(), "archived", oldArchived, list.getArchived());
        syncEventRecorder.recordHierarchyChange("LIST", list.getId(), "statuses", oldStatuses, list.getStatuses());
    }

    public void archiveMissingLists(String spaceId, Set<String> currentListIds) {
        for (Lists existingList : listRepository.findBySpaceId(spaceId)) {
            if (!currentListIds.contains(existingList.getId()) && !Boolean.TRUE.equals(existingList.getArchived())) {
                Boolean oldArchived = existingList.getArchived();
                existingList.setArchived(true);
                listRepository.save(existingList);
                syncEventRecorder.recordHierarchyChange(
                        "LIST", existingList.getId(), "archived", oldArchived, true);
            }
        }
    }

    private List<StatusDto> resolveStatuses(
            String listId, Boolean overrideStatuses, List<StatusDto> listStatuses, Space space, String accessToken) {
        if (!Boolean.TRUE.equals(overrideStatuses)) {
            return space.getStatuses() == null ? List.of() : space.getStatuses();
        }

        if (listStatuses != null && !listStatuses.isEmpty()) {
            return listStatuses;
        }

        log.info("Liste {} özel statüleri yanıt içinde yok; liste detayı isteniyor.", listId);
        ListResponseDto.ListDto details = client.getList(listId, accessToken);
        if (details == null || details.statuses() == null) {
            throw new IllegalStateException("ClickUp liste detayında statüler bulunamadı: " + listId);
        }
        return details.statuses();
    }
}
