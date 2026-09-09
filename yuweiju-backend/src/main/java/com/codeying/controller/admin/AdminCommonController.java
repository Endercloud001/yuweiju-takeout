package com.codeying.controller.admin;

import com.codeying.dto.admin.common.DefaultImageQueryDTO;
import com.codeying.dto.admin.common.UploadFileDTO;
import com.codeying.result.ApiResult;
import com.codeying.service.DefaultImageLibraryService;
import com.codeying.utils.AliOssUtil;
import com.codeying.vo.admin.common.DefaultImageVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 管理端通用接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/common")
public class AdminCommonController {
/**
 * LoggerFactory.getLogger
 * @return 
 */

    private static final Logger log = LoggerFactory.getLogger(AdminCommonController.class);
/**
 * Set.of
 * @return 
 */
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "bmp");
    private static final long DEFAULT_MAX_SIZE = 5L * 1024 * 1024;
    private static final long HARD_MAX_SIZE = 20L * 1024 * 1024;

    private final AliOssUtil aliOssUtil;
    private final DefaultImageLibraryService defaultImageLibraryService;

    public AdminCommonController(AliOssUtil aliOssUtil, DefaultImageLibraryService defaultImageLibraryService) {
        this.aliOssUtil = aliOssUtil;
        this.defaultImageLibraryService = defaultImageLibraryService;
    }

    /**
     * 上传文件到 OSS 并返回文件访问地址。
     *
     * @param body 上传参数
     * @return 文件 URL
     */
    @PostMapping("/upload")
    public ApiResult<String> upload(@Valid UploadFileDTO body) {
        if (body.getFile() == null || body.getFile().isEmpty()) {
            return ApiResult.badRequest("文件为空");
        }
        String originalFilename = body.getFile().getOriginalFilename();
        if (!org.springframework.util.StringUtils.hasText(originalFilename)) {
            return ApiResult.badRequest("文件名为空");
        }

        String extension = getExtension(originalFilename);
        if (!org.springframework.util.StringUtils.hasText(extension) || !ALLOWED_EXT.contains(extension)) {
            return ApiResult.badRequest("文件格式错误");
        }

        long limit = body.getMaxSize() != null && body.getMaxSize() > 0 ? Math.min(body.getMaxSize(), HARD_MAX_SIZE) : DEFAULT_MAX_SIZE;
        if (body.getFile().getSize() > limit) {
            return ApiResult.badRequest("文件过大");
        }

        String baseName = stripExtension(originalFilename);
        String newFileName = baseName + "_" + UUID.randomUUID() + "." + extension;
        String dir = org.springframework.util.StringUtils.hasText(body.getDir()) ? sanitizePath(body.getDir()) : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String scene = sanitizePath(body.getScene());
        String prefix = org.springframework.util.StringUtils.hasText(scene) ? scene + "/" : "";
        String objectName = (org.springframework.util.StringUtils.hasText(dir) ? prefix + dir : prefix + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))) + "/" + newFileName;
        try {
            String url = aliOssUtil.upload(body.getFile().getBytes(), objectName);
            return ApiResult.successData(url);
        } catch (Exception e) {
            log.error("上传文件失败，objectName={}", objectName, e);
            return ApiResult.badRequest("上传失败");
        }
    }

    /**
     * 获取默认图片列表。
     *
     * @param query 查询参数
     * @return 默认图片列表
     */
    @GetMapping("/default-images")
    public ApiResult<List<DefaultImageVO>> defaultImages(@ModelAttribute @Valid DefaultImageQueryDTO query) {
        List<DefaultImageVO> result = defaultImageLibraryService.listDefaultImages(query.getScene(), query.getLimit());
        return ApiResult.successData(result);
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return null;
        }
        return name.substring(idx + 1).trim().toLowerCase();
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
        if (!org.springframework.util.StringUtils.hasText(input)) {
            return null;
        }
        String normalized = input.replace('\\', '/');
        String[] parts = normalized.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!org.springframework.util.StringUtils.hasText(part)) {
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