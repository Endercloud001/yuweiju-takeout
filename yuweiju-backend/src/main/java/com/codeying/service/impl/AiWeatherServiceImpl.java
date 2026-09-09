package com.codeying.service.impl;

import com.codeying.constant.RedisKeys;
import com.codeying.properties.AnalysisProperties;
import com.codeying.properties.SkyProperties;
import com.codeying.service.AiWeatherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * AI 推荐天气服务实现。
 *
 * @author Endercloud
 */
@Service
@Profile({"dev", "prod"})
public class AiWeatherServiceImpl implements AiWeatherService {
/**
 * LoggerFactory.getLogger
 * @return 
 */

    private static final Logger log = LoggerFactory.getLogger(AiWeatherServiceImpl.class);
    private static final String DEFAULT_API_HOST = "https://jc67cf742g.re.qweatherapi.com";
    private static final String GEO_PATH = "/geo/v2/city/lookup";
    private static final String WEATHER_PATH = "/v7/weather/now";
    private static final String HISTORICAL_WEATHER_PATH = "/v7/historical/weather";
    private static final long CACHE_MINUTES = 20;
    private static final long TOKEN_EXPIRE_SECONDS = 300;
/**
 * ZoneId.of
 * @return 
 */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HISTORY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final SkyProperties skyProperties;
    private final AnalysisProperties analysisProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public AiWeatherServiceImpl(
            SkyProperties skyProperties,
            AnalysisProperties analysisProperties,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper
    ) {
        this.skyProperties = skyProperties;
        this.analysisProperties = analysisProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void validateQWeatherConfig() {
        if (!hasQWeatherConfig()) {
            SkyProperties.QWeatherProperties qweather = skyProperties.getQweather();
            boolean failFast = qweather != null && Boolean.TRUE.equals(qweather.getFailFast());
            String message = "QWeather configuration missing: please set sky.qweather.api-host, sky.qweather.project-id, " +
                    "sky.qweather.credential-id and sky.qweather.private-key. You can provide them via .env " +
                    "(QWEATHER_API_HOST, QWEATHER_PROJECT_ID, QWEATHER_CREDENTIAL_ID, QWEATHER_PRIVATE_KEY).";
            if (failFast) {
                throw new IllegalStateException(message);
            }
            log.warn("{} Weather recommendation will be downgraded.", message);
        }
    }

    @Override
    public String getWeatherSummary(String locationText) {
        if (!StringUtils.hasText(locationText) || !hasQWeatherConfig()) {
            return null;
        }
        String location = locationText.trim();
        String cacheKey = RedisKeys.aiWeatherCacheKey(location);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return cached;
        }

        try {
            String locationId = lookupLocationId(location);
            if (!StringUtils.hasText(locationId)) {
                return null;
            }
            String summary = queryWeatherNow(locationId);
            if (StringUtils.hasText(summary)) {
                stringRedisTemplate.opsForValue().set(cacheKey, summary, CACHE_MINUTES, TimeUnit.MINUTES);
            }
            return summary;
        } catch (Exception ex) {
            log.warn("获取天气信息失败，location={}, error={}", location, ex.getMessage());
            return null;
        }
    }

    @Override
    public String getWeatherCode(String locationText, LocalDate date) {
        if (!StringUtils.hasText(locationText) || date == null || !hasQWeatherConfig()) {
            return null;
        }
        String location = locationText.trim();
        String cacheKey = RedisKeys.analysisWeatherFeatureKey(location, date.toString());
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return cached;
        }

