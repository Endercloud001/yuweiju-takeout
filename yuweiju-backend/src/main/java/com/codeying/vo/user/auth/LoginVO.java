package com.codeying.vo.user.auth;

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
    /** openid identifier. */
    private String openid;
    /** token field. */
    private String token;
}

