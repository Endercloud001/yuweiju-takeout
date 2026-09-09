package com.codeying.vo.admin.employee;

import lombok.Data;

/**
 * Login VO view object.
 *
 * @author Endercloud
 */
@Data
public class LoginVO {
    /** Primary key id. */
    private Long id;
    /** Display name. */
    private String name;
    /** Display name. */
    private String userName;
    /** token field. */
    private String token;
    /** ttlMillis field. */
    private Long ttlMillis;
}

