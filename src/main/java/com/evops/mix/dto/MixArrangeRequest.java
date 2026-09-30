package com.evops.mix.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 混编编排 / 重排请求。
 */
@Data
public class MixArrangeRequest {
    @NotNull(message = "批次不能为空")
    private Long batchId;
    /**
     * 指定混编算法版本 id；为空时取适用项目当前 ACTIVE 版本。
     */
    private Long algorithmVersionId;
    /**
     * 重排时指定新种子：为空则随机换新种子（整改重排默认换种子）。
     */
    private Long seed;
    /** 可选：指定考场使用顺序（考场号），不填按考场号升序。 */
    private List<String> roomOrder;
    /** 重排原因（整改通报等），记进编排记录 remark。 */
    private String reason;
}
