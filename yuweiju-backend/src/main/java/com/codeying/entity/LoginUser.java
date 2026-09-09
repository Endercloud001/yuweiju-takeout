package com.codeying.entity;

import lombok.Data;

/**
 * 可登录用户抽象模型。
 *
 * @author Endercloud
 */
@Data
public class LoginUser {

    /** 用户 ID（不同角色实体可复用） */
    protected String id;
    /** 登录用户名 */
    protected String username;
    /** 登录密码 */
    protected String password;
    /** 角色标识 */
    protected String role;
    /** 角色中文名 */
    protected String rolech;

}
