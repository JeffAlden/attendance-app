package com.company.attendance.repository;
import com.company.attendance.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ShiftRepository extends JpaRepository<Shift, Long> {}
