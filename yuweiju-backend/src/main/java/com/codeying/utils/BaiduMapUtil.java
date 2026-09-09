package com.codeying.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 百度地图工具类，用于地址解析与驾车路线规划。
 *
 * @author Endercloud
 */
@Component
public class BaiduMapUtil {

    private static final String GEOCODING_URL = "https://api.map.baidu.com/geocoding/v3/";
    private static final String DIRECTION_URL = "https://api.map.baidu.com/directionlite/v1/driving";
    private static final double MAX_DELIVERY_DISTANCE_METERS = 50_000.0;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 校验配送范围并计算驾车时长（分钟）。
     *
     * @param shopAddress 门店地址
     * @param userAddress 用户地址
     * @param ak          百度地图 AK
     * @return 驾车时长（分钟，向上取整）
     */
    public int checkAndGetDrivingMinutes(String shopAddress, String userAddress, String ak) {
        if (!StringUtils.hasText(shopAddress) || !StringUtils.hasText(userAddress) || !StringUtils.hasText(ak)) {
            throw new IllegalStateException("服务未配置");
        }

        String shopLatLng = geocoding(shopAddress, ak);
        String userLatLng = geocoding(userAddress, ak);
        DrivingResult driving = getDrivingRoute(shopLatLng, userLatLng, ak);

        if (driving.distanceMeters > MAX_DELIVERY_DISTANCE_METERS) {
            double km = driving.distanceMeters / 1000.0;
            throw new IllegalStateException("超出配送范围，配送距离为 " + String.format("%.2f", km) + " 公里，最远仅支持 50 公里配送");
        }
        return (int) Math.ceil(driving.durationSeconds / 60.0);
    }

    private String geocoding(String address, String ak) {
        String url = GEOCODING_URL
                + "?address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
                + "&output=json"
                + "&ak=" + URLEncoder.encode(ak, StandardCharsets.UTF_8);
        URI uri = URI.create(url);

        Map<String, Object> json = getJson(uri);
        Object statusObj = json.get("status");
        int status = statusObj instanceof Number n ? n.intValue() : -1;
        if (status != 0) {
            String apiMessage = firstNonBlank(getString(json.get("msg")), getString(json.get("message")));
            if (StringUtils.hasText(apiMessage)) {
                throw new IllegalStateException("地址解析失败: " + apiMessage);
            }
            throw new IllegalStateException("地址解析失败，请检查地址是否正确：" + address);
        }

        Object resultObj = json.get("result");
        if (!(resultObj instanceof Map<?, ?> result)) throw new IllegalStateException("地址解析失败，请检查地址是否正确：" + address);
        Object locationObj = result.get("location");
        if (!(locationObj instanceof Map<?, ?> location)) throw new IllegalStateException("地址解析失败，请检查地址是否正确：" + address);

        double lat = getDouble(location.get("lat"));
        double lng = getDouble(location.get("lng"));
        return lat + "," + lng;
    }

    private DrivingResult getDrivingRoute(String originLatLng, String destinationLatLng, String ak) {
        String url = DIRECTION_URL
                + "?origin=" + URLEncoder.encode(originLatLng, StandardCharsets.UTF_8)
                + "&destination=" + URLEncoder.encode(destinationLatLng, StandardCharsets.UTF_8)
                + "&ak=" + URLEncoder.encode(ak, StandardCharsets.UTF_8);
        URI uri = URI.create(url);

        Map<String, Object> json = getJson(uri);
        Object statusObj = json.get("status");
        int status = statusObj instanceof Number n ? n.intValue() : -1;
        if (status != 0) {
            String apiMessage = firstNonBlank(getString(json.get("msg")), getString(json.get("message")));
            if (StringUtils.hasText(apiMessage)) {
                throw new IllegalStateException("路线规划失败: " + apiMessage);
            }
            throw new IllegalStateException("路线规划查询失败，请稍后重试");
        }

        Object resultObj = json.get("result");
        if (!(resultObj instanceof Map<?, ?> result)) throw new IllegalStateException("路线规划查询失败，请稍后重试");
        Object routesObj = result.get("routes");
        if (!(routesObj instanceof List<?> routes) || routes.isEmpty()) throw new IllegalStateException("未找到可用驾车路线，请检查地址是否可达");
        Object route0Obj = routes.get(0);
        if (!(route0Obj instanceof Map<?, ?> route0)) throw new IllegalStateException("未找到可用驾车路线，请检查地址是否可达");

        double distance = getDouble(route0.get("distance"));
        int duration = (int) Math.round(getDouble(route0.get("duration")));
        return new DrivingResult(distance, duration);
    }

    private Map<String, Object> getJson(URI uri) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.ACCEPT, "application/json");
                headers.add(HttpHeaders.USER_AGENT, "Mozilla/5.0");
                ResponseEntity<String> resp = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                String raw = resp.getBody();
                if (!StringUtils.hasText(raw)) throw new IllegalStateException("服务异常");
                return objectMapper.readValue(raw, new TypeReference<>() {});
            } catch (RestClientException e) {
                if (attempts < 3) {
                    sleepSilently(200L);
                    continue;
                }
                throw new IllegalStateException("服务异常");
            } catch (Exception e) {
                if (attempts < 3) {
                    sleepSilently(200L);
                    continue;
                }
                throw new IllegalStateException("服务异常");
            }
        }
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private double getDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (Exception ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    private String getString(Object o) {
        if (o instanceof String s) return s;
        return null;
    }

    private String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a;
        if (StringUtils.hasText(b)) return b;
        return null;
    }

    private static class DrivingResult {
        final double distanceMeters;
        final int durationSeconds;

        DrivingResult(double distanceMeters, int durationSeconds) {
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
        }
    }
}
