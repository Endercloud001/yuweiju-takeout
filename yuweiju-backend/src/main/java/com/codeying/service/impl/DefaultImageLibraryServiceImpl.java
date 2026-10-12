package com.codeying.service.impl;

import com.aliyun.oss.model.OSSObjectSummary;
import com.codeying.properties.AliOssProperties;
import com.codeying.exception.BusinessException;
import com.codeying.service.DefaultImageLibraryService;
import com.codeying.utils.AliOssUtil;
import com.codeying.vo.admin.common.DefaultImageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 默认图片库查询服务实现。
 *
 * @author Endercloud
 */
@Service
public class DefaultImageLibraryServiceImpl implements DefaultImageLibraryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultImageLibraryServiceImpl.class);

    private final AliOssUtil aliOssUtil;
    private final AliOssProperties aliOssProperties;

    public DefaultImageLibraryServiceImpl(AliOssUtil aliOssUtil, AliOssProperties aliOssProperties) {
        this.aliOssUtil = aliOssUtil;
        this.aliOssProperties = aliOssProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DefaultImageVO> listDefaultImages(String scene, Integer limit) {
        String normalizedScene = normalizeScene(scene);
        String prefix = resolvePrefix(normalizedScene);
        int maxCount = resolveLimit(limit);
        try {
            List<OSSObjectSummary> summaries = aliOssUtil.listObjects(prefix, maxCount, this::isImage);
            List<DefaultImageVO> result = new ArrayList<>(summaries.size());
            for (OSSObjectSummary summary : summaries) {
                String objectKey = summary.getKey();
                DefaultImageVO vo = new DefaultImageVO();
                vo.setName(StringUtils.getFilename(objectKey));
                vo.setObjectKey(objectKey);
                vo.setUrl(aliOssUtil.buildObjectUrl(objectKey));
                vo.setScene(normalizedScene);
                vo.setSize(summary.getSize());
                Date lastModified = summary.getLastModified();
                vo.setLastModified(lastModified == null ? null : lastModified.toInstant().toString());
                result.add(vo);
            }
            log.info("查询默认图片库完成，limit={}, count={}", maxCount, result.size());
            return result;
        } catch (Exception e) {
            log.warn("默认图片库上游查询失败");
            throw new BusinessException("获取默认图片失败", e);
        }
    }

    private boolean isImage(OSSObjectSummary summary) {
        return summary != null && StringUtils.hasText(summary.getKey()) && !summary.getKey().endsWith("/")
                && isAllowedExtension(StringUtils.getFilenameExtension(summary.getKey()));
    }

    private String resolvePrefix(String normalizedScene) {
        String prefix;
        if ("dish".equals(normalizedScene)) {
            prefix = aliOssProperties.getDishDefaultImagePrefix();
        } else if ("setmeal".equals(normalizedScene)) {
            prefix = aliOssProperties.getSetmealDefaultImagePrefix();
        } else {
            prefix = aliOssProperties.getDefaultImagePrefix();
        }
        if (!StringUtils.hasText(prefix)) {
            log.warn("默认图片库前缀未配置，回退到 bucket 根目录");
            return "";
        }
        String normalized = prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private int resolveLimit(Integer limit) {
        int configuredMax = aliOssProperties.getMaxListCount() == null || aliOssProperties.getMaxListCount() <= 0
                ? 100
                : aliOssProperties.getMaxListCount();
        if (limit == null || limit <= 0) {
            return configuredMax;
        }
        return Math.min(limit, configuredMax);
    }

    private boolean isAllowedExtension(String extension) {
        if (!StringUtils.hasText(extension) || aliOssProperties.getAllowedExtensions() == null) {
            return false;
        }
        String lowerCase = extension.toLowerCase(Locale.ROOT);
        for (String allowed : aliOssProperties.getAllowedExtensions()) {
            if (allowed != null && lowerCase.equals(allowed.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return "general";
        }
        return scene.trim().toLowerCase(Locale.ROOT);
    }
}