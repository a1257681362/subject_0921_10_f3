package com.evops.mix.algo;

/**
 * 一条座次结果：学员落在某考场某座号；seatIndex 为全批次连续序号（跨考场不断号）。
 */
public class MixAssignedSeat {
    private final MixStudent student;
    private final MixRoom room;
    private final int seatNo;
    private final int seatIndex;

    public MixAssignedSeat(MixStudent student, MixRoom room, int seatNo, int seatIndex) {
        this.student = student;
        this.room = room;
        this.seatNo = seatNo;
        this.seatIndex = seatIndex;
    }

    public MixStudent getStudent() { return student; }
    public MixRoom getRoom() { return room; }
    public int getSeatNo() { return seatNo; }
    public int getSeatIndex() { return seatIndex; }
}
