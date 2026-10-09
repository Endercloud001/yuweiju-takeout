package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeying.assembler.OrderAssembler;
import com.codeying.mapper.OrdersMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codeying.common.page.PageData;
import com.codeying.constant.RedisKeys;
import com.codeying.dto.admin.order.OrderConditionQuery;
import com.codeying.dto.user.order.OrdersPaymentDTO;
import com.codeying.dto.user.order.OrdersSubmitDTO;
import com.codeying.dto.user.order.OrderHistoryQuery;
import com.codeying.entity.AddressBook;
import com.codeying.entity.Dish;
import com.codeying.entity.OrderDetail;
import com.codeying.entity.OrderRiskResult;
import com.codeying.entity.Orders;
import com.codeying.entity.Setmeal;
import com.codeying.entity.ShoppingCart;
import com.codeying.entity.User;
import com.codeying.exception.OrderBusinessException;
import com.codeying.exception.RateLimitException;
import com.codeying.properties.BaiduMapProperties;
import com.codeying.properties.ShopProperties;
import com.codeying.service.AddressBookService;
import com.codeying.service.AnalysisObservationService;
import com.codeying.service.OrderDetailService;
import com.codeying.service.OrderRiskService;
import com.codeying.service.OrdersApplicationService;
import com.codeying.service.OrdersService;
import com.codeying.service.SetmealService;
import com.codeying.service.ShoppingCartService;
import com.codeying.service.DishService;
import com.codeying.service.UserService;
import com.codeying.utils.BaiduMapUtil;
import com.codeying.vo.user.order.OrderPaymentVO;
import com.codeying.vo.user.order.OrderSubmitVO;
import com.codeying.vo.admin.order.OrderStatisticsVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 订单应用服务实现。
 *
 * @author Endercloud
 */
