package com.codeying.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.OSSObjectSummary;

import java.io.ByteArrayInputStream;
import java.net.URL;
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
        OSS ossClient = createClient();
        try {
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
        } catch (OSSException | ClientException e) {
            throw new IllegalStateException("OSS 上传失败", e);
        } finally {
            closeQuietly(ossClient);
        }

        return buildObjectUrl(objectName);
    }

    /**
     * 列出指定前缀下的对象。
     *
     * @param prefix  前缀
     * @param maxKeys 最大数量
     * @return 对象摘要列表
     */
    public List<OSSObjectSummary> listObjects(String prefix, int maxKeys) {
        OSS ossClient = createClient();
        try {
            ListObjectsRequest request = new ListObjectsRequest(bucketName);
            if (prefix != null && !prefix.isBlank()) {
                request.setPrefix(prefix);
            }
            request.setMaxKeys(maxKeys);
            ObjectListing listing = ossClient.listObjects(request);
            return new ArrayList<>(listing.getObjectSummaries());
        } catch (OSSException | ClientException e) {
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

    private OSS createClient() {
        return new OSSClientBuilder().build(normalizeEndpoint(endpoint), accessKeyId, accessKeySecret);
    }

    private String buildPublicUrl(String objectName) {
        String baseUrl = org.springframework.util.StringUtils.hasText(publicBaseUrl) ? trimTrailingSlash(publicBaseUrl) : "https://" + bucketName + "." + normalizeEndpoint(endpoint);
        return trimTrailingSlash(baseUrl) + "/" + objectName;
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