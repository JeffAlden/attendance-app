package com.company.attendance.service;

import com.company.attendance.config.AppProperties;
import com.company.attendance.domain.Employee;
import com.company.attendance.repository.*;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeRepository employees;
    private final ShiftRepository shifts;
    private final AppProperties props;

    @Transactional(rollbackFor = Exception.class)
    public Employee save(Employee form, Long shiftId, MultipartFile photo) throws IOException {
        validate(form);
        Employee employee = form.getId() == null ? new Employee() : employees.findById(form.getId())
            .orElseThrow(() -> new IllegalArgumentException("Employee no longer exists. Please reload the list."));
        employee.setCode(form.getCode());
        employee.setFullName(form.getFullName());
        employee.setDepartment(form.getDepartment());
        employee.setBiometricId(form.getBiometricId());
        employee.setShift(shiftId == null ? null : shifts.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("The selected shift no longer exists.")));
        if (photo != null && !photo.isEmpty()) {
            if (photo.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("Photo must be 5 MB or smaller.");
            var image = ImageIO.read(new ByteArrayInputStream(photo.getBytes()));
            if (image == null) throw new IllegalArgumentException("Please select a valid JPG, PNG, or GIF photo.");
            Path directory = Path.of(props.getDataDir(), "photos");
            Files.createDirectories(directory);
            String filename = UUID.randomUUID() + ".png";
            ImageIO.write(image, "png", directory.resolve(filename).toFile());
            employee.setPhotoPath(filename);
        }
        return employees.saveAndFlush(employee);
    }

    private void validate(Employee form) {
        form.setCode(required(form.getCode(), "Employee code"));
        form.setFullName(required(form.getFullName(), "Full name"));
        form.setDepartment(form.getDepartment() == null ? "" : form.getDepartment().trim());
        form.setBiometricId(form.getBiometricId() == null || form.getBiometricId().isBlank()
            ? form.getCode() : form.getBiometricId().trim());
        if (form.getDepartment().length() > 255 || form.getBiometricId().length() > 255)
            throw new IllegalArgumentException("Department and device ID must be 255 characters or fewer.");
        // Codes and device PINs share a lookup namespace; neither may identify another employee.
        boolean conflict = employees.findAll().stream()
            .filter(e -> !Objects.equals(e.getId(), form.getId()))
            .anyMatch(e -> form.getCode().equals(e.getCode()) || form.getCode().equals(e.getBiometricId())
                || form.getBiometricId().equals(e.getCode()) || form.getBiometricId().equals(e.getBiometricId()));
        if (conflict) throw new IllegalArgumentException("Employee code or device ID is already used by another employee.");
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        if (value.trim().length() > 255) throw new IllegalArgumentException(label + " must be 255 characters or fewer.");
        return value.trim();
    }

    @Transactional(rollbackFor = Exception.class)
    public int importCsv(MultipartFile file) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("Select a CSV file to import.");
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null || header.length < 2 || !header[0].replace("\uFEFF", "").trim().equalsIgnoreCase("code")
                || !header[1].trim().equalsIgnoreCase("name"))
                throw new IllegalArgumentException("CSV must begin with the header: code,name,department,biometricId.");
            String[] cells; int count = 0, line = 1;
            while ((cells = reader.readNext()) != null) {
                line++;
                if (Arrays.stream(cells).allMatch(String::isBlank)) continue;
                if (cells.length < 2) throw new IllegalArgumentException("CSV row " + line + " needs a code and name.");
                Employee form = new Employee();
                form.setId(employees.findByCode(cells[0].trim()).map(Employee::getId).orElse(null));
                form.setCode(cells[0]); form.setFullName(cells[1]);
                form.setDepartment(cells.length > 2 ? cells[2] : "");
                form.setBiometricId(cells.length > 3 ? cells[3] : "");
                try {
                    validate(form);
                    Employee employee = form.getId() == null ? form : employees.findById(form.getId()).orElseThrow();
                    employee.setFullName(form.getFullName()); employee.setDepartment(form.getDepartment());
                    employee.setBiometricId(form.getBiometricId());
                    employees.saveAndFlush(employee); count++;
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("CSV row " + line + ": " + ex.getMessage());
                }
            }
            if (count == 0) throw new IllegalArgumentException("CSV has no employee rows.");
            return count;
        }
    }
}
