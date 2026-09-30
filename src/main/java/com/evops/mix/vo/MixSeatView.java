package com.evops.mix.vo;

import lombok.Data;

/**
 * 座次视图：一人一条，含考场号、座位号、全批次序号与相邻标记。
 */
@Data
public class MixSeatView {
    private Long registrationId;
    private String regNo;
    private String name;
    private String schoolCode;
    private String schoolName;
    private Long roomId;
    private String roomNo;
    private Integer seatNo;
    private Integer seatIndex;
    private String ticketNo;
    /** NONE / OK / SAME_LEFT / SAME_RIGHT / SAME_BOTH */
    private String adjacentFlag;
}
