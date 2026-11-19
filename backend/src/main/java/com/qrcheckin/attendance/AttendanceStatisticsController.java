
package com.qrcheckin.attendance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/attendance")
public class AttendanceStatisticsController {

    private final AttendanceStatisticsService statisticsService;

    public AttendanceStatisticsController(
            AttendanceStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<AttendanceStatisticsResponse> getStatistics(
            @PathVariable Long courseId) {

        AttendanceStatisticsResponse response =
                statisticsService.getStatistics(courseId);

        return ResponseEntity.ok(response);
    }
}
