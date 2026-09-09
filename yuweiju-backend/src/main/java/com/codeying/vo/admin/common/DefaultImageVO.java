package com.codeying.vo.admin.common;

import lombok.Data;

/**
 * 默认图片信息。
 *
 * @author Endercloud
 */
@Data
public class DefaultImageVO {

    /** Display name. */
    private String name;

    /** objectKey field. */
    private String objectKey;

    /** url field. */
    private String url;

    /** scene field. */
    private String scene;

    /** size field. */
    private Long size;

    /** lastModified field. */
    private String lastModified;
}