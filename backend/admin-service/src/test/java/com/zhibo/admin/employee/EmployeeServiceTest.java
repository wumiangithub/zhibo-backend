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
        when(userRepository.searchActive(isNull(), eq(PageRequest.of(0, 20)))).thenReturn(List.of(user));

        EmployeeListResponse response = employeeService.list("  ");
        assertEquals(1, response.getList().size());
        assertEquals("10001", response.getList().get(0).getId());
        assertEquals("余灵巧", response.getList().get(0).getUserName());
        verify(userRepository).searchActive(isNull(), eq(PageRequest.of(0, 20)));
    }
}
