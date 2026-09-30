package com.evops.mix.algo;

/**
 * 考场布局：混编只关心考场身份与容量。
 */
public class MixRoom {
    private final Long roomId;
    private final String roomNo;
    private final int capacity;

    public MixRoom(Long roomId, String roomNo, int capacity) {
        this.roomId = roomId;
        this.roomNo = roomNo;
        this.capacity = capacity;
    }

    public Long getRoomId() { return roomId; }
    public String getRoomNo() { return roomNo; }
    public int getCapacity() { return capacity; }
}
