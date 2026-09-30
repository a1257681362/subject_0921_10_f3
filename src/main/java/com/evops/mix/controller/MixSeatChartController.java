package com.evops.mix.controller;

import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.ExamSiteService;
import com.evops.mix.entity.MixArrangement;
import com.evops.mix.entity.MixArrangementSeat;
import com.evops.mix.service.MixArrangeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 座次表导出打印：按考场分页，含考场号、座位号、准考证号、姓名、学校与同校相邻标记。
 * 走 HTTP Basic（浏览器弹框输入 bootstrap/bootstrap），不放开匿名，避免名册外泄。
 */
@Controller
@RequestMapping("/print/mix")
public class MixSeatChartController {

    private final MixArrangeService arrangeService;
    private final ExamBatchService batchService;
    private final ExamSiteService siteService;

    public MixSeatChartController(MixArrangeService arrangeService,
                                  ExamBatchService batchService,
                                  ExamSiteService siteService) {
        this.arrangeService = arrangeService;
        this.batchService = batchService;
        this.siteService = siteService;
    }

    @GetMapping("/seat-chart")
    public String seatChart(@RequestParam Long batchId, Model model) {
        ExamBatch batch = batchService.getBatch(batchId);
        ExamSite site = siteService.getSite(batch.getSiteId());
        MixArrangement arrangement = arrangeService.requireActiveArrangement(batchId);
        List<MixArrangementSeat> seats = arrangeService.listSeats(arrangement.getId());

        // 按考场分组（座次已按 seat_index 排序，即考场顺序 + 座号顺序）
        Map<Long, List<MixArrangementSeat>> byRoom = new LinkedHashMap<>();
        for (MixArrangementSeat seat : seats) {
            byRoom.computeIfAbsent(seat.getRoomId(), k -> new ArrayList<>()).add(seat);
        }
        List<RoomBlock> blocks = new ArrayList<>();
        int blockIndex = 1;
        for (Map.Entry<Long, List<MixArrangementSeat>> e : byRoom.entrySet()) {
            ExamRoom room = siteService.getRoom(e.getKey());
            RoomBlock block = new RoomBlock();
            block.index = blockIndex++;
            block.room = room;
            block.seats = e.getValue();
            int samePairs = 0;
            for (MixArrangementSeat s : e.getValue()) {
                if ("SAME_LEFT".equals(s.getAdjacentFlag()) || "SAME_BOTH".equals(s.getAdjacentFlag())) {
                    samePairs++;
                }
            }
            block.sameSchoolPairs = samePairs;
            blocks.add(block);
        }

        model.addAttribute("site", site);
        model.addAttribute("batch", batch);
        model.addAttribute("arrangement", arrangement);
        model.addAttribute("blocks", blocks);
        model.addAttribute("generatedAt", LocalDateTime.now());
        return "mix/seat-chart";
    }

    /** 模板用：一间考场一块座次表。 */
    public static class RoomBlock {
        private int index;
        private ExamRoom room;
        private List<MixArrangementSeat> seats;
        private int sameSchoolPairs;

        public int getIndex() { return index; }
        public ExamRoom getRoom() { return room; }
        public List<MixArrangementSeat> getSeats() { return seats; }
        public int getSameSchoolPairs() { return sameSchoolPairs; }
    }
}
