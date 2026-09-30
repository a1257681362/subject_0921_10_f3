package com.evops.mix.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.entity.AdmissionTicket;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.ExamSite;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.AdmissionTicketMapper;
import com.evops.exam.mapper.ExamRoomMapper;
import com.evops.exam.service.ExamBatchService;
import com.evops.exam.service.ExamSiteService;
import com.evops.exam.service.RegistrationService;
import com.evops.exam.service.StageResultService;
import com.evops.mix.algo.AdjacencyCalculator;
import com.evops.mix.algo.AdjacencyStats;
import com.evops.mix.algo.MixAlgoParams;
import com.evops.mix.algo.MixAssignedSeat;
import com.evops.mix.algo.MixRoom;
import com.evops.mix.algo.MixStudent;
import com.evops.mix.algo.SchoolKeys;
import com.evops.mix.algo.SeatMixAlgorithm;
import com.evops.mix.algo.SeatMixAlgorithmRegistry;
import com.evops.mix.common.MixConstants;
import com.evops.mix.dto.MixArrangeRequest;
import com.evops.mix.entity.MixAlgorithmVersion;
import com.evops.mix.entity.MixArrangement;
import com.evops.mix.entity.MixArrangementSeat;
import com.evops.mix.mapper.MixArrangementMapper;
import com.evops.mix.mapper.MixArrangementSeatMapper;
import com.evops.mix.vo.MixArrangePreview;
import com.evops.mix.vo.MixArrangeResult;
import com.evops.mix.vo.MixSchoolStat;
import com.evops.mix.vo.MixSeatView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 混编编排：
 * - 同校学员错开相邻座位；单间考场装不下时按比例摊到多个考场（轮转发牌天然分摊）；
 * - 算法版本 + 种子一起固化到编排记录，座次可复现；
 * - 重排整批删除旧座次 / 旧准考证再整批写新，同一批次绝无半新半旧；
 * - 排完给出同校相邻统计，供巡考抽查。
 */
@Service
public class MixArrangeService {

    private final AdmissionTicketMapper ticketMapper;
    private final ExamRoomMapper roomMapper;
    private final MixArrangementMapper arrangementMapper;
    private final MixArrangementSeatMapper seatMapper;
    private final MixAlgorithmService algorithmService;
    private final SeatMixAlgorithmRegistry registry;
    private final ExamBatchService batchService;
    private final ExamSiteService siteService;
    private final RegistrationService registrationService;
    private final StageResultService stageResultService;

    public MixArrangeService(AdmissionTicketMapper ticketMapper,
                             ExamRoomMapper roomMapper,
                             MixArrangementMapper arrangementMapper,
                             MixArrangementSeatMapper seatMapper,
                             MixAlgorithmService algorithmService,
                             SeatMixAlgorithmRegistry registry,
                             ExamBatchService batchService,
                             ExamSiteService siteService,
                             RegistrationService registrationService,
                             StageResultService stageResultService) {
        this.ticketMapper = ticketMapper;
        this.roomMapper = roomMapper;
        this.arrangementMapper = arrangementMapper;
        this.seatMapper = seatMapper;
        this.algorithmService = algorithmService;
        this.registry = registry;
        this.batchService = batchService;
        this.siteService = siteService;
        this.registrationService = registrationService;
        this.stageResultService = stageResultService;
    }

    // ---------- 预检 ----------

    @Transactional(readOnly = true)
    public MixArrangePreview preview(MixArrangeRequest request, boolean rearrange) {
        Plan plan = buildPlan(request, rearrange);

        MixArrangePreview vo = new MixArrangePreview();
        vo.setAction(rearrange ? "MIX_REARRANGE" : "MIX_ARRANGE");
        vo.setBatchId(plan.batch.getId());
        vo.setBatchCode(plan.batch.getCode());
        vo.setArrangeKind(rearrange ? MixConstants.KIND_REARRANGED : MixConstants.KIND_INITIAL);
        vo.setAlgorithmVersionId(plan.version.getId());
        vo.setVersionNo(plan.version.getVersionNo());
        vo.setImplKey(plan.version.getImplKey());
        vo.setSeed(plan.seed);

        vo.setTotalScanned(plan.students.size());
        vo.setTotalCapacity(plan.totalCapacity);
        vo.setUsedSeats(plan.assigned.size());
        vo.setRoomCount(countUsedRooms(plan.assigned));
        vo.setShortage(Math.max(0, plan.students.size() - plan.totalCapacity));

        vo.setTicketsToReplace(plan.existingTickets.size());
        vo.setPrintedToReset(plan.printedToReset);

        vo.setAdjacentPairs(plan.stats.getAdjacentPairs());
        vo.setSameSchoolPairs(plan.stats.getSameSchoolPairs());
        vo.setSameSchoolPeople(plan.stats.getSameSchoolPeople());
        vo.setSchoolStats(buildSchoolStats(plan.assigned, plan.stats.getFlags()));
        vo.setRoomUsages(buildRoomUsages(plan.rooms, plan.assigned));
        vo.setSeats(toViews(plan.assigned, plan.stats.getFlags(), plan.ticketNos));

        String kindLabel = rearrange ? "整改重排" : "混编编排";
        vo.setSummary(String.format(
                "批次 %s %s：报名 %d 人，可用考位 %d，使用考场 %d 间；版本 %s（%s），种子 %d；"
                        + "相邻座位对 %d，同校相邻 %d 对、涉及 %d 人%s",
                plan.batch.getCode(), kindLabel, plan.students.size(), plan.totalCapacity,
                vo.getRoomCount(), plan.version.getVersionNo(), plan.version.getImplKey(), plan.seed,
                plan.stats.getAdjacentPairs(), plan.stats.getSameSchoolPairs(),
                plan.stats.getSameSchoolPeople(),
                rearrange ? String.format("；整批替换旧准考证 %d 张（其中已打印重置 %d 张）",
                        plan.existingTickets.size(), plan.printedToReset) : ""));
        return vo;
    }

    // ---------- 执行编排 / 重排 ----------

    @Transactional
    public MixArrangeResult execute(MixArrangeRequest request, boolean rearrange) {
        Plan plan = buildPlan(request, rearrange);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        // 容量不足整批拒收，不落半条
        if (plan.totalCapacity < plan.students.size()) {
            throw new BusinessException("CAPACITY_EXCEEDED",
                    String.format("考位不足：报名 %d 人，可用考位 %d 个，缺口 %d 个",
                            plan.students.size(), plan.totalCapacity,
                            plan.students.size() - plan.totalCapacity));
        }

        // 已发放准考证不允许重排：证到人，座次不能再动
        for (AdmissionTicket t : plan.existingTickets) {
            if (ExamConstants.TICKET_ISSUED.equals(t.getStatus())) {
                throw new BusinessException("TICKET_ISSUED_NO_REARRANGE",
                        "批次 " + plan.batch.getCode() + " 存在已发放准考证（" + t.getTicketNo()
                                + "），不能重排；如需调整请先回收或回退版本");
            }
        }

        // 1) 整批作废旧座次 / 旧编排记录 / 旧准考证（同一事务，失败整体回滚，绝不留半新半旧）
        Long oldArrangementId = plan.activeArrangement == null ? null : plan.activeArrangement.getId();
        if (oldArrangementId != null) {
            seatMapper.delete(new QueryWrapper<MixArrangementSeat>()
                    .eq("arrangement_id", oldArrangementId));
            plan.activeArrangement.setStatus(MixConstants.ARRANGE_SUPERSEDED);
            plan.activeArrangement.setRemark(appendReason(plan.activeArrangement.getRemark(),
                    request.getReason(), now, operator));
            arrangementMapper.updateById(plan.activeArrangement);
        }
        if (!plan.existingTickets.isEmpty()) {
            ticketMapper.delete(new QueryWrapper<AdmissionTicket>()
                    .eq("batch_id", plan.batch.getId()));
        }

        // 2) 写新编排记录（固化版本与种子）
        MixArrangement arrangement = new MixArrangement();
        arrangement.setBatchId(plan.batch.getId());
        arrangement.setAlgorithmId(plan.version.getAlgorithmId());
        arrangement.setAlgorithmVersionId(plan.version.getId());
        arrangement.setVersionNo(plan.version.getVersionNo());
        arrangement.setImplKey(plan.version.getImplKey());
        arrangement.setSeed(plan.seed);
        arrangement.setArrangeKind(rearrange ? MixConstants.KIND_REARRANGED : MixConstants.KIND_INITIAL);
        arrangement.setTotalCount(plan.assigned.size());
        arrangement.setRoomCount(countUsedRooms(plan.assigned));
        arrangement.setUsedSeats(plan.assigned.size());
        arrangement.setAdjacentPairs(plan.stats.getAdjacentPairs());
        arrangement.setSameSchoolPairs(plan.stats.getSameSchoolPairs());
        arrangement.setSameSchoolPeople(plan.stats.getSameSchoolPeople());
        arrangement.setStatus(MixConstants.ARRANGE_ACTIVE);
        arrangement.setOperator(operator);
        arrangement.setArrangedAt(now);
        arrangement.setRemark(request.getReason());
        arrangementMapper.insert(arrangement);

        // 3) 整批写新座次 + 准考证
        List<String> affected = new ArrayList<>(plan.assigned.size());
        for (int i = 0; i < plan.assigned.size(); i++) {
            MixAssignedSeat seat = plan.assigned.get(i);
            String flag = plan.stats.getFlags().get(i);
            String ticketNo = plan.ticketNos.get(i);
            MixStudent student = seat.getStudent();

            MixArrangementSeat seatEntity = new MixArrangementSeat();
            seatEntity.setArrangementId(arrangement.getId());
            seatEntity.setBatchId(plan.batch.getId());
            seatEntity.setRegistrationId(student.getRegistrationId());
            seatEntity.setRegNo(student.getRegNo());
            seatEntity.setName(student.getName());
            seatEntity.setSchoolCode(student.getSchoolCode());
            seatEntity.setSchoolName(student.getSchoolName());
            seatEntity.setRoomId(seat.getRoom().getRoomId());
            seatEntity.setRoomNo(seat.getRoom().getRoomNo());
            seatEntity.setSeatNo(seat.getSeatNo());
            seatEntity.setSeatIndex(seat.getSeatIndex());
            seatEntity.setTicketNo(ticketNo);
            seatEntity.setAdjacentFlag(flag);
            seatMapper.insert(seatEntity);

            AdmissionTicket ticket = new AdmissionTicket();
            ticket.setTicketNo(ticketNo);
            ticket.setRegistrationId(student.getRegistrationId());
            ticket.setBatchId(plan.batch.getId());
            ticket.setSiteId(plan.batch.getSiteId());
            ticket.setRoomId(seat.getRoom().getRoomId());
            ticket.setSeatNo(seat.getSeatNo());
            // 重排后无论旧证是否打印，一律回到待打印，须按新座次重新打证
            ticket.setStatus(ExamConstants.TICKET_PENDING);
            ticketMapper.insert(ticket);

            Registration reg = plan.registrationById.get(student.getRegistrationId());
            if (reg != null && !ExamConstants.REG_ARRANGED.equals(reg.getStatus())) {
                reg.setStatus(ExamConstants.REG_ARRANGED);
                registrationService.updateRegistration(reg);
            }
            affected.add(ticketNo);
        }

        plan.batch.setStatus(ExamConstants.BATCH_ARRANGED);
        batchService.updateBatch(plan.batch);

        stageResultService.record(plan.batch.getId(), ExamConstants.STAGE_ARRANGEMENT,
                affected.size(),
                String.format("%s：版本 %s（%s），种子 %d，使用考场 %d 间，同校相邻 %d 对/涉及 %d 人%s",
                        rearrange ? "混编整改重排 " + affected.size() + " 人" : "混编编排 " + affected.size() + " 人",
                        plan.version.getVersionNo(), plan.version.getImplKey(), plan.seed,
                        arrangement.getRoomCount(), arrangement.getSameSchoolPairs(),
                        arrangement.getSameSchoolPeople(),
                        plan.existingTickets.isEmpty() ? ""
                                : "；整批替换旧准考证 " + plan.existingTickets.size()
                                  + " 张（已打印重置 " + plan.printedToReset + " 张）"),
                operator);

        MixArrangeResult result = new MixArrangeResult();
        result.setAction(rearrange ? "MIX_REARRANGE" : "MIX_ARRANGE");
        result.setArrangementId(arrangement.getId());
        result.setBatchId(plan.batch.getId());
        result.setBatchCode(plan.batch.getCode());
        result.setArrangeKind(arrangement.getArrangeKind());
        result.setAlgorithmVersionId(plan.version.getId());
        result.setVersionNo(plan.version.getVersionNo());
        result.setImplKey(plan.version.getImplKey());
        result.setSeed(plan.seed);
        result.setAffectedCount(affected.size());
        result.setRoomCount(arrangement.getRoomCount());
        result.setUsedSeats(arrangement.getUsedSeats());
        result.setTicketsReplaced(plan.existingTickets.size());
        result.setPrintedReset(plan.printedToReset);
        result.setOperator(operator);
        result.setExecutedAt(now.toString());
        result.setAffectedKeys(affected);
        result.setAdjacentPairs(plan.stats.getAdjacentPairs());
        result.setSameSchoolPairs(plan.stats.getSameSchoolPairs());
        result.setSameSchoolPeople(plan.stats.getSameSchoolPeople());
        result.setSchoolStats(buildSchoolStats(plan.assigned, plan.stats.getFlags()));
        result.setSummary(String.format("%s完成：%d 人，考场 %d 间，同校相邻 %d 对、涉及 %d 人",
                rearrange ? "整改重排" : "混编编排", affected.size(), arrangement.getRoomCount(),
                plan.stats.getSameSchoolPairs(), plan.stats.getSameSchoolPeople()));
        return result;
    }

