package com.zhibo.admin.org;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.org.dto.DeptTreeNode;
import com.zhibo.admin.org.dto.OrgEmployeeListResponse;
import com.zhibo.admin.org.dto.SetLiveEnabledResponse;
import com.zhibo.liveaccount.LiveAccountEntity;
import com.zhibo.liveaccount.LiveAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgServiceTest {

    @Mock
    private SysUimsbmxxRepository deptRepository;
    @Mock
    private HrUimsyhgwxxRepository positionRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private LiveAccountRepository liveAccountRepository;

    @InjectMocks
    private OrgService orgService;

    @Test
    void departmentTree_buildsParentChild() {
        when(deptRepository.findActiveEnabled()).thenReturn(List.of(
                dept(1L, "QKY", "钱坤云", 0L),
                dept(66L, "QKYSY01", "事业一部", 1L),
                dept(75L, "QKYSY0101", "销售部", 66L)
        ));

        List<DeptTreeNode> roots = orgService.departmentTree();
        assertEquals(1, roots.size());
        assertEquals("钱坤云", roots.get(0).getName());
        assertEquals("QKY", roots.get(0).getCode());
        assertEquals(1, roots.get(0).getChildren().size());
        assertEquals("事业一部", roots.get(0).getChildren().get(0).getName());
        assertEquals(1, roots.get(0).getChildren().get(0).getChildren().size());
    }

    @Test
    void listEmployees_onlyThatDept_andMissingLiveRowMeansEnabled() {
        when(deptRepository.findById(58L)).thenReturn(Optional.of(dept(58L, "QKYZB0102", "技术管理中心", 1L)));
        when(positionRepository.findDistinctYhidByBmid(58L)).thenReturn(List.of(1L, 19L));
        when(userRepository.findActiveByYhidIn(eq(List.of(1L, 19L)), isNull())).thenReturn(List.of(
                user(1L, 1L, "蔡奕坤", 10),
                user(19L, 19L, "王某", null)
        ));
        when(liveAccountRepository.findByUserIdIn(any())).thenReturn(List.of());

        OrgEmployeeListResponse resp = orgService.listEmployees(58L, null, 1, 20);
        assertEquals(2, resp.getTotal());
        assertEquals(2, resp.getList().size());
        assertTrue(resp.getList().get(0).isLiveEnabled());
        assertEquals("normal", resp.getList().get(0).getHqStatus());
        assertEquals("unknown", resp.getList().get(1).getHqStatus());
        assertEquals("技术管理中心", resp.getList().get(0).getDepartmentName());
    }

    @Test
    void listEmployees_respectsLiveDisabledRow() {
        when(deptRepository.findById(58L)).thenReturn(Optional.of(dept(58L, "QKYZB0102", "技术管理中心", 1L)));
        when(positionRepository.findDistinctYhidByBmid(58L)).thenReturn(List.of(19L));
        when(userRepository.findActiveByYhidIn(eq(List.of(19L)), isNull())).thenReturn(List.of(
                user(19L, 19L, "王某", 0)
        ));
        LiveAccountEntity disabled = new LiveAccountEntity();
        disabled.setUserId(19L);
        disabled.setEnabled(0);
        when(liveAccountRepository.findByUserIdIn(Set.of(19L))).thenReturn(List.of(disabled));

        OrgEmployeeListResponse resp = orgService.listEmployees(58L, null, 1, 20);
        assertFalse(resp.getList().get(0).isLiveEnabled());
        assertEquals("disabled", resp.getList().get(0).getHqStatus());
    }

    @Test
    void listEmployees_rejectsBadPage() {
        assertThrows(IllegalArgumentException.class, () -> orgService.listEmployees(1L, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> orgService.listEmployees(1L, null, 1, 0));
    }

    @Test
    void listEmployees_allDepts_joinsDepartmentNames() {
        when(positionRepository.findDistinctYhidAll()).thenReturn(List.of(1L, 19L));
        when(userRepository.findActiveByYhidIn(eq(List.of(1L, 19L)), isNull())).thenReturn(List.of(
                user(1L, 1L, "蔡奕坤", 10),
                user(19L, 19L, "王某", null)
        ));
        when(liveAccountRepository.findByUserIdIn(any())).thenReturn(List.of());
        HrUimsyhgwxxEntity p1 = position(1L, 1L, 58L);
        HrUimsyhgwxxEntity p2 = position(2L, 19L, 58L);
        HrUimsyhgwxxEntity p3 = position(3L, 19L, 66L);
        when(positionRepository.findActiveByYhidIn(any())).thenReturn(List.of(p1, p2, p3));
        when(deptRepository.findAllById(any())).thenReturn(List.of(
                dept(58L, "QKYZB0102", "技术管理中心", 1L),
                dept(66L, "QKYSY01", "事业一部", 1L)
        ));

        OrgEmployeeListResponse resp = orgService.listEmployees(null, null, 1, 20);
        assertEquals(2, resp.getTotal());
        assertEquals("技术管理中心", resp.getList().get(0).getDepartmentName());
        assertEquals("技术管理中心、事业一部", resp.getList().get(1).getDepartmentName());
    }

    private static HrUimsyhgwxxEntity position(Long id, Long yhid, Long bmid) {
        HrUimsyhgwxxEntity g = new HrUimsyhgwxxEntity();
        g.setId(id);
        g.setYhid(yhid);
        g.setBmid(bmid);
        g.setRsStatus(1);
        return g;
    }

    @Test
    void setLiveEnabled_upsertsRow() {
        when(userRepository.findById(19L)).thenReturn(Optional.of(user(19L, 19L, "王某", 10)));
        when(liveAccountRepository.findById(19L)).thenReturn(Optional.empty());
        when(liveAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SetLiveEnabledResponse resp = orgService.setLiveEnabled(19L, false, 1L);
        assertFalse(resp.isLiveEnabled());

        ArgumentCaptor<LiveAccountEntity> cap = ArgumentCaptor.forClass(LiveAccountEntity.class);
        verify(liveAccountRepository).save(cap.capture());
        assertEquals(19L, cap.getValue().getUserId());
        assertEquals(0, cap.getValue().getEnabled());
        assertEquals(1L, cap.getValue().getUpdatedBy());
    }

    @Test
    void mapHqStatus_values() {
        assertEquals("normal", OrgService.mapHqStatus(10));
        assertEquals("disabled", OrgService.mapHqStatus(0));
        assertEquals("unknown", OrgService.mapHqStatus(null));
        assertEquals("unknown", OrgService.mapHqStatus(99));
    }

    private static SysUimsbmxxEntity dept(Long id, String code, String name, Long parent) {
        SysUimsbmxxEntity d = new SysUimsbmxxEntity();
        d.setId(id);
        d.setBmbm(code);
        d.setBmmc(name);
        d.setFbmid(parent);
        d.setRsStatus(1);
        d.setQybz(1);
        return d;
    }

    private static SysUserAccountEntity user(Long id, Long yhid, String name, Integer shzt) {
        SysUserAccountEntity u = new SysUserAccountEntity();
        u.setId(id);
        u.setYhid(yhid);
        u.setUserName(name);
        u.setUserAccount("a" + id);
        u.setPassword("x");
        u.setRsStatus(1);
        u.setShzt(shzt);
        return u;
    }
}