@Service
public class OrdersApplicationServiceImpl implements OrdersApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrdersApplicationServiceImpl.class);
    private final OrdersMapper ordersMapper;
    private final OrdersService ordersService;
    private final OrderDetailService orderDetailService;
    private final DishService dishService;
    private final SetmealService setmealService;
    private final ShoppingCartService shoppingCartService;
    private final AddressBookService addressBookService;
    private final UserService userService;
    private final ShopProperties shopProperties;
    private final BaiduMapProperties baiduMapProperties;
    private final BaiduMapUtil baiduMapUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final AnalysisObservationService analysisObservationService;
    private final OrderRiskService orderRiskService;

    public OrdersApplicationServiceImpl(
            OrdersService ordersService,
            OrderDetailService orderDetailService,
            DishService dishService,
            SetmealService setmealService,
            ShoppingCartService shoppingCartService,
            AddressBookService addressBookService,
            UserService userService,
            ShopProperties shopProperties,
            BaiduMapProperties baiduMapProperties,
            BaiduMapUtil baiduMapUtil,
            StringRedisTemplate stringRedisTemplate,
            AnalysisObservationService analysisObservationService,
            OrderRiskService orderRiskService,
            OrdersMapper ordersMapper
    ) {
        this.ordersMapper = ordersMapper;
        this.ordersService = ordersService;
        this.orderDetailService = orderDetailService;
        this.dishService = dishService;
        this.setmealService = setmealService;
        this.shoppingCartService = shoppingCartService;
        this.addressBookService = addressBookService;
        this.userService = userService;
        this.shopProperties = shopProperties;
        this.baiduMapProperties = baiduMapProperties;
        this.baiduMapUtil = baiduMapUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.analysisObservationService = analysisObservationService;
        this.orderRiskService = orderRiskService;
    }

    @Override
    public com.codeying.vo.user.order.OrderVO buildUserOrderVO(Orders order) {
        List<OrderDetail> details = listDetails(order.getId());
        return OrderAssembler.toUserVO(order, details);
    }

    @Override
    public com.codeying.vo.admin.order.OrderVO buildAdminOrderVO(Orders order) {
        List<OrderDetail> details = listDetails(order.getId());
        return OrderAssembler.toAdminVO(order, details);
    }

    private List<OrderDetail> listDetails(Long orderId) {
        QueryWrapper<OrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId);
        wrapper.orderByAsc("id");
        List<OrderDetail> details = orderDetailService.list(wrapper);
        refreshDetailImageSnapshots(details);
        return details;
    }

    /**
     * 历史订单图片兜底：使用当前菜品/套餐图片覆盖 order_detail.image 的旧值。
     */
    private void refreshDetailImageSnapshots(List<OrderDetail> details) {
        if (details == null || details.isEmpty()) {
            return;
        }

        Set<Long> dishIds = new HashSet<>();
        Set<Long> setmealIds = new HashSet<>();
        for (OrderDetail detail : details) {
            if (detail.getDishId() != null) {
                dishIds.add(detail.getDishId());
            }
            if (detail.getSetmealId() != null) {
                setmealIds.add(detail.getSetmealId());
            }
        }

        Map<Long, String> dishImageMap = new HashMap<>();
        if (!dishIds.isEmpty()) {
            QueryWrapper<Dish> dishWrapper = new QueryWrapper<>();
            dishWrapper.in("id", dishIds);
            dishWrapper.select("id", "image");
            List<Dish> dishes = dishService.list(dishWrapper);
            for (Dish dish : dishes) {
                if (dish.getId() != null && StringUtils.hasText(dish.getImage())) {
                    dishImageMap.put(dish.getId(), dish.getImage());
                }
            }
        }

        Map<Long, String> setmealImageMap = new HashMap<>();
        if (!setmealIds.isEmpty()) {
            QueryWrapper<Setmeal> setmealWrapper = new QueryWrapper<>();
            setmealWrapper.in("id", setmealIds);
            setmealWrapper.select("id", "image");
            List<Setmeal> setmeals = setmealService.list(setmealWrapper);
            for (Setmeal setmeal : setmeals) {
                if (setmeal.getId() != null && StringUtils.hasText(setmeal.getImage())) {
                    setmealImageMap.put(setmeal.getId(), setmeal.getImage());
                }
            }
        }

        List<OrderDetail> updates = new ArrayList<>();
        for (OrderDetail detail : details) {
            String latestImage = null;
            if (detail.getDishId() != null) {
                latestImage = dishImageMap.get(detail.getDishId());
            } else if (detail.getSetmealId() != null) {
                latestImage = setmealImageMap.get(detail.getSetmealId());
            }
            if (!StringUtils.hasText(latestImage) || latestImage.equals(detail.getImage())) {
                continue;
            }

            detail.setImage(latestImage);
            OrderDetail update = new OrderDetail();
            update.setId(detail.getId());
            update.setImage(latestImage);
            updates.add(update);
        }

        if (!updates.isEmpty()) {
            orderDetailService.updateBatchById(updates);
        }
    }

    @Override
    @Transactional
    public OrderSubmitVO submit(Long userId, OrdersSubmitDTO body) {
        if (userId == null || body == null || body.getAddressBookId() == null) {
            throw new OrderBusinessException("参数错误");
        }

        List<ShoppingCart> carts = shoppingCartService.listForUser(userId);
        if (carts == null || carts.isEmpty()) throw new OrderBusinessException("购物车为空");

        AddressBook address = addressBookService.getById(body.getAddressBookId());
        if (address == null || !userId.equals(address.getUserId())) throw new OrderBusinessException("地址不存在");

        Integer packAmount = body.getPackAmount() == null ? 0 : body.getPackAmount();
        BigDecimal goodsTotal = BigDecimal.ZERO;
        for (ShoppingCart c : carts) {
            shoppingCartService.requireSaleable(c.getDishId(), c.getSetmealId());
            BigDecimal unit = c.getAmount() == null ? BigDecimal.ZERO : c.getAmount();
            int num = c.getNumber() == null ? 0 : c.getNumber();
            goodsTotal = goodsTotal.add(unit.multiply(BigDecimal.valueOf(num)));
        }
        BigDecimal total = goodsTotal.add(BigDecimal.valueOf(packAmount));

        Date now = new Date();
        Date estimatedDeliveryTime = calculateEstimatedDeliveryTime(address, now);

        User user = userService.getById(userId);

        Orders order = new Orders();
        order.setNumber(generateOrderNumber());
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setUserId(userId);
        order.setAddressBookId(body.getAddressBookId());
        order.setOrderTime(now);
        order.setPayMethod(body.getPayMethod());
        order.setPayStatus(Orders.UN_PAID);
        order.setAmount(total);
        order.setRemark(body.getRemark());
        order.setPhone(address.getPhone());
        order.setConsignee(address.getConsignee());
        order.setUserName(user == null ? null : user.getName());
        order.setAddress(buildAddress(address));
        order.setEstimatedDeliveryTime(estimatedDeliveryTime);
        order.setDeliveryStatus(body.getDeliveryStatus());
        order.setPackAmount(packAmount);
        order.setTablewareNumber(body.getTablewareNumber());
        order.setTablewareStatus(body.getTablewareStatus());

        if (!ordersService.save(order)) throw new OrderBusinessException("订单写入失败");

        List<OrderDetail> details = new ArrayList<>(carts.size());
        for (ShoppingCart c : carts) {
            OrderDetail d = new OrderDetail();
            d.setOrderId(order.getId());
            d.setName(c.getName());
            d.setImage(c.getImage());
            d.setDishId(c.getDishId());
            d.setSetmealId(c.getSetmealId());
            d.setDishFlavor(c.getDishFlavor());
            d.setNumber(c.getNumber());
            d.setAmount(c.getAmount());
            details.add(d);
        }
        if (!orderDetailService.saveBatch(details)) throw new OrderBusinessException("订单明细写入失败");
        // Finish every core write before optional Redis/risk side effects.
        if (shoppingCartService.clearForUser(userId) != carts.size()) throw new OrderBusinessException("购物车清理失败");
        analysisObservationService.recordRecommendationConversion(
                userId,
                order.getId(),
                carts.stream().map(ShoppingCart::getDishId).filter(id -> id != null && id > 0).distinct().toList()
        );
        orderRiskService.scoreOrder(order.getId(), "submit");

        OrderSubmitVO vo = new OrderSubmitVO();
        vo.setId(order.getId());
        vo.setOrderNumber(order.getNumber());
        vo.setOrderTime(order.getOrderTime());
        vo.setOrderAmount(order.getAmount());
        vo.setEstimatedDeliveryTime(order.getEstimatedDeliveryTime());
        return vo;
    }

    @Override
    @Transactional
    public OrderPaymentVO paymentMock(Long userId, OrdersPaymentDTO body) {
        if (userId == null || body == null || !StringUtils.hasText(body.getOrderNumber())) {
            throw new OrderBusinessException("参数错误");
        }

        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.eq("number", body.getOrderNumber().trim());
        wrapper.eq("user_id", userId);
        wrapper.last("limit 1");
        Orders order = ordersService.getOne(wrapper);
        if (order == null) throw new OrderBusinessException("订单不存在");

        User user = userService.getById(userId);
        String openid = user == null ? null : user.getOpenid();
        if (!StringUtils.hasText(openid)) throw new OrderBusinessException("用户信息异常");

        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);

        markPaid(order.getId());

        OrderPaymentVO vo = new OrderPaymentVO();
        vo.setTimeStamp(timeStamp);
        vo.setNonceStr("mock");
        vo.setPackageStr("prepay_id=mock");
        vo.setSignType("RSA");
        vo.setPaySign("mock");
        vo.setEstimatedDeliveryTime(order.getEstimatedDeliveryTime());
        return vo;
    }

    @Override
    @Transactional
    public void cancelByUser(Long userId, Long orderId) {
        if (userId == null || orderId == null) throw new OrderBusinessException("参数错误");
        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.eq("id", orderId);
        wrapper.eq("user_id", userId);
        wrapper.last("limit 1");
        Orders order = ordersService.getOne(wrapper);
        if (order == null) throw new OrderBusinessException("订单不存在");
        Integer status = order.getStatus();
        if (status == null || (!Integer.valueOf(Orders.PENDING_PAYMENT).equals(status) && !Integer.valueOf(Orders.TO_BE_CONFIRMED).equals(status))) {
            throw new OrderBusinessException("当前订单状态不可取消");
        }
        Orders update = new Orders();
        update.setId(order.getId());
        update.setStatus(Orders.CANCELLED);
        update.setCancelReason("用户取消订单");
        update.setCancelTime(new Date());
        ordersService.updateById(update);
    }

    @Override
    @Transactional
    public void repetition(Long userId, Long orderId) {
        if (userId == null || orderId == null) throw new OrderBusinessException("参数错误");
        Orders order = ordersService.getById(orderId);
        if (order == null || !userId.equals(order.getUserId())) throw new OrderBusinessException("订单不存在");

        List<OrderDetail> details = listDetails(orderId);

        QueryWrapper<ShoppingCart> clearWrapper = new QueryWrapper<>();
        clearWrapper.eq("user_id", userId);
        shoppingCartService.remove(clearWrapper);

        List<ShoppingCart> carts = new ArrayList<>(details.size());
        Date now = new Date();
        for (OrderDetail d : details) {
            ShoppingCart c = new ShoppingCart();
            c.setUserId(userId);
            c.setDishId(d.getDishId());
            c.setSetmealId(d.getSetmealId());
            c.setName(d.getName());
            c.setImage(d.getImage());
            c.setAmount(d.getAmount());
            c.setNumber(d.getNumber());
            c.setDishFlavor(d.getDishFlavor());
            c.setCreateTime(now);
            carts.add(c);
        }
        if (!carts.isEmpty()) shoppingCartService.saveBatch(carts);
    }

    @Override
    public String estimatedDeliveryTime(Long userId, Long addressBookId) {
        if (userId == null || addressBookId == null) throw new OrderBusinessException("参数错误");
        QueryWrapper<AddressBook> wrapper = new QueryWrapper<>();
        wrapper.eq("id", addressBookId);
        wrapper.eq("user_id", userId);
        wrapper.last("limit 1");
        AddressBook address = addressBookService.getOne(wrapper);
        if (address == null) throw new OrderBusinessException("地址不存在");
        Date estimated = calculateEstimatedDeliveryTime(address, new Date());
        return new SimpleDateFormat("HH:mm").format(estimated);
    }

    @Override
    public void reminder(Long userId, Long orderId) {
        if (userId == null || orderId == null) throw new OrderBusinessException("参数错误");
        boolean allowed;
        try {
            allowed = Boolean.TRUE.equals(
                    stringRedisTemplate.opsForValue().setIfAbsent(
                            RedisKeys.orderReminderThrottleKey(userId, orderId),
                            "1",
                            60,
                            TimeUnit.SECONDS
                    )
            );
        } catch (Exception e) {
            allowed = true;
        }
        if (!allowed) throw new RateLimitException("操作过于频繁");
    }

    @Override
    public PageData<com.codeying.vo.user.order.OrderVO> historyOrders(Long userId, Integer page, Integer pageSize, Integer status) {
        if (userId == null || page == null || pageSize == null || page <= 0 || pageSize <= 0) {
            throw new OrderBusinessException("参数错误");
        }
        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("order_time").orderByDesc("id");

        IPage<Orders> result = ordersService.page(new Page<>(page, pageSize), wrapper);
        List<com.codeying.vo.user.order.OrderVO> records = new ArrayList<>();
        for (Orders o : result.getRecords()) {
            records.add(buildUserOrderVO(o));
        }
        PageData<com.codeying.vo.user.order.OrderVO> data = new PageData<>();
        data.setTotal(result.getTotal());
        data.setRecords(records);
        return data;
    }

    @Override
    public PageData<com.codeying.vo.admin.order.OrderVO> adminConditionSearch(OrderConditionQuery query) {
        if (query == null || query.getPage() == null || query.getPageSize() == null || query.getPage() <= 0 || query.getPageSize() <= 0) {
            throw new OrderBusinessException("参数错误");
        }
        String riskLevel = StringUtils.hasText(query.getRiskLevel())
                ? query.getRiskLevel().trim().toUpperCase(java.util.Locale.ROOT) : null;
        IPage<Orders> result = ordersMapper.selectAdminConditionPage(
                new Page<>(query.getPage(), query.getPageSize()), query,
                query.beginDateTime(), query.endDateTime(), riskLevel);
        List<com.codeying.vo.admin.order.OrderVO> records = new ArrayList<>();
        for (Orders o : result.getRecords()) {
            records.add(buildAdminOrderVO(o));
        }
        enrichRiskFields(records);
        PageData<com.codeying.vo.admin.order.OrderVO> data = new PageData<>();
        data.setTotal(result.getTotal());
        data.setRecords(records);
        return data;
    }

    @Override
    public PageData<com.codeying.vo.user.order.OrderVO> historyOrders(Long userId, OrderHistoryQuery query) {
        if (query == null) {
            throw new OrderBusinessException("参数错误");
        }
        return historyOrders(userId, query.getPage(), query.getPageSize(), query.getStatus());
    }

    @Override
    public com.codeying.vo.admin.order.OrderVO adminOrderDetail(Long id) {
        if (id == null) throw new OrderBusinessException("参数错误");
        Orders orders = ordersService.getById(id);
        if (orders == null) throw new OrderBusinessException("订单不存在");
        com.codeying.vo.admin.order.OrderVO vo = buildAdminOrderVO(orders);
        enrichRiskFields(List.of(vo));
        return vo;
    }

    @Override
    public OrderStatisticsVO adminStatistics() {
        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setToBeConfirmed((int) ordersService.count(new QueryWrapper<Orders>().eq("status", Orders.TO_BE_CONFIRMED)));
        vo.setConfirmed((int) ordersService.count(new QueryWrapper<Orders>().eq("status", Orders.CONFIRMED)));
        vo.setDeliveryInProgress((int) ordersService.count(new QueryWrapper<Orders>().eq("status", Orders.DELIVERY_IN_PROGRESS)));
        return vo;
    }

    @Override
    public com.codeying.vo.user.order.OrderVO orderDetail(Long userId, Long orderId) {
        if (userId == null || orderId == null) throw new OrderBusinessException("参数错误");
        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.eq("id", orderId);
        wrapper.eq("user_id", userId);
        wrapper.last("limit 1");
        Orders order = ordersService.getOne(wrapper);
        if (order == null) throw new OrderBusinessException("订单不存在");
        return buildUserOrderVO(order);
    }

    private Date calculateEstimatedDeliveryTime(AddressBook address, Date now) {
        if (address == null || now == null) throw new OrderBusinessException("参数错误");
        String userFullAddress = buildAddress(address);
        int drivingMinutes = baiduMapUtil.checkAndGetDrivingMinutes(
                shopProperties.getAddress(),
                userFullAddress,
                baiduMapProperties.getAk()
        );
        int preparationMinutes = 10;
        long totalMinutes = (long) preparationMinutes + (long) drivingMinutes;
        return new Date(now.getTime() + totalMinutes * 60_000L);
    }

    private String buildAddress(AddressBook address) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(address.getProvinceName())) sb.append(address.getProvinceName());
        if (StringUtils.hasText(address.getCityName())) sb.append(address.getCityName());
        if (StringUtils.hasText(address.getDistrictName())) sb.append(address.getDistrictName());
        if (StringUtils.hasText(address.getDetail())) sb.append(address.getDetail());
        return sb.toString();
    }

    private String generateOrderNumber() {
        return String.valueOf(System.currentTimeMillis());
    }

    @Override
    @Transactional
    public void markPaid(Long orderId) {
        Orders update = new Orders();
        update.setId(orderId);
        update.setPayStatus(Orders.PAID);
        update.setStatus(Orders.TO_BE_CONFIRMED);
        update.setCheckoutTime(new Date());
        ordersService.updateById(update);
        orderRiskService.scoreOrder(orderId, "paid");
    }

    private void enrichRiskFields(List<com.codeying.vo.admin.order.OrderVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> orderIds = records.stream().map(com.codeying.vo.admin.order.OrderVO::getId).toList();
        Map<Long, OrderRiskResult> riskMap;
        try {
            riskMap = orderRiskService.findLatestByOrderIds(orderIds);
        } catch (RuntimeException failure) {
            log.warn("Optional order risk lookup unavailable for orderIds={}", orderIds, failure);
            records.forEach(this::markRiskUnavailable);
            return;
        }
        for (com.codeying.vo.admin.order.OrderVO vo : records) {
            try {
                OrderRiskResult riskResult = riskMap.get(vo.getId());
                if (riskResult == null) {
                    markRiskUnavailable(vo);
                    continue;
                }
                // Assemble optional reason before publishing any risk fields.
                String reason = orderRiskService.summarizeReason(riskResult);
                vo.setRiskScore(riskResult.getRiskScore());
                vo.setRiskLevel(riskResult.getRiskLevel());
                vo.setModelVersion(riskResult.getModelVersion());
                vo.setRiskReasons(reason);
            } catch (RuntimeException failure) {
                log.warn("Optional order risk assembly unavailable for orderId={}", vo.getId(), failure);
                markRiskUnavailable(vo);
            }
        }
    }

    private void markRiskUnavailable(com.codeying.vo.admin.order.OrderVO vo) {
        vo.setRiskScore(null);
        vo.setRiskLevel("UNAVAILABLE");
        vo.setModelVersion(null);
        vo.setRiskReasons("风险结果暂不可用，请稍后重试");
    }

    @Override
    @Transactional
    public void confirm(Long id) {
        Orders orders = ordersService.getById(id);
        if (orders == null || !Integer.valueOf(Orders.TO_BE_CONFIRMED).equals(orders.getStatus())) {
            throw new OrderBusinessException("当前订单状态不可接单");
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(Orders.CONFIRMED);
        ordersService.updateById(update);
    }

    @Override
    @Transactional
    public void reject(Long id, String reason) {
        Orders orders = ordersService.getById(id);
        if (orders == null || !Integer.valueOf(Orders.TO_BE_CONFIRMED).equals(orders.getStatus())) {
            throw new OrderBusinessException("当前订单状态不可拒单");
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(Orders.CANCELLED);
        update.setRejectionReason(reason);
        update.setCancelTime(new Date());
        ordersService.updateById(update);
    }

    @Override
    @Transactional
    public void cancelByAdmin(Long id, String reason) {
        Orders orders = ordersService.getById(id);
        if (orders == null) throw new OrderBusinessException("订单不存在");
        Integer s = orders.getStatus();
        boolean allowed = Integer.valueOf(Orders.PENDING_PAYMENT).equals(s)
                || Integer.valueOf(Orders.TO_BE_CONFIRMED).equals(s)
                || Integer.valueOf(Orders.CONFIRMED).equals(s);
        if (!allowed) throw new OrderBusinessException("当前订单状态不可取消");
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(Orders.CANCELLED);
        update.setCancelReason(reason);
        update.setCancelTime(new Date());
        ordersService.updateById(update);
    }

    @Override
    @Transactional
    public void deliver(Long id) {
        Orders orders = ordersService.getById(id);
        if (orders == null || !Integer.valueOf(Orders.CONFIRMED).equals(orders.getStatus())) {
            throw new OrderBusinessException("当前订单状态不可派送");
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(Orders.DELIVERY_IN_PROGRESS);
        ordersService.updateById(update);
    }

    @Override
    @Transactional
    public void complete(Long id) {
        Orders orders = ordersService.getById(id);
        if (orders == null || !Integer.valueOf(Orders.DELIVERY_IN_PROGRESS).equals(orders.getStatus())) {
            throw new OrderBusinessException("当前订单状态不可完成");
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(Orders.COMPLETED);
        update.setDeliveryTime(new Date());
        ordersService.updateById(update);
    }
}
