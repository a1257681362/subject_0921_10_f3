package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RegistrationRequest {
    @NotBlank(message = "报名号不能为空")
    private String regNo;
    @NotBlank(message = "姓名不能为空")
    private String name;
    private String idType;
    private String idNo;
    private String gender;
    private String phone;
    private String subject;
    /** 学员所在学校（同校学员需错开相邻座位）；编码与名称至少给一个，建议都给 */
    private String schoolCode;
    private String schoolName;
    private String remark;
}
