package com.company.attendance.repository;
import com.company.attendance.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface ScanLogRepository extends JpaRepository<ScanLog, Long>, JpaSpecificationExecutor<ScanLog> {
    boolean existsByDeviceIdAndRawCodeAndTimestamp(String deviceId, String rawCode, LocalDateTime timestamp);
    Optional<ScanLog> findFirstByEmployeeAndStatusOrderByTimestampDesc(Employee e, ScanStatus s);
    List<ScanLog> findTop20ByOrderByTimestampDesc();
    long countByDirectionAndStatusAndTimestampBetween(Direction d, ScanStatus s, LocalDateTime a, LocalDateTime b);
    long countByStatusAndTimestampBetween(ScanStatus s, LocalDateTime a, LocalDateTime b);
    List<ScanLog> findByStatusAndTimestampBetweenOrderByTimestamp(ScanStatus s, LocalDateTime a, LocalDateTime b);

    /** Employees whose latest accepted scan is IN = currently inside. */
    @Query("select l from ScanLog l where l.status = :ok and l.entryOnly = false and l.direction = :in and l.timestamp = "
         + "(select max(x.timestamp) from ScanLog x where x.employee = l.employee and x.status = :ok) order by l.timestamp desc")
    List<ScanLog> inside(@Param("ok") ScanStatus ok, @Param("in") Direction in);
}
