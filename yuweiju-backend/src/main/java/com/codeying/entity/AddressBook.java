package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 地址簿实体。
 *
 * @author Endercloud
 */
@Data
@TableName("address_book")
public class AddressBook implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("user_id")
    /** 用户 ID */
    private Long userId;

    @TableField("consignee")
    /** 收货人 */
    private String consignee;

    @TableField("sex")
    /** 性别：0-女 1-男（项目约定） */
    private String sex;

    @TableField("phone")
    /** 联系电话 */
    private String phone;

    @TableField("province_code")
    /** 省份编码 */
    private String provinceCode;

    @TableField("province_name")
    /** 省份名称 */
    private String provinceName;

    @TableField("city_code")
    /** 城市编码 */
    private String cityCode;

    @TableField("city_name")
    /** 城市名称 */
    private String cityName;

    @TableField("district_code")
    /** 区县编码 */
    private String districtCode;

    @TableField("district_name")
    /** 区县名称 */
    private String districtName;

    @TableField("detail")
    /** 详细地址 */
    private String detail;

    @TableField("label")
    /** 地址标签（如 家/公司/学校） */
    private String label;

    @TableField("is_default")
    /** 是否默认地址：1-默认 0-非默认 */
    private Integer isDefault;
}
