package com.evops.mix.vo;

import lombok.Data;

import java.util.List;

/**
 * 混编编排 / 重排执行结果：整批成功（否则整批拒收、不落半条）。
 */
@Data
public class MixArrangeResult {
    private String action;
    private Long arrangementId;
    private Long batchId;
    private String batchCode;
    /** INITIAL / REARRANGED */
    private String arrangeKind;
    private Long algorithmVersionId;
    private String versionNo;
    private String implKey;
    private long seed;

    private int affectedCount;
    private int roomCount;
    private int usedSeats;
    private int ticketsReplaced;
    private int printedReset;

    private String operator;
    private String executedAt;
    private List<String> affectedKeys;

    private int adjacentPairs;
    private int sameSchoolPairs;
    private int sameSchoolPeople;
    private List<MixSchoolStat> schoolStats;

    private String summary;
}
