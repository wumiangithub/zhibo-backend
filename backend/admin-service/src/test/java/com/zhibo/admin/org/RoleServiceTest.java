package com.zhibo.admin.org;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.org.dto.RoleDetailResponse;
import com.zhibo.liverole.LiveRoleMenuEntity;
import com.zhibo.liverole.LiveRoleMenuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private SysUimsRoleRepository roleRepository;
    @Mock
    private SysUimsYhidRoleRepository yhidRoleRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private HrUimsyhgwxxRepository positionRepository;
    @Mock
    private SysUimsbmxxRepository deptRepository;
    @Mock
    private LiveRoleMenuRepository liveRoleMenuRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void list_returnsNumericId() {
        when(roleRepository.findActive()).thenReturn(List.of(role(1L, "系统管理员", "")));
        var item = roleService.list().get(0);
        assertEquals(1L, item.getId());
        assertEquals("系统管理员", item.getName());
    }

    @Test
    void detail_joinsAccountAndDepartment() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "系统管理员", "最高权限")));
        when(yhidRoleRepository.findActiveAccountIds(1L)).thenReturn(List.of(31L));
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(31L);
        user.setYhid(9L);
        user.setUserName("王五");
        user.setRsStatus(1);
        when(userRepository.findAllById(List.of(31L))).thenReturn(List.of(user));
        HrUimsyhgwxxEntity pos = new HrUimsyhgwxxEntity();
        pos.setYhid(9L);
        pos.setBmid(58L);
        pos.setRsStatus(1);
        when(positionRepository.findActiveByYhidIn(any())).thenReturn(List.of(pos));
        SysUimsbmxxEntity dept = new SysUimsbmxxEntity();
        dept.setId(58L);
        dept.setBmmc("技术管理中心");
        when(deptRepository.findAllById(any())).thenReturn(List.of(dept));
        when(liveRoleMenuRepository.findByRoleIdAndRsStatus(1L, 1)).thenReturn(List.of());

        RoleDetailResponse detail = roleService.detail(1L);
        assertEquals(1L, detail.getId());
        assertEquals("王五", detail.getMembers().get(0).getUserName());
        assertEquals("技术管理中心", detail.getMembers().get(0).getDepartmentName());
        assertEquals("系统管理员", detail.getName());
    }

    @Test
    void saveMenus_rejectsUnknownCode() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "系统管理员", "")));
        assertThrows(IllegalArgumentException.class,
                () -> roleService.saveMenus(1L, List.of("not-a-menu"), 1L));
    }

    @Test
    void saveMenus_replacesRows() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "系统管理员", "")));
        when(liveRoleMenuRepository.findByRoleIdAndRsStatus(1L, 1)).thenReturn(List.of());
        when(yhidRoleRepository.findActiveAccountIds(1L)).thenReturn(List.of());

        roleService.saveMenus(1L, List.of("live-manage", "live-manage"), 8L);
        verify(liveRoleMenuRepository).deleteByRoleId(1L);
        verify(liveRoleMenuRepository).save(any(LiveRoleMenuEntity.class));
    }

    @Test
    void saveMenus_pairsReportCreateWithReport() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "系统管理员", "")));
        when(liveRoleMenuRepository.findByRoleIdAndRsStatus(1L, 1)).thenReturn(List.of());
        when(yhidRoleRepository.findActiveAccountIds(1L)).thenReturn(List.of());

        roleService.saveMenus(1L, List.of("live-report"), 8L);
        verify(liveRoleMenuRepository, times(2)).save(any(LiveRoleMenuEntity.class));
    }

    private static SysUimsRoleEntity role(Long id, String name, String remark) {
        SysUimsRoleEntity role = new SysUimsRoleEntity();
        role.setId(id);
        role.setRoleName(name);
        role.setRemark(remark);
        role.setRsStatus(1);
        role.setRsDispindex(0);
        return role;
    }
}
