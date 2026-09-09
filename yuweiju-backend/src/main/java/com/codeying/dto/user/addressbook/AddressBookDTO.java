package com.codeying.dto.user.addressbook;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Address Book DTO 
 *
 * @author Endercloud
 */
@Data
public class AddressBookDTO {
    /**  ID */
    private Long id;
    /** consignee  */
    private String consignee;
    /** sex  */
    private String sex;
    /** Phone number. */
    @NotBlank(message = "手机号不能为空")
    private String phone;
    /** provinceCode  */
    private String provinceCode;
    /**  */
    private String provinceName;
    /** cityCode  */
    private String cityCode;
    /**  */
    private String cityName;
    /** districtCode  */
    private String districtCode;
    /**  */
    private String districtName;
    /** detail field. */
    @NotBlank(message = "详细地址不能为空")
    private String detail;
    /** label  */
    private String label;
    /**  */
    private Integer isDefault;
}

