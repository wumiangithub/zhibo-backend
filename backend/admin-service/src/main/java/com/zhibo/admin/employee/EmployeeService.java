package com.zhibo.admin.employee;

import com.zhibo.admin.auth.AuthService;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.employee.dto.EmployeeItem;
import com.zhibo.admin.employee.dto.EmployeeListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/** 归属人候选：只读已有员工表，不建表、不插入。 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final int LIMIT = 20;

    private final SysUserAccountRepository userRepository;

    public EmployeeListResponse list(String keyword) {
        String filter = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<SysUserAccountEntity> rows = userRepository.searchActive(filter, PageRequest.of(0, LIMIT));
        EmployeeListResponse response = new EmployeeListResponse();
        response.setList(rows.stream().map(this::toItem).toList());
        return response;
    }

    private EmployeeItem toItem(SysUserAccountEntity user) {
        EmployeeItem item = new EmployeeItem();
        item.setId(Long.toString(user.getId()));
        item.setUserName(user.getUserName());
        item.setAvatar(AuthService.resolveAvatar(user));
        item.setDepartmentName(AuthService.resolveDepartmentName(user));
        return item;
    }
}
