package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.dto.common.ai_assistant.AiAssistantMessageSendDTO;
import com.codeying.entity.AddressBook;
import com.codeying.entity.AiAssistantMessage;
import com.codeying.entity.AiAssistantSession;
import com.codeying.entity.Category;
import com.codeying.entity.Dish;
import com.codeying.entity.DishFlavor;
import com.codeying.entity.OrderDetail;
import com.codeying.entity.Orders;
import com.codeying.entity.User;
import com.codeying.service.AddressBookService;
import com.codeying.service.AiAssistantApplicationService;
import com.codeying.service.AiAssistantMessageService;
import com.codeying.service.AiAssistantSessionService;
import com.codeying.service.AiModelService;
import com.codeying.service.AiWeatherService;
import com.codeying.service.AnalysisApplicationService;
import com.codeying.service.AnalysisObservationService;
import com.codeying.service.CategoryService;
import com.codeying.service.DishFlavorService;
import com.codeying.service.DishService;
import com.codeying.service.OrderDetailService;
import com.codeying.service.OrdersService;
import com.codeying.service.UserService;
import com.codeying.vo.admin.analysis.DishHeatPredictionVO;
import com.codeying.vo.admin.ai_assistant.AiAssistantSessionSummaryVO;
import com.codeying.vo.common.ai_assistant.AiAssistantDishCardVO;
import com.codeying.vo.common.ai_assistant.AiAssistantMessageVO;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.codeying.vo.user.ai_assistant.AiAssistantSessionVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 智能助手应用服务实现，负责会话、推荐与消息编排。
 *
 * @author Endercloud
 */
@Service
public class AiAssistantApplicationServiceImpl implements AiAssistantApplicationService {
/**
 * LoggerFactory.getLogger
 * @return 
 */

    private static final Logger log = LoggerFactory.getLogger(AiAssistantApplicationServiceImpl.class);
/**
 * ZoneId.of
 * @return 
 */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int CANDIDATE_LIMIT = 10;
    private static final int DISH_QUERY_LIMIT = 200;
    private static final String ACTION_SUGGEST_DISH = "suggest_dish";
/**
 * Pattern.compile
 * @return 
 */
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);
/**
 * Pattern.compile
 * @return 
 */
    private static final Pattern REJECT_DISH_PATTERN = Pattern.compile("\\[(.+?)]");
/**
 * Pattern.compile
 * @return 
 */
    private static final Pattern TEMP_PATTERN = Pattern.compile("(-?\\d+)\\s*[°度]?\\s*c", Pattern.CASE_INSENSITIVE);
/**
 * Pattern.compile
 * @return 
 */
    private static final Pattern FALLBACK_TEMP_PATTERN = Pattern.compile("(-?\\d+)");
/**
 * Pattern.compile
 * @return 
 */
    private static final Pattern SINGLE_DISH_REQUEST_PATTERN = Pattern.compile("(一道|一个|1道|1个|一份|一个菜)");
/**
 * Pattern.compile
 * @return 
 */
    private static final Pattern NONE_STYLE_REPLY_PATTERN = Pattern.compile("(没有|暂无|不含).*(口味|风味|菜系)");
