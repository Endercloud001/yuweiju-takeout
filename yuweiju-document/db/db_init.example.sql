CREATE DATABASE  IF NOT EXISTS `yuweiju`;
USE `yuweiju`;

-- =========================
-- 人工客服（customer service）相关表
-- =========================

DROP TABLE IF EXISTS `customer_service_message`;
DROP TABLE IF EXISTS `customer_service_session`;

CREATE TABLE `customer_service_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id（关联 user.id）',
  `status` int NOT NULL DEFAULT '1' COMMENT '会话状态 1-进行中 2-已关闭',
  `assigned_admin_id` bigint DEFAULT NULL COMMENT '当前处理管理员id（预留）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间（最后消息/状态变更）',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `close_reason` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关闭原因',
  PRIMARY KEY (`id`),
  KEY `idx_css_user_status` (`user_id`, `status`),
  KEY `idx_css_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='人工客服会话表';

CREATE TABLE `customer_service_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '会话id（customer_service_session.id）',
  `sender_type` int NOT NULL COMMENT '发送方类型 1-用户 2-管理员 3-系统',
  `sender_id` bigint DEFAULT NULL COMMENT '发送方id（SYSTEM 为 NULL）',
  `content` varchar(2000) COLLATE utf8mb4_bin NOT NULL COMMENT '消息内容',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_csm_session_id` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='人工客服消息表';

-- =========================
-- AI 对话助手（ai assistant）相关表
-- =========================

DROP TABLE IF EXISTS `ai_assistant_message`;
DROP TABLE IF EXISTS `ai_assistant_session`;

CREATE TABLE `ai_assistant_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id（关联 user.id）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '会话状态 1-进行中 2-已关闭',
  `last_intent` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近一次意图',
  `pending_action` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '待确认动作（前端卡片交互）',
  `pending_payload` json DEFAULT NULL COMMENT '待确认动作所需参数（JSON）',
  `model` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模型标识（便于排查与回放）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间（最后消息/状态变更）',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `close_reason` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关闭原因',
  PRIMARY KEY (`id`),
  KEY `idx_aas_user_status` (`user_id`, `status`),
  KEY `idx_aas_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AI助手会话表';

