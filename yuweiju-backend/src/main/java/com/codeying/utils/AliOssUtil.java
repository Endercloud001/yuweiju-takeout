package com.codeying.utils;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.OSSObjectSummary;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Predicate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 阿里 OSS 工具类。
 *
 * @author Endercloud
 */
public class AliOssUtil {

    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucketName;
    private final String publicBaseUrl;
    private final Boolean privateBucket;
    private final Integer signedUrlExpireSeconds;

    public AliOssUtil(String endpoint, String accessKeyId, String accessKeySecret, String bucketName,
                      String publicBaseUrl, Boolean privateBucket, Integer signedUrlExpireSeconds) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucketName = bucketName;
        this.publicBaseUrl = publicBaseUrl;
        this.privateBucket = privateBucket;
        this.signedUrlExpireSeconds = signedUrlExpireSeconds;
    }

    /**
     * 上传文件到 OSS。
     *
     * @param bytes      文件内容
     * @param objectName 对象名（含路径）
     * @return 文件访问 URL
     */
    public String upload(byte[] bytes, String objectName) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return upload(input, objectName);
        } catch (IOException e) {
            throw new IllegalStateException("OSS 上传失败", e);
        }
    }

    /** 上传调用方拥有的流；调用方负责关闭流，本方法负责 SDK 客户端。 */
    public String upload(InputStream input, String objectName) {
        OSS ossClient = createClient();
        try {
            PutObjectRequest request = new PutObjectRequest(bucketName, objectName, input);
            // SDK 默认请求错误日志包含上游正文；由 Service 记录不含正文的失败信息。
            request.setLogEnabled(false);
            ossClient.putObject(request);
            return buildObjectUrl(objectName);
        } catch (Exception e) {
            throw new IllegalStateException("OSS 上传失败", e);
        } finally {
            closeQuietly(ossClient);
        }
    }

    /**
     * 列出指定前缀下的对象。
     *
     * @param prefix  前缀
     * @param maxKeys 最大数量
     * @return 对象摘要列表
     */
    public List<OSSObjectSummary> listObjects(String prefix, int maxKeys) {
        return listObjects(prefix, maxKeys, summary -> true);
    }

    /** 按 SDK marker 分页，过滤后累计到返回上限；保留 OSS 的对象顺序。 */
    public List<OSSObjectSummary> listObjects(String prefix, int maxKeys, Predicate<OSSObjectSummary> filter) {
        if (maxKeys <= 0) return List.of();
        OSS ossClient = createClient();
        try {
            ListObjectsRequest request = new ListObjectsRequest(bucketName);
            request.setLogEnabled(false);
            if (prefix != null && !prefix.isBlank()) request.setPrefix(prefix);
            request.setMaxKeys(Math.min(maxKeys, 1000));
            List<OSSObjectSummary> result = new ArrayList<>();
            while (result.size() < maxKeys) {
                ObjectListing listing = ossClient.listObjects(request);
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    if (filter.test(summary)) result.add(summary);
                    if (result.size() == maxKeys) break;
                }
                if (!listing.isTruncated() || result.size() == maxKeys) break;
                String marker = listing.getNextMarker();
                if (marker == null || marker.isBlank() || marker.equals(request.getMarker())) {
                    throw new IllegalStateException("OSS 列表分页未前进");
                }
                request.setMarker(marker);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("OSS 列表查询失败", e);
        } finally {
            closeQuietly(ossClient);
        }
    }

    /**
     * 构建对象访问地址。
     *
     * @param objectName 对象名
     * @return 可访问 URL
     */
    public String buildObjectUrl(String objectName) {
        if (Boolean.TRUE.equals(privateBucket)) {
            return buildSignedUrl(objectName);
        }
        return buildPublicUrl(objectName);
    }

    protected OSS createClient() {
        return new OSSClientBuilder().build(endpoint.trim(), accessKeyId, accessKeySecret);
    }

    private String buildPublicUrl(String objectName) {
        String baseUrl = org.springframework.util.StringUtils.hasText(publicBaseUrl) ? trimTrailingSlash(publicBaseUrl) : "https://" + bucketName + "." + normalizeEndpoint(endpoint);
        try {
            String encodedPath = new URI(null, null, "/" + objectName, null).toASCIIString();
            return trimTrailingSlash(baseUrl) + encodedPath;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("OSS 对象地址无效", e);
        }
    }

    private String buildSignedUrl(String objectName) {
        OSS ossClient = createClient();
        try {
            int expireSeconds = signedUrlExpireSeconds == null || signedUrlExpireSeconds <= 0 ? 300 : signedUrlExpireSeconds;
            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET);
            request.setExpiration(expiration);
            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        } finally {
            closeQuietly(ossClient);
        }
    }

    private String normalizeEndpoint(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("https://")) {
            return normalized.substring("https://".length());
        }
        if (normalized.startsWith("http://")) {
            return normalized.substring("http://".length());
        }
        return normalized;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void closeQuietly(OSS ossClient) {
        if (ossClient == null) {
            return;
        }
        try {
            ossClient.shutdown();
        } catch (Exception ignored) {
            // ignore
        }
    }
}