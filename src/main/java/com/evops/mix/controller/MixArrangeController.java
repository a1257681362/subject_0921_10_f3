package com.evops.mix.controller;

import com.evops.common.ApiResponse;
import com.evops.mix.dto.MixArrangeRequest;
import com.evops.mix.entity.MixArrangement;
import com.evops.mix.entity.MixArrangementSeat;
import com.evops.mix.service.MixArrangeService;
import com.evops.mix.vo.MixArrangePreview;
import com.evops.mix.vo.MixArrangeResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混编编排：首次编排与整改重排都分两步（preview → execute）；
 * 重排整批删除重建，不留半新半旧；提供座次与同校相邻统计供巡考抽查。
 */
@RestController
@RequestMapping("/api/mix/arrange")
public class MixArrangeController {

    private final MixArrangeService arrangeService;

    public MixArrangeController(MixArrangeService arrangeService) {
        this.arrangeService = arrangeService;
    }

    // ---------- 首次混编编排 ----------

    @PostMapping("/preview")
    public ApiResponse<MixArrangePreview> preview(@Valid @RequestBody MixArrangeRequest request) {
        return ApiResponse.ok(arrangeService.preview(request, false));
    }

    @PostMapping
    public ApiResponse<MixArrangeResult> arrange(@Valid @RequestBody MixArrangeRequest request) {
        return ApiResponse.ok(arrangeService.execute(request, false));
    }

    // ---------- 整改重排（整批来） ----------

    @PostMapping("/rearrange/preview")
    public ApiResponse<MixArrangePreview> rearrangePreview(@Valid @RequestBody MixArrangeRequest request) {
        return ApiResponse.ok(arrangeService.preview(request, true));
    }

    @PostMapping("/rearrange")
    public ApiResponse<MixArrangeResult> rearrange(@Valid @RequestBody MixArrangeRequest request) {
        return ApiResponse.ok(arrangeService.execute(request, true));
    }

    // ---------- 查询：当前座次 ----------

    @GetMapping("/seats")
    public ApiResponse<List<MixArrangementSeat>> seats(@RequestParam Long batchId) {
        MixArrangement arrangement = arrangeService.requireActiveArrangement(batchId);
        return ApiResponse.ok(arrangeService.listSeats(arrangement.getId()));
    }

    // ---------- 统计：同校相邻（巡考抽查） ----------

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam Long batchId) {
        MixArrangement arrangement = arrangeService.requireActiveArrangement(batchId);
        List<MixArrangementSeat> seats = arrangeService.listSeats(arrangement.getId());

        int samePairs = arrangement.getSameSchoolPairs() == null ? 0 : arrangement.getSameSchoolPairs();
        int adjacentPairs = arrangement.getAdjacentPairs() == null ? 0 : arrangement.getAdjacentPairs();
        int involved = arrangement.getSameSchoolPeople() == null ? 0 : arrangement.getSameSchoolPeople();

        // 逐校明细（座次表已落 adjacent_flag，这里直接按座位顺序汇总）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchId", arrangement.getBatchId());
        data.put("arrangementId", arrangement.getId());
        data.put("arrangeKind", arrangement.getArrangeKind());
        data.put("versionNo", arrangement.getVersionNo());
        data.put("implKey", arrangement.getImplKey());
        data.put("seed", arrangement.getSeed());
        data.put("totalCount", arrangement.getTotalCount());
        data.put("roomCount", arrangement.getRoomCount());
        data.put("adjacentPairs", adjacentPairs);
        data.put("sameSchoolPairs", samePairs);
        data.put("sameSchoolPeople", involved);
        data.put("sameSchoolRatio", adjacentPairs == 0 ? 0
                : Math.round(samePairs * 10000.0 / adjacentPairs) / 100.0);
        data.put("schoolStats", summarizeBySchool(seats));
        return ApiResponse.ok(data);
    }

    private List<Map<String, Object>> summarizeBySchool(List<MixArrangementSeat> seats) {
        Map<String, Map<String, Object>> bySchool = new LinkedHashMap<>();
        Map<String, java.util.Set<Long>> rooms = new LinkedHashMap<>();
        for (MixArrangementSeat seat : seats) {
            String key = seat.getSchoolCode() != null ? seat.getSchoolCode()
                    : (seat.getSchoolName() != null ? seat.getSchoolName() : null);
            if (key == null) {
                continue;
            }
            Map<String, Object> row = bySchool.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("schoolCode", seat.getSchoolCode());
                m.put("schoolName", seat.getSchoolName());
                m.put("people", 0);
                m.put("sameSchoolPairs", 0);
                m.put("involvedPeople", 0);
                return m;
            });
            row.put("people", (int) row.get("people") + 1);
            rooms.computeIfAbsent(key, k -> new java.util.HashSet<>()).add(seat.getRoomId());
            boolean sameLeft = "SAME_LEFT".equals(seat.getAdjacentFlag())
                    || "SAME_BOTH".equals(seat.getAdjacentFlag());
            if (sameLeft) {
                row.put("sameSchoolPairs", (int) row.get("sameSchoolPairs") + 1);
            }
            boolean involvedFlag = sameLeft
                    || "SAME_RIGHT".equals(seat.getAdjacentFlag());
            if (involvedFlag) {
                row.put("involvedPeople", (int) row.get("involvedPeople") + 1);
            }
        }
        for (Map.Entry<String, Map<String, Object>> e : bySchool.entrySet()) {
            e.getValue().put("roomCount", rooms.getOrDefault(e.getKey(), new java.util.HashSet<>()).size());
        }
        return new java.util.ArrayList<>(bySchool.values());
    }
}
