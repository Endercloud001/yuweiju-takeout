package com.codeying.assembler;

import com.codeying.entity.OrderDetail;
import com.codeying.entity.Orders;

import java.util.List;

/**
 * 订单装配器：将 Entity 装配为 VO。
 *
 * @author Endercloud
 */
public final class OrderAssembler {
    private OrderAssembler() {}

    /**
     * 
     *
     * @param order order 
     * @param details details 
     * @return com.codeying.vo.user.order.OrderVO 
     */
    public static com.codeying.vo.user.order.OrderVO toUserVO(Orders order, List<OrderDetail> details) {
        com.codeying.vo.user.order.OrderVO vo = new com.codeying.vo.user.order.OrderVO();
        vo.setId(order.getId());
        vo.setNumber(order.getNumber());
        vo.setStatus(order.getStatus());
        vo.setUserId(order.getUserId());
        vo.setAddressBookId(order.getAddressBookId());
        vo.setOrderTime(order.getOrderTime());
        vo.setCheckoutTime(order.getCheckoutTime());
        vo.setPayMethod(order.getPayMethod());
        vo.setPayStatus(order.getPayStatus());
        vo.setAmount(order.getAmount());
        vo.setRemark(order.getRemark());
        vo.setPhone(order.getPhone());
        vo.setAddress(order.getAddress());
        vo.setUserName(order.getUserName());
        vo.setConsignee(order.getConsignee());
        vo.setCancelReason(order.getCancelReason());
        vo.setCancelTime(order.getCancelTime());
        vo.setRejectionReason(order.getRejectionReason());
        vo.setEstimatedDeliveryTime(order.getEstimatedDeliveryTime());
        vo.setDeliveryStatus(order.getDeliveryStatus());
        vo.setDeliveryTime(order.getDeliveryTime());
        vo.setPackAmount(order.getPackAmount());
        vo.setTablewareNumber(order.getTablewareNumber());
        vo.setTablewareStatus(order.getTablewareStatus());
        vo.setOrderDetailList(details == null ? List.of() : details);
        vo.setOrderDishes(buildOrderDishes(vo.getOrderDetailList()));
        return vo;
    }

    /**
     * 
     *
     * @param order order 
     * @param details details 
     * @return com.codeying.vo.admin.order.OrderVO 
     */
    public static com.codeying.vo.admin.order.OrderVO toAdminVO(Orders order, List<OrderDetail> details) {
        com.codeying.vo.admin.order.OrderVO vo = new com.codeying.vo.admin.order.OrderVO();
        vo.setId(order.getId());
        vo.setNumber(order.getNumber());
        vo.setStatus(order.getStatus());
        vo.setUserId(order.getUserId());
        vo.setAddressBookId(order.getAddressBookId());
        vo.setOrderTime(order.getOrderTime());
        vo.setCheckoutTime(order.getCheckoutTime());
        vo.setPayMethod(order.getPayMethod());
        vo.setPayStatus(order.getPayStatus());
        vo.setAmount(order.getAmount());
        vo.setRemark(order.getRemark());
        vo.setPhone(order.getPhone());
        vo.setAddress(order.getAddress());
        vo.setUserName(order.getUserName());
        vo.setConsignee(order.getConsignee());
        vo.setCancelReason(order.getCancelReason());
        vo.setCancelTime(order.getCancelTime());
        vo.setRejectionReason(order.getRejectionReason());
        vo.setEstimatedDeliveryTime(order.getEstimatedDeliveryTime());
        vo.setDeliveryStatus(order.getDeliveryStatus());
        vo.setDeliveryTime(order.getDeliveryTime());
        vo.setPackAmount(order.getPackAmount());
        vo.setTablewareNumber(order.getTablewareNumber());
        vo.setTablewareStatus(order.getTablewareStatus());
        vo.setOrderDetailList(details == null ? List.of() : details);
        vo.setOrderDishes(buildOrderDishes(vo.getOrderDetailList()));
        return vo;
    }

    /**
     * Convert data structure.
     *
     * @param details details parameter
     * @return String result
     */
    public static String buildOrderDishes(List<OrderDetail> details) {
        if (details == null || details.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (OrderDetail d : details) {
            if (d == null) continue;
            if (!sb.isEmpty()) sb.append(";");
            sb.append(d.getName() == null ? "" : d.getName());
            sb.append("*");
            sb.append(d.getNumber() == null ? 0 : d.getNumber());
        }
        return sb.toString();
    }
}
