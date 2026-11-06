package com.qrcheckin.checkin;

/** 위치 검증(계획서 4.2). */
public final class GeoUtils {

    public static final double EARTH_RADIUS_M = 6_371_000d;

    public enum LocationVerdict { OK, LOW_ACCURACY, OUT_OF_RANGE }

    private GeoUtils() {
    }

    /** 하버사인 공식으로 두 위·경도 사이의 지표면 거리(m) */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * 정확도를 먼저 본다: 정확도가 나쁘면 거리가 멀어도 OUT_OF_RANGE 가 아니라 LOW_ACCURACY 로 안내한다.
     * 통과 조건: accuracy ≤ maxAccuracy 그리고 distance ≤ radius
     */
    public static LocationVerdict judge(double distanceM, double accuracyM, int radiusM, int maxAccuracyM) {
        if (accuracyM > maxAccuracyM) {
            return LocationVerdict.LOW_ACCURACY;
        }
        if (distanceM > radiusM) {
            return LocationVerdict.OUT_OF_RANGE;
        }
        return LocationVerdict.OK;
    }

    public static boolean isValidCoordinate(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }
}
