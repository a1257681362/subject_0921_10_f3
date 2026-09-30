package com.evops.mix.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class MixAlgorithmRequest {
    @NotBlank(message = "混编算法模块编码不能为空")
    private String code;
    @NotBlank(message = "混编算法模块名称不能为空")
    private String name;
    /** 适用项目编码，缺省 DEFAULT（与批次 project_code 对齐） */
    private String projectCode;
    private String description;
    private String remark;
}
