package com.codeying.vo.admin.dish;

import com.codeying.entity.DishFlavor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 管理端菜品详情 VO。
 *
 * @author Endercloud
 */
@Data
public class DishVO {
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
    /** flavors field. */
    private List<DishFlavor> flavors;
}

