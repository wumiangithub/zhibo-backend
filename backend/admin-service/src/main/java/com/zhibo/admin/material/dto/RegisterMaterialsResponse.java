package com.zhibo.admin.material.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterMaterialsResponse {
    private List<MaterialItemResponse> list;
}
