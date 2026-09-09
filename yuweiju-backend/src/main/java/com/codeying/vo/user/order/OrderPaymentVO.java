package com.codeying.vo.user.order;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

/**
 * 用户支付返回参数（前端拉起支付需要的字段）。
 *
 * @author Endercloud
 */
@Data
public class OrderPaymentVO {
    /** timeStamp field. */
    private String timeStamp;
    /** nonceStr field. */
    private String nonceStr;
    /** packageStr field. */
    private String packageStr;
    /** signType field. */
    private String signType;
    /** paySign field. */
    private String paySign;
    /** Time value. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date estimatedDeliveryTime;
}

