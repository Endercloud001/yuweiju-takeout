package com.codeying.vo.admin.setmeal;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 管理端套餐分页 VO。
 *
 * @author Endercloud
 */
@Data
public class SetmealPageVO {
    /** Primary key id. */
    private Long id;
    /** categoryId identifier. */
    private Long categoryId;
    /** Display name. */
    private String categoryName;
    /** description field. */
    private String description;
    /** image field. */
    private String image;
    /** Display name. */
    private String name;
    /** Amount value. */
    private BigDecimal price;
    /** Status value. */
    private Integer status;
    /** Time value. */
    private Date updateTime;
}

