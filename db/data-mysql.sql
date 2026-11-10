-- ============================================================================
-- TG 德州 —— 种子数据（MySQL 8.4）
--
-- 用途：教学课程 / 训练题库 / 活动配置的初始内容。
-- 执行时机：只在初始化数据库时手动跑一次，应用启动不再插入任何数据。
--   mysql -uroot -p tgpoker < db/data-mysql.sql
--
-- 幂等：每条都带 NOT EXISTS 判断，重复执行不会产生重复行，也不会覆盖运营改过的内容。
-- ============================================================================

USE tgpoker;

-- ---------------------------------------------------------------------------
-- 教学课程
-- ---------------------------------------------------------------------------
INSERT INTO lesson (category, title, summary, cover, media_type, media_url, duration_sec, likes, views, content, sort_no)
SELECT 'STRATEGY', '德扑小绿皮书 01：翻前该玩多少手', '从位置与对手数量出发，给出可执行的入池范围表。', '', 'VIDEO', '', 68, 314, 78700,
       '翻前决定了一手牌 70% 的期望。记住三件事：位置越靠后范围越宽；对手越多越要收紧；不要因为上一手被诈唬就放宽范围。', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lesson WHERE title = '德扑小绿皮书 01：翻前该玩多少手');

INSERT INTO lesson (category, title, summary, cover, media_type, media_url, duration_sec, likes, views, content, sort_no)
SELECT 'STRATEGY', '短牌为什么同花比葫芦大', '36 张牌带来概率结构变化，牌型强弱必须重排。', '', 'ARTICLE', '', 240, 128, 21500,
       '短牌去掉 2/3/4/5 后，成同花的概率显著高于成葫芦，所以规则把同花排在葫芦之上；顺子也更容易成，于是三条反超顺子。最小顺子是 A6789，A 不再能当 1。', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lesson WHERE title = '短牌为什么同花比葫芦大');

INSERT INTO lesson (category, title, summary, cover, media_type, media_url, duration_sec, likes, views, content, sort_no)
SELECT 'STRATEGY', '保险到底划算吗：用 outs 算一遍', '把保费换算成隐含赔率，看清什么时候该买。', '', 'ARTICLE', '', 300, 96, 12400,
       '保费 = 保额 ÷ 赔率。以 14 outs 为例，真实反超概率约 31.8%，公平赔率约 3.14，而常见赔付只有 1.6 倍——长期买保险是负 EV。保险的价值在于降低波动，不是提高收益。', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lesson WHERE title = '保险到底划算吗：用 outs 算一遍');

INSERT INTO lesson (category, title, summary, cover, media_type, media_url, duration_sec, likes, views, content, sort_no)
SELECT 'TOOL', '胜率计算器怎么用', '选两张底牌、可选公牌，得到对 N 个对手的胜率。', '', 'ARTICLE', '', 120, 64, 9800,
       '计算器用 Monte Carlo 抽样 2 万次，标准误约 0.35%。翻前全下对比时，把对手数设为实际人数即可。', 4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lesson WHERE title = '胜率计算器怎么用');

INSERT INTO lesson (category, title, summary, cover, media_type, media_url, duration_sec, likes, views, content, sort_no)
SELECT 'TRAINING', '练习题：这手该跟吗', '给出位置、底池赔率与对手动作，选择跟注 / 加注 / 弃牌。', '', 'ARTICLE', '', 90, 41, 6200,
       '先算底池赔率，再看隐含赔率，最后考虑对手范围。不要凭感觉。', 5
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lesson WHERE title = '练习题：这手该跟吗');

-- ---------------------------------------------------------------------------
-- 训练题库（explain 是 MySQL 保留字，列名必须带反引号）
-- ---------------------------------------------------------------------------
INSERT INTO quiz_question (category, question, options, answer, `explain`)
SELECT '翻前', 'UTG 位拿到 AJo，前面都弃牌，标准动作是？', '["加注","跟注","弃牌"]', 0,
       'UTG 是最差位置，AJo 被统治风险高但仍有开池价值，标准是加注而不是跟注。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM quiz_question WHERE question = 'UTG 位拿到 AJo，前面都弃牌，标准动作是？');

INSERT INTO quiz_question (category, question, options, answer, `explain`)
SELECT '赔率', '底池 100，对手下注 50，你需要多少胜率才值得跟？', '["25%","33%","50%"]', 0,
       '跟 50 去赢 150，需要 50/(100+50+50)=25% 的胜率。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM quiz_question WHERE question = '底池 100，对手下注 50，你需要多少胜率才值得跟？');

INSERT INTO quiz_question (category, question, options, answer, `explain`)
SELECT '短牌', '短牌里，下面哪个牌型更大？', '["同花","葫芦","一样大"]', 0,
       '短牌规则把同花排在葫芦之上，因为 36 张牌时成同花概率更高。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM quiz_question WHERE question = '短牌里，下面哪个牌型更大？');

INSERT INTO quiz_question (category, question, options, answer, `explain`)
SELECT '保险', '转牌圈你有 14 个 outs，真实反超概率约为？', '["约 32%","约 50%","约 14%"]', 0,
       '44 张未知牌里有 14 张能反超，14/44 ≈ 31.8%。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM quiz_question WHERE question = '转牌圈你有 14 个 outs，真实反超概率约为？');

INSERT INTO quiz_question (category, question, options, answer, `explain`)
SELECT '位置', '按钮位相比大盲位的主要优势是？', '["可以先看到对手动作","盲注更低","牌更好"]', 0,
       '按钮位翻后最后行动，信息优势最大，所以可以玩更宽的范围。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM quiz_question WHERE question = '按钮位相比大盲位的主要优势是？');

-- ---------------------------------------------------------------------------
-- 活动配置
-- ---------------------------------------------------------------------------
INSERT INTO activity (code, name, icon, description, link, reward_amount, daily_limit, enabled, sort_no)
SELECT 'VIP_GROUP', 'VIP 专属群', '👑', '加入 VIP 群，第一时间获取开桌通知与活动', 'https://t.me/', 0, 1, 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM activity WHERE code = 'VIP_GROUP');

INSERT INTO activity (code, name, icon, description, link, reward_amount, daily_limit, enabled, sort_no)
SELECT 'BIG_HAND', '大牌奖励', '🃏', '打出四条及以上，领取 2 USDT 奖励', '', 2000000, 1, 1, 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM activity WHERE code = 'BIG_HAND');

INSERT INTO activity (code, name, icon, description, link, reward_amount, daily_limit, enabled, sort_no)
SELECT 'REFERRAL', '推荐红包', '🎁', '好友通过你的邀请码注册并打过一手牌，你可得 1 USDT', '', 1000000, 1, 1, 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM activity WHERE code = 'REFERRAL');

INSERT INTO activity (code, name, icon, description, link, reward_amount, daily_limit, enabled, sort_no)
SELECT 'LUCKY_DICE', '幸运骰子', '🎲', '每天一次，掷出 0.1 ~ 1 USDT', '', 0, 1, 1, 4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM activity WHERE code = 'LUCKY_DICE');
