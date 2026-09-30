package com.evops.mix.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 混编算法版本：版本 + 状态。
 * DRAFT 草稿 / ACTIVE 生效（同一模块同时刻仅一版生效） / DISABLED 停用。
 * 发布后 params_json（含种子）冻结，永不修改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_mix_algorithm_version")
public class MixAlgorithmVersion extends BaseEntity {
    private Long algorithmId;
    private String versionNo;
    /** DRAFT / ACTIVE / DISABLED */
    private String status;
    /** 算法实现标识（Spring bean 名），如 schoolShuffleV1 */
    private String implKey;
    /** 冻结参数 JSON：{"seed":...}；种子保存在这里，座次可复现 */
    private String paramsJson;
    private String remark;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String disabledBy;
    private LocalDateTime disabledAt;
    private String operator;
}