        try {
            String locationId = lookupLocationId(location);
            if (!StringUtils.hasText(locationId)) {
                return null;
            }

            LocalDate today = LocalDate.now(APP_ZONE);
            String weatherCode = date.isEqual(today)
                    ? queryWeatherNowCode(locationId)
                    : queryHistoricalWeatherCode(locationId, date, today);
            if (StringUtils.hasText(weatherCode)) {
                long ttlMinutes = TimeUnit.HOURS.toMinutes(Math.max(1, analysisProperties.getExternal().getWeatherCacheHours()));
                stringRedisTemplate.opsForValue().set(cacheKey, weatherCode, ttlMinutes, TimeUnit.MINUTES);
            }
            return weatherCode;
        } catch (Exception ex) {
            log.warn("获取天气编码失败，location={}, date={}, error={}", location, date, ex.getMessage());
            return null;
        }
    }

    private String lookupLocationId(String location) throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(getApiHost() + GEO_PATH)
                .queryParam("location", location)
                .queryParam("range", "cn")
                .queryParam("lang", "zh")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        JsonNode root = requestJson(uri);
        if (root == null || !"200".equals(root.path("code").asText())) {
            return null;
        }
        JsonNode first = root.path("location").isArray() && root.path("location").size() > 0
                ? root.path("location").get(0) : null;
        if (first == null) {
            return null;
        }
        return first.path("id").asText(null);
    }

    private String queryWeatherNow(String locationId) throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(getApiHost() + WEATHER_PATH)
                .queryParam("location", locationId)
                .queryParam("lang", "zh")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        JsonNode root = requestJson(uri);
        if (root == null || !"200".equals(root.path("code").asText())) {
            return null;
        }
        JsonNode now = root.path("now");
        if (now == null || now.isMissingNode()) {
            return null;
        }
        String text = now.path("text").asText("");
        String temp = now.path("temp").asText("");
        if (!StringUtils.hasText(text) && !StringUtils.hasText(temp)) {
            return null;
        }
        if (!StringUtils.hasText(temp)) {
            return text;
        }
        if (!StringUtils.hasText(text)) {
            return temp + "°C";
        }
        return text + "，" + temp + "°C";
    }

    private String queryWeatherNowCode(String locationId) throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(getApiHost() + WEATHER_PATH)
                .queryParam("location", locationId)
                .queryParam("lang", "zh")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        JsonNode root = requestJson(uri);
        if (root == null || !"200".equals(root.path("code").asText())) {
            return null;
        }
        JsonNode now = root.path("now");
        if (now == null || now.isMissingNode()) {
            return null;
        }
        return normalizeWeatherCode(now.path("icon").asText(""), now.path("text").asText(""));
    }

    private String queryHistoricalWeatherCode(String locationId, LocalDate date, LocalDate today) throws Exception {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(date, today);
        int historyDays = Math.min(10, Math.max(1, analysisProperties.getExternal().getWeatherHistoryDays()));
        if (daysBetween <= 0 || daysBetween > historyDays) {
            return null;
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(getApiHost() + HISTORICAL_WEATHER_PATH)
                .queryParam("location", locationId)
                .queryParam("date", HISTORY_DATE_FORMATTER.format(date))
                .queryParam("lang", "zh")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        JsonNode root = requestJson(uri);
        if (root == null || !"200".equals(root.path("code").asText())) {
            return null;
        }
        JsonNode weatherHourly = root.path("weatherHourly");
        if (!weatherHourly.isArray() || weatherHourly.isEmpty()) {
            return null;
        }

        List<String> codes = new ArrayList<>();
        weatherHourly.forEach(hourly -> {
            String code = normalizeWeatherCode(hourly.path("icon").asText(""), hourly.path("text").asText(""));
            if (StringUtils.hasText(code)) {
                codes.add(code);
            }
        });
        if (codes.isEmpty()) {
            return null;
        }
        return codes.stream()
                .collect(java.util.stream.Collectors.groupingBy(code -> code, java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String normalizeWeatherCode(String icon, String text) {
        String normalizedText = StringUtils.hasText(text) ? text.trim().toLowerCase() : "";
        if (normalizedText.contains("雷") || normalizedText.contains("thunder")) {
            return "THUNDER";
        }
        if (normalizedText.contains("雪") || normalizedText.contains("sleet") || normalizedText.contains("snow")) {
            return "SNOW";
        }
        if (normalizedText.contains("雨") || normalizedText.contains("shower") || normalizedText.contains("rain")) {
            return "RAIN";
        }
        if (normalizedText.contains("雾") || normalizedText.contains("霾") || normalizedText.contains("fog")
                || normalizedText.contains("haze") || normalizedText.contains("沙")) {
            return "FOG";
        }
        if (normalizedText.contains("风") || normalizedText.contains("wind")) {
            return "WIND";
        }
        if (normalizedText.contains("阴") || normalizedText.contains("overcast")) {
            return "OVERCAST";
        }
        if (normalizedText.contains("云") || normalizedText.contains("cloud")) {
            return "CLOUDY";
        }
        if (normalizedText.contains("晴") || normalizedText.contains("clear") || normalizedText.contains("sunny")) {
            return "CLEAR";
        }

        if (StringUtils.hasText(icon)) {
            if (icon.startsWith("1")) {
                return "CLEAR";
            }
            if (icon.startsWith("2")) {
                return "WIND";
            }
            if (icon.startsWith("3")) {
                return "RAIN";
            }
            if (icon.startsWith("4")) {
                return "SNOW";
            }
            if (icon.startsWith("5")) {
                return "FOG";
            }
        }
        return null;
    }

    private JsonNode requestJson(URI uri) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String bearerToken = buildJwtToken();
        headers.setBearerAuth(bearerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);
            String responseBody = decodeBody(response.getBody(), response.getHeaders());
            if (!StringUtils.hasText(responseBody)) {
                return null;
            }
            return objectMapper.readTree(responseBody);
        } catch (HttpStatusCodeException ex) {
            String errorBody = decodeBody(ex.getResponseBodyAsByteArray(), ex.getResponseHeaders());
            throw new IllegalStateException("QWeather API request failed, status=" + ex.getStatusCode().value()
                    + ", body=" + abbreviate(errorBody), ex);
        }
    }

    private String buildJwtToken() throws Exception {
        SkyProperties.QWeatherProperties qweather = skyProperties.getQweather();
        String header = toBase64Url(objectMapper.writeValueAsBytes(Map.of(
                "alg", "EdDSA",
                "kid", qweather.getCredentialId()
        )));
        long iat = Instant.now().getEpochSecond() - 30;
        long exp = iat + TOKEN_EXPIRE_SECONDS;
        String payload = toBase64Url(objectMapper.writeValueAsBytes(Map.of(
                "sub", qweather.getProjectId(),
                "iat", iat,
                "exp", exp
        )));
        String signingInput = header + "." + payload;
        String signature = sign(signingInput, qweather.getPrivateKey());
        return signingInput + "." + signature;
    }

    private String sign(String signingInput, String singleLinePkcs8) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(singleLinePkcs8.getBytes(StandardCharsets.UTF_8));
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return toBase64Url(signature.sign());
    }

    private String toBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean hasQWeatherConfig() {
        SkyProperties.QWeatherProperties qweather = skyProperties.getQweather();
        return qweather != null
                && StringUtils.hasText(qweather.getApiHost())
                && StringUtils.hasText(qweather.getProjectId())
                && StringUtils.hasText(qweather.getCredentialId())
                && StringUtils.hasText(qweather.getPrivateKey());
    }

    private String getApiHost() {
        SkyProperties.QWeatherProperties qweather = skyProperties.getQweather();
        if (qweather == null || !StringUtils.hasText(qweather.getApiHost())) {
            return DEFAULT_API_HOST;
        }
        return qweather.getApiHost().trim().replaceAll("/+$", "");
    }

    private String decodeBody(byte[] bytes, HttpHeaders headers) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try {
            boolean gzip = isGzip(headers, bytes);
            byte[] content = gzip ? gunzip(bytes) : bytes;
            return new String(content, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private boolean isGzip(HttpHeaders headers, byte[] bytes) {
        if (headers != null) {
            List<String> encodingValues = headers.getOrEmpty(HttpHeaders.CONTENT_ENCODING);
            for (String value : encodingValues) {
                if (StringUtils.hasText(value) && value.toLowerCase().contains("gzip")) {
                    return true;
                }
            }
        }
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
    }

    private byte[] gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return outputStream.toByteArray();
        }
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }
}
