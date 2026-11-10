-- ============================================================================
-- TG 德州 / 短牌 / 牛牛 —— 生产库（MySQL 8.4 LTS）
--
-- 与 src/main/resources/schema.sql 表结构一致，仅做三处方言适配：
--   1) 索引写在 CREATE TABLE 内（MySQL 8 不支持 CREATE INDEX IF NOT EXISTS）；
--   2) 追加 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci；
--   3) hand 表给出按月分区的写法（分区键必须包含在主键里）。
--
-- 应用侧连接后需执行：
--   SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- 原因：MySQL 默认 REPEATABLE READ，InnoDB 间隙锁会把并发行锁升级成死锁。
-- ============================================================================

-- 数据库需先手动创建（应用启动会自动执行本文件建表）：
--   CREATE DATABASE tgpoker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS app_user (
  id          BIGINT       NOT NULL COMMENT '用户ID，直接使用 Telegram user.id',
  tg_id       BIGINT                COMMENT 'Telegram user.id',
  username    VARCHAR(64)           COMMENT 'Telegram username',
  nick        VARCHAR(64)           COMMENT '昵称',
  avatar      VARCHAR(512)          COMMENT '头像 URL',
  inviter_id  BIGINT                COMMENT '邀请人用户ID',
  vip_level   INT          DEFAULT 0 COMMENT 'VIP 等级',
  insurance_opt_in TINYINT DEFAULT 1 COMMENT '是否开启保险 1/0',
  created_at  DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间（UTC）',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主表';

CREATE TABLE IF NOT EXISTS account (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  balance BIGINT NOT NULL DEFAULT 0 COMMENT '可用余额（micro-USDT）',
  frozen  BIGINT NOT NULL DEFAULT 0 COMMENT '冻结金额（micro-USDT）',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包账户';

CREATE TABLE IF NOT EXISTS ledger_entry (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id       BIGINT      NOT NULL COMMENT '用户ID',
  direction     TINYINT     NOT NULL COMMENT '1=支出 2=收入',
  amount        BIGINT      NOT NULL COMMENT '金额（micro-USDT）',
  biz_type      VARCHAR(24) NOT NULL COMMENT '业务类型',
  biz_id        VARCHAR(64) NOT NULL COMMENT '业务幂等键',
  balance_after BIGINT      NOT NULL COMMENT '变动后余额',
  memo          VARCHAR(128)         COMMENT '摘要',
  created_at    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记账时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ledger_idem (biz_type, biz_id, user_id),
  KEY idx_ledger_user (user_id, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水（只增不改）';

CREATE TABLE IF NOT EXISTS hand (
  hand_id          VARCHAR(64) NOT NULL COMMENT '手牌ID',
  table_id         VARCHAR(24) NOT NULL COMMENT '牌桌ID',
  club_id          BIGINT      DEFAULT 0 COMMENT '俱乐部ID',
  variant          VARCHAR(10) NOT NULL COMMENT 'LONG/SHORT',
  blinds           VARCHAR(48)          COMMENT '盲注展示串',
  ante             BIGINT      DEFAULT 0 COMMENT '前注（micro-USDT）',
  board            VARCHAR(24)          COMMENT '公牌',
  pot              BIGINT               COMMENT '底池',
  rake             BIGINT               COMMENT '抽水',
  server_seed      VARCHAR(64)          COMMENT '服务端种子（结算后揭示）',
  server_seed_hash VARCHAR(64)          COMMENT '种子 SHA256（开局公示）',
  client_seeds     VARCHAR(256)         COMMENT '玩家种子（逗号连接）',
  nonce            BIGINT               COMMENT '手号',
  verify_code      VARCHAR(24)          COMMENT '存证标识',
  started_at       DATETIME(3)          COMMENT '开局时间（UTC）',
  ended_at         DATETIME(3)          COMMENT '结算时间（UTC）',
  PRIMARY KEY (hand_id),
  KEY idx_hand_table (table_id, started_at DESC),
  KEY idx_hand_club (club_id, started_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手牌存证';

-- 数据量上来后按月分区。分区键必须包含在主键里，所以主键是 (hand_id, started_at)。
-- 切换分区需要重建表，建议上线前就定好；示例：
-- ALTER TABLE hand
--   PARTITION BY RANGE COLUMNS(started_at) (
--     PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
--     PARTITION p202701 VALUES LESS THAN ('2027-02-01'),
--     PARTITION pmax    VALUES LESS THAN (MAXVALUE)
--   );

CREATE TABLE IF NOT EXISTS hand_player (
  hand_id       VARCHAR(64) NOT NULL COMMENT '手牌ID',
  seat          TINYINT     NOT NULL COMMENT '座位号',
  user_id       BIGINT               COMMENT '用户ID',
  hole_cards    VARCHAR(24)          COMMENT '底牌（生产应改为密文）',
  start_stack   BIGINT               COMMENT '开始筹码',
  end_stack     BIGINT               COMMENT '结束筹码',
  net           BIGINT               COMMENT '本手输赢',
  vpip          TINYINT     DEFAULT 0 COMMENT '翻前入池 1/0',
  pfr           TINYINT     DEFAULT 0 COMMENT '翻前加注 1/0',
  actions       VARCHAR(2000)        COMMENT '动作序列',
  insurance_net BIGINT      DEFAULT 0 COMMENT '保险净额',
  PRIMARY KEY (hand_id, seat),
  KEY idx_hp_user (user_id, hand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每手每座位战绩';

CREATE TABLE IF NOT EXISTS insurance_bet (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  hand_id       VARCHAR(64)  NOT NULL COMMENT '手牌ID',
  table_id      VARCHAR(24)  NOT NULL COMMENT '牌桌ID',
  pot_index     INT          DEFAULT 0 COMMENT '底池序号',
  buyer_user_id BIGINT       NOT NULL COMMENT '投保人',
  outs          INT          NOT NULL COMMENT '反超 outs',
  odds          DECIMAL(6,3) NOT NULL COMMENT '主险赔率',
  tie_outs      INT          DEFAULT 0 COMMENT '平局 outs',
  tie_odds      DECIMAL(6,3)          COMMENT '平局赔率',
  premium_main  BIGINT       NOT NULL COMMENT '主险保费',
  premium_tie   BIGINT       DEFAULT 0 COMMENT '平局险保费',
  coverage      BIGINT       NOT NULL COMMENT '保额',
  result        VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/WON/LOST',
  settled_at    DATETIME(3)           COMMENT '结算时间',
  created_at    DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '投保时间',
  PRIMARY KEY (id),
  KEY idx_ins_hand (hand_id, pot_index),
  KEY idx_ins_buyer (buyer_user_id, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保险单';

CREATE TABLE IF NOT EXISTS club (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '俱乐部ID',
  name              VARCHAR(64)  NOT NULL COMMENT '名称',
  owner_id          BIGINT       NOT NULL COMMENT '盟主用户ID',
  avatar            VARCHAR(512)          COMMENT '头像',
  notice            VARCHAR(512)          COMMENT '公告',
  join_type         VARCHAR(16)  DEFAULT 'OPEN' COMMENT 'OPEN/APPROVAL',
  min_buy_in        BIGINT       DEFAULT 10000000 COMMENT '最小买入',
  max_buy_in        BIGINT       DEFAULT 400000000 COMMENT '最大买入',
  insurance_enabled TINYINT      DEFAULT 1 COMMENT '保险开关',
  created_at        DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='俱乐部';

CREATE TABLE IF NOT EXISTS club_member (
  club_id    BIGINT    NOT NULL COMMENT '俱乐部ID',
  user_id    BIGINT    NOT NULL COMMENT '用户ID',
  role       VARCHAR(16) DEFAULT 'MEMBER' COMMENT 'OWNER/ADMIN/MEMBER',
  status     VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PENDING',
  joined_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
  PRIMARY KEY (club_id, user_id),
  KEY idx_cm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='俱乐部成员';

CREATE TABLE IF NOT EXISTS club_treasury (
  club_id     BIGINT NOT NULL COMMENT '俱乐部ID',
  balance     BIGINT NOT NULL DEFAULT 0 COMMENT '金库余额',
  min_reserve BIGINT NOT NULL DEFAULT 0 COMMENT '最低保证金',
  version     BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (club_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='俱乐部金库';

CREATE TABLE IF NOT EXISTS deposit_address (
  address      VARCHAR(64) NOT NULL COMMENT 'TRON 地址',
  user_id      BIGINT      NOT NULL COMMENT '用户ID',
  chain        VARCHAR(16) DEFAULT 'TRON' COMMENT '链',
  asset        VARCHAR(16) DEFAULT 'USDT' COMMENT '资产',
  allocated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '分配时间',
  PRIMARY KEY (address),
  UNIQUE KEY uk_dep_addr_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值地址（一人一址）';

CREATE TABLE IF NOT EXISTS deposit_order (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id       BIGINT       NOT NULL COMMENT '用户ID',
  address       VARCHAR(64)  NOT NULL COMMENT '收款地址',
  tx_hash       VARCHAR(128) NOT NULL COMMENT '链上交易哈希',
  amount        BIGINT       NOT NULL COMMENT '金额',
  confirmations INT          DEFAULT 0 COMMENT '确认数',
  status        VARCHAR(16)  DEFAULT 'CREDITED' COMMENT '状态',
  credited_at   DATETIME(3)           COMMENT '入账时间',
  created_at    DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dep_tx (tx_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值订单';

CREATE TABLE IF NOT EXISTS withdraw_order (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id     BIGINT       NOT NULL COMMENT '用户ID',
  address     VARCHAR(64)  NOT NULL COMMENT '收款地址',
  amount      BIGINT       NOT NULL COMMENT '提现金额',
  fee         BIGINT       NOT NULL COMMENT '手续费',
  tx_hash     VARCHAR(128)          COMMENT '链上交易哈希',
  status      VARCHAR(16)  DEFAULT 'PENDING_REVIEW' COMMENT '状态',
  memo        VARCHAR(128)          COMMENT '备注',
  created_at  DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  reviewed_at DATETIME(3)           COMMENT '审核时间',
  PRIMARY KEY (id),
  KEY idx_wd_user (user_id, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现工单';

CREATE TABLE IF NOT EXISTS red_packet (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  sender_id   BIGINT      NOT NULL COMMENT '发送人',
  receiver_id BIGINT      NOT NULL COMMENT '接收人',
  amount      BIGINT      NOT NULL COMMENT '金额',
  memo        VARCHAR(64)          COMMENT '备注',
  scene       VARCHAR(16) DEFAULT 'CHAT' COMMENT 'CHAT/TABLE/ACTIVITY',
  table_id    VARCHAR(24)          COMMENT '牌桌ID',
  status      VARCHAR(16) DEFAULT 'CLAIMED' COMMENT '状态',
  created_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  claimed_at  DATETIME(3)          COMMENT '领取时间',
  PRIMARY KEY (id),
  KEY idx_rp_recv (receiver_id, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='红包';

CREATE TABLE IF NOT EXISTS lesson (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  category     VARCHAR(16)  DEFAULT 'STRATEGY' COMMENT '分类',
  title        VARCHAR(128) NOT NULL COMMENT '标题',
  summary      VARCHAR(512)          COMMENT '摘要',
  cover        VARCHAR(512)          COMMENT '封面',
  media_type   VARCHAR(16)  DEFAULT 'VIDEO' COMMENT 'VIDEO/ARTICLE',
  media_url    VARCHAR(512)          COMMENT '媒体地址',
  duration_sec INT          DEFAULT 0 COMMENT '时长（秒）',
  likes        INT          DEFAULT 0 COMMENT '点赞数',
  views        INT          DEFAULT 0 COMMENT '播放数',
  content      VARCHAR(4000)         COMMENT '正文',
  sort_no      INT          DEFAULT 0 COMMENT '排序号',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学课程';

CREATE TABLE IF NOT EXISTS lesson_favorite (
  user_id    BIGINT NOT NULL COMMENT '用户ID',
  lesson_id  BIGINT NOT NULL COMMENT '课程ID',
  created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
  PRIMARY KEY (user_id, lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程收藏';

CREATE TABLE IF NOT EXISTS quiz_question (
  id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  category VARCHAR(16)           COMMENT '分类',
  question VARCHAR(512) NOT NULL COMMENT '题干',
  options  VARCHAR(512) NOT NULL COMMENT '选项 JSON',
    `answer` int NOT NULL COMMENT '正确选项下标',
    `explain` varchar(1024) DEFAULT NULL COMMENT '解析',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练题库';

CREATE TABLE IF NOT EXISTS quiz_attempt (
  id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id     BIGINT NOT NULL COMMENT '用户ID',
  question_id BIGINT NOT NULL COMMENT '题目ID',
  correct     TINYINT NOT NULL COMMENT '是否答对',
  created_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '答题时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录';

CREATE TABLE IF NOT EXISTS activity (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  code          VARCHAR(32) NOT NULL COMMENT '活动编码',
  name          VARCHAR(64) NOT NULL COMMENT '名称',
  icon          VARCHAR(16)          COMMENT '图标',
  description   VARCHAR(512)         COMMENT '说明',
  link          VARCHAR(512)         COMMENT '跳转链接',
  reward_amount BIGINT      DEFAULT 0 COMMENT '固定奖励',
  daily_limit   INT         DEFAULT 1 COMMENT '每日次数',
  enabled       TINYINT     DEFAULT 1 COMMENT '是否启用',
  sort_no       INT         DEFAULT 0 COMMENT '排序号',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动配置';

CREATE TABLE IF NOT EXISTS activity_claim (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id       BIGINT      NOT NULL COMMENT '用户ID',
  activity_code VARCHAR(32) NOT NULL COMMENT '活动编码',
  amount        BIGINT      DEFAULT 0 COMMENT '发放金额',
  biz_id        VARCHAR(64) NOT NULL COMMENT '业务幂等键',
  created_at    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '领取时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim_once (activity_code, user_id, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动领取记录';

CREATE TABLE IF NOT EXISTS niuniu_round (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  table_id       VARCHAR(24) NOT NULL COMMENT '牌桌ID',
  round_no       BIGINT      NOT NULL COMMENT '局号',
  banker_user_id BIGINT               COMMENT '庄家',
  banker_cards   VARCHAR(24)          COMMENT '庄家牌',
  detail         VARCHAR(4000)        COMMENT '明细 JSON',
  total_stake    BIGINT      DEFAULT 0 COMMENT '总下注',
  created_at     DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开牌时间',
  PRIMARY KEY (id),
  KEY idx_nn_table (table_id, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='牛牛牌局';

CREATE TABLE IF NOT EXISTS risk_event (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id    BIGINT               COMMENT '用户ID',
  table_id   VARCHAR(24)          COMMENT '牌桌ID',
  kind       VARCHAR(24) NOT NULL COMMENT '类型',
  score      INT         DEFAULT 0 COMMENT '风险分',
  detail     VARCHAR(512)         COMMENT '详情',
  created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_risk_user (user_id, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控事件';

CREATE TABLE IF NOT EXISTS app_config (
  k          VARCHAR(64)  NOT NULL COMMENT '配置键',
  v          VARCHAR(512)          COMMENT '配置值',
  updated_at DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行时配置';

CREATE TABLE IF NOT EXISTS niuniu_result (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  table_id   VARCHAR(24) NOT NULL COMMENT '牌桌ID',
  round_no   BIGINT      NOT NULL COMMENT '局号',
  user_id    BIGINT      NOT NULL COMMENT '用户ID',
  seat       TINYINT     NOT NULL COMMENT '座位号',
  banker     TINYINT     DEFAULT 0 COMMENT '是否庄家',
  bet        BIGINT      DEFAULT 0 COMMENT '下注额',
  net        BIGINT      DEFAULT 0 COMMENT '净输赢',
  created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录时间',
  PRIMARY KEY (id),
  KEY idx_nnr_user (user_id, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='牛牛单局单人战绩';
