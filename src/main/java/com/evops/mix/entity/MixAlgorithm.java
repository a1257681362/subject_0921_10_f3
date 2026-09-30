package com.evops.mix.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 混编算法模块：一个模块挂多个版本；模块本身只有 ENABLED / DISABLED 管理状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_mix_algorithm")
public class MixAlgorithm extends BaseEntity {
    private String code;
    private String name;
    /** 适用范围：DEFAULT 通用，或与批次 project_code 对齐的项目编码 */
    private String projectCode;
    private String description;
    /** ENABLED 启用 / DISABLED 停用 */
    private String status;
    private String remark;
}
