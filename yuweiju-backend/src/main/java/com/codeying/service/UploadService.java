package com.codeying.service;

import com.codeying.dto.admin.common.UploadFileDTO;

/** 商品图片上传用例，保留既有格式、大小和对象路径规则。 */
public interface UploadService {
    /** 校验并上传图片，返回访问 URL；无效文件或上游失败抛业务异常，保留上游 cause。 */
    String upload(UploadFileDTO body);
}
