-- ============================================================================
-- TG 德州 / 短牌 / 牛牛 —— 本地开发库（H2 MySQL 兼容模式）
--
-- 约定：
--   1) 金额统一 micro-USDT，1 USDT = 1,000,000，全部 BIGINT，不用浮点；
--   2) 时间统一存 UTC；
--   3) 每个字段都带 COMMENT，生产库（MySQL 8.4）沿用同一套注释；
--   4) 索引名以 uk_ / idx_ 开头，uk = 唯一，idx = 普通。
--
-- 生产环境请用 db/schema-mysql.sql（索引写在建表语句内，无 CREATE INDEX IF NOT EXISTS）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 用户
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_user (
  id               BIGINT       NOT NULL COMMENT '用户ID，直接使用 Telegram user.id',
  tg_id            BIGINT                COMMENT 'Telegram user.id，冗余保存便于排查',
  username         VARCHAR(64)           COMMENT 'Telegram username，可能为空',
  nick             VARCHAR(64)           COMMENT '昵称',
  avatar           VARCHAR(512)          COMMENT '头像 URL',
  inviter_id       BIGINT                COMMENT '邀请人用户ID，用于推荐红包',
  vip_level        INT          DEFAULT 0 COMMENT 'VIP 等级，0=普通',
  insurance_opt_in INT          DEFAULT 1 COMMENT '是否开启保险：1=开，0=关',
  created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间（UTC）',
  PRIMARY KEY (id)
) COMMENT='用户主表，一个 Telegram 账号一行';

-- ---------------------------------------------------------------------------
-- 钱包账户
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS account (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  balance BIGINT NOT NULL DEFAULT 0 COMMENT '可用余额（micro-USDT）',
  frozen  BIGINT NOT NULL DEFAULT 0 COMMENT '冻结金额（micro-USDT）',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (user_id)
) COMMENT='钱包账户，余额只在这里维护';

-- ---------------------------------------------------------------------------
-- 资金流水
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ledger_entry (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id       BIGINT      NOT NULL COMMENT '用户ID',
  direction     INT         NOT NULL COMMENT '1=支出(DEBIT)，2=收入(CREDIT)',
  amount        BIGINT      NOT NULL COMMENT '发生金额（micro-USDT，正数）',
  biz_type      VARCHAR(24) NOT NULL COMMENT 'DEPOSIT/WITHDRAW/TABLE_BUYIN/TABLE_CASHOUT/INSURANCE_*/TRANSFER_*/ACTIVITY',
  biz_id        VARCHAR(64) NOT NULL COMMENT '业务幂等键：txHash / handId-座位 / code-bizId',
  balance_after BIGINT      NOT NULL COMMENT '变动后余额，用于客服追溯与对账',
  memo          VARCHAR(128)         COMMENT '展示用摘要',
  created_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '记账时间（UTC）',
  PRIMARY KEY (id)
) COMMENT='资金流水（只增不改）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ledger_idem ON ledger_entry (biz_type, biz_id, user_id);
CREATE INDEX IF NOT EXISTS idx_ledger_user ON ledger_entry (user_id, id DESC);

-- ---------------------------------------------------------------------------
-- 一手牌（存证主表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hand (
  hand_id          VARCHAR(64) NOT NULL COMMENT '手牌ID，格式 {tableId}-{handNo}',
  table_id         VARCHAR(24) NOT NULL COMMENT '牌桌ID',
  club_id          BIGINT      DEFAULT 0 COMMENT '俱乐部ID，0=无',
  variant          VARCHAR(10) NOT NULL COMMENT 'LONG=52张，SHORT=36张',
  blinds           VARCHAR(48)          COMMENT '盲注展示串，如 0.1/0.2/0.4',
  ante             BIGINT      DEFAULT 0 COMMENT '前注金额（micro-USDT）',
  board            VARCHAR(24)          COMMENT '公牌拼接，如 2s5sAh3d5h',
  pot              BIGINT               COMMENT '本手底池总额',
  rake             BIGINT               COMMENT '抽水金额，当前 0',
  server_seed      VARCHAR(64)          COMMENT '服务端种子（结算后揭示）',
  server_seed_hash VARCHAR(64)          COMMENT '服务端种子 SHA256（开局公示）',
  client_seeds     VARCHAR(256)         COMMENT '玩家种子，逗号连接',
  nonce            BIGINT               COMMENT '手号，参与种子派生',
  verify_code      VARCHAR(24)          COMMENT '存证标识，如 1#9E18D6BB2C',
  started_at       TIMESTAMP            COMMENT '开局时间（UTC）',
  ended_at         TIMESTAMP            COMMENT '结算时间（UTC）',
  PRIMARY KEY (hand_id)
) COMMENT='每手牌存证记录';

CREATE INDEX IF NOT EXISTS idx_hand_table ON hand (table_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_hand_club ON hand (club_id, started_at DESC);

-- ---------------------------------------------------------------------------
-- 手牌-玩家
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hand_player (
  hand_id       VARCHAR(64) NOT NULL COMMENT '手牌ID',
  seat          INT         NOT NULL COMMENT '座位号',
  user_id       BIGINT               COMMENT '用户ID',
  hole_cards    VARCHAR(24)          COMMENT '底牌，如 KsKh（生产应加密）',
  start_stack   BIGINT               COMMENT '本手开始筹码',
  end_stack     BIGINT               COMMENT '结算后筹码',
  net           BIGINT               COMMENT '本手输赢',
  vpip          INT         DEFAULT 0 COMMENT '翻前主动入池 1/0',
  pfr           INT         DEFAULT 0 COMMENT '翻前加注/全下 1/0',
  actions       VARCHAR(2000)        COMMENT '动作序列，风控回放用',
  insurance_net BIGINT      DEFAULT 0 COMMENT '保险净额 = 赔付 - 保费',
  PRIMARY KEY (hand_id, seat)
) COMMENT='每手每座位战绩';

CREATE INDEX IF NOT EXISTS idx_hp_user ON hand_player (user_id, hand_id);

-- ---------------------------------------------------------------------------
-- 保险单
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS insurance_bet (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  hand_id       VARCHAR(64)  NOT NULL COMMENT '手牌ID',
  table_id      VARCHAR(24)  NOT NULL COMMENT '牌桌ID',
  pot_index     INT          DEFAULT 0 COMMENT '底池序号',
  buyer_user_id BIGINT       NOT NULL COMMENT '投保人（领先方）',
  outs          INT          NOT NULL COMMENT '反超 outs 数量',
  odds          DECIMAL(6,3) NOT NULL COMMENT '主险赔率',
  tie_outs      INT          DEFAULT 0 COMMENT '平局 outs',
  tie_odds      DECIMAL(6,3)          COMMENT '平局险赔率',
  premium_main  BIGINT       NOT NULL COMMENT '主险保费',
  premium_tie   BIGINT       DEFAULT 0 COMMENT '平局险保费',
  coverage      BIGINT       NOT NULL COMMENT '保额，上限为满池',
  result        VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/WON/LOST',
  settled_at    TIMESTAMP             COMMENT '结算时间',
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '投保时间',
  PRIMARY KEY (id)
) COMMENT='保险单，投保与赔付分列';

CREATE INDEX IF NOT EXISTS idx_ins_hand ON insurance_bet (hand_id, pot_index);
CREATE INDEX IF NOT EXISTS idx_ins_buyer ON insurance_bet (buyer_user_id, id DESC);

-- ---------------------------------------------------------------------------
-- 俱乐部
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS club (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '俱乐部ID',
  name              VARCHAR(64)  NOT NULL COMMENT '名称',
  owner_id          BIGINT       NOT NULL COMMENT '盟主用户ID，0=系统默认',
  avatar            VARCHAR(512)          COMMENT '头像',
  notice            VARCHAR(512)          COMMENT '公告',
  join_type         VARCHAR(16)  DEFAULT 'OPEN' COMMENT 'OPEN=直接加入，APPROVAL=需审核',
  min_buy_in        BIGINT       DEFAULT 10000000 COMMENT '最小买入（micro-USDT）',
  max_buy_in        BIGINT       DEFAULT 400000000 COMMENT '最大买入（micro-USDT）',
  insurance_enabled INT          DEFAULT 1 COMMENT '是否开启保险：1=开，0=关',
  created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id)
) COMMENT='俱乐部';

CREATE TABLE IF NOT EXISTS club_member (
  club_id   BIGINT      NOT NULL COMMENT '俱乐部ID',
  user_id   BIGINT      NOT NULL COMMENT '用户ID',
  role      VARCHAR(16) DEFAULT 'MEMBER' COMMENT 'OWNER/ADMIN/MEMBER',
  status    VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PENDING',
  joined_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (club_id, user_id)
) COMMENT='俱乐部成员';

CREATE INDEX IF NOT EXISTS idx_cm_user ON club_member (user_id);

