package com.zhibo.admin.org;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 员工部门：账号 {@code rs_mainkey_id → yhid} → 岗位表 {@code hr_uimsyhgwxx.bmid} →
 * 部门表 {@code sys_uimsbmxx}。
 * <strong>禁止</strong>读 {@code sys_useraccount.qw_bmxx}。
 */
@Component
@RequiredArgsConstructor
public class EmployeeDeptResolver {

    private final SysUserAccountRepository userRepository;
    private final HrUimsyhgwxxRepository positionRepository;
    private final SysUimsbmxxRepository deptRepository;

    /**
     * 取该员工在职岗位中 id 最小的一条对应部门（稳定、单值，对齐订单中心单个 deptId）。
     * 无账号 / 无岗位 / 部门缺失 → empty。
     *
     * @param employeeId 账号主键 {@code sys_useraccount.rs_mainkey_id}（中控台 JWT 员工 id）
     */
    public Optional<Dept> resolvePrimary(long employeeId) {
        if (employeeId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolvePrimaryBatch(List.of(employeeId)).get(employeeId));
    }

    /**
     * 批量解析员工第一部门，口径与 {@link #resolvePrimary(long)} 相同，避免 N+1。
     */
    public Map<Long, Dept> resolvePrimaryBatch(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : employeeIds) {
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SysUserAccountEntity> accounts = userRepository.findAllById(ids);
        Map<Long, Long> employeeToYhid = new LinkedHashMap<>();
        for (SysUserAccountEntity account : accounts) {
            Long yhid = account.getYhid();
            if (yhid != null && yhid > 0) {
                employeeToYhid.put(account.getId(), yhid);
            }
        }
        if (employeeToYhid.isEmpty()) {
            return Map.of();
        }
        Set<Long> yhids = new LinkedHashSet<>(employeeToYhid.values());
        List<HrUimsyhgwxxEntity> positions = positionRepository.findActiveByYhidIn(new ArrayList<>(yhids));
        Map<Long, HrUimsyhgwxxEntity> yhidToPosition = new HashMap<>();
        if (positions != null) {
            for (HrUimsyhgwxxEntity g : positions) {
                if (g.getYhid() == null || g.getBmid() == null || g.getBmid() <= 0) {
                    continue;
                }
                HrUimsyhgwxxEntity existing = yhidToPosition.get(g.getYhid());
                if (existing == null
                        || (g.getId() != null
                                && (existing.getId() == null || g.getId() < existing.getId()))) {
                    yhidToPosition.put(g.getYhid(), g);
                }
            }
        }
        Set<Long> bmids = new LinkedHashSet<>();
        for (HrUimsyhgwxxEntity g : yhidToPosition.values()) {
            bmids.add(g.getBmid());
        }
        Map<Long, SysUimsbmxxEntity> deptRows = new HashMap<>();
        if (!bmids.isEmpty()) {
            for (SysUimsbmxxEntity d : deptRepository.findAllById(bmids)) {
                if (d.getBmmc() != null && !d.getBmmc().isBlank()) {
                    deptRows.put(d.getId(), d);
                }
            }
        }
        Map<Long, Dept> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Long> e : employeeToYhid.entrySet()) {
            HrUimsyhgwxxEntity pos = yhidToPosition.get(e.getValue());
            if (pos == null) {
                continue;
            }
            SysUimsbmxxEntity dept = deptRows.get(pos.getBmid());
            if (dept == null) {
                continue;
            }
            result.put(e.getKey(), new Dept(dept.getId(), dept.getBmmc().trim()));
        }
        return result;
    }

    public record Dept(long deptId, String deptName) {}
}
