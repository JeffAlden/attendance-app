package com.company.attendance.repository;
import com.company.attendance.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByCodeOrBiometricId(String code, String biometricId);
    Optional<Employee> findByCode(String code);
    List<Employee> findByActiveTrue();
    boolean existsByShiftId(Long shiftId);
}
