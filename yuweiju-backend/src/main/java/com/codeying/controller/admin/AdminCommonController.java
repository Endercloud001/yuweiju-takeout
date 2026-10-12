package com.codeying.controller.admin;

import com.codeying.dto.admin.common.DefaultImageQueryDTO;
import com.codeying.dto.admin.common.UploadFileDTO;
import com.codeying.result.ApiResult;
import com.codeying.service.DefaultImageLibraryService;
import com.codeying.service.UploadService;
import com.codeying.vo.admin.common.DefaultImageVO;
import jakarta.validation.Valid;
import com.codeying.exception.BusinessException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 管理端通用协议入口；认证由现有 JwtAuthInterceptor 负责。 */
@RestController
@RequestMapping("/admin/common")
public class AdminCommonController {
    private final UploadService uploadService;
    private final DefaultImageLibraryService defaultImageLibraryService;

    public AdminCommonController(UploadService uploadService, DefaultImageLibraryService defaultImageLibraryService) {
        this.uploadService = uploadService;
        this.defaultImageLibraryService = defaultImageLibraryService;
    }

    @PostMapping("/upload")
    public ApiResult<String> upload(@Valid UploadFileDTO body, BindingResult binding) {
        if (binding.hasErrors()) throw new BusinessException(binding.getAllErrors().get(0).getDefaultMessage());
        return ApiResult.successData(uploadService.upload(body));
    }

    @GetMapping("/default-images")
    public ApiResult<List<DefaultImageVO>> defaultImages(@ModelAttribute @Valid DefaultImageQueryDTO query, BindingResult binding) {
        if (binding.hasErrors()) throw new BusinessException(binding.getAllErrors().get(0).getDefaultMessage());
        return ApiResult.successData(defaultImageLibraryService.listDefaultImages(query.getScene(), query.getLimit()));
    }
}