CREATE TABLE `ai_assistant_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '会话id（ai_assistant_session.id）',
  `sender_type` int NOT NULL COMMENT '发送方类型 1-用户 2-AI 3-系统',
  `sender_id` bigint DEFAULT NULL COMMENT '发送方id（AI/系统为 NULL）',
  `content` varchar(2000) COLLATE utf8mb4_bin NOT NULL COMMENT '消息内容',
  `intent` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '意图（可为空）',
  `metadata` json DEFAULT NULL COMMENT '元信息（候选菜品、天气摘要、推荐依据等）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_aam_session_id` (`session_id`, `id`),
  KEY `idx_aam_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AI助手消息表';

-- =========================
-- 热度分析与数据挖掘相关表
-- =========================
create table if not exists user_behavior_feature_snapshot (
  id                      bigint primary key auto_increment comment '主键',
  snapshot_date           date not null comment '快照日期',
  user_id                 bigint not null comment '用户ID',
  rfm_recency_days        int not null comment '最近一次下单距今天数',
  rfm_frequency_30d       int not null comment '近30天下单次数',
  rfm_monetary_30d        decimal(10,2) not null comment '近30天消费金额',
  order_lunch_ratio       decimal(6,4) not null default 0 comment '午餐时段下单占比',
  order_dinner_ratio      decimal(6,4) not null default 0 comment '晚餐时段下单占比',
  order_night_ratio       decimal(6,4) not null default 0 comment '夜宵时段下单占比',
  price_low_ratio         decimal(6,4) not null default 0 comment '低价带偏好占比',
  price_mid_ratio         decimal(6,4) not null default 0 comment '中价带偏好占比',
  price_high_ratio        decimal(6,4) not null default 0 comment '高价带偏好占比',
  category_pref_top1      bigint null comment '偏好Top1品类ID',
  category_pref_top2      bigint null comment '偏好Top2品类ID',
  flavor_vector_json      json null comment '口味向量（Multi-Hot 或 TF-IDF）',
  feature_version         varchar(32) not null default 'v1' comment '特征版本号',
  create_time             datetime not null default current_timestamp comment '创建时间',
  update_time             datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_user_snapshot (snapshot_date, user_id, feature_version),
  key idx_user_snapshot_user_date (user_id, snapshot_date)
) engine=InnoDB default charset=utf8mb4 comment='用户行为特征快照表';

create table if not exists dish_timeseries_feature_snapshot (
  id                      bigint primary key auto_increment comment '主键',
  snapshot_date           date not null comment '快照日期',
  dish_id                 bigint not null comment '菜品ID',
  category_id             bigint not null comment '品类ID',
  sales_qty_1d            int not null default 0 comment '1日销量',
  sales_qty_7d            int not null default 0 comment '7日销量',
  sales_qty_30d           int not null default 0 comment '30日销量',
  refund_qty_30d          int not null default 0 comment '30日退款数量',
  decay_sales_30d         decimal(12,4) not null default 0 comment '30日衰减销量',
  is_holiday              tinyint not null default 0 comment '是否节假日',
  is_promo                tinyint not null default 0 comment '是否促销日',
  weather_code            varchar(32) null comment '天气分类编码',
  price                   decimal(10,2) not null default 0 comment '菜品价格',
  feature_version         varchar(32) not null default 'v1' comment '特征版本号',
  create_time             datetime not null default current_timestamp comment '创建时间',
  update_time             datetime not null default current_timestamp on update current_timestamp comment '更新时间',
  unique key uk_dish_snapshot (snapshot_date, dish_id, feature_version),
  key idx_dish_snapshot_date (snapshot_date),
  key idx_dish_snapshot_category (category_id, snapshot_date)
) engine=InnoDB default charset=utf8mb4 comment='菜品时序特征快照表';

create table if not exists dish_heat_prediction_result (
  id                      bigint primary key auto_increment comment '主键',
  window_start            date not null comment '预测窗口开始日期',
  window_end              date not null comment '预测窗口结束日期',
  dish_id                 bigint not null comment '菜品ID',
  predicted_sales_qty     decimal(12,4) not null default 0 comment '预测销量',
  heat_score              decimal(12,6) not null default 0 comment '热度得分',
  model_version           varchar(64) not null comment '模型版本号',
  feature_version         varchar(32) not null comment '特征版本号',
  explain_json            json null comment '解释信息',
  status                  tinyint not null default 1 comment '1有效 0无效',
  create_time             datetime not null default current_timestamp comment '创建时间',
  unique key uk_heat_result (window_start, window_end, dish_id, model_version),
  key idx_heat_rank (window_start, window_end, heat_score),
  key idx_heat_dish (dish_id, window_start)
) engine=InnoDB default charset=utf8mb4 comment='菜品热度预测结果表';

create table if not exists user_cluster_result (
  id                      bigint primary key auto_increment comment '主键',
  snapshot_date           date not null comment '聚类快照日期',
  user_id                 bigint not null comment '用户ID',
  cluster_id              int not null comment '簇ID',
  cluster_score           decimal(10,6) null comment '簇归属置信度',
  model_version           varchar(64) not null comment '模型版本号',
  feature_version         varchar(32) not null comment '特征版本号',
  topn_dish_json          json null comment '簇内推荐候选菜品',
  create_time             datetime not null default current_timestamp comment '创建时间',
  unique key uk_user_cluster (snapshot_date, user_id, model_version),
  key idx_cluster_snapshot (snapshot_date, cluster_id),
  key idx_cluster_user (user_id, snapshot_date)
) engine=InnoDB default charset=utf8mb4 comment='用户聚类结果表';

-- =========================
-- 异常订单识别逻辑相关表
-- =========================

create table if not exists order_risk_feature_snapshot (
  order_id bigint not null comment '订单ID',
  feature_version varchar(32) not null comment '特征版本',
  snapshot_json json not null comment '原始(未标准化)特征向量',
  created_at datetime not null default current_timestamp comment '创建时间',
  primary key (order_id, feature_version),
  key idx_orfs_created_at (created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin comment='订单风险特征快照';

create table if not exists order_risk_standardizer_meta (
  feature_version varchar(32) not null comment '特征版本',
  feature_name varchar(64) not null comment '特征名',
  mean_value double not null comment '均值',
  std_value double not null comment '标准差',
  created_at datetime not null default current_timestamp comment '创建时间',
  primary key (feature_version, feature_name),
  key idx_orsm_created_at (created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin comment='标准化参数元数据';

create table if not exists order_risk_result (
  order_id bigint not null comment '订单ID',
  model_version varchar(64) not null comment '模型版本',
  feature_version varchar(32) not null comment '特征版本',
  risk_score int not null comment '风险分(0-100)',
  risk_level varchar(10) not null comment '风险等级(LOW/MEDIUM/HIGH)',
  p_lr decimal(8,6) null comment 'LR概率(规则降级可为空)',
  p_rf decimal(8,6) null comment 'RF概率(规则降级可为空)',
  reason_json json not null comment '风险解释JSON',
  evaluated_at datetime not null default current_timestamp comment '评分时间',
  decision_status varchar(16) not null default 'PENDING' comment '复核状态',
  primary key (order_id, model_version),
  key idx_orr_level_time (risk_level, evaluated_at),
  key idx_orr_feature_version (feature_version)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin comment='订单风险评分结果';

create table if not exists order_risk_feedback (
  id bigint not null auto_increment comment '主键',
  order_id bigint not null comment '订单ID',
  decision varchar(16) not null comment '复核决策(approve/reject/review)',
  operator_id bigint not null comment '操作人ID',
  reason varchar(255) null comment '复核原因',
  created_at datetime not null default current_timestamp comment '创建时间',
  primary key (id),
  key idx_orf_order_created (order_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin comment='订单风险复核反馈';

create table if not exists order_risk_model_meta (
  model_version varchar(64) not null comment '模型版本',
  artifact_path varchar(255) not null comment '模型文件路径',
  metrics_json json not null comment '离线评估指标JSON',
  train_window_start date not null comment '训练窗口开始日期',
  train_window_end date not null comment '训练窗口结束日期',
  created_at datetime not null default current_timestamp comment '创建时间',
  status varchar(16) not null comment '模型状态(ACTIVE/REJECTED/ARCHIVED)',
  primary key (model_version),
  key idx_ormm_status_created (status, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_bin comment='风险模型元数据';


-- =========================
-- 其他表
-- =========================

DROP TABLE IF EXISTS `address_book`;
CREATE TABLE `address_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `consignee` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '收货人',
  `sex` varchar(2) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '性别',
  `phone` varchar(11) COLLATE utf8mb4_bin NOT NULL COMMENT '手机号',
  `province_code` varchar(12) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '区级名称',
  `detail` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '默认 0 否 1是',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='地址簿';

DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int DEFAULT NULL COMMENT '类型   1 菜品分类 2 套餐分类',
  `name` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `status` int DEFAULT 1 NOT NULL COMMENT '分类状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_category_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='菜品及套餐分类';

DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '菜品名称',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `price` decimal(10,2) DEFAULT 1 NOT NULL COMMENT '菜品价格',
  `image` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图片',
  `description` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '描述信息',
  `status` int DEFAULT '1' COMMENT '0 停售 1 起售',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_dish_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='菜品';

DROP TABLE IF EXISTS `dish_flavor`;
CREATE TABLE `dish_flavor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint NOT NULL COMMENT '菜品',
  `name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '口味名称',
  `value` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '口味数据list',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='菜品口味关系表';


DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '姓名',
  `username` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '用户名',
  `password` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '密码',
  `phone` varchar(11) COLLATE utf8mb4_bin NOT NULL COMMENT '手机号',
  `sex` varchar(2) COLLATE utf8mb4_bin NOT NULL COMMENT '性别',
  `id_number` varchar(18) COLLATE utf8mb4_bin NOT NULL COMMENT '身份证号',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='员工信息';

DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '名字',
  `image` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图片',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单明细表';

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '订单号',
  `status` int NOT NULL DEFAULT '1' COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
  `user_id` bigint NOT NULL COMMENT '下单用户',
  `address_book_id` bigint NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '结账时间',
  `pay_method` int NOT NULL DEFAULT '1' COMMENT '支付方式 1微信,2支付宝',
  `pay_status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态 0未支付 1已支付 2退款',
  `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户名称',
  `consignee` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '配送状态  1立即送出  0选择具体时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '送达时间',
  `pack_amount` int DEFAULT NULL COMMENT '打包费',
  `tableware_number` int DEFAULT NULL COMMENT '餐具数量',
  `tableware_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '餐具数量状态  1按餐量提供  0选择具体数量',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单表';

DROP TABLE IF EXISTS `setmeal`;
CREATE TABLE `setmeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `name` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '套餐名称',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `status` int DEFAULT '1' NOT NULL COMMENT '售卖状态 0:停售 1:起售',
  `description` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '描述信息',
  `image` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图片',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_setmeal_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='套餐';