CREATE TABLE IF NOT EXISTS club_treasury (
  club_id     BIGINT NOT NULL COMMENT '俱乐部ID',
  balance     BIGINT NOT NULL DEFAULT 0 COMMENT '金库余额（micro-USDT）',
  min_reserve BIGINT NOT NULL DEFAULT 0 COMMENT '最低保证金',
  version     BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (club_id)
) COMMENT='俱乐部金库，保险承保方';

-- ---------------------------------------------------------------------------
-- 充提
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS deposit_address (
  address      VARCHAR(64) NOT NULL COMMENT 'TRON 地址',
  user_id      BIGINT      NOT NULL COMMENT '归属用户ID',
  chain        VARCHAR(16) DEFAULT 'TRON' COMMENT '链',
  asset        VARCHAR(16) DEFAULT 'USDT' COMMENT '资产',
  allocated_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
  PRIMARY KEY (address)
) COMMENT='充值地址，一人一址';

CREATE UNIQUE INDEX IF NOT EXISTS uk_dep_addr_user ON deposit_address (user_id);

CREATE TABLE IF NOT EXISTS deposit_order (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id       BIGINT       NOT NULL COMMENT '用户ID',
  address       VARCHAR(64)  NOT NULL COMMENT '收款地址',
  tx_hash       VARCHAR(128) NOT NULL COMMENT '链上交易哈希，唯一',
  amount        BIGINT       NOT NULL COMMENT '到账金额',
  confirmations INT          DEFAULT 0 COMMENT '确认数',
  status        VARCHAR(16)  DEFAULT 'CREDITED' COMMENT 'CREDITED/PENDING',
  credited_at   TIMESTAMP             COMMENT '入账时间',
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id)
) COMMENT='充值订单，tx_hash 唯一保证只入账一次';

CREATE UNIQUE INDEX IF NOT EXISTS uk_dep_tx ON deposit_order (tx_hash);

CREATE TABLE IF NOT EXISTS withdraw_order (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id     BIGINT       NOT NULL COMMENT '用户ID',
  address     VARCHAR(64)  NOT NULL COMMENT 'TRON 收款地址',
  amount      BIGINT       NOT NULL COMMENT '提现金额（不含手续费）',
  fee         BIGINT       NOT NULL COMMENT '手续费',
  tx_hash     VARCHAR(128)          COMMENT '链上交易哈希',
  status      VARCHAR(16)  DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW/PROCESSING/SUCCESS/REJECTED',
  memo        VARCHAR(128)          COMMENT '备注',
  created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  reviewed_at TIMESTAMP             COMMENT '审核时间',
  PRIMARY KEY (id)
) COMMENT='提现工单';

CREATE INDEX IF NOT EXISTS idx_wd_user ON withdraw_order (user_id, id DESC);

-- ---------------------------------------------------------------------------
-- 红包
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS red_packet (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  sender_id   BIGINT      NOT NULL COMMENT '发送人',
  receiver_id BIGINT      NOT NULL COMMENT '接收人',
  amount      BIGINT      NOT NULL COMMENT '金额',
  memo        VARCHAR(64)          COMMENT '备注',
  scene       VARCHAR(16) DEFAULT 'CHAT' COMMENT 'CHAT/TABLE/ACTIVITY',
  table_id    VARCHAR(24)          COMMENT '桌内红包所属牌桌',
  status      VARCHAR(16) DEFAULT 'CLAIMED' COMMENT 'CLAIMED/PENDING',
  created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  claimed_at  TIMESTAMP            COMMENT '领取时间',
  PRIMARY KEY (id)
) COMMENT='红包，本质是用户间转账';

CREATE INDEX IF NOT EXISTS idx_rp_recv ON red_packet (receiver_id, id DESC);

-- ---------------------------------------------------------------------------
-- 教学
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lesson (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  category     VARCHAR(16)  DEFAULT 'STRATEGY' COMMENT 'STRATEGY/TOOL/TRAINING',
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
) COMMENT='教学课程';

CREATE TABLE IF NOT EXISTS lesson_favorite (
  user_id    BIGINT NOT NULL COMMENT '用户ID',
  lesson_id  BIGINT NOT NULL COMMENT '课程ID',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (user_id, lesson_id)
) COMMENT='课程收藏';

CREATE TABLE IF NOT EXISTS quiz_question (
  id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  category VARCHAR(16)           COMMENT '分类',
  question VARCHAR(512) NOT NULL COMMENT '题干',
  options  VARCHAR(512) NOT NULL COMMENT '选项 JSON',
  answer   INT          NOT NULL COMMENT '正确选项下标',
  explain  VARCHAR(1024)         COMMENT '解析',
  PRIMARY KEY (id)
) COMMENT='训练题库';

