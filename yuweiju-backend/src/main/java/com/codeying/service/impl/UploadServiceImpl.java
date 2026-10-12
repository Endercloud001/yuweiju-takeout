package com.codeying.service.impl;

import com.codeying.dto.admin.common.UploadFileDTO;
import com.codeying.exception.BusinessException;
import com.codeying.service.UploadService;
import com.codeying.utils.AliOssUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.io.InputStream;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/** 图片上传业务规则与存储用例。 */
@Service
public class UploadServiceImpl implements UploadService {
    private static final Logger log = LoggerFactory.getLogger(UploadServiceImpl.class);
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "bmp");
    private static final long DEFAULT_MAX_SIZE = 5L * 1024 * 1024;
    private static final long HARD_MAX_SIZE = 20L * 1024 * 1024;
    private final AliOssUtil aliOssUtil;

    public UploadServiceImpl(AliOssUtil aliOssUtil) {
        this.aliOssUtil = aliOssUtil;
    }

    @Override
    public String upload(UploadFileDTO body) {
        if (body == null || body.getFile() == null || body.getFile().isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String originalFilename = body.getFile().getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException("文件名为空");
        }

        String extension = getExtension(originalFilename);
        if (!StringUtils.hasText(extension) || !ALLOWED_EXT.contains(extension)) {
            throw new BusinessException("文件格式错误");
        }

        long limit = body.getMaxSize() != null && body.getMaxSize() > 0 ? Math.min(body.getMaxSize(), HARD_MAX_SIZE) : DEFAULT_MAX_SIZE;
        if (body.getFile().getSize() > limit) {
            throw new BusinessException("文件过大");
        }

        String baseName = stripExtension(originalFilename);
        String newFileName = baseName + "_" + UUID.randomUUID() + "." + extension;
        String dir = sanitizePath(body.getDir());
        if (!StringUtils.hasText(dir)) {
            dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        }
        String scene = sanitizePath(body.getScene());
        String prefix = StringUtils.hasText(scene) ? scene + "/" : "";
        String objectName = prefix + dir + "/" + newFileName;
        try (InputStream input = body.getFile().getInputStream()) {
            return aliOssUtil.upload(input, objectName);
        } catch (Exception e) {
            log.warn("上传文件上游失败");
            throw new BusinessException("上传失败", e);
        }
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return null;
        }
        return name.substring(idx + 1).trim().toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String name) {
        String n = name.trim();
        int idx = n.lastIndexOf('.');
        if (idx <= 0) {
            return n;
        }
        return n.substring(0, idx);
    }

    private String sanitizePath(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String normalized = input.replace('\\', '/');
        String[] parts = normalized.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (".".equals(part) || "..".equals(part)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(part.trim());
        }
        return sb.toString();
    }
}