    // ---------- 查询：当前座次 / 统计 ----------

    @Transactional(readOnly = true)
    public MixArrangement requireActiveArrangement(Long batchId) {
        batchService.getBatch(batchId);
        MixArrangement arrangement = arrangementMapper.selectOne(new QueryWrapper<MixArrangement>()
                .eq("batch_id", batchId)
                .eq("status", MixConstants.ARRANGE_ACTIVE)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (arrangement == null) {
            throw new BusinessException("MIX_ARRANGEMENT_NOT_FOUND",
                    "批次还没有混编座次，请先执行混编编排：batchId=" + batchId);
        }
        return arrangement;
    }

    @Transactional(readOnly = true)
    public List<MixArrangementSeat> listSeats(Long arrangementId) {
        return seatMapper.selectList(new QueryWrapper<MixArrangementSeat>()
                .eq("arrangement_id", arrangementId)
                .orderByAsc("seat_index"));
    }

    // ---------- 方案构建（预检与执行共用，保证两者一致） ----------

    private Plan buildPlan(MixArrangeRequest request, boolean rearrange) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        ExamSite site = siteService.getSite(batch.getSiteId());

        List<Registration> regs = registrationService.listByBatch(batch.getId());
        if (regs.isEmpty()) {
            throw new BusinessException("NO_REGISTRATION", "批次下还没有报名名册，无法混编编排：" + batch.getCode());
        }

        List<ExamRoom> rooms = resolveRooms(batch, request.getRoomOrder());
        int totalCapacity = 0;
        for (ExamRoom room : rooms) {
            totalCapacity += room.getCapacity();
        }

        // 既有准考证 / 既有混编记录
        List<AdmissionTicket> existingTickets = ticketMapper.selectList(new QueryWrapper<AdmissionTicket>()
                .eq("batch_id", batch.getId()));
        MixArrangement activeArrangement = arrangementMapper.selectOne(new QueryWrapper<MixArrangement>()
                .eq("batch_id", batch.getId())
                .eq("status", MixConstants.ARRANGE_ACTIVE)
                .orderByDesc("id")
                .last("LIMIT 1"));

        if (rearrange) {
            if (existingTickets.isEmpty() && activeArrangement == null) {
                throw new BusinessException("NOT_ARRANGED",
                        "批次 " + batch.getCode() + " 尚未编排，无需重排，请走混编编排");
            }
        } else if (!existingTickets.isEmpty()) {
            throw new BusinessException("ALREADY_ARRANGED",
                    "批次 " + batch.getCode() + " 已编排过，不能重复编排；如需整改请走整批重排");
        }

        // 解析算法版本：
        // - 请求指定 id 时按指定；
        // - 重排未指定时默认沿用该批次上一次编排的版本（只换种子）；
        // - 首次编排未指定时取项目当前生效版。
        MixAlgorithmVersion version;
        if (request.getAlgorithmVersionId() != null) {
            version = algorithmService.getVersion(request.getAlgorithmVersionId());
        } else if (rearrange && activeArrangement != null) {
            version = algorithmService.getVersion(activeArrangement.getAlgorithmVersionId());
        } else {
            version = algorithmService.requireActiveVersion(batch.getProjectCode());
        }
        if (!MixConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("MIX_VERSION_NOT_ACTIVE",
                    "仅生效版本可用于编排：" + version.getVersionNo() + "（当前 " + version.getStatus() + "）");
        }
        MixAlgoParams frozen = algorithmService.readParams(version);

        // 种子：首排用版本冻结种子；重排默认换新种子，也可用请求指定
        long seed;
        if (rearrange) {
            seed = request.getSeed() == null ? ThreadLocalRandom.current().nextLong() : request.getSeed();
        } else {
            seed = request.getSeed() == null ? frozen.getSeed() : request.getSeed();
        }
        int maxRounds = frozen.getMaxRounds();

        List<MixStudent> students = new ArrayList<>(regs.size());
        Map<Long, Registration> regById = new LinkedHashMap<>();
        for (Registration reg : regs) {
            students.add(new MixStudent(reg.getId(), reg.getRegNo(), reg.getName(),
                    reg.getSchoolCode(), reg.getSchoolName()));
            regById.put(reg.getId(), reg);
        }
        List<MixRoom> mixRooms = new ArrayList<>(rooms.size());
        for (ExamRoom room : rooms) {
            mixRooms.add(new MixRoom(room.getId(), room.getRoomNo(), room.getCapacity()));
        }

        SeatMixAlgorithm algorithm = registry.require(version.getImplKey());
        // 容量不足时预检也要能给出缺口：不排座次，执行阶段再整批拒收
        List<MixAssignedSeat> assigned;
        AdjacencyStats stats;
        if (totalCapacity < students.size()) {
            assigned = new ArrayList<>();
            stats = AdjacencyCalculator.compute(assigned);
        } else {
            assigned = algorithm.arrange(students, mixRooms, new MixAlgoParams(seed, maxRounds));
            stats = AdjacencyCalculator.compute(assigned);
        }

        // 证号按新座位顺序重发：ZK + 批次编码 + 4 位流水
        List<String> ticketNos = new ArrayList<>(assigned.size());
        for (int i = 0; i < assigned.size(); i++) {
            ticketNos.add(generateTicketNo(batch, i + 1));
        }

        int printedToReset = 0;
        for (AdmissionTicket t : existingTickets) {
            if (ExamConstants.TICKET_PRINTED.equals(t.getStatus())) {
                printedToReset++;
            }
        }

        Plan plan = new Plan();
        plan.batch = batch;
        plan.site = site;
        plan.rooms = rooms;
        plan.totalCapacity = totalCapacity;
        plan.students = students;
        plan.registrationById = regById;
        plan.version = version;
        plan.seed = seed;
        plan.assigned = assigned;
        plan.stats = stats;
        plan.ticketNos = ticketNos;
        plan.existingTickets = existingTickets;
        plan.activeArrangement = activeArrangement;
        plan.printedToReset = printedToReset;
        return plan;
    }

