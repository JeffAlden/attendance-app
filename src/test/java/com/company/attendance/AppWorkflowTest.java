package com.company.attendance;

import com.company.attendance.domain.*;
import com.company.attendance.repository.*;
import com.company.attendance.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:workflow;DB_CLOSE_DELAY=-1",
    "attendance.data-dir=target/workflow-data"
})
@AutoConfigureMockMvc @WithMockUser(username="admin",roles="ADMIN") @Transactional
class AppWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired EmployeeRepository employees;
    @Autowired ShiftRepository shifts;
    @Autowired EmployeeService employeeService;
    @Autowired ScanService scans;
    @Autowired SummaryService summary;
    @Autowired DashboardService dashboard;
    @Autowired ReaderSettingsService readerSettings;

    @Test void allTabsRenderAndFormsIncludeCsrf() throws Exception {
        for (String path : List.of("/dashboard", "/employees", "/shifts", "/logs", "/settings", "/simulator"))
            mvc.perform(get(path)).andExpect(status().isOk());
        for (String path : List.of("/employees", "/shifts", "/settings")) {
            String html = mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("name=\"_csrf\"");
        }
    }

    @Test void addEditAssignAndDeactivateEmployee() throws Exception {
        Shift shift = shift("Test day", LocalTime.of(8,0), LocalTime.of(17,0));
        mvc.perform(multipart("/employees").param("code","TEST01").param("fullName","Test Employee")
            .param("department","QA").param("shiftId", shift.getId().toString()).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("success","Employee added."));
        Employee e = employees.findByCode("TEST01").orElseThrow();
        assertThat(e.getBiometricId()).isEqualTo("TEST01");
        assertThat(e.getShift().getId()).isEqualTo(shift.getId());
        mvc.perform(multipart("/employees").param("id",e.getId().toString()).param("code","TEST01")
            .param("fullName","Updated Employee").param("biometricId","TEST-PIN").with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertThat(employees.findById(e.getId()).orElseThrow().getFullName()).isEqualTo("Updated Employee");
        mvc.perform(post("/employees/{id}/toggle",e.getId()).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(employees.findById(e.getId()).orElseThrow().isActive()).isFalse();
        assertThat(scans.process("TEST01","TEST",LocalDateTime.now(),Direction.IN,"TEST").getStatus()).isEqualTo(ScanStatus.DENIED);
        mvc.perform(post("/employees/{id}/toggle",e.getId()).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(employees.findById(e.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test void duplicateIdentifiersAndBlankNamesShowErrors() throws Exception {
        employee("DUP1",null);
        mvc.perform(multipart("/employees").param("code","NEW1").param("fullName","New Person").param("biometricId","DUP1").with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        mvc.perform(multipart("/employees").param("code","NEW2").param("fullName","   ").with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        assertThat(employees.findByCode("NEW1")).isEmpty();
    }

    @Test void photoUploadIsValidatedAndServed() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=");
        mvc.perform(multipart("/employees").file(new MockMultipartFile("photo","photo.png","image/png",png))
            .param("code","PHOTO1").param("fullName","Photo Person").with(csrf())).andExpect(status().is3xxRedirection());
        Employee e = employees.findByCode("PHOTO1").orElseThrow();
        mvc.perform(get("/photos/" + e.getPhotoPath())).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(multipart("/employees").file(new MockMultipartFile("photo","bad.png","image/png","not an image".getBytes()))
            .param("code","BADPHOTO").param("fullName","Bad Photo").with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        assertThat(employees.findByCode("BADPHOTO")).isEmpty();
    }

    @Test void csvImportUpdatesExistingEmployees() throws Exception {
        employee("CSV1",null);
        MockMultipartFile csv = new MockMultipartFile("file","employees.csv","text/csv",
            "code,name,department,biometricId\nCSV1,Updated CSV,QA,PINCSV1\nCSV2,Second CSV,HR,PINCSV2\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/employees/import").file(csv).with(csrf())).andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("success","Imported 2 employees."));
        assertThat(employees.findByCode("CSV1").orElseThrow().getFullName()).isEqualTo("Updated CSV");
        assertThat(employees.findByCode("CSV2")).isPresent();
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void invalidCsvRollsBackEarlierRows() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file","bad.csv","text/csv",
            "code,name,department,biometricId\nROLLBACK1,Valid,QA,ROLLBACK1\nROLLBACK2,,QA,ROLLBACK2\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/employees/import").file(csv).with(csrf())).andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("error"));
        assertThat(employees.findByCode("ROLLBACK1")).isEmpty();
    }

    @Test void shiftsCanBeEditedAndOnlyUnusedShiftsDeleted() throws Exception {
        mvc.perform(post("/shifts").param("name","Workflow shift").param("start","08:00").param("end","17:00").param("grace","10").with(csrf()))
            .andExpect(status().is3xxRedirection());
        Shift shift = shifts.findAll().stream().filter(s -> s.getName().equals("Workflow shift")).findFirst().orElseThrow();
        mvc.perform(post("/shifts").param("id",shift.getId().toString()).param("name","Updated shift")
            .param("start","22:00").param("end","06:00").param("grace","5").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/shifts").param("edit",shift.getId().toString())).andExpect(status().isOk());
        Employee e = employee("SHIFTEMP",shift);
        mvc.perform(post("/shifts/{id}/delete",shift.getId()).with(csrf())).andExpect(flash().attributeExists("error"));
        assertThat(shifts.existsById(shift.getId())).isTrue();
        e.setShift(null); employees.saveAndFlush(e);
        mvc.perform(post("/shifts/{id}/delete",shift.getId()).with(csrf())).andExpect(flash().attributeExists("success"));
        assertThat(shifts.existsById(shift.getId())).isFalse();
    }

    @Test void invalidShiftKeepsFormAndDoesNotSave() throws Exception {
        long before = shifts.count();
        mvc.perform(post("/shifts").param("name","Invalid").param("start","08:00").param("end","08:00").param("grace","-1").with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        assertThat(shifts.count()).isEqualTo(before);
    }

    @Test void scansUpdateDashboardAndFiltersAndExportsWork() throws Exception {
        employee("SCAN1",null);
        LocalDateTime time = LocalDate.now().atTime(9,0);
        assertThat(scans.process("SCAN1","TEST",time,Direction.IN,"TEST").getStatus()).isEqualTo(ScanStatus.OK);
        assertThat(scans.process("SCAN1","TEST",time.plusSeconds(1),Direction.OUT,"TEST").getStatus()).isEqualTo(ScanStatus.DUPLICATE);
        assertThat(scans.process("SCAN1","TEST",time.plusMinutes(1),Direction.OUT,"TEST").getStatus()).isEqualTo(ScanStatus.OK);
        var recent = (List<DashboardService.Row>) dashboard.data().get("recent");
        assertThat(recent).allMatch(row -> row.id() != null);
        mvc.perform(get("/logs").param("q","SCAN1")).andExpect(status().isOk());
        mvc.perform(get("/logs/export/csv").param("q","SCAN1")).andExpect(status().isOk())
            .andExpect(content().contentType("text/csv")).andExpect(content().string(org.hamcrest.Matchers.containsString("SCAN1")));
        mvc.perform(get("/logs/export/xlsx")).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.endsWith(".xlsx")));
        mvc.perform(get("/logs/summary")).andExpect(status().isOk());
        mvc.perform(post("/api/scans").contentType("application/json").content("{\"employeeCode\":\"\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/scans").contentType("application/json").content("{\"employeeCode\":\"MISSING\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DENIED"));
    }

    @Test void overnightSummaryPairsScansAcrossMidnight() {
        Shift night = shift("Night test",LocalTime.of(22,0),LocalTime.of(6,0));
        employee("NIGHT1",night);
        LocalDate date = LocalDate.of(2026,10,12);
        scans.process("NIGHT1","TEST",date.atTime(22,15),Direction.IN,"TEST");
        scans.process("NIGHT1","TEST",date.plusDays(1).atTime(6,30),Direction.OUT,"TEST");
        List<Object> row = summary.rows(date,date).stream().filter(r -> r.get(1).equals("NIGHT1")).findFirst().orElseThrow();
        assertThat(row.get(6)).isEqualTo("8.25");
        assertThat(row.get(7)).isEqualTo("15 min");
        assertThat(row.get(8)).isEqualTo("");
        assertThat(row.get(9)).isEqualTo("30 min");
    }

    @Test @WithMockUser(roles="GUARD") void guardCannotManageEmployeesOrShifts() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
        for (String path : List.of("/employees","/shifts","/logs","/settings","/simulator"))
            mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test void writesRequireCsrfAndLoginStylesArePublic() throws Exception {
        mvc.perform(post("/shifts").param("name","Blocked")).andExpect(status().isForbidden());
        mvc.perform(get("/css/sidebar.css").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
            .andExpect(status().isOk());
    }

    @Test void settingsSaveAndValidateAndReaderDirectionIsApplied() throws Exception {
        mvc.perform(post("/settings").param("readerName","Front reader").param("readerHost","192.168.1.201")
            .param("readerPort","4370").param("serialNumber","READER01").param("direction","IN")
            .param("entryOnly","true").param("debounceSeconds","3").param("antiPassback","true").with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        assertThat(readerSettings.current().isEntryOnly()).isTrue();
        assertThat(readerSettings.current().getReaderHost()).isEqualTo("192.168.1.201");
        assertThat(readerSettings.direction("READER01")).isEqualTo(Direction.IN);
        mvc.perform(post("/settings").param("readerName","Invalid").param("readerPort","0").with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        mvc.perform(post("/settings").param("readerName","Invalid").param("readerPort","abc").with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        assertThat(readerSettings.current().getReaderName()).isEqualTo("Front reader");
    }

    @Test void entryOnlyDisablesOutAndPassbackButKeepsDuplicateProtection() throws Exception {
        ReaderSettings config = readerSettings.current(); config.setEntryOnly(true); config.setAntiPassback(true);
        readerSettings.save(config);
        employee("ENTRY01",null);
        LocalDateTime time = LocalDate.now().atTime(10,0);
        ScanLog first = scans.process("ENTRY01","TEST",time,null,"TEST");
        ScanLog duplicate = scans.process("ENTRY01","TEST",time.plusSeconds(1),Direction.OUT,"TEST");
        ScanLog second = scans.process("ENTRY01","TEST",time.plusMinutes(1),Direction.OUT,"TEST");
        assertThat(first.getDirection()).isEqualTo(Direction.IN);
        assertThat(first.isEntryOnly()).isTrue();
        assertThat(duplicate.getStatus()).isEqualTo(ScanStatus.DUPLICATE);
        assertThat(second.getStatus()).isEqualTo(ScanStatus.OK);
        assertThat(second.getDirection()).isEqualTo(Direction.IN);
        var data = dashboard.data();
        assertThat(data.get("entryOnly")).isEqualTo(true);
        var entries = (List<DashboardService.Row>) data.get("insideList");
        assertThat(entries.stream().filter(r -> r.code().equals("ENTRY01")).count()).isEqualTo(1);
        List<Object> row = summary.rows(time.toLocalDate(),time.toLocalDate()).stream()
            .filter(r -> r.get(1).equals("ENTRY01")).findFirst().orElseThrow();
        assertThat(row.get(5)).isEqualTo(""); assertThat(row.get(6)).isEqualTo("");
        assertThat(row.get(10)).isEqualTo("ENTRY RECORDED");
        byte[] workbook = mvc.perform(get("/logs/entries").param("q","ENTRY01"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var excel = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(workbook))) {
            var sheet = excel.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("ENTRY01");
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("2");
        }
        mvc.perform(get("/dashboard")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Employees entered today")));
        mvc.perform(get("/simulator")).andExpect(status().isOk());
    }

    @Test void switchingBackRestoresInOutWithoutAssumingFreeExitOccupancy() {
        Employee e = employee("MODE01",null);
        ReaderSettings config = readerSettings.current(); config.setEntryOnly(true); readerSettings.save(config);
        LocalDateTime time = LocalDate.now().atTime(10,0);
        scans.process("MODE01","TEST",time,null,"TEST");
        config = readerSettings.current(); config.setEntryOnly(false); readerSettings.save(config);
        var before = (List<DashboardService.Row>) dashboard.data().get("insideList");
        assertThat(before).noneMatch(r -> r.code().equals("MODE01"));
        assertThat(scans.process("MODE01","TEST",time.plusMinutes(1),null,"TEST").getDirection()).isEqualTo(Direction.IN);
        assertThat(scans.process("MODE01","TEST",time.plusMinutes(2),null,"TEST").getDirection()).isEqualTo(Direction.OUT);
    }

    @Test void demoEnrollmentCanBeSavedAndCleared() throws Exception {
        Employee e = employee("ENROLL01",null);
        mvc.perform(post("/settings/enrollment").param("employeeId",e.getId().toString()).param("finger","RIGHT_INDEX").with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        assertThat(employees.findById(e.getId()).orElseThrow().getDummyEnrolledAt()).isNotNull();
        mvc.perform(get("/settings")).andExpect(status().isOk());
        mvc.perform(post("/settings/enrollment/{id}/clear",e.getId()).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(employees.findById(e.getId()).orElseThrow().getDummyEnrolledAt()).isNull();
    }

    @Test void readerPushUsesConfiguredDirectionAndRecordsContact() throws Exception {
        Employee e = employee("PUSH01",null); e.setBiometricId("777"); employees.saveAndFlush(e);
        ReaderSettings config = readerSettings.current(); config.setSerialNumber("PUSHREADER"); config.setDirection("IN"); readerSettings.save(config);
        mvc.perform(get("/iclock/cdata").param("SN","PUSHREADER")).andExpect(status().isOk());
        assertThat(readerSettings.current().getLastSeenSerial()).isEqualTo("PUSHREADER");
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        mvc.perform(post("/iclock/cdata").param("SN","PUSHREADER").param("table","ATTLOG")
            .contentType("text/plain").content("777\t" + timestamp + "\t0\t1"))
            .andExpect(status().isOk()).andExpect(content().string("OK: 1"));
        var data = (List<DashboardService.Row>) dashboard.data().get("recent");
        assertThat(data).anyMatch(r -> r.code().equals("PUSH01") && r.direction().equals("IN"));
    }

    private Employee employee(String code, Shift shift) {
        Employee e = new Employee(); e.setCode(code); e.setBiometricId(code); e.setFullName("Person " + code); e.setShift(shift);
        return employees.saveAndFlush(e);
    }
    private Shift shift(String name, LocalTime start, LocalTime end) {
        Shift s = new Shift(); s.setName(name); s.setStartTime(start); s.setEndTime(end); return shifts.saveAndFlush(s);
    }
}
