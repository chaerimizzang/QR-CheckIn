
package com.qrcheckin.course;

import java.util.List;

public record StudentCsvResult(
        int successCount,
        int failureCount,
        List<FailedStudent> failures
) {
    public record FailedStudent(
            int row,
            String studentNo,
            String reason
    ) {
    }
}