DROP TABLE IF EXISTS `setmeal_dish`;
CREATE TABLE `setmeal_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint NOT NULL COMMENT '套餐id',
  `dish_id` bigint NOT NULL COMMENT '菜品id',
  `name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '菜品名称 （冗余字段）',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品单价（冗余字段）',
  `copies` int DEFAULT NULL COMMENT '菜品份数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='套餐菜品关系';

DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '商品名称',
  `image` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图片',
  `user_id` bigint NOT NULL COMMENT '主键',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='购物车';

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` varchar(45) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '微信用户唯一标识',
  `name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '姓名',
  `phone` varchar(11) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '性别',
  `id_number` varchar(18) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '身份证号',
  `avatar` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '头像',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户信息';

-- 插入管理员数据
INSERT INTO `employee` VALUES (1,'管理员','admin','admin','13800000000','1','110101199001010000',1,'2026-02-17 15:51:20','2026-02-17 15:51:21',1,1);

-- 清空分类表原有数据
TRUNCATE TABLE category;
-- 插入菜品分类（type=1）
INSERT INTO category (type, name, sort, status, create_time, update_time, create_user, update_user)
VALUES
(1, '川味凉菜', 1, 1, NOW(), NOW(), 1, 1),
(1, '经典热炒', 2, 1, NOW(), NOW(), 1, 1),
(1, '川湘干锅', 3, 1, NOW(), NOW(), 1, 1),
(1, '粤式烧腊', 4, 1, NOW(), NOW(), 1, 1),
(1, '鲜爽海鲜', 5, 1, NOW(), NOW(), 1, 1),
(1, '家常蒸菜', 6, 1, NOW(), NOW(), 1, 1),
(1, '营养汤羹', 7, 1, NOW(), NOW(), 1, 1),
(1, '特色主食', 8, 1, NOW(), NOW(), 1, 1),
(1, '风味小吃', 9, 1, NOW(), NOW(), 1, 1),
(1, '菌菇时蔬', 10, 1, NOW(), NOW(), 1, 1),
(1, '红烧焖煮', 11, 1, NOW(), NOW(), 1, 1),
(1, '火锅涮菜', 12, 1, NOW(), NOW(), 1, 1),
-- 插入套餐分类（type=2）
(2, '单人经济餐', 1, 1, NOW(), NOW(), 1, 1),
(2, '双人乐享餐', 2, 1, NOW(), NOW(), 1, 1),
(2, '家庭欢聚餐', 3, 1, NOW(), NOW(), 1, 1),
(2, '朋友聚大餐', 4, 1, NOW(), NOW(), 1, 1);

-- 清空菜品表原有数据（可选，测试环境使用）
TRUNCATE TABLE dish;
-- 1. 川味凉菜（category_id=1）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('夫妻肺片', 1, 38.00, '/images/cfpf.jpg', '经典川味，牛腱牛肚搭配秘制红油，麻辣鲜香', 1, NOW(), NOW(), 1, 1),
('凉拌猪耳', 1, 28.00, '/images/lbzr.jpg', '卤香猪耳切丝，拌红油蒜泥，脆嫩爽口', 1, NOW(), NOW(), 1, 1),
('拍黄瓜', 1, 12.00, '/images/phy.jpg', '新鲜黄瓜拍碎，拌蒜末香醋，解腻开胃', 1, NOW(), NOW(), 1, 1),
('凉拌三丝', 1, 18.00, '/images/lbsj.jpg', '土豆丝+粉丝+豆皮丝，酸辣开胃，清爽解腻', 1, NOW(), NOW(), 1, 1);

