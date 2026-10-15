package com.zhibo.admin.employee;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.employee.dto.EmployeeListResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private SysUserAccountRepository userRepository;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void list_mapsIdAsDecimalString() {
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(10001L);
        user.setRsStatus(1);
        user.setUserName("余灵巧");
        user.setPassword("secret");
        user.setHeadimgurl("https://wx.example/a.jpg");
        when(userRepository.searchActive(isNull(), eq(PageRequest.of(0, 20)))).thenReturn(List.of(user));

        EmployeeListResponse response = employeeService.list("  ");
        assertEquals(1, response.getList().size());
        assertEquals("10001", response.getList().get(0).getId());
        assertEquals("余灵巧", response.getList().get(0).getUserName());
        assertEquals("https://wx.example/a.jpg", response.getList().get(0).getAvatar());
        verify(userRepository).searchActive(isNull(), eq(PageRequest.of(0, 20)));
    }

    @Test
    void list_fallsBackToUserAvatarWhenHeadimgurlBlank() {
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(10002L);
        user.setRsStatus(1);
        user.setUserName("张三");
        user.setPassword("secret");
        user.setHeadimgurl("  ");
        user.setUserAvatar("https://cdn.example/b.png");
        when(userRepository.searchActive(isNull(), eq(PageRequest.of(0, 20)))).thenReturn(List.of(user));

        EmployeeListResponse response = employeeService.list(null);
        assertEquals("https://cdn.example/b.png", response.getList().get(0).getAvatar());
    }

    @Test
    void list_mapsDepartmentNameFromQwBmxx() {
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(10003L);
        user.setRsStatus(1);
        user.setUserName("王-模拟");
        user.setPassword("secret");
        user.setQwBmxx(
                "[{\"is_leader\":false,\"department_id\":41,\"department_name\":\"技术测试部门001\"},"
                        + "{\"is_leader\":false,\"department_id\":43,\"department_name\":\"技术测试部门005\"}]");
        when(userRepository.searchActive(isNull(), eq(PageRequest.of(0, 20)))).thenReturn(List.of(user));

        EmployeeListResponse response = employeeService.list(null);
        assertEquals("技术测试部门001、技术测试部门005", response.getList().get(0).getDepartmentName());
    }
}