    private List<ExamRoom> resolveRooms(ExamBatch batch, List<String> roomOrder) {
        List<ExamRoom> rooms = roomMapper.selectList(new QueryWrapper<ExamRoom>()
                .eq("site_id", batch.getSiteId())
                .eq("status", "ENABLED")
                .orderByAsc("room_no"));
        if (rooms.isEmpty()) {
            throw new BusinessException("NO_ROOM", "考点下没有启用的考场，无法混编编排");
        }
        if (roomOrder == null || roomOrder.isEmpty()) {
            return rooms;
        }
        List<String> order = new ArrayList<>();
        for (String no : roomOrder) {
            order.add(no.trim());
        }
        if (new HashSet<>(order).size() != order.size()) {
            throw new BusinessException("ROOM_ORDER_DUP", "指定的考场顺序存在重复考场号");
        }
        List<ExamRoom> ordered = new ArrayList<>();
        for (String roomNo : order) {
            ExamRoom hit = null;
            for (ExamRoom room : rooms) {
                if (room.getRoomNo().equals(roomNo)) {
                    hit = room;
                    break;
                }
            }
            if (hit == null) {
                throw new BusinessException("ROOM_NOT_IN_SITE", "考场不属于本批次考点或未启用：" + roomNo);
            }
            ordered.add(hit);
        }
        Set<String> picked = new HashSet<>(order);
        for (ExamRoom room : rooms) {
            if (!picked.contains(room.getRoomNo())) {
                ordered.add(room);
            }
        }
        return ordered;
    }