-- 2. 经典热炒（category_id=2）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('鱼香肉丝', 2, 26.00, '/images/yxrs.jpg', '经典川味，肉丝配木耳笋丝，鱼香味浓', 1, NOW(), NOW(), 1, 1),
('宫保鸡丁', 2, 28.00, '/images/gbdj.jpg', '鸡丁配花生米，麻辣酸甜，外酥里嫩', 1, NOW(), NOW(), 1, 1),
('番茄炒蛋', 2, 16.00, '/images/fqcd.jpg', '家常经典，番茄酸甜，鸡蛋嫩滑，拌饭神器', 1, NOW(), NOW(), 1, 1),
('青椒炒肉', 2, 22.00, '/images/qjcr.jpg', '土猪肉配线椒，咸香微辣，下饭必备', 1, NOW(), NOW(), 1, 1);

-- 3. 川湘干锅（category_id=3）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('干锅牛蛙', 3, 88.00, '/images/ggnw.jpg', '牛蛙配藕片土豆，麻辣鲜香，越煮越有味', 1, NOW(), NOW(), 1, 1),
('干锅肥肠', 3, 68.00, '/images/ggfc.jpg', '卤肥肠配青椒洋葱，焦香麻辣，口感筋道', 1, NOW(), NOW(), 1, 1),
('干锅花菜', 3, 26.00, '/images/gghc.jpg', '有机花菜配腊肉，干香微辣，家常风味', 1, NOW(), NOW(), 1, 1);

-- 4. 粤式烧腊（category_id=4）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('广式烧鹅', 4, 98.00, '/images/gsse.jpg', '深井烧鹅，皮脆肉嫩，搭配酸梅酱', 1, NOW(), NOW(), 1, 1),
('蜜汁叉烧', 4, 48.00, '/images/mzcs.jpg', '精选梅花肉，蜜汁腌制，香甜不腻', 1, NOW(), NOW(), 1, 1),
('白切鸡', 4, 68.00, '/images/bqj.jpg', '清远鸡白切，皮滑肉嫩，搭配姜蒜蘸料', 1, NOW(), NOW(), 1, 1);

-- 5. 鲜爽海鲜（category_id=5）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('清蒸鲈鱼', 5, 78.00, '/images/qzly.jpg', '鲜活鲈鱼，清蒸锁鲜，鲜嫩无刺', 1, NOW(), NOW(), 1, 1),
('香辣虾', 5, 88.00, '/images/xlx.jpg', '基围虾开背，麻辣入味，外酥里嫩', 1, NOW(), NOW(), 1, 1),
('蒜蓉粉丝扇贝', 5, 6.80, '/images/snfs sb.jpg', '鲜活扇贝，铺蒜蓉粉丝，蒸制鲜香（单个价）', 1, NOW(), NOW(), 1, 1),
('白灼虾', 5, 98.00, '/images/bzx.jpg', '鲜活基围虾，白灼锁鲜，蘸芥末酱油', 1, NOW(), NOW(), 1, 1),
('罗勒薄荷炒海瓜子', 5, 48.00, 'https://java-learning-endercloud.oss-cn-shanghai.aliyuncs.com/2026/03/罗勒薄荷炒海瓜子_d7560448-596d-4dcf-a95b-77859384051d.png', '选用鲜活海瓜子快火爆炒，加入罗勒与薄荷叶增香提味，去腥解腻。海瓜子肉质细嫩多汁，汤汁鲜浓，入口带着淡淡清凉回甘，粤式小炒风味十足，开胃下饭，是极具特色的海鲜下酒菜。', 1, NOW(), NOW(), 1, 1);


-- 6. 家常蒸菜（category_id=6）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('梅菜扣肉', 6, 48.00, '/images/mckr.jpg', '五花肉配梅干菜，蒸制软糯，咸香入味', 1, NOW(), NOW(), 1, 1),
('剁椒鱼头', 6, 88.00, '/images/djyt.jpg', '胖头鱼鱼头，铺剁椒蒸制，鲜辣开胃', 1, NOW(), NOW(), 1, 1),
('清蒸排骨', 6, 38.00, '/images/qzpg.jpg', '精排腌制，清蒸软糯，老少皆宜', 1, NOW(), NOW(), 1, 1);

