package com.evops.mix.vo;

import lombok.Data;

import java.util.List;

/**
 * 混编编排 / 重排预检：不落库，展示算法、种子、考场分摊、座次方案与同校相邻统计。
 */
@Data
public class MixArrangePreview {
    private String action;
    private Long batchId;
    private String batchCode;
    /** INITIAL / REARRANGED */
    private String arrangeKind;
    private Long algorithmVersionId;
    private String versionNo;
    private String implKey;
    /** 本次将使用的种子 */
    private long seed;

    private int totalScanned;
    private int totalCapacity;
    private int usedSeats;
    private int roomCount;
    /** 容量缺口（>0 时整批拒收） */
    private int shortage;

    /** 重排影响：将整批删除重建的准考证数 / 其中已打印需重打的张数（已发放则禁止重排） */
    private int ticketsToReplace;
    private int printedToReset;

    private String summary;
    /** 同校相邻总统计 */
    private int adjacentPairs;
    private int sameSchoolPairs;
    private int sameSchoolPeople;
    private List<MixSchoolStat> schoolStats;
    /** 各考场分摊人数 */
    private List<RoomUsage> roomUsages;
    /** 逐人座次方案 */
    private List<MixSeatView> seats;

    @Data
    public static class RoomUsage {
        private Long roomId;
        private String roomNo;
        private int capacity;
        private int used;
    }
}