    // ---------- 统计与视图 ----------

    private int countUsedRooms(List<MixAssignedSeat> assigned) {
        Set<Long> roomIds = new HashSet<>();
        for (MixAssignedSeat s : assigned) {
            roomIds.add(s.getRoom().getRoomId());
        }
        return roomIds.size();
    }

    private List<MixArrangePreview.RoomUsage> buildRoomUsages(List<ExamRoom> rooms,
                                                              List<MixAssignedSeat> assigned) {
        Map<Long, Integer> used = new LinkedHashMap<>();
        for (ExamRoom room : rooms) {
            used.put(room.getId(), 0);
        }
        for (MixAssignedSeat s : assigned) {
            used.merge(s.getRoom().getRoomId(), 1, Integer::sum);
        }
        List<MixArrangePreview.RoomUsage> usages = new ArrayList<>();
        for (ExamRoom room : rooms) {
            MixArrangePreview.RoomUsage u = new MixArrangePreview.RoomUsage();
            u.setRoomId(room.getId());
            u.setRoomNo(room.getRoomNo());
            u.setCapacity(room.getCapacity());
            u.setUsed(used.getOrDefault(room.getId(), 0));
            usages.add(u);
        }
        return usages;
    }

    private List<MixSchoolStat> buildSchoolStats(List<MixAssignedSeat> assigned, List<String> flags) {
        Map<String, MixSchoolStat> statMap = new LinkedHashMap<>();
        Map<String, String> nameMap = new LinkedHashMap<>();
        Map<String, Set<Long>> roomMap = new LinkedHashMap<>();
        Map<String, Set<Integer>> involvedMap = new LinkedHashMap<>();

        for (int i = 0; i < assigned.size(); i++) {
            MixAssignedSeat s = assigned.get(i);
            String key = SchoolKeys.of(s.getStudent());
            if (!SchoolKeys.isCountable(key)) {
                continue;
            }
            nameMap.putIfAbsent(key, s.getStudent().getSchoolName());
            MixSchoolStat stat = statMap.computeIfAbsent(key, k -> {
                MixSchoolStat m = new MixSchoolStat();
                m.setSchoolCode(k);
                return m;
            });
            stat.setPeople(stat.getPeople() + 1);
            roomMap.computeIfAbsent(key, k -> new HashSet<>()).add(s.getRoom().getRoomId());

            boolean sameLeft = i > 0 && isSameSchoolEdge(assigned, i - 1, i);
            boolean sameRight = i < assigned.size() - 1 && isSameSchoolEdge(assigned, i, i + 1);
            if (sameLeft || sameRight) {
                involvedMap.computeIfAbsent(key, k -> new HashSet<>()).add(i);
            }
            if (sameLeft) {
                stat.setSameSchoolPairs(stat.getSameSchoolPairs() + 1);
            }
        }

        List<MixSchoolStat> result = new ArrayList<>();
        for (Map.Entry<String, MixSchoolStat> e : statMap.entrySet()) {
            MixSchoolStat stat = e.getValue();
            stat.setSchoolName(nameMap.get(e.getKey()));
            stat.setRoomCount(roomMap.getOrDefault(e.getKey(), new HashSet<>()).size());
            stat.setInvolvedPeople(involvedMap.getOrDefault(e.getKey(), new HashSet<>()).size());
            result.add(stat);
        }
        result.sort((a, b) -> Integer.compare(b.getSameSchoolPairs(), a.getSameSchoolPairs()));
        return result;
    }

