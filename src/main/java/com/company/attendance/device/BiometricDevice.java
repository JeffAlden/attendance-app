package com.company.attendance.device;

/** Implement this to plug in another reader brand. Each implementation just calls ScanService.process(...). */
public interface BiometricDevice {
    String brand();
}
