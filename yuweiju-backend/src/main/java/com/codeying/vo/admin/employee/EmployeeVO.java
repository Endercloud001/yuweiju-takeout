package com.codeying.vo.admin.employee;

import lombok.Data;

import java.util.Date;

/**
 * Employee VO view object.
 *
 * @author Endercloud
 */
@Data
public class EmployeeVO {
    /** Primary key id. */
    private Long id;
    /** Display name. */
    private String username;
    /** Display name. */
    private String name;
    /** Phone number. */
    private String phone;
    /** sex field. */
    private String sex;
    /** idNumber field. */
    private String idNumber;
    /** Status value. */
    private Integer status;
    /** Time value. */
    private Date createTime;
    /** Time value. */
    private Date updateTime;
    /** createUser field. */
    private Long createUser;
    /** updateUser field. */
    private Long updateUser;
}