    private boolean isSameSchoolEdge(List<MixAssignedSeat> assigned, int i, int j) {
        MixAssignedSeat a = assigned.get(i);
        MixAssignedSeat b = assigned.get(j);
        if (!a.getRoom().getRoomId().equals(b.getRoom().getRoomId())
                || b.getSeatNo() != a.getSeatNo() + 1) {
            return false;
        }
        String ka = SchoolKeys.of(a.getStudent());
        String kb = SchoolKeys.of(b.getStudent());
        return SchoolKeys.isCountable(ka) && ka.equals(kb);
    }

    private List<MixSeatView> toViews(List<MixAssignedSeat> assigned, List<String> flags,
                                      List<String> ticketNos) {
        List<MixSeatView> views = new ArrayList<>(assigned.size());
        for (int i = 0; i < assigned.size(); i++) {
            MixAssignedSeat s = assigned.get(i);
            MixSeatView v = new MixSeatView();
            v.setRegistrationId(s.getStudent().getRegistrationId());
            v.setRegNo(s.getStudent().getRegNo());
            v.setName(s.getStudent().getName());
            v.setSchoolCode(s.getStudent().getSchoolCode());
            v.setSchoolName(s.getStudent().getSchoolName());
            v.setRoomId(s.getRoom().getRoomId());
            v.setRoomNo(s.getRoom().getRoomNo());
            v.setSeatNo(s.getSeatNo());
            v.setSeatIndex(s.getSeatIndex());
            v.setTicketNo(ticketNos.get(i));
            v.setAdjacentFlag(flags.get(i));
            views.add(v);
        }
        return views;
    }

    private String generateTicketNo(ExamBatch batch, int seq) {
        return "ZK" + batch.getCode() + "-" + String.format("%04d", seq);
    }

    private String appendReason(String oldRemark, String reason, LocalDateTime now, String operator) {
        String tag = String.format("[%s %s 置 SUPERSEDED]%s", now, operator,
                reason == null ? "" : " " + reason.trim());
        if (oldRemark == null || oldRemark.trim().isEmpty()) {
            return tag;
        }
        return oldRemark + " " + tag;
    }

    private static class Plan {
        private ExamBatch batch;
        private ExamSite site;
        private List<ExamRoom> rooms;
        private int totalCapacity;
        private List<MixStudent> students;
        private Map<Long, Registration> registrationById;
        private MixAlgorithmVersion version;
        private long seed;
        private List<MixAssignedSeat> assigned;
        private AdjacencyStats stats;
        private List<String> ticketNos;
        private List<AdmissionTicket> existingTickets;
        private MixArrangement activeArrangement;
        private int printedToReset;
    }
}
