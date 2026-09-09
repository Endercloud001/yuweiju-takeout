package com.codeying.vo.user.dish;

import com.codeying.entity.DishFlavor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 用户端菜品 VO。
 *
 * @author Endercloud
 */
@Data
public class DishVO {
    /** Primary key id. */
    private Long id;
    /** categoryId identifier. */
    private Long categoryId;
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

