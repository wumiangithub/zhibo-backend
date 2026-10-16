package com.zhibo.admin.org;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.org.dto.RoleDetailResponse;
import com.zhibo.admin.org.dto.RoleListItem;
import com.zhibo.admin.org.dto.RoleMemberItem;
import com.zhibo.liverole.LiveRoleMenuEntity;
import com.zhibo.liverole.LiveRoleMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 角色只读共用主系统表；功能权限只写本站 {@code live_role_menu}。 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysUimsRoleRepository roleRepository;
    private final SysUimsYhidRoleRepository yhidRoleRepository;
    private final SysUserAccountRepository userRepository;
    private final HrUimsyhgwxxRepository positionRepository;
    private final SysUimsbmxxRepository deptRepository;
    private final LiveRoleMenuRepository liveRoleMenuRepository;

    public List<RoleListItem> list() {
        List<RoleListItem> list = new ArrayList<>();
        for (SysUimsRoleEntity role : roleRepository.findActive()) {
            RoleListItem item = new RoleListItem();
            item.setId(role.getId());
            item.setName(role.getRoleName());
            list.add(item);
        }
        return list;
    }

    public RoleDetailResponse detail(long roleId) {
        SysUimsRoleEntity role = requireActive(roleId);
        RoleDetailResponse response = new RoleDetailResponse();
        response.setId(role.getId());
        response.setName(role.getRoleName());
        response.setRemark(role.getRemark() == null ? "" : role.getRemark());
        response.setMenus(LiveMenuCatalog.tree());
        response.setMenuCodes(liveRoleMenuRepository.findByRoleIdAndRsStatus(roleId, 1).stream()
                .map(LiveRoleMenuEntity::getMenuCode)
                .toList());
        response.setMembers(loadMembers(roleId));
        return response;
    }

    @Transactional
    public RoleDetailResponse saveMenus(long roleId, List<String> menuCodes, long operatorId) {
        requireActive(roleId);
        List<String> codes = menuCodes == null ? List.of() : menuCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        for (String code : codes) {
            if (!LiveMenuCatalog.known(code)) {
                throw new IllegalArgumentException("未知菜单：" + code);
            }
        }
        liveRoleMenuRepository.deleteByRoleId(roleId);
        for (String code : codes) {
            LiveRoleMenuEntity row = new LiveRoleMenuEntity();
            row.setRsStatus(1);
            row.setRoleId(roleId);
            row.setMenuCode(code);
            row.setUpdatedBy(operatorId);
            liveRoleMenuRepository.save(row);
        }
        return detail(roleId);
    }

    private SysUimsRoleEntity requireActive(long roleId) {
        SysUimsRoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        if (!Objects.equals(role.getRsStatus(), 1)) {
            throw new IllegalArgumentException("角色已停用");
        }
        return role;
    }

    private List<RoleMemberItem> loadMembers(long roleId) {
        List<Long> accountIds = yhidRoleRepository.findActiveAccountIds(roleId);
        if (accountIds.isEmpty()) {
            return List.of();
        }
        List<SysUserAccountEntity> users = userRepository.findAllById(accountIds).stream()
                .filter(u -> Objects.equals(u.getRsStatus(), 1))
                .toList();
        Map<Long, String> deptByYhid = loadDeptNames(
                users.stream().map(SysUserAccountEntity::getYhid).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<RoleMemberItem> members = new ArrayList<>();
        for (SysUserAccountEntity user : users) {
            RoleMemberItem item = new RoleMemberItem();
            item.setId(Long.toString(user.getId()));
            item.setUserName(user.getUserName());
            item.setDepartmentName(user.getYhid() == null ? "" : deptByYhid.getOrDefault(user.getYhid(), ""));
            members.add(item);
        }
        return members;
    }

    private Map<Long, String> loadDeptNames(Collection<Long> yhids) {
        Map<Long, String> result = new HashMap<>();
        if (yhids.isEmpty()) {
            return result;
        }
        List<HrUimsyhgwxxEntity> positions = positionRepository.findActiveByYhidIn(yhids);
        Set<Long> bmids = positions.stream().map(HrUimsyhgwxxEntity::getBmid).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        for (SysUimsbmxxEntity dept : deptRepository.findAllById(bmids)) {
            names.put(dept.getId(), dept.getBmmc());
        }
        Map<Long, LinkedHashSet<String>> grouped = new HashMap<>();
        for (HrUimsyhgwxxEntity row : positions) {
            String name = names.get(row.getBmid());
            if (name == null || name.isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(row.getYhid(), key -> new LinkedHashSet<>()).add(name);
        }
        for (Map.Entry<Long, LinkedHashSet<String>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), String.join("、", entry.getValue()));
        }
        return result;
    }
}
