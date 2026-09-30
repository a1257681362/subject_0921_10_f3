package com.evops.mix.algo;

/**
 * 参与混编的学员（编排时的最小输入）。
 */
public class MixStudent {
    private final Long registrationId;
    private final String regNo;
    private final String name;
    private final String schoolCode;
    private final String schoolName;

    public MixStudent(Long registrationId, String regNo, String name,
                      String schoolCode, String schoolName) {
        this.registrationId = registrationId;
        this.regNo = regNo;
        this.name = name;
        this.schoolCode = schoolCode;
        this.schoolName = schoolName;
    }

    public Long getRegistrationId() { return registrationId; }
    public String getRegNo() { return regNo; }
    public String getName() { return name; }
    public String getSchoolCode() { return schoolCode; }
    public String getSchoolName() { return schoolName; }
}
