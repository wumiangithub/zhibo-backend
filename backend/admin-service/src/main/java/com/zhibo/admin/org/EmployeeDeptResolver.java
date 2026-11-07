package com.zhibo.admin.org;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
        Optional<SysUserAccountEntity> accountOpt = userRepository.findById(employeeId);
        if (accountOpt.isEmpty()) {
            return Optional.empty();
        }
        Long yhid = accountOpt.get().getYhid();
        if (yhid == null || yhid <= 0) {
            return Optional.empty();
        }
        List<HrUimsyhgwxxEntity> positions = positionRepository.findActiveByYhidIn(List.of(yhid));
        if (positions == null || positions.isEmpty()) {
            return Optional.empty();
        }
        HrUimsyhgwxxEntity first = positions.stream()
                .filter(g -> g.getBmid() != null && g.getBmid() > 0)
                .min(Comparator.comparing(HrUimsyhgwxxEntity::getId, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
        if (first == null) {
            return Optional.empty();
        }
        return deptRepository.findById(first.getBmid())
                .filter(d -> d.getBmmc() != null && !d.getBmmc().isBlank())
                .map(d -> new Dept(d.getId(), d.getBmmc().trim()));
    }

    public record Dept(long deptId, String deptName) {}
}