-- 7. 营养汤羹（category_id=7）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('冬瓜海带排骨汤', 7, 32.00, '/images/dghdpgt.jpg', '慢炖2小时，汤鲜浓郁，清热祛湿', 1, NOW(), NOW(), 1, 1),
('番茄鸡蛋汤', 7, 12.00, '/images/fqjd汤.jpg', '家常汤品，酸甜爽口，解腻开胃', 1, NOW(), NOW(), 1, 1),
('菌菇土鸡汤', 7, 58.00, '/images/jgtjt.jpg', '老母鸡配多种菌菇，慢炖3小时，营养滋补', 1, NOW(), NOW(), 1, 1),
('紫菜蛋花汤', 7, 10.00, '/images/zcdht.jpg', '鲜香清淡，简单暖胃，老少皆宜', 1, NOW(), NOW(), 1, 1),
('平菇豆腐汤', 7, 10.00, 'https://java-learning-endercloud.oss-cn-shanghai.aliyuncs.com/2026/03/平菇豆腐汤_46717e92-b557-43e6-a4d9-04a6a4ed9d9f.jpg', '平菇的鲜美与嫩豆腐的细腻完美结合，呈现出清淡又健康的味道。', 1, NOW(), NOW(), 1, 1);

-- 8. 特色主食（category_id=8）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('扬州炒饭', 8, 18.00, '/images/yzcf.jpg', '米饭配火腿鸡蛋玉米，粒粒分明', 1, NOW(), NOW(), 1, 1),
('重庆小面', 8, 16.00, '/images/cqxm.jpg', '麻辣汤底，配青菜花生，筋道爽滑', 1, NOW(), NOW(), 1, 1),
('蛋炒饭', 8, 12.00, '/images/dcf.jpg', '家常蛋炒饭，粒粒金黄，咸香适口', 1, NOW(), NOW(), 1, 1),
('手工水饺', 8, 20.00, '/images/sgsj.jpg', '猪肉白菜馅，现包现煮，皮薄馅大', 1, NOW(), NOW(), 1, 1);

-- 9. 风味小吃（category_id=9）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('红糖糍粑', 9, 18.00, '/images/htcb.jpg', '糯米糍粑，煎至金黄，蘸红糖豆粉', 1, NOW(), NOW(), 1, 1),
('狼牙土豆', 9, 15.00, '/images/lytd.jpg', '土豆切狼牙状，拌红油调料，麻辣鲜香', 1, NOW(), NOW(), 1, 1),
('炸春卷', 9, 20.00, '/images/zcj.jpg', '素菜馅春卷，炸至金黄，外酥里嫩', 1, NOW(), NOW(), 1, 1);

-- 10. 菌菇时蔬（category_id=10）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('清炒油麦菜', 10, 16.00, '/images/qcyml.jpg', '清炒锁鲜，清淡爽口，解腻解辣', 1, NOW(), NOW(), 1, 1),
('香菇青菜', 10, 18.00, '/images/xgqc.jpg', '香菇配上海青，鲜香味浓，清淡低脂', 1, NOW(), NOW(), 1, 1),
('清炒西兰花', 10, 20.00, '/images/qcxlh.jpg', '西兰花配胡萝卜，清炒清淡，营养健康', 1, NOW(), NOW(), 1, 1),
('蒜蓉娃娃菜', 10, 18.00, '/images/snwwc.jpg', '娃娃菜铺蒜蓉，蒸制鲜香，清淡适口', 1, NOW(), NOW(), 1, 1);

-- 11. 红烧焖煮（category_id=11）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('红烧肉', 11, 48.00, '/images/hsrou.jpg', '精选五花肉，冰糖红烧，肥而不腻', 1, NOW(), NOW(), 1, 1),
('红烧鱼块', 11, 38.00, '/images/hsyk.jpg', '草鱼块红烧，酱香浓郁，肉质鲜嫩', 1, NOW(), NOW(), 1, 1),
('黄焖鸡米饭', 11, 26.00, '/images/hmjmf.jpg', '鸡腿肉配土豆青椒，焖煮入味，拌米饭超香', 1, NOW(), NOW(), 1, 1);

