package com.evops.mix.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 混编座次：一人一条。重排先整批删旧座次、再整批写新座次，
 * 同一个 arrangement_id 下绝无半新半旧；同时同步覆盖 t_admission_ticket。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_mix_arrangement_seat")
public class MixArrangementSeat extends BaseEntity {
    private Long arrangementId;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    private String schoolCode;
    private String schoolName;
    private Long roomId;
    private String roomNo;
    private Integer seatNo;
    /** 全批次座位序列位置（跨考场按考场顺序连续编号），相邻判定/导出用 */
    private Integer seatIndex;
    private String ticketNo;
    /** NONE 无相邻 / OK 非同校 / SAME_LEFT / SAME_RIGHT / SAME_BOTH */
    private String adjacentFlag;
}
