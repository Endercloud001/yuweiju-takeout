-- CREATE TABLE IF NOT EXISTS only; no original seed data, DROP or USE.
CREATE TABLE IF NOT EXISTS `customer_service_session` (
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

CREATE TABLE IF NOT EXISTS `customer_service_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '会话id（customer_service_session.id）',
  `sender_type` int NOT NULL COMMENT '发送方类型 1-用户 2-管理员 3-系统',
  `sender_id` bigint DEFAULT NULL COMMENT '发送方id（SYSTEM 为 NULL）',
  `content` varchar(2000) COLLATE utf8mb4_bin NOT NULL COMMENT '消息内容',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_csm_session_id` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='人工客服消息表';

CREATE TABLE IF NOT EXISTS `ai_assistant_session` (
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

CREATE TABLE IF NOT EXISTS `ai_assistant_message` (
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

CREATE TABLE IF NOT EXISTS `address_book` (
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

CREATE TABLE IF NOT EXISTS `category` (
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

CREATE TABLE IF NOT EXISTS `dish` (
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

CREATE TABLE IF NOT EXISTS `dish_flavor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint NOT NULL COMMENT '菜品',
  `name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '口味名称',
  `value` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '口味数据list',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='菜品口味关系表';

CREATE TABLE IF NOT EXISTS `employee` (
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

CREATE TABLE IF NOT EXISTS `order_detail` (
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

CREATE TABLE IF NOT EXISTS `orders` (
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

CREATE TABLE IF NOT EXISTS `setmeal` (
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

CREATE TABLE IF NOT EXISTS `setmeal_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint NOT NULL COMMENT '套餐id',
  `dish_id` bigint NOT NULL COMMENT '菜品id',
  `name` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '菜品名称 （冗余字段）',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品单价（冗余字段）',
  `copies` int DEFAULT NULL COMMENT '菜品份数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='套餐菜品关系';

CREATE TABLE IF NOT EXISTS `shopping_cart` (
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

CREATE TABLE IF NOT EXISTS `user` (
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