-- 12. 火锅涮菜（category_id=12）
INSERT INTO dish (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES
('精品肥牛卷', 12, 32.00, '/images/jpfnj.jpg', '新鲜肥牛，薄切卷状，涮煮鲜嫩', 1, NOW(), NOW(), 1, 1),
('手打牛肉丸', 12, 28.00, '/images/sdnrw.jpg', '纯手工牛肉丸，Q弹劲道，涮煮鲜香', 1, NOW(), NOW(), 1, 1),
('金针菇', 12, 10.00, '/images/jzg.jpg', '新鲜金针菇，涮煮入味，口感脆嫩', 1, NOW(), NOW(), 1, 1),
('冻豆腐', 12, 8.00, '/images/ddf.jpg', '冻豆腐吸汁，涮火锅必备，口感筋道', 1, NOW(), NOW(), 1, 1),
('宽粉', 12, 10.00, '/images/kf.jpg', '红薯宽粉，涮煮软糯，吸满汤底香味', 1, NOW(), NOW(), 1, 1);

-- 清空口味表原有数据（可选，测试环境使用）
TRUNCATE TABLE dish_flavor;

-- 一、川味凉菜（ID1-4）- 辣度+葱花香菜为主
-- 1.夫妻肺片：辣度+葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (1, '辣度', '微辣,中辣,特辣,免辣'), (1, '葱花香菜', '加葱,加香菜,都加,都不加');
-- 2.凉拌猪耳：辣度+葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (2, '辣度', '微辣,中辣,特辣,免辣'), (2, '葱花香菜', '加葱,加香菜,都加,都不加');
-- 3.拍黄瓜：辣度+酸甜度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (3, '辣度', '微辣,中辣,免辣'), (3, '酸甜度', '微酸微甜,酸多甜少,甜多酸少,正常');
-- 4.凉拌三丝：辣度+葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (4, '辣度', '微辣,中辣,特辣,免辣'), (4, '葱花香菜', '加葱,加香菜,都加,都不加');

-- 二、经典热炒（ID5-8）- 辣度为主，宫保鸡丁额外加甜度
-- 5.鱼香肉丝：辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (5, '辣度', '微辣,中辣,特辣,免辣');
-- 6.宫保鸡丁：辣度+甜度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (6, '辣度', '微辣,中辣,免辣'), (6, '甜度', '微甜,正常甜,少甜');
-- 7.番茄炒蛋：甜度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (7, '甜度', '微甜,正常甜,少甜');
-- 8.青椒炒肉：辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (8, '辣度', '微辣,中辣,特辣,免辣');

-- 三、川湘干锅（ID9-11）- 辣度（干锅核心口味）
-- 9.干锅牛蛙：辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (9, '辣度', '微辣,中辣,特辣,免辣');
-- 10.干锅肥肠：辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (10, '辣度', '微辣,中辣,特辣,免辣');
-- 11.干锅花菜：辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (11, '辣度', '微辣,中辣,特辣,免辣');

-- 四、粤式烧腊（ID12-14）- 白切鸡加蘸料选择，蜜汁叉烧加甜度
-- 12.广式烧鹅：无口味（默认配酸梅酱）
-- 13.蜜汁叉烧：甜度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (13, '甜度', '微甜,正常甜,少甜');
-- 14.白切鸡：蘸料选择
INSERT INTO dish_flavor (dish_id, name, value) VALUES (14, '蘸料', '姜蒜汁,酱油碟,芥末酱油,麻辣蘸料');

-- 五、鲜爽海鲜（ID15-18）- 香辣虾加辣度，白灼/清蒸加葱花香菜
-- 15.清蒸鲈鱼：葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (15, '葱花香菜', '加葱,加香菜,都加,都不加');
-- 16.香辣虾：辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (16, '辣度', '微辣,中辣,特辣,免辣');
-- 17.蒜蓉粉丝扇贝：葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (17, '葱花香菜', '加葱,加香菜,都加,都不加');
-- 18.白灼虾：蘸料+葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (18, '蘸料', '芥末酱油,蒜蓉香醋,生抽蚝油'), (18, '葱花香菜', '加葱,加香菜,都加,都不加');
-- 58.罗勒薄荷炒海瓜子: 薄荷的量
INSERT INTO dish_flavor (dish_id, name, value) VALUES (58, '薄荷', '多加,少加,不加');

-- 六、家常蒸菜（ID19-21）- 剁椒鱼头加辣度，其余无
-- 19.梅菜扣肉：无口味
-- 20.剁椒鱼头：辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (20, '辣度', '微辣,中辣,特辣');
-- 21.清蒸排骨：无口味

-- 七、营养汤羹（ID22-25）- 全部加葱花香菜（汤品基础调味）
-- 22.冬瓜海带排骨汤：葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (22, '葱花香菜', '加葱,加香菜,都加,都不加');
-- 23.番茄鸡蛋汤：葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (23, '葱花香菜', '加葱,加香菜,都加,都不加');
-- 24.菌菇土鸡汤：葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (24, '葱花香菜', '加葱,加香菜,都加,都不加');
-- 25.紫菜蛋花汤：葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (25, '葱花香菜', '加葱,加香菜,都加,都不加');

-- 八、特色主食（ID26-29）- 重庆小面/水饺专属口味，炒饭无
-- 26.扬州炒饭：无口味
-- 27.重庆小面：辣度+葱花香菜+汤底
INSERT INTO dish_flavor (dish_id, name, value) VALUES (27, '辣度', '微辣,中辣,特辣,免辣'), (27, '葱花香菜', '加葱,加香菜,都加,都不加'), (27, '汤底', '清汤,红汤,微麻');
-- 28.蛋炒饭：无口味
-- 29.手工水饺：馅料+葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (29, '馅料', '猪肉白菜,猪肉大葱,韭菜鸡蛋,玉米猪肉'), (29, '葱花香菜', '加葱,加香菜,都加,都不加');

-- 九、风味小吃（ID30-32）- 红糖糍粑加甜度，其余加辣度
-- 30.红糖糍粑：甜度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (30, '甜度', '微甜,正常甜,少甜,无糖');
-- 31.狼牙土豆：辣度+酸甜度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (31, '辣度', '微辣,中辣,特辣,免辣'), (31, '酸甜度', '微酸微甜,酸多甜少,正常');
-- 32.炸春卷：无口味（默认素菜馅）

-- 十、菌菇时蔬（ID33-36）- 蒜蓉娃娃菜加葱花香菜，其余无
-- 33.清炒油麦菜：无口味
-- 34.香菇青菜：无口味
-- 35.清炒西兰花：无口味
-- 36.蒜蓉娃娃菜：葱花香菜
INSERT INTO dish_flavor (dish_id, name, value) VALUES (36, '葱花香菜', '加葱,加香菜,都加,都不加');

-- 十一、红烧焖煮（ID37-39）- 黄焖鸡米饭加辣度，其余无
-- 37.红烧肉：无口味
-- 38.红烧鱼块：无口味
-- 39.黄焖鸡米饭：辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (39, '辣度', '微辣,中辣,特辣,免辣');

-- 十二、火锅涮菜（ID40-44）- 全部加蘸料选择（火锅核心）
-- 40.精品肥牛卷：蘸料
INSERT INTO dish_flavor (dish_id, name, value) VALUES (40, '蘸料', '麻酱,香油蒜泥,小米辣碟,生抽蚝油');
-- 41.手打牛肉丸：蘸料
INSERT INTO dish_flavor (dish_id, name, value) VALUES (41, '蘸料', '麻酱,香油蒜泥,小米辣碟,生抽蚝油');
-- 42.金针菇：蘸料
INSERT INTO dish_flavor (dish_id, name, value) VALUES (42, '蘸料', '麻酱,香油蒜泥,小米辣碟,生抽蚝油');
-- 43.冻豆腐：蘸料
INSERT INTO dish_flavor (dish_id, name, value) VALUES (43, '蘸料', '麻酱,香油蒜泥,小米辣碟,生抽蚝油');
-- 44.宽粉：蘸料+辣度
INSERT INTO dish_flavor (dish_id, name, value) VALUES (44, '蘸料', '麻酱,香油蒜泥,小米辣碟,生抽蚝油'), (44, '辣度', '微辣,中辣,特辣,免辣');
