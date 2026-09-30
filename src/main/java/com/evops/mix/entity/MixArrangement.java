package com.evops.mix.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 混编编排批次记录：每批次一条，保存所用算法版本与实际种子。
 * 重排时整条作废新建记录（uk_mix_arrangement_batch），不留半新半旧。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_mix_arrangement")
public class MixArrangement extends BaseEntity {
    private Long batchId;
    private Long algorithmId;
    private Long algorithmVersionId;
    private String versionNo;
    private String implKey;
    /** 本次排座实际使用的混编种子 */
    private Long seed;
    /** INITIAL 首次编排 / REARRANGED 整改重排 */
    private String arrangeKind;
    private Integer totalCount;
    private Integer roomCount;
    private Integer usedSeats;
    /** 同校相邻统计 */
    private Integer adjacentPairs;
    private Integer sameSchoolPairs;
    private Integer sameSchoolPeople;
    /** ACTIVE 当前有效 / SUPERSEDED 已被整批重排取代 */
    private String status;
    private String operator;
    private LocalDateTime arrangedAt;
    private String remark;
}
