package com.codeying.vo.user.setmeal;

import lombok.Data;

/**
 * 用户端套餐菜品 VO。
 *
 * @author Endercloud
 */
@Data
public class SetmealDishVO {
    /** copies field. */
    private Integer copies;
    /** description field. */
    private String description;
    /** image field. */
    private String image;
    /** Display name. */
    private String name;
}

