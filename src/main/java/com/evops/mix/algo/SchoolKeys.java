package com.evops.mix.algo;

import com.evops.mix.common.MixConstants;

/**
 * 学校分组键：优先学校编码，其次学校名称；都为空归为“无学校”，不计同校相邻。
 */
public final class SchoolKeys {

    private SchoolKeys() {
    }

    public static String of(MixStudent s) {
        if (s.getSchoolCode() != null && !s.getSchoolCode().trim().isEmpty()) {
            return s.getSchoolCode().trim();
        }
        if (s.getSchoolName() != null && !s.getSchoolName().trim().isEmpty()) {
            return s.getSchoolName().trim();
        }
        return MixConstants.NO_SCHOOL_KEY;
    }

    public static boolean isCountable(String key) {
        return !MixConstants.NO_SCHOOL_KEY.equals(key);
    }
}