/**
 * Pattern.compile
 * @return 
 */
    private static final Pattern MULTI_DISH_REPLY_PATTERN = Pattern.compile("(几道|几款|多道|多个|以下菜品)");
    private static final String INTENT_RECOMMEND = "recommend_dish";
    private static final String INTENT_ONE_KEY = "one_key_recommend";
    private static final String INTENT_ORDER_QUERY = "order_query";
    private static final String INTENT_ORDER_REMIND = "order_remind";
    private static final String INTENT_ORDER_CANCEL = "order_cancel";
    private static final String INTENT_MANUAL_SERVICE = "manual_service";
    private static final String INTENT_ADDRESS_BOOK = "address_book_manage";
    private static final String INTENT_CART = "cart_manage";
    private static final String INTENT_OUT_OF_SCOPE = "out_of_scope";
    private static final Set<String> ALLOWED_INTENTS = Set.of(
            INTENT_RECOMMEND,
            INTENT_ONE_KEY,
            INTENT_ORDER_QUERY,
            INTENT_ORDER_REMIND,
            INTENT_ORDER_CANCEL,
            INTENT_MANUAL_SERVICE,
            INTENT_ADDRESS_BOOK,
            INTENT_CART,
            INTENT_OUT_OF_SCOPE
    );
    private static final double INTENT_CONFIDENCE_THRESHOLD = 0.70D;

    private final AiAssistantSessionService sessionService;
    private final AiAssistantMessageService messageService;
    private final CategoryService categoryService;
    private final DishService dishService;
    private final DishFlavorService dishFlavorService;
    private final AddressBookService addressBookService;
    private final OrdersService ordersService;
    private final OrderDetailService orderDetailService;
    private final UserService userService;
    private final AiWeatherService aiWeatherService;
    private final AnalysisApplicationService analysisApplicationService;
    private final AnalysisObservationService analysisObservationService;
    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;

    public AiAssistantApplicationServiceImpl(
            AiAssistantSessionService sessionService,
            AiAssistantMessageService messageService,
            CategoryService categoryService,
            DishService dishService,
            DishFlavorService dishFlavorService,
            AddressBookService addressBookService,
            OrdersService ordersService,
            OrderDetailService orderDetailService,
            UserService userService,
            AiWeatherService aiWeatherService,
            AnalysisApplicationService analysisApplicationService,
            AnalysisObservationService analysisObservationService,
            AiModelService aiModelService,
            ObjectMapper objectMapper
    ) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.categoryService = categoryService;
        this.dishService = dishService;
        this.dishFlavorService = dishFlavorService;
        this.addressBookService = addressBookService;
        this.ordersService = ordersService;
        this.orderDetailService = orderDetailService;
        this.userService = userService;
        this.aiWeatherService = aiWeatherService;
        this.analysisApplicationService = analysisApplicationService;
        this.analysisObservationService = analysisObservationService;
        this.aiModelService = aiModelService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AiAssistantSessionVO openSession(Long userId) {
        if (userId == null) {
            return null;
        }
        Date dayStart = todayStart();
        Date nextDayStart = tomorrowStart();
        AiAssistantSession existing = sessionService.getOne(new QueryWrapper<AiAssistantSession>()
                .eq("user_id", userId)
                .eq("status", AiAssistantSession.STATUS_OPEN)
                .ge("create_time", dayStart)
                .lt("create_time", nextDayStart)
                .orderByDesc("update_time")
                .last("limit 1"));
        if (existing != null) {
            return toSessionVO(existing);
        }
        Date now = new Date();
        AiAssistantSession session = new AiAssistantSession();
        session.setUserId(userId);
        session.setStatus(AiAssistantSession.STATUS_OPEN);
        session.setModel("deepseek-chat");
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionService.save(session);
        return toSessionVO(session);
    }

    @Override
    @Transactional
    public AiAssistantSendReplyVO sendMessage(Long userId, AiAssistantMessageSendDTO body) {
        if (userId == null || body == null || body.getSessionId() == null || !StringUtils.hasText(body.getContent())) {
            return buildBusyReply();
        }
        AiAssistantSession session = sessionService.getById(body.getSessionId());
        if (session == null
                || !userId.equals(session.getUserId())
                || !isSessionOpen(session.getStatus())
                || !isSessionInToday(session)) {
            return buildBusyReply();
        }

        int senderType = body.getSenderType() == null ? AiAssistantMessage.SENDER_USER : body.getSenderType();
        String content = body.getContent().trim();
        saveIncomingMessage(session.getId(), userId, senderType, content);

        String intent = senderType == AiAssistantMessage.SENDER_SYSTEM
                ? INTENT_RECOMMEND : resolveIntentByRuleThenModel(content);
        AiAssistantSendReplyVO replyVO = handleIntent(userId, intent, content, senderType);

        AiAssistantMessage aiMessage = new AiAssistantMessage();
        aiMessage.setSessionId(session.getId());
        aiMessage.setSenderType(AiAssistantMessage.SENDER_AI);
        aiMessage.setSenderId(null);
        aiMessage.setContent(composeAiMessageText(replyVO));
        aiMessage.setIntent(intent);
        aiMessage.setMetadata(buildAiMetadata(intent, replyVO));
        aiMessage.setCreateTime(new Date());
        messageService.save(aiMessage);
        if (analysisObservationService != null) {
            analysisObservationService.recordRecommendationExposure(userId, session.getId(), aiMessage.getId(), replyVO);
        }

        AiAssistantSession update = new AiAssistantSession();
        update.setId(session.getId());
        update.setLastIntent(intent);
        update.setPendingAction(replyVO.getAction());
        update.setPendingPayload(null);
        update.setUpdateTime(new Date());
        sessionService.updateById(update);
        return replyVO;
    }

    @Override
    public List<AiAssistantMessageVO> listMessages(Long sessionId, Long beforeId, Integer limit) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        AiAssistantSession session = sessionService.getById(sessionId);
        if (session == null) {
            return Collections.emptyList();
        }
        Date dayStart = todayStart();
        Date nextDayStart = tomorrowStart();
        int size = normalizeLimit(limit);
        QueryWrapper<AiAssistantMessage> wrapper = new QueryWrapper<AiAssistantMessage>()
                .eq("session_id", sessionId)
                .ge("create_time", dayStart)
                .lt("create_time", nextDayStart);
        if (beforeId != null) {
            wrapper.lt("id", beforeId);
        }
        wrapper.orderByDesc("id").last("limit " + size);
        List<AiAssistantMessage> messages = messageService.list(wrapper);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiAssistantMessageVO> result = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            result.add(toMessageVO(messages.get(i)));
        }
        return result;
    }

    @Override
    public List<AiAssistantSessionSummaryVO> listOpenSessions() {
        Date dayStart = todayStart();
        Date nextDayStart = tomorrowStart();
        List<AiAssistantSession> sessions = sessionService.list(new QueryWrapper<AiAssistantSession>()
                .eq("status", AiAssistantSession.STATUS_OPEN)
                .ge("update_time", dayStart)
                .lt("update_time", nextDayStart)
                .orderByDesc("update_time"));
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = sessions.stream().map(AiAssistantSession::getUserId).distinct().toList();
        Map<Long, User> userMap = buildUserMap(userIds);
        List<AiAssistantSessionSummaryVO> result = new ArrayList<>(sessions.size());
        for (AiAssistantSession session : sessions) {
            AiAssistantSessionSummaryVO vo = new AiAssistantSessionSummaryVO();
            vo.setId(session.getId());
            vo.setUserId(session.getUserId());
            vo.setStatus(session.getStatus());
            vo.setUpdateTime(session.getUpdateTime());
            User user = userMap.get(session.getUserId());
            if (user != null) {
                vo.setUserName(user.getName());
                vo.setUserAvatar(user.getAvatar());
            }
            AiAssistantMessage last = findLastMessage(session.getId());
            if (last != null) {
                vo.setLastMessagePreview(limitPreview(last.getContent()));
            }
            result.add(vo);
        }
        return result;
    }

    private void saveIncomingMessage(Long sessionId, Long userId, int senderType, String content) {
        AiAssistantMessage message = new AiAssistantMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderId(senderType == AiAssistantMessage.SENDER_USER ? userId : null);
        message.setContent(content);
        message.setCreateTime(new Date());
        messageService.save(message);
    }

    private AiAssistantSendReplyVO handleIntent(Long userId, String intent, String userText, int senderType) {
        if (INTENT_MANUAL_SERVICE.equals(intent)) {
            return buildGuideReply("我可以先帮您推荐菜品；如需人工客服，请前往“客服消息”页面继续沟通。");
        }
        if (INTENT_ORDER_QUERY.equals(intent)) {
            return buildGuideReply("您可以在“历史订单”页面查看订单状态与配送进度。");
        }
        if (INTENT_ORDER_REMIND.equals(intent)) {
            return buildGuideReply("催单请在订单详情页点击“催单”按钮，我这边先为您推荐可快速出餐的菜品。");
        }
        if (INTENT_ORDER_CANCEL.equals(intent)) {
            return buildGuideReply("退单/退款请在订单详情页提交申请，系统会尽快处理。");
        }
        if (INTENT_ADDRESS_BOOK.equals(intent)) {
            return buildGuideReply("修改或管理收货地址，请前往“地址管理”页面操作。");
        }
        if (INTENT_CART.equals(intent)) {
            return buildGuideReply("购物车的增删与清空操作，请前往“购物车”页面处理。");
        }
        if (INTENT_OUT_OF_SCOPE.equals(intent)) {
            return buildGuideReply("我当前主要支持菜品推荐、一键推荐、订单查询/催单/退款指引与人工客服引导。");
        }

        Set<String> excludes = new HashSet<>();
        if (senderType == AiAssistantMessage.SENDER_SYSTEM) {
            String rejectedDish = parseRejectedDishName(userText);
            if (StringUtils.hasText(rejectedDish)) {
                excludes.add(rejectedDish.trim());
            }
        }
        if (INTENT_ONE_KEY.equals(intent)) {
            return buildOneKeyRecommendReply(userId, userText, excludes);
        }
        return buildRecommendReply(userId, userText, excludes, false);
    }

    private AiAssistantSendReplyVO buildOneKeyRecommendReply(Long userId, String userText, Set<String> excludes) {
        List<Long> analyzedDishIds = analysisApplicationService.listRecommendedDishIdsForUser(userId, CANDIDATE_LIMIT);
        if (analyzedDishIds != null && !analyzedDishIds.isEmpty()) {
            List<DishCandidate> analyzedCandidates = buildDishCandidatesByIds(analyzedDishIds, excludes);
            if (!analyzedCandidates.isEmpty()) {
                String historySummary = "已使用聚类与热度分析结果生成候选菜品。";
                return callModelForRecommend(
                        userId,
                        userText,
                        analyzedCandidates,
                        historySummary,
                        true,
                        buildAnalysisPayload("one_key_analysis", resolveCurrentHeatModelVersion(), false)
                );
            }
        }

        List<Orders> orders = ordersService.list(new QueryWrapper<Orders>()
                .eq("user_id", userId)
                .orderByDesc("order_time")
                .last("limit 30"));
        if (orders == null || orders.size() < 5) {
            return buildRecommendReply(userId, userText, excludes, true);
        }
        List<Long> orderIds = orders.stream().map(Orders::getId).toList();
        if (orderIds.isEmpty()) {
            return buildRecommendReply(userId, userText, excludes, true);
        }
        List<OrderDetail> details = orderDetailService.list(new QueryWrapper<OrderDetail>()
                .in("order_id", orderIds)
                .isNotNull("dish_id"));
        if (details == null || details.isEmpty()) {
            return buildRecommendReply(userId, userText, excludes, true);
        }
        Map<Long, Integer> freq = new HashMap<>();
        for (OrderDetail detail : details) {
            if (detail.getDishId() == null) {
                continue;
            }
            int add = detail.getNumber() == null ? 1 : Math.max(1, detail.getNumber());
            freq.put(detail.getDishId(), freq.getOrDefault(detail.getDishId(), 0) + add);
        }
        List<Long> sortedDishIds = freq.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(CANDIDATE_LIMIT)
                .toList();
        if (sortedDishIds.isEmpty()) {
            return buildRecommendReply(userId, userText, excludes, true);
        }
        List<DishCandidate> candidates = buildDishCandidatesByIds(sortedDishIds, excludes);
        if (candidates.isEmpty()) {
            return buildRecommendReply(userId, userText, excludes, true);
        }
        String historySummary = "历史偏好菜品ID频次：" + freq.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));
        return callModelForRecommend(
                userId,
                userText,
                candidates,
                historySummary,
                true,
                buildAnalysisPayload("one_key_history", resolveCurrentHeatModelVersion(), true)
        );
    }

    private AiAssistantSendReplyVO buildRecommendReply(Long userId, String userText, Set<String> excludes, boolean appendHistoryHint) {
        String weatherSummary = resolveWeatherSummary(userId);
        AnalyzedRecommendSelection analyzedSelection = selectAnalyzedRecommendSelection(userId, userText, excludes, appendHistoryHint);
        if (!analyzedSelection.candidates().isEmpty()) {
            return callModelForRecommend(
                    userId,
                    userText,
                    analyzedSelection.candidates(),
                    analyzedSelection.historySummary(),
                    false,
                    buildAnalysisPayload(
                            analyzedSelection.strategy(),
                            analyzedSelection.modelVersion(),
                            analyzedSelection.fallback()
                    )
            );
        }
        List<DishCandidate> candidates = buildDishCandidates(excludes, weatherSummary, userText);
        if (candidates.isEmpty()) {
            return buildGuideReply("暂时没有可推荐的菜品，您可以稍后再试。");
        }
        String historySummary = appendHistoryHint ? "历史订单不足 5 单，已退化为普通推荐。" : "无特殊历史偏好。";
        return callModelForRecommend(
                userId,
                userText,
                candidates,
                historySummary,
                false,
                buildAnalysisPayload("rule_fallback", resolveCurrentHeatModelVersion(), true)
        );
    }

    private AnalyzedRecommendSelection selectAnalyzedRecommendSelection(
            Long userId,
            String userText,
            Set<String> excludes,
            boolean appendHistoryHint
    ) {
        if (analysisApplicationService == null) {
            return AnalyzedRecommendSelection.empty();
        }
        if (shouldPreferHeatRecommendations(userText)) {
            List<DishHeatPredictionVO> heatPredictions = analysisApplicationService.listHeatPredictions(LocalDate.now(APP_ZONE), CANDIDATE_LIMIT);
            List<Long> hotDishIds = heatPredictions.stream()
                    .map(DishHeatPredictionVO::getDishId)
                    .filter(Objects::nonNull)
                    .toList();
            List<DishCandidate> hotCandidates = buildDishCandidatesByIds(hotDishIds, excludes);
            if (!hotCandidates.isEmpty()) {
                return new AnalyzedRecommendSelection(
                        hotCandidates,
                        "已根据热度分析结果优先筛选最近热门菜品。",
                        resolveModelVersion(heatPredictions),
                        "heat_analysis",
                        false
                );
            }
        }

        List<Long> analyzedDishIds = analysisApplicationService.listRecommendedDishIdsForUser(userId, CANDIDATE_LIMIT);
        List<DishCandidate> analyzedCandidates = buildDishCandidatesByIds(analyzedDishIds, excludes);
        if (!analyzedCandidates.isEmpty()) {
            String historySummary = appendHistoryHint
                    ? "历史订单不足 5 单，已优先使用热度分析与冷启动候选进行推荐。"
                    : "已优先使用用户分群与热度分析结果生成候选菜品。";
            return new AnalyzedRecommendSelection(
                    analyzedCandidates,
                    historySummary,
                    resolveCurrentHeatModelVersion(),
                    "analysis_personalized",
                    appendHistoryHint
            );
        }
        return AnalyzedRecommendSelection.empty();
    }
    private AiAssistantSendReplyVO callModelForRecommend(
            Long userId,
            String userText,
            List<DishCandidate> candidates,
            String historySummary,
            boolean oneKey,
            Map<String, Object> payload
    ) {
        int desiredDishCount = resolveDesiredDishCount(userText);
        String weatherSummary = resolveWeatherSummary(userId);
        String candidateJson = safeWriteJson(candidates.stream().map(DishCandidate::toMap).toList());
        String systemPrompt = buildRecommendPrompt(weatherSummary, candidateJson, userText, historySummary, oneKey, desiredDishCount);
        String content;
        try {
            content = aiModelService.chat(systemPrompt, userText);
        } catch (Exception ex) {
            log.error("AI model call failed", ex);
            return buildBusyReply();
        }

        AiAssistantSendReplyVO parsed = parseModelReply(content, candidates, desiredDishCount);
        if (parsed != null) {
            if (parsed.getDishes() != null && !parsed.getDishes().isEmpty()) {
                parsed.setPayload(payload);
            }
            return finalizeReply(userText, parsed);
        }
        AiAssistantSendReplyVO fallback = new AiAssistantSendReplyVO();
        fallback.setReply(oneKey ? "为您按历史口味推荐了几道常点菜品。" : "为您挑了几道菜，您看看是否喜欢。");
        fallback.setDishes(candidates.stream().limit(Math.max(1, desiredDishCount)).map(DishCandidate::toCardVO).toList());
        fallback.setAction(ACTION_SUGGEST_DISH);
        fallback.setPayload(payload);
        return finalizeReply(userText, fallback);
    }
    private String resolveWeatherSummary(Long userId) {
        AddressBook address = addressBookService.getOne(new QueryWrapper<AddressBook>()
                .eq("user_id", userId)
                .eq("is_default", 1)
                .last("limit 1"));
        if (address == null) {
            return null;
        }
        String city = address.getCityName();
        String fullAddress = joinAddress(address);
        if (StringUtils.hasText(city)) {
            String weather = aiWeatherService.getWeatherSummary(city);
            if (StringUtils.hasText(weather)) {
                logWeatherSummary(city, weather);
                return weather;
            }
        }
        if (StringUtils.hasText(fullAddress)) {
            String weather = aiWeatherService.getWeatherSummary(fullAddress);
            if (StringUtils.hasText(weather)) {
                logWeatherSummary(fullAddress, weather);
            }
            return weather;
        }
        return null;
    }

    private void logWeatherSummary(String address, String weather) {
        log.info("[AI天气] 查询地址: {}", address);
        log.info("[AI天气] 天气摘要: {}", weather);
    }

    private List<DishCandidate> buildDishCandidates(Set<String> excludes, String weatherSummary, String userText) {
        List<Dish> dishes = dishService.list(new QueryWrapper<Dish>()
                .eq("status", 1)
                .orderByDesc("update_time")
                .last("limit " + DISH_QUERY_LIMIT));
        if (dishes == null || dishes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> categoryMap = buildCategoryNameMap(dishes.stream().map(Dish::getCategoryId).toList());
        Map<Long, List<DishFlavor>> flavorMap = buildFlavorMap(dishes.stream().map(Dish::getId).toList());
        List<DishCandidate> all = new ArrayList<>();
        for (Dish dish : dishes) {
            if (dish == null || dish.getId() == null) {
                continue;
            }
            if (excludes.contains(dish.getName())) {
                continue;
            }
            all.add(DishCandidate.of(
                    dish,
                    categoryMap.get(dish.getCategoryId()),
                    flavorMap.getOrDefault(dish.getId(), List.of()),
                    objectMapper
            ));
        }
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        List<DishCandidate> byPreference = applyPreferenceRule(all, userText);
        List<DishCandidate> byWeather = applyWeatherRule(byPreference, weatherSummary);
        return byWeather.stream().limit(CANDIDATE_LIMIT).toList();
    }

    private List<DishCandidate> buildDishCandidatesByIds(List<Long> dishIds, Set<String> excludes) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Dish> dishes = dishService.listByIds(dishIds);
        if (dishes == null || dishes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Dish> dishMap = dishes.stream()
                .filter(d -> d.getId() != null && d.getStatus() != null && d.getStatus() == 1)
                .collect(Collectors.toMap(Dish::getId, d -> d, (a, b) -> a));
        Map<Long, String> categoryMap = buildCategoryNameMap(dishes.stream().map(Dish::getCategoryId).toList());
        Map<Long, List<DishFlavor>> flavorMap = buildFlavorMap(dishIds);
        List<DishCandidate> result = new ArrayList<>();
        for (Long dishId : dishIds) {
            Dish dish = dishMap.get(dishId);
            if (dish == null || excludes.contains(dish.getName())) {
                continue;
            }
            result.add(DishCandidate.of(
                    dish,
                    categoryMap.get(dish.getCategoryId()),
                    flavorMap.getOrDefault(dish.getId(), List.of()),
                    objectMapper
            ));
        }
        return result;
    }

    private Map<Long, List<DishFlavor>> buildFlavorMap(List<Long> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DishFlavor> flavors = dishFlavorService.list(new QueryWrapper<DishFlavor>().in("dish_id", dishIds));
        if (flavors == null || flavors.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<DishFlavor>> map = new HashMap<>();
        for (DishFlavor flavor : flavors) {
            if (flavor.getDishId() == null) {
                continue;
            }
            map.computeIfAbsent(flavor.getDishId(), k -> new ArrayList<>()).add(flavor);
        }
        return map;
    }

    private Map<Long, String> buildCategoryNameMap(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> validIds = categoryIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Category> categories = categoryService.listByIds(validIds);
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (Category category : categories) {
            if (category.getId() == null || !StringUtils.hasText(category.getName())) {
                continue;
            }
            map.put(category.getId(), category.getName().trim());
        }
        return map;
    }

    private List<DishCandidate> applyWeatherRule(List<DishCandidate> candidates, String weatherSummary) {
        if (!StringUtils.hasText(weatherSummary)) {
            return candidates;
        }
        String lower = weatherSummary.toLowerCase(Locale.ROOT);
        boolean rainLike = lower.contains("雨") || lower.contains("雪") || lower.contains("冰雹");
        Integer temp = parseTemp(weatherSummary);
        boolean cold = temp != null && temp < 10;
        if (!rainLike && !cold) {
            return candidates;
        }
        List<DishCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Boolean.compare(isWarmDish(b), isWarmDish(a)));
        return sorted;
    }

    private List<DishCandidate> applyPreferenceRule(List<DishCandidate> candidates, String userText) {
        List<String> keys = extractPreferenceKeywords(userText);
        if (keys.isEmpty()) {
            return candidates;
        }
        List<DishCandidate> matched = candidates.stream().filter(candidate -> matchCandidate(candidate, keys)).toList();
        if (!matched.isEmpty()) {
            return matched;
        }
        return candidates;
    }

    private List<String> extractPreferenceKeywords(String userText) {
        if (!StringUtils.hasText(userText)) {
            return Collections.emptyList();
        }
        String text = userText.toLowerCase(Locale.ROOT);
        List<String> keys = new ArrayList<>();
        if (text.contains("粤") || text.contains("粤式") || text.contains("广东") || text.contains("广式") || text.contains("烧腊")) {
            keys.add("粤");
            keys.add("粤式");
            keys.add("广东");
            keys.add("广式");
            keys.add("烧腊");
        }
        if (text.contains("川") || text.contains("川味") || text.contains("川菜") || text.contains("麻辣")) {
            keys.add("川");
            keys.add("川味");
            keys.add("川菜");
            keys.add("麻辣");
        }
        if (text.contains("湘") || text.contains("湘味") || text.contains("湘菜")) {
            keys.add("湘");
            keys.add("湘味");
            keys.add("湘菜");
        }
        if (text.contains("清淡")) {
            keys.add("清淡");
            keys.add("汤");
        }
        if (text.contains("不辣")) {
            keys.add("不辣");
        }
        return keys.stream().distinct().toList();
    }

    private boolean matchCandidate(DishCandidate candidate, List<String> keys) {
        StringBuilder text = new StringBuilder();
        text.append(candidate.getName() == null ? "" : candidate.getName()).append(" ");
        text.append(candidate.getCategoryName() == null ? "" : candidate.getCategoryName()).append(" ");
        text.append(candidate.getDescription() == null ? "" : candidate.getDescription()).append(" ");
        List<String> flavors = candidate.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            text.append(String.join(" ", flavors));
        }
        String all = text.toString().toLowerCase(Locale.ROOT);
        for (String key : keys) {
            if (all.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isWarmDish(DishCandidate candidate) {
        String desc = candidate.getDescription() == null ? "" : candidate.getDescription();
        String text = (candidate.getName() + " " + desc).toLowerCase(Locale.ROOT);
        return text.contains("汤") || text.contains("粥") || text.contains("锅")
                || text.contains("煲") || text.contains("热");
    }

    private Integer parseTemp(String weatherSummary) {
        Matcher matcher = TEMP_PATTERN.matcher(weatherSummary);
        if (!matcher.find()) {
            matcher = FALLBACK_TEMP_PATTERN.matcher(weatherSummary);
            if (!matcher.find()) {
                return null;
            }
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveIntentByRuleThenModel(String text) {
        String byRule = resolveIntentByRule(text);
        if (!"unknown".equals(byRule)) {
            return byRule;
        }
        try {
            String modelOutput = aiModelService.chat(buildIntentClassifierPrompt(), text);
            IntentClassifyResult classifyResult = parseIntentClassifyResult(modelOutput);
            if (classifyResult != null
                    && ALLOWED_INTENTS.contains(classifyResult.intent())
                    && classifyResult.confidence() >= INTENT_CONFIDENCE_THRESHOLD) {
                return classifyResult.intent();
            }
        } catch (Exception ex) {
            log.warn("Intent model classify failed: {}", ex.getMessage());
        }
        return INTENT_OUT_OF_SCOPE;
    }

    private String resolveIntentByRule(String text) {
        if (!StringUtils.hasText(text)) {
            return INTENT_OUT_OF_SCOPE;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("地址") || lower.contains("收货地址") || lower.contains("地址簿")) {
            return INTENT_ADDRESS_BOOK;
        }
        if (lower.contains("购物车") || lower.contains("清空购物车")) {
            return INTENT_CART;
        }
        if (lower.contains("一键推荐")) return INTENT_ONE_KEY;
        if (lower.contains("人工") || lower.contains("客服")) return INTENT_MANUAL_SERVICE;
        if (lower.contains("催单")) return INTENT_ORDER_REMIND;
        if (lower.contains("退单") || lower.contains("退款")) return INTENT_ORDER_CANCEL;
        if (lower.contains("订单") || lower.contains("查单")) return INTENT_ORDER_QUERY;
        if (lower.contains("推荐")
                || lower.contains("吃什么")
                || lower.contains("不辣")
                || lower.contains("清淡")
                || lower.contains("热门")
                || lower.contains("最火")
                || lower.contains("热度")) {
            return INTENT_RECOMMEND;
        }
        return "unknown";
    }

    private boolean shouldPreferHeatRecommendations(String userText) {
        if (!StringUtils.hasText(userText)) {
            return false;
        }
        String lower = userText.toLowerCase(Locale.ROOT);
        return lower.contains("热门")
                || lower.contains("最火")
                || lower.contains("爆款")
                || lower.contains("热度")
                || lower.contains("人气")
                || lower.contains("热销");
    }

    private Map<String, Object> buildAnalysisPayload(String strategy, String modelVersion, boolean fallback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("analysisStrategy", strategy);
        payload.put("analysisModelVersion", modelVersion);
        payload.put("analysisFallback", fallback);
        return payload;
    }

    private String resolveCurrentHeatModelVersion() {
        return resolveModelVersion(analysisApplicationService == null
                ? Collections.emptyList()
                : analysisApplicationService.listHeatPredictions(LocalDate.now(APP_ZONE), 1));
    }

    private String resolveModelVersion(List<DishHeatPredictionVO> heatPredictions) {
        if (heatPredictions == null || heatPredictions.isEmpty()) {
            return "unknown";
        }
        return heatPredictions.stream()
                .map(DishHeatPredictionVO::getModelVersion)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("unknown");
    }

    private String buildIntentClassifierPrompt() {
        return """
You are an intent classifier for a food delivery assistant.
Return JSON only with keys: intent, confidence.
intent must be one of: recommend_dish, one_key_recommend, order_query, order_remind, order_cancel, manual_service, address_book_manage, cart_manage, out_of_scope.
confidence must be a decimal between 0 and 1.
If uncertain, choose out_of_scope.
Do not output markdown or explanations.
""";
    }

    private IntentClassifyResult parseIntentClassifyResult(String output) {
        if (!StringUtils.hasText(output)) {
            return null;
        }
        String normalized = output.trim();
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(normalized);
        if (matcher.find()) {
            normalized = matcher.group();
        }
        try {
            JsonNode node = objectMapper.readTree(normalized);
            String intent = node.path("intent").asText("");
            double confidence = node.path("confidence").asDouble(0D);
            if (!StringUtils.hasText(intent)) {
                return null;
            }
            return new IntentClassifyResult(intent.trim().toLowerCase(Locale.ROOT), confidence);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildRecommendPrompt(
            String weatherSummary,
            String candidateDishes,
            String userText,
            String historySummary,
            boolean oneKey,
            int desiredDishCount
    ) {
        return """
You are the Yuweiju takeout AI assistant, only for dish recommendation and ordering guidance.
Output JSON only with keys: reply, dishes, action, payload.
action must be suggest_dish and payload must be null.
dishes must be selected from candidate dishes only, and cannot modify imageUrl, flavors, categoryName.
If user has flavor/cuisine preference and matched candidates exist, do not answer "none available".
If user asks for exactly one dish, dishes must contain exactly one item.
Weather summary: %s
History summary: %s
Candidates: %s
User input: %s
Scenario: %s
Desired dish count: %s
""".formatted(
                weatherSummary == null ? "none" : weatherSummary,
                historySummary == null ? "none" : historySummary,
                candidateDishes,
                userText,
                oneKey ? "one_key_recommend" : "recommend",
                desiredDishCount
        );
    }

    private AiAssistantSendReplyVO parseModelReply(String raw, List<DishCandidate> candidates, int desiredDishCount) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim();
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(normalized);
        if (matcher.find()) {
            normalized = matcher.group();
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            String reply = root.path("reply").asText();
            List<AiAssistantDishCardVO> dishes = parseDishCards(root.path("dishes"), candidates, desiredDishCount);
            AiAssistantSendReplyVO vo = new AiAssistantSendReplyVO();
            vo.setReply(StringUtils.hasText(reply) ? reply : "为您推荐以下菜品。");
            vo.setDishes(dishes);
            vo.setAction(dishes.isEmpty() ? null : ACTION_SUGGEST_DISH);
            vo.setPayload(null);
            return vo;
        } catch (Exception ex) {
            return null;
        }
    }

    private List<AiAssistantDishCardVO> parseDishCards(JsonNode dishesNode, List<DishCandidate> candidates, int desiredDishCount) {
        if (dishesNode == null || !dishesNode.isArray()) {
            return Collections.emptyList();
        }
        Map<Long, DishCandidate> candidateMap = new LinkedHashMap<>();
        for (DishCandidate candidate : candidates) {
            candidateMap.put(candidate.getDishId(), candidate);
        }
        List<AiAssistantDishCardVO> list = new ArrayList<>();
        for (JsonNode node : dishesNode) {
            Long dishId = node.path("dishId").isNumber() ? node.path("dishId").asLong() : null;
            if (dishId == null) {
                continue;
            }
            DishCandidate candidate = candidateMap.get(dishId);
            if (candidate == null) {
                continue;
            }
            list.add(candidate.toCardVO());
        }
        if (list.isEmpty()) {
            return candidates.stream().limit(Math.max(1, desiredDishCount)).map(DishCandidate::toCardVO).toList();
        }
        return list.stream().distinct().limit(Math.max(1, desiredDishCount)).toList();
    }

    private AiAssistantSendReplyVO finalizeReply(String userText, AiAssistantSendReplyVO vo) {
        if (vo == null) {
            return buildBusyReply();
        }
        int desiredDishCount = resolveDesiredDishCount(userText);
        if (vo.getDishes() != null && !vo.getDishes().isEmpty()) {
            vo.setDishes(vo.getDishes().stream().limit(Math.max(1, desiredDishCount)).toList());
            if (desiredDishCount == 1 && vo.getDishes().size() == 1) {
                String reply = vo.getReply();
                if (!StringUtils.hasText(reply)
                        || NONE_STYLE_REPLY_PATTERN.matcher(reply).find()
                        || MULTI_DISH_REPLY_PATTERN.matcher(reply).find()) {
                    vo.setReply("为您推荐一道菜：" + vo.getDishes().get(0).getName() + "。");
                }
            }
        }
        return vo;
    }

    private int resolveDesiredDishCount(String userText) {
        if (!StringUtils.hasText(userText)) {
            return 3;
        }
        return SINGLE_DISH_REQUEST_PATTERN.matcher(userText).find() ? 1 : 3;
    }

    private AiAssistantSendReplyVO buildGuideReply(String reply) {
        AiAssistantSendReplyVO vo = new AiAssistantSendReplyVO();
        vo.setReply(reply);
        vo.setDishes(Collections.emptyList());
        vo.setAction(null);
        vo.setPayload(null);
        return vo;
    }

    private AiAssistantSendReplyVO buildBusyReply() {
        AiAssistantSendReplyVO vo = new AiAssistantSendReplyVO();
        vo.setReply("当前较繁忙，请稍后重试。");
        vo.setDishes(Collections.emptyList());
        vo.setAction(null);
        vo.setPayload(null);
        return vo;
    }

    private String parseRejectedDishName(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = REJECT_DISH_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String joinAddress(AddressBook addressBook) {
        StringBuilder builder = new StringBuilder();
        appendIfHasText(builder, addressBook.getProvinceName());
        appendIfHasText(builder, addressBook.getCityName());
        appendIfHasText(builder, addressBook.getDistrictName());
        appendIfHasText(builder, addressBook.getDetail());
        return builder.toString();
    }

    private void appendIfHasText(StringBuilder builder, String text) {
        if (StringUtils.hasText(text)) {
            builder.append(text.trim());
        }
    }

    private boolean isSessionOpen(Integer status) {
        return status != null && status == AiAssistantSession.STATUS_OPEN;
    }

    private boolean isSessionInToday(AiAssistantSession session) {
        if (session == null || session.getCreateTime() == null) {
            return false;
        }
        Date dayStart = todayStart();
        Date nextDayStart = tomorrowStart();
        Date createTime = session.getCreateTime();
        return !createTime.before(dayStart) && createTime.before(nextDayStart);
    }

    private Date todayStart() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        return Date.from(todayStart.atZone(APP_ZONE).toInstant());
    }

    private Date tomorrowStart() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime tomorrowStart = tomorrow.atStartOfDay();
        return Date.from(tomorrowStart.atZone(APP_ZONE).toInstant());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private AiAssistantSessionVO toSessionVO(AiAssistantSession session) {
        AiAssistantSessionVO vo = new AiAssistantSessionVO();
        vo.setId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setStatus(session.getStatus());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        vo.setCloseTime(session.getCloseTime());
        return vo;
    }

    private AiAssistantMessageVO toMessageVO(AiAssistantMessage message) {
        AiAssistantMessageVO vo = new AiAssistantMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setSenderType(message.getSenderType());
        vo.setSenderId(message.getSenderId());
        vo.setContent(message.getContent());
        vo.setIntent(message.getIntent());
        vo.setMetadata(message.getMetadata());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private Map<Long, User> buildUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userService.listByIds(userIds);
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new HashMap<>();
        for (User user : users) {
            if (user.getId() != null) {
                map.put(user.getId(), user);
            }
        }
        return map;
    }

    private AiAssistantMessage findLastMessage(Long sessionId) {
        return messageService.getOne(new QueryWrapper<AiAssistantMessage>()
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("limit 1"));
    }

    private String limitPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "暂无消息";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= 60) {
            return trimmed;
        }
        return trimmed.substring(0, 60) + "...";
    }

    private String buildAiMetadata(String intent, AiAssistantSendReplyVO replyVO) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("intent", intent);
        metadata.put("action", replyVO.getAction());
        metadata.put("dishCount", replyVO.getDishes() == null ? 0 : replyVO.getDishes().size());
        metadata.put("dishIds", replyVO.getDishes() == null ? List.of() :
                replyVO.getDishes().stream().map(AiAssistantDishCardVO::getDishId).filter(Objects::nonNull).toList());
        metadata.put("dishNames", replyVO.getDishes() == null ? List.of() :
                replyVO.getDishes().stream().map(AiAssistantDishCardVO::getName).toList());
        if (replyVO.getPayload() instanceof Map<?, ?> payload) {
            metadata.put("payload", payload);
        }
        return safeWriteJson(metadata);
    }

    private String composeAiMessageText(AiAssistantSendReplyVO replyVO) {
        String reply = replyVO == null || !StringUtils.hasText(replyVO.getReply()) ? "好的" : replyVO.getReply().trim();
        if (replyVO == null || replyVO.getDishes() == null || replyVO.getDishes().isEmpty()) {
            return reply;
        }
        StringBuilder builder = new StringBuilder(reply).append("\n");
        for (AiAssistantDishCardVO dish : replyVO.getDishes()) {
            if (dish != null && StringUtils.hasText(dish.getName())) {
                builder.append("· ").append(dish.getName().trim()).append("\n");
            }
        }
        return builder.toString().trim();
    }

    private String safeWriteJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private record IntentClassifyResult(String intent, double confidence) {
    }

    private record AnalyzedRecommendSelection(
            List<DishCandidate> candidates,
            String historySummary,
            String modelVersion,
            String strategy,
            boolean fallback
    ) {

        private static AnalyzedRecommendSelection empty() {
            return new AnalyzedRecommendSelection(Collections.emptyList(), null, "unknown", "unknown", true);
        }
    }

    private static final class DishCandidate {
        private final Long dishId;
        private final String name;
        private final BigDecimal price;
        private final String imageUrl;
        private final List<String> flavors;
        private final String description;
        private final String categoryName;

        private DishCandidate(Long dishId, String name, BigDecimal price, String imageUrl, List<String> flavors, String description, String categoryName) {
            this.dishId = dishId;
            this.name = name;
            this.price = price;
            this.imageUrl = imageUrl;
            this.flavors = flavors;
            this.description = description;
            this.categoryName = categoryName;
        }

        static DishCandidate of(Dish dish, String categoryName, List<DishFlavor> flavorList, ObjectMapper objectMapper) {
            return new DishCandidate(
                    dish.getId(),
                    dish.getName(),
                    dish.getPrice(),
                    dish.getImage(),
                    parseFlavors(flavorList, objectMapper),
                    dish.getDescription(),
                    categoryName
            );
        }

        static List<String> parseFlavors(List<DishFlavor> flavorList, ObjectMapper objectMapper) {
            if (flavorList == null || flavorList.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (DishFlavor flavor : flavorList) {
                if (flavor == null || !StringUtils.hasText(flavor.getValue())) {
                    continue;
                }
                String value = flavor.getValue().trim();
                if (value.startsWith("[") && value.endsWith("]")) {
                    try {
                        List<String> jsonValues = objectMapper.readValue(value, new TypeReference<List<String>>() {});
                        for (String item : jsonValues) {
                            if (StringUtils.hasText(item)) {
                                result.add(item.trim());
                            }
                        }
                        continue;
                    } catch (Exception ignored) {
                    }
                }
                String[] pieces = value.split(",");
                for (String piece : pieces) {
                    if (StringUtils.hasText(piece)) {
                        result.add(piece.trim().replace("\"", ""));
                    }
                }
            }
            return result.stream().distinct().toList();
        }

        Long getDishId() {
            return dishId;
        }

        String getName() {
            return name;
        }

        String getDescription() {
            return description;
        }

        String getCategoryName() {
            return categoryName;
        }

        List<String> getFlavors() {
            return flavors;
        }

        AiAssistantDishCardVO toCardVO() {
            AiAssistantDishCardVO vo = new AiAssistantDishCardVO();
            vo.setDishId(dishId);
            vo.setName(name);
            vo.setPrice(price);
            vo.setImageUrl(imageUrl);
            vo.setFlavors(flavors);
            return vo;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("dishId", dishId);
            map.put("name", name);
            map.put("price", price);
            map.put("imageUrl", imageUrl);
            map.put("flavors", flavors);
            map.put("description", description);
            map.put("categoryName", categoryName);
            return map;
        }
    }
}

