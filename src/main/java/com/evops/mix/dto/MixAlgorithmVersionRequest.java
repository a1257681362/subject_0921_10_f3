package com.evops.mix.dto;

import lombok.Data;

/**
 * 新建混编算法版本：算法实现标识 + 参数（含种子）。
 * seed 为空时由系统按批次/时间生成；一旦发布参数冻结。
 */
@Data
public class MixAlgorithmVersionRequest {
    /** 算法实现标识，如 schoolShuffleV1；为空则用模块默认实现 */
    private String implKey;
    /** 混编种子；为空系统生成。同版本 + 同名册 + 同考场，同种子座次可复现 */
    private Long seed;
    /** 贪心交换最大轮数（可空，算法给默认值） */
    private Integer maxRounds;
    private String remark;
}
