package com.zhibo.admin.org;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.org.dto.DeptTreeNode;
import com.zhibo.admin.org.dto.OrgEmployeeItem;
import com.zhibo.admin.org.dto.OrgEmployeeListResponse;
import com.zhibo.admin.org.dto.SetLiveEnabledResponse;
import com.zhibo.liveaccount.LiveAccountEntity;
import com.zhibo.liveaccount.LiveAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 员工组织：CRM 三表只读联查 + 本站 live_account 启停。 */
@Service
@RequiredArgsConstructor
public class OrgService {

    private final SysUimsbmxxRepository deptRepository;
    private final HrUimsyhgwxxRepository positionRepository;
    private final SysUserAccountRepository userRepository;
    private final LiveAccountRepository liveAccountRepository;

    public List<DeptTreeNode> departmentTree() {
        List<SysUimsbmxxEntity> rows = deptRepository.findActiveEnabled();
        Map<Long, DeptTreeNode> nodes = new LinkedHashMap<>();
        for (SysUimsbmxxEntity row : rows) {
            DeptTreeNode node = new DeptTreeNode();
            node.setId(Long.toString(row.getId()));
            node.setName(row.getBmmc());
            node.setCode(row.getBmbm());
            long parent = row.getFbmid() == null ? 0L : row.getFbmid();
            node.setParentId(Long.toString(parent));
            nodes.put(row.getId(), node);
        }
        List<DeptTreeNode> roots = new ArrayList<>();
        for (SysUimsbmxxEntity row : rows) {
            DeptTreeNode node = nodes.get(row.getId());
            long parentId = row.getFbmid() == null ? 0L : row.getFbmid();
            DeptTreeNode parent = parentId == 0L ? null : nodes.get(parentId);
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * @param deptId 部门 id；{@code null} 表示「全部」（有在职岗位的全部账号）
     */
    public OrgEmployeeListResponse listEmployees(Long deptId, String userName, int page, int pageSize) {
        if (deptId != null && deptId <= 0) {
            throw new IllegalArgumentException("deptId 无效");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page 从 1 开始");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 范围 1～100");
        }

        OrgEmployeeListResponse response = new OrgEmployeeListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);

        final boolean allDepts = deptId == null;
        String singleDeptName = null;
        List<Long> yhids;
        if (allDepts) {
            yhids = positionRepository.findDistinctYhidAll();
        } else {
            Optional<SysUimsbmxxEntity> deptOpt = deptRepository.findById(deptId);
            if (deptOpt.isEmpty()) {
                response.setTotal(0);
                return response;
            }
            singleDeptName = deptOpt.get().getBmmc();
            yhids = positionRepository.findDistinctYhidByBmid(deptId);
        }
        if (yhids.isEmpty()) {
            response.setTotal(0);
            return response;
        }

        String nameFilter = (userName == null || userName.isBlank()) ? null : userName.trim();
        List<SysUserAccountEntity> users = userRepository.findActiveByYhidIn(yhids, nameFilter);
        Map<Long, SysUserAccountEntity> unique = new LinkedHashMap<>();
        for (SysUserAccountEntity u : users) {
            unique.putIfAbsent(u.getId(), u);
        }
        List<SysUserAccountEntity> ordered = new ArrayList<>(unique.values());
        response.setTotal(ordered.size());

        int from = (page - 1) * pageSize;
        if (from >= ordered.size()) {
            return response;
        }
        int to = Math.min(from + pageSize, ordered.size());
        List<SysUserAccountEntity> pageRows = ordered.subList(from, to);

        Set<Long> ids = pageRows.stream().map(SysUserAccountEntity::getId).collect(Collectors.toSet());
        Map<Long, Boolean> liveMap = loadLiveEnabled(ids);

        Map<Long, String> deptNameByYhid = allDepts
                ? loadDeptNamesByYhid(pageRows.stream().map(SysUserAccountEntity::getYhid).collect(Collectors.toSet()))
                : Map.of();

        for (SysUserAccountEntity u : pageRows) {
            OrgEmployeeItem item = new OrgEmployeeItem();
            item.setId(Long.toString(u.getId()));
            item.setUserName(u.getUserName());
            if (allDepts) {
                item.setDepartmentName(deptNameByYhid.getOrDefault(u.getYhid(), ""));
            } else {
                item.setDepartmentName(singleDeptName);
            }
            item.setLiveEnabled(liveMap.getOrDefault(u.getId(), true));
            item.setHqStatus(mapHqStatus(u.getShzt()));
            response.getList().add(item);
        }
        return response;
    }

    @Transactional
    public SetLiveEnabledResponse setLiveEnabled(long userId, Boolean enabled, long operatorId) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 必填");
        }
        SysUserAccountEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        if (!Objects.equals(user.getRsStatus(), 1)) {
            throw new IllegalArgumentException("员工非在职");
        }

        LiveAccountEntity row = liveAccountRepository.findById(userId).orElseGet(LiveAccountEntity::new);
        row.setUserId(userId);
        row.setEnabled(enabled ? 1 : 0);
        row.setUpdatedBy(operatorId);
        liveAccountRepository.save(row);

        SetLiveEnabledResponse response = new SetLiveEnabledResponse();
        response.setId(Long.toString(userId));
        response.setLiveEnabled(enabled);
        return response;
    }

    private Map<Long, String> loadDeptNamesByYhid(Collection<Long> yhids) {
        Map<Long, String> result = new HashMap<>();
        if (yhids == null || yhids.isEmpty()) {
            return result;
        }
        List<Long> idList = yhids.stream().filter(Objects::nonNull).distinct().toList();
        if (idList.isEmpty()) {
            return result;
        }
        List<HrUimsyhgwxxEntity> positions = positionRepository.findActiveByYhidIn(idList);
        Set<Long> bmids = positions.stream().map(HrUimsyhgwxxEntity::getBmid).collect(Collectors.toSet());
        Map<Long, String> bmmcById = new HashMap<>();
        for (SysUimsbmxxEntity d : deptRepository.findAllById(bmids)) {
            bmmcById.put(d.getId(), d.getBmmc());
        }
        Map<Long, LinkedHashSet<String>> namesByYhid = new HashMap<>();
        for (HrUimsyhgwxxEntity g : positions) {
            String name = bmmcById.get(g.getBmid());
            if (name == null || name.isBlank()) {
                continue;
            }
            namesByYhid.computeIfAbsent(g.getYhid(), k -> new LinkedHashSet<>()).add(name);
        }
        for (Map.Entry<Long, LinkedHashSet<String>> e : namesByYhid.entrySet()) {
            result.put(e.getKey(), String.join("、", e.getValue()));
        }
        return result;
    }

    private Map<Long, Boolean> loadLiveEnabled(Set<Long> userIds) {
        Map<Long, Boolean> map = new HashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        for (LiveAccountEntity row : liveAccountRepository.findByUserIdIn(userIds)) {
            map.put(row.getUserId(), Objects.equals(row.getEnabled(), 1));
        }
        return map;
    }

    static String mapHqStatus(Integer shzt) {
        if (shzt == null) {
            return "unknown";
        }
        if (shzt == 10) {
            return "normal";
        }
        if (shzt == 0) {
            return "disabled";
        }
        return "unknown";
    }
}
