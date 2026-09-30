package com.evops.mix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 混编整改端到端：
 * 多校学员 → 同校错开相邻、装不下摊到多个考场 → 整批重排不留半新半旧 → 种子可复现 → 统计与座次表。
 * 用内存 H2，避免污染工作区文件库。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mixit;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
class MixArrangementE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper json = new ObjectMapper();

    /** Java 8 没有 Map.of，统一用保序 LinkedHashMap 构造。 */
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth("bootstrap", "bootstrap");
        headers.set("X-Operator", "tester");
        return headers;
    }

    private JsonNode post(String path, Object body) {
        try {
            String req = json.writeValueAsString(body);
            ResponseEntity<String> resp = restTemplate.exchange(url(path), HttpMethod.POST,
                    new HttpEntity<>(req, jsonHeaders()), String.class);
            JsonNode node = json.readTree(resp.getBody());
            assertTrue(node.path("success").asBoolean(),
                    "请求应成功：" + path + " -> " + resp.getBody());
            return node.path("data");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode get(String path) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth("bootstrap", "bootstrap");
            ResponseEntity<String> resp = restTemplate.exchange(url(path), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            JsonNode node = json.readTree(resp.getBody());
            assertTrue(node.path("success").asBoolean(),
                    "请求应成功：" + path + " -> " + resp.getBody());
            return node.path("data");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getRaw(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("bootstrap", "bootstrap");
        ResponseEntity<String> resp = restTemplate.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(200, resp.getStatusCode().value());
        return resp.getBody();
    }

    private Long createBase(String batchCode, int total) {
        Long siteId = post("/api/sites", m("code", "S-" + batchCode, "name", "混编测试考点")).path("id").asLong();
        int roomCount = (total + 9) / 10;
        for (int r = 1; r <= roomCount; r++) {
            post("/api/rooms", m("siteId", siteId, "roomNo", String.format("R%02d", r), "capacity", 10));
        }
        Long batchId = post("/api/batches", m("code", batchCode, "name", "混编测试批次 " + batchCode,
                "siteId", siteId, "projectCode", "DEFAULT")).path("id").asLong();

        List<Object> regs = new ArrayList<>();
        int seq = 0;
        seq = addSchool(regs, batchCode, "A", 25, seq);
        seq = addSchool(regs, batchCode, "B", 20, seq);
        seq = addSchool(regs, batchCode, "C", 10, seq);
        for (int i = 0; i < 5; i++) {
            regs.add(m("regNo", batchCode + "-" + (++seq), "name", "无校" + i));
        }
        post("/api/registrations/import", m("batchId", batchId, "registrations", regs));
        return batchId;
    }

    private int addSchool(List<Object> regs, String batchCode, String school, int count, int startSeq) {
        int seq = startSeq;
        for (int i = 1; i <= count; i++) {
            regs.add(m("regNo", batchCode + "-" + (++seq),
                    "name", school + "校学员" + i,
                    "schoolCode", "SCH-" + school,
                    "schoolName", school + "中学"));
        }
        return seq;
    }

    private Long publishModule(String moduleCode) {
        Long moduleId = post("/api/mix/algorithms", m("code", moduleCode, "name", "同校错开混编",
                "projectCode", "DEFAULT", "description", "school shuffle")).path("id").asLong();
        Long versionId = post("/api/mix/algorithms/" + moduleId + "/versions",
                m("implKey", "schoolShuffleV1", "seed", 42, "maxRounds", 500)).path("id").asLong();
        post("/api/mix/algorithms/versions/" + versionId + "/publish", new LinkedHashMap<>());
        return versionId;
    }

    private List<String> seatOrderFromPreview(JsonNode preview) {
        List<String> order = new ArrayList<>();
        for (JsonNode seat : preview.path("seats")) {
            order.add(seat.path("regNo").asText());
        }
        return order;
    }

    private List<String> seatOrderFromSeats(JsonNode seats) {
        List<String> order = new ArrayList<>();
        for (JsonNode seat : seats) {
            order.add(seat.path("regNo").asText());
        }
        return order;
    }

    @Test
    void mixArrange_spreadRooms_zeroSameSchoolAdjacent_andWholeBatchRearrange() {
        // 1) 建档：60 人（A25/B20/C10/无校5），6 间各 10 座
        Long batchId = createBase("B60", 60);
        Long versionId = publishModule("SCHOOL_SHUFFLE");

        // 2) 预检：6 间都用上，同校相邻为 0
        JsonNode preview = post("/api/mix/arrange/preview",
                m("batchId", batchId, "algorithmVersionId", versionId));
        assertEquals(60, preview.path("totalScanned").asInt());
        assertEquals(6, preview.path("roomCount").asInt(), "单间装不下应摊到多个考场");
        assertEquals(0, preview.path("shortage").asInt());
        assertEquals(0, preview.path("sameSchoolPairs").asInt(), "预检同校相邻应为 0");
        assertEquals(0, preview.path("sameSchoolPeople").asInt());

        // 3) 执行：一人一证，跨 6 间
        JsonNode result = post("/api/mix/arrange",
                m("batchId", batchId, "algorithmVersionId", versionId));
        assertEquals(60, result.path("affectedCount").asInt());
        assertEquals(6, result.path("roomCount").asInt());
        assertEquals(0, result.path("sameSchoolPairs").asInt());

        // 4) 统计接口：版本、种子固化，A 校摊到多间
        JsonNode stats = get("/api/mix/arrange/stats?batchId=" + batchId);
        assertEquals(60, stats.path("totalCount").asInt());
        assertEquals(0, stats.path("sameSchoolPairs").asInt());
        assertEquals("42", stats.path("seed").asText(), "应固化版本种子");
        assertEquals("SCHOOL_SHUFFLE-R1", stats.path("versionNo").asText());
        boolean aSpread = false;
        for (JsonNode row : stats.path("schoolStats")) {
            if ("SCH-A".equals(row.path("schoolCode").asText())) {
                assertEquals(25, row.path("people").asInt());
                assertTrue(row.path("roomCount").asInt() >= 3, "A 校 25 人应摊到 3 间以上考场");
                aSpread = true;
            }
        }
        assertTrue(aSpread);

        // 5) 座次表可打印，含考场号/座位号/准考证号
        String chart = getRaw("/print/mix/seat-chart?batchId=" + batchId);
        assertTrue(chart.contains("R01") && chart.contains("R06"));
        assertTrue(chart.contains("ZKB60-0001"));
        assertTrue(chart.contains("同校相邻"));

        // 6) 同种子两次重排座次完全一致（可复现）
        JsonNode re1 = post("/api/mix/arrange/rearrange/preview",
                m("batchId", batchId, "seed", 123));
        JsonNode re2 = post("/api/mix/arrange/rearrange/preview",
                m("batchId", batchId, "seed", 123));
        assertEquals(seatOrderFromPreview(re1), seatOrderFromPreview(re2), "同种子座次必须可复现");
        assertEquals(60, re1.path("ticketsToReplace").asInt(), "重排应整批替换 60 张旧证");

        // 7) 执行重排：REARRANGED/ACTIVE，准考证仍是 60 张，结果与同种子预检一致
        JsonNode rearranged = post("/api/mix/arrange/rearrange",
                m("batchId", batchId, "seed", 123, "reason", "巡考通报整改"));
        assertEquals("REARRANGED", rearranged.path("arrangeKind").asText());
        assertEquals(60, rearranged.path("ticketsReplaced").asInt());
        JsonNode seatsNow = get("/api/mix/arrange/seats?batchId=" + batchId);
        assertEquals(60, seatsNow.size(), "重排后座次仍须整批 60 条，不留半新半旧");
        assertEquals(seatOrderFromPreview(re1), seatOrderFromSeats(seatsNow), "执行结果须与同种子预检一致");

        // 8) 再重排一次，当前有效编排始终唯一、数量不变
        post("/api/mix/arrange/rearrange",
                m("batchId", batchId, "seed", 999));
        JsonNode stats2 = get("/api/mix/arrange/stats?batchId=" + batchId);
        assertEquals("999", stats2.path("seed").asText());
        assertEquals(60, get("/api/mix/arrange/seats?batchId=" + batchId).size());
    }

    @Test
    void mixArrange_capacityShortage_wholeBatchRejected() {
        // 30 人 / 三间各 10 座 = 30，够；再验证容量不足走报错（建两间即 20）
        Long siteId = post("/api/sites", m("code", "S-SHORT", "name", "容量不足考点")).path("id").asLong();
        post("/api/rooms", m("siteId", siteId, "roomNo", "R01", "capacity", 10));
        post("/api/rooms", m("siteId", siteId, "roomNo", "R02", "capacity", 10));
        Long batchId = post("/api/batches", m("code", "BSHORT", "name", "容量不足批次",
                "siteId", siteId, "projectCode", "DEFAULT")).path("id").asLong();

        List<Object> regs = new ArrayList<>();
        int seq = 0;
        seq = addSchool(regs, "BSHORT", "A", 15, seq);
        seq = addSchool(regs, "BSHORT", "B", 15, seq);
        post("/api/registrations/import", m("batchId", batchId, "registrations", regs));
        Long versionId = publishModule("SCHOOL_SHUFFLE_2");

        JsonNode preview = post("/api/mix/arrange/preview",
                m("batchId", batchId, "algorithmVersionId", versionId));
        assertEquals(10, preview.path("shortage").asInt(), "预检应报缺口 10");

        // 容量不足时执行应失败且不落任何准考证
        HttpHeaders headers = jsonHeaders();
        try {
            String req = json.writeValueAsString(m("batchId", batchId, "algorithmVersionId", versionId));
            ResponseEntity<String> resp = restTemplate.exchange(url("/api/mix/arrange"), HttpMethod.POST,
                    new HttpEntity<>(req, headers), String.class);
            JsonNode node = json.readTree(resp.getBody());
            assertTrue(!node.path("success").asBoolean(), "容量不足应失败");
            assertEquals("CAPACITY_EXCEEDED", node.path("code").asText());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        JsonNode after = get("/api/query/by-batch?batchId=" + batchId);
        assertEquals(0, after.size(), "容量不足整批拒收，不应落下任何准考证");
    }

    @Test
    void mixArrange_dominantSchool_reachesMinimumResidualAndReportsIt() {
        // 单间考场 10 座：A 校 8 人、B 校 2 人。数学上无法完全错开：
        // 2 个 B 最多把 A 切成 3 段，同校相邻至少 8-3=5 对。算法应达到该最小值并如实上报。
        Long siteId = post("/api/sites", m("code", "S-DOM", "name", "极端占比考点")).path("id").asLong();
        post("/api/rooms", m("siteId", siteId, "roomNo", "R01", "capacity", 10));
        Long batchId = post("/api/batches", m("code", "BDOM", "name", "极端占比批次",
                "siteId", siteId, "projectCode", "DEFAULT")).path("id").asLong();

        List<Object> regs = new ArrayList<>();
        int seq = 0;
        seq = addSchool(regs, "BDOM", "A", 8, seq);
        seq = addSchool(regs, "BDOM", "B", 2, seq);
        post("/api/registrations/import", m("batchId", batchId, "registrations", regs));
        Long versionId = publishModule("SCHOOL_SHUFFLE_3");

        JsonNode result = post("/api/mix/arrange",
                m("batchId", batchId, "algorithmVersionId", versionId));
        assertEquals(10, result.path("affectedCount").asInt());
        assertEquals(1, result.path("roomCount").asInt());
        // 相邻座位对共 9；同校相邻应压到理论最小 5 对（贪心 + 多种子起点）
        assertEquals(9, result.path("adjacentPairs").asInt());
        assertTrue(result.path("sameSchoolPairs").asInt() <= 5,
                "同校相邻应不超过理论最小值 5，实际：" + result.path("sameSchoolPairs").asInt());
        assertTrue(result.path("sameSchoolPairs").asInt() >= 5,
                "数学下界为 5，不应更小：" + result.path("sameSchoolPairs").asInt());

        // 座次表对残留相邻逐座标红：SAME_LEFT/SAME_BOTH 数量应等于同校相邻对数
        String chart = getRaw("/print/mix/seat-chart?batchId=" + batchId);
        int marked = countOccurrences(chart, "左邻同校") + countOccurrences(chart, "左右均同校");
        assertEquals(result.path("sameSchoolPairs").asInt(), marked,
                "座次表标红的左邻/双邻数应等于同校相邻对数，供巡考抽查");
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
