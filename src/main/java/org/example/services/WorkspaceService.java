package org.example.services;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.client.ClickupClient;
import org.example.dto.UserDto;
import org.example.dto.WorkspaceResponseDto;
import org.example.entity.User;
import org.example.entity.Workspace;
import org.example.entity.WorkspaceMember;
import org.example.enums.ClickupRole;
import org.example.mapper.UserMapper;
import org.example.mapper.WorkspaceMapper;
import org.example.repository.UserRepository;
import org.example.repository.WorkspaceMemberRepository;
import org.example.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final ClickupClient client;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceMapper workspaceMapper;
    private final UserMapper userMapper;
    private final SyncEventRecorder syncEventRecorder;

    @Transactional
    public List<String> syncWorkspaces(String userAccessToken) {

        WorkspaceResponseDto response = client.getWorkspaces(userAccessToken);

        if (response == null || response.teams() == null) {
            return List.of();
        }

        List<String> workspaceIds = new ArrayList<>();

        for (WorkspaceResponseDto.WorkspaceDto teamDto : response.teams()) {
            workspaceIds.add(teamDto.id());

            Workspace workspace = workspaceRepository.findById(teamDto.id()).orElseGet(Workspace::new);
            String oldWorkspaceName = workspace.getName();

            workspaceMapper.updateEntityFromDto(teamDto, workspace);
            workspaceRepository.save(workspace);
            syncEventRecorder.recordHierarchyChange(
                    "WORKSPACE", workspace.getId(), "name", oldWorkspaceName, workspace.getName());
            if (teamDto.members() != null) {
                for (WorkspaceResponseDto.WorkspaceMemberDto memberDto : teamDto.members()) {
                    if (memberDto.user() == null) continue;

                    User user = saveOrUpdateUser(memberDto.user());
                    User invitedBy = saveOrUpdateUser(memberDto.invitedBy());

                    WorkspaceMember member = workspaceMemberRepository
                            .findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                            .orElseGet(WorkspaceMember::new);
                    Long oldInvitedById =
                            member.getInvitedBy() == null ? null : member.getInvitedBy().getId();
                    ClickupRole oldRole = member.getRole();
                    String oldDateInvited = member.getDateInvited();
                    String oldDateJoined = member.getDateJoined();
                    boolean isNewMember = member.getId() == null;

                    member.setWorkspace(workspace);
                    member.setUser(user);
                    member.setInvitedBy(invitedBy);
                    member.setRole(ClickupRole.fromKey(memberDto.user().roleKey()));
                    member.setDateInvited(memberDto.user().dateInvited());
                    member.setDateJoined(memberDto.user().dateJoined());

                    workspaceMemberRepository.save(member);
                    if (isNewMember) {
                        syncEventRecorder.recordHierarchyChange(
                                "WORKSPACE_MEMBER", workspace.getId(), "user_id", null, user.getId());
                    }
                    syncEventRecorder.recordHierarchyChange(
                            "WORKSPACE_MEMBER",
                            workspace.getId(),
                            "invited_by_user_id:" + user.getId(),
                            oldInvitedById,
                            invitedBy == null ? null : invitedBy.getId());
                    syncEventRecorder.recordHierarchyChange(
                            "WORKSPACE_MEMBER",
                            workspace.getId(),
                            "role:" + user.getId(),
                            oldRole,
                            member.getRole());
                    syncEventRecorder.recordHierarchyChange(
                            "WORKSPACE_MEMBER",
                            workspace.getId(),
                            "date_invited:" + user.getId(),
                            oldDateInvited,
                            member.getDateInvited());
                    syncEventRecorder.recordHierarchyChange(
                            "WORKSPACE_MEMBER",
                            workspace.getId(),
                            "date_joined:" + user.getId(),
                            oldDateJoined,
                            member.getDateJoined());
                }
            }
        }

        return List.copyOf(workspaceIds);
    }

    private User saveOrUpdateUser(UserDto userDto) {
        if (userDto == null) return null;

        User user = userRepository.findById(userDto.id()).orElseGet(User::new);
        String oldUsername = user.getUsername();
        String oldEmail = user.getEmail();
        String oldProfilePicture = user.getProfilePicture();

        userMapper.updateEntityFromDto(userDto, user);

        User savedUser = userRepository.save(user);
        syncEventRecorder.recordHierarchyChange(
                "USER", String.valueOf(savedUser.getId()), "username", oldUsername, savedUser.getUsername());
        syncEventRecorder.recordHierarchyChange(
                "USER", String.valueOf(savedUser.getId()), "email", oldEmail, savedUser.getEmail());
        syncEventRecorder.recordHierarchyChange(
                "USER",
                String.valueOf(savedUser.getId()),
                "profile_picture",
                oldProfilePicture,
                savedUser.getProfilePicture());
        return savedUser;
    }
}