CREATE TABLE IF NOT EXISTS quiz_attempt (
  id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id     BIGINT NOT NULL COMMENT '用户ID',
  question_id BIGINT NOT NULL COMMENT '题目ID',
  correct     INT    NOT NULL COMMENT '是否答对 1/0',
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  PRIMARY KEY (id)
) COMMENT='答题记录';

-- ---------------------------------------------------------------------------
-- 活动
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  code          VARCHAR(32) NOT NULL COMMENT 'VIP_GROUP/BIG_HAND/REFERRAL/LUCKY_DICE',
  name          VARCHAR(64) NOT NULL COMMENT '名称',
  icon          VARCHAR(16)          COMMENT '图标',
  description   VARCHAR(512)         COMMENT '说明',
  link          VARCHAR(512)         COMMENT '跳转链接',
  reward_amount BIGINT      DEFAULT 0 COMMENT '固定奖励，0=按规则计算',
  daily_limit   INT         DEFAULT 1 COMMENT '每日次数',
  enabled       INT         DEFAULT 1 COMMENT '是否启用',
  sort_no       INT         DEFAULT 0 COMMENT '排序号',
  PRIMARY KEY (id)
) COMMENT='活动配置';

CREATE UNIQUE INDEX IF NOT EXISTS uk_activity_code ON activity (code);

CREATE TABLE IF NOT EXISTS activity_claim (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id       BIGINT      NOT NULL COMMENT '用户ID',
  activity_code VARCHAR(32) NOT NULL COMMENT '活动编码',
  amount        BIGINT      DEFAULT 0 COMMENT '发放金额',
  biz_id        VARCHAR(64) NOT NULL COMMENT '业务幂等键',
  created_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  PRIMARY KEY (id)
) COMMENT='活动领取记录';

CREATE UNIQUE INDEX IF NOT EXISTS uk_claim_once ON activity_claim (activity_code, user_id, biz_id);

-- ---------------------------------------------------------------------------
-- 牛牛
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS niuniu_round (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  table_id       VARCHAR(24) NOT NULL COMMENT '牌桌ID',
  round_no       BIGINT      NOT NULL COMMENT '局号',
  banker_user_id BIGINT               COMMENT '庄家',
  banker_cards   VARCHAR(24)          COMMENT '庄家 5 张牌',
  detail         VARCHAR(4000)        COMMENT '明细 JSON',
  total_stake    BIGINT      DEFAULT 0 COMMENT '总下注',
  created_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '开牌时间',
  PRIMARY KEY (id)
) COMMENT='牛牛牌局记录';

CREATE INDEX IF NOT EXISTS idx_nn_table ON niuniu_round (table_id, id DESC);

CREATE TABLE IF NOT EXISTS niuniu_result (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  table_id   VARCHAR(24) NOT NULL COMMENT '牌桌ID',
  round_no   BIGINT      NOT NULL COMMENT '局号',
  user_id    BIGINT      NOT NULL COMMENT '用户ID',
  seat       INT         NOT NULL COMMENT '座位号 0~3',
  banker     INT         DEFAULT 0 COMMENT '是否庄家 1/0',
  bet        BIGINT      DEFAULT 0 COMMENT '本局下注额',
  net        BIGINT      DEFAULT 0 COMMENT '本局净输赢',
  created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (id)
) COMMENT='牛牛单局单人战绩';

CREATE INDEX IF NOT EXISTS idx_nnr_user ON niuniu_result (user_id, id DESC);

-- ---------------------------------------------------------------------------
-- 风控与配置
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_event (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  user_id    BIGINT               COMMENT '用户ID',
  table_id   VARCHAR(24)          COMMENT '牌桌ID',
  kind       VARCHAR(24) NOT NULL COMMENT 'COLLUSION/MULTI_ACCOUNT/BOT/WASH/INSURANCE_ARBITRAGE',
  score      INT         DEFAULT 0 COMMENT '风险分 0~100',
  detail     VARCHAR(512)         COMMENT '详情',
  created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id)
) COMMENT='风控事件';

CREATE INDEX IF NOT EXISTS idx_risk_user ON risk_event (user_id, id DESC);

CREATE TABLE IF NOT EXISTS app_config (
  k          VARCHAR(64)  NOT NULL COMMENT '配置键',
  v          VARCHAR(512)          COMMENT '配置值',
  updated_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (k)
) COMMENT='运行时配置';
