# tg-poker · Telegram 德州 / 短牌 / 牛牛 Mini App

前后端同目录单体工程：**Spring Boot 3.2.7 + JDK 21 + MyBatis-Plus + MySQL 8**，
前端 **React 19 + TypeScript + Vite 6**，构建产物直接输出到 `src/main/resources/static`。

业务范围对齐 `D:\sofa\buddy_space\tg-poker-miniapp\技术方案-TG德州竞技MiniApp.md`（v2.6）。

---

## 一、功能清单（对照技术方案）

| 方案章节 | 需求 | 落点 |
|---|---|---|
| §2.1 | 五个 Tab：大厅 / 教学 / 俱乐部 / 活动 / 我的 | `web/src/App.tsx` + `pages/*` |
| §2.3 | 大厅：玩法筛选、座位环、盲注/前注、保险标、入池率、运行时长 | `Lobby.tsx`、`TableActor.LobbyItem` |
| §2.4 | 桌内：动作标签、倒计时环、存证号、桌上三 Tab | `TablePage.tsx` |
| §2.4 | 桌内四快捷：笔记 / 聊天 / 表情 / 牌型参考 | `TablePage.tsx` + `TableActor.chat` |
| §2.5 | 钱包：一人一址充值、提现工单、红包 | `BlockchainService`、`RedPacketService` |
| §2.6 | 我的数据：长牌 / 短牌 / 牛牛三 Tab，总输赢 / 均注 / VPIP / PFR | `MeController`、`HandPlayerMapper.stats`、`NiuniuResultMapper` |
| §2.7 | 桌内账单 / 保险 / 红包三 Tab（桌级公开） | `/api/table/{id}/ledger`、`/insurance`、`/redpacket` |
| §5.2 | 1 桌 = 1 Actor，单线程串行 + 纯函数引擎 | `TableActor`、`TexasEngine` |
| §6 | 钱包适配层、幂等、对账、禁人工补单 | `BlockchainGateway`、`BlockchainService` |
| §7.1 | 德州状态机、All-in runout、超时 | `TexasEngine` |
| §7.2 | 盲注三档 + 前注制（按钮 2 倍）+ Straddle（UTG 2BB） | `TableConfig.withAnte/withStraddle`、`TexasEngine.startHand` |
| §7.3 | 边池分层 | `PotBuilder` |
| §7.4 | 短牌 36 张 + 独立牌型表 + A6789 | `Variant`、`Deck`、`HandEvaluator` |
| §7.5 | 牛牛 1 庄 3 闲、倍数、排队上庄/连庄/下庄/选庄/补庄 | `NiuniuEngine`、`NiuniuRoom.resolveBanker` |
| §7.6 | 保险：2~4 人 All-in、outs 赔率、保费=保额÷赔率、金库承保、玩家开关 | `InsuranceOdds.quoteMulti`、`TableActor.buyInsurance` |
| §8.5 | 旁观者角色、裁剪下发、0.5s 批量合并 | `TableManager` |
| §7.1 | PAUSED / DISBANDED 旁路状态（运营暂停/恢复/解散） | `TableActor.pause/resume/disband` |
| §9 | 双种子发牌、存证号、分层公开、开源验证脚本 | `Deck`、`FairService`、`web/public/verify.js` |
| §10 | 教学：知识 / 工具（胜率计算器）/ 训练 / 我的 | `TeachingService`、`EquityCalculator`、`Learn.tsx` |
| §11 | 风控事件表、桌内公开统计 | `risk_event`、`ledger` |
| §12 | 数据模型、MySQL 迁移注意点 | `schema.sql` |

---

## 二、启动

### 1. 后端

双击 `run.bat`，或：

```bat
set JAVA_HOME=D:\jdk21
mvn -DskipTests package
java -jar target\tg-poker.jar --server.port=8085
```

> **必须显式带 `--server.port=8085`**。本机环境存在 `SERVER__PORT` 变量，
> Spring 会把它当成 `server.port`，不覆盖会跟沙箱服务抢端口。

浏览器打开 `http://localhost:8085` 即可试玩（浏览器模式自动建号、送 1000 USDT）。

### 2. 前端（改 UI 时）

```bat
cd web
npm install
npm run build      :: 产物直接进 src/main/resources/static
npm run dev        :: 或起 Vite 开发服务器（需自行配 proxy）
```

> `src/main/resources/static` 是构建产物，已在 `.gitignore` 中排除，仓库里只保留 `web/` 源码。
> 新克隆仓库后先执行一次 `npm run build`（`run.bat` 检测到产物缺失会自动帮你构建），
> 否则打包出的 jar 里没有页面。

### 3. 数据库

默认连本地 MySQL（`127.0.0.1:3306/tgpoker`，root/root），**应用启动不建表、不插数据**，
建表和种子数据都在 `db/` 下手动跑一次：

```bat
:: 1) 建库建表
mysql -uroot -p < db\schema-mysql.sql
:: 2) 灌种子数据（幂等，可重复执行）
mysql -uroot -p < db\data-mysql.sql
:: 3) 直接启动，默认走 mysql profile
java -jar target\tg-poker.jar --server.port=8085
```

| 文件 | 用途 |
|---|---|
| `db/schema-mysql.sql` | MySQL 8.4 建库建表，索引写在建表语句内，`ENGINE=InnoDB` |
| `db/data-mysql.sql` | 教学课程 / 训练题库 / 活动配置种子数据，带 `NOT EXISTS` 幂等判断 |

**所有表、所有字段都有 COMMENT**，可直接在 Navicat / DBeaver 里看到中文说明。

> `explain` 是 MySQL 保留字，`quiz_question` 里该列名必须写成 `` `explain` ``。

> **改过表结构后**，同步更新 `db/schema-mysql.sql`。启动时 `spring.sql.init` 会执行它
> （全部 `IF NOT EXISTS`，可重复执行），不需要手动删库。

---

## 三、接 Telegram

Token **不入库**，写在 `src/main/resources/application-local.yml`（已被 `.gitignore` 排除），
或用环境变量 `TG_BOT_TOKEN` 注入：

```yaml
# application-local.yml
app:
  tg:
    bot-token: "<BotFather 发放的 token>"
    bot-username: momofayu123
    mini-app-url: "https://xxx.trycloudflare.com"
```

启动后 Bot 会自动：设置命令列表（`/start` `/help` `/invite` `/fair`）、注册长轮询。
在 TG 里搜 **@momofayu123** 发 `/start` 即可。

> 仓库启用 GitHub secret scanning，明文 token / AWS key 会被 push protection 直接拒绝推送。

**Mini App 需要 HTTPS。** 本地跑（不上云）用临时隧道即可：

```bat
cloudflared tunnel --url http://localhost:8085
```

把拿到的 `https://xxx.trycloudflare.com` 填进 `app.tg.mini-app-url` 并重启，
Bot 才会显示「🎮 开始游戏」按钮（Telegram 会直接拒收 http:// 地址）。

**网络**：国内直连 `api.telegram.org` 经常超时。启动日志里出现
`Telegram Bot 启动失败：Unable to execute deleteWebhook method` 就是连不上，
在 `application.yml` 配代理即可（Bot 会自动每分钟重试，最多 10 次）：

```yaml
app.tg.proxy-host: 127.0.0.1
app.tg.proxy-port: 7890
app.tg.proxy-type: HTTP   # 或 SOCKS
```

### 上线前必须改

| 配置 | 值 | 原因 |
|---|---|---|
| `app.dev-mode` | `false` | 否则任何人都能用 devId 冒充任意用户 |
| `app.tg.mini-app-url` | 正式 HTTPS 域名 | Mini App 入口 |
| `app.blockchain.ready` | 接平台后 `true` | 现在地址是模拟的，不能真充 |
| `app.blockchain.callback-secret` | 环境变量注入 | 回调验签 |
| `app.tg.proxy-*` | 按部署环境 | 服务器能直连就留空 |

---

## 四、可验证发牌怎么验

1. 牌桌右上角复制**存证号**（如 `1#9E18D6BB2C`）。
2. 结算后点「可存证」，拿到 `serverSeed`、玩家种子、nonce。
3. 离线复算：

```bash
node verify.js <serverSeed> "<clientSeeds>" <nonce> LONG 6
```

输出会打印 `sha256(serverSeed)` 与完整牌序；与平台记录不一致即为作弊。

算法（服务端与脚本一字不差）：

```
secret = HMAC_SHA256(key="WebAppData", message=botToken)   # 这是 initData 验签，别混
seed   = HMAC_SHA256(serverSeed, clientSeeds + ":" + nonce)
deck   = FisherYates(标准牌序, HMAC-DRBG(seed))
```

**分层公开**（§9.4）：L1 结论一致 ✓ / L2 公开 seed 可复算 / L3 含他人底牌的完整牌序不主动展示，
仅纠纷取证 / L4 延迟 30 天全量可查。

---

## 五、关键接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | initData 验签（HMAC 方向已修正）+ auth_date 时效校验 |
| GET | `/api/lobby/tables?clubId=&variant=` | 大厅，`LONG` / `SHORT` |
| GET | `/api/table/{id}` | 按查看者裁剪的牌桌视图 |
| POST | `/api/table/{id}/sit` `/rebuy` `/leave` `/action` | 入座 / 补码 / 离桌 / 行动 |
| GET | `/api/table/{id}/ledger` | 桌内账单（买入仅本人可见） |
| GET | `/api/table/{id}/insurance/quote` | 保险报价（outs / 赔率 / 保费） |
| POST | `/api/table/{id}/insurance` | 买入保险（从场外钱包扣保费） |
| POST | `/api/table/{id}/redpacket` | 桌内红包 |
| GET | `/api/fair/table/{id}` | 当前手承诺（hash + 存证号） |
| POST | `/api/fair/client-seed` | 提交玩家种子（下一手生效） |
| GET | `/api/fair/hand/{handId}/verify` | 站内复算 |
| GET | `/api/club/mine` `/search` `/{id}` | 俱乐部 |
| GET | `/api/activity` / POST `/claim` | 四个活动（配置化） |
| GET | `/api/teach/lessons` `/quiz` `/equity` | 教学：知识 / 训练 / 胜率计算器 |
| GET | `/api/me` `/stats` `/hands` | 我的数据（长牌 / 短牌分开统计） |
| GET | `/api/wallet/deposit-address` | 一人一址（TRON） |
| POST | `/api/wallet/withdraw` | 提现工单 |
| GET | `/api/niuniu/rooms` `/{id}` | 牛牛桌 |
| POST | `/api/niuniu/{id}/sit` `/bet` `/leave` | 牛牛入座 / 下注 / 离桌 |

开发辅助（生产用网关屏蔽）：`/api/dev/insurance-quote`、`/api/dev/rank`、`/api/dev/equity`。

---

## 六、目录

```
src/main/java/com/tgpoker
├── auth/        initData 验签、登录、拦截器
├── bot/         Telegram Bot（长轮询）
├── config/      Web/WebSocket 配置
├── engine/      牌型、牌堆、边池、德州状态机、胜率计算
├── niuniu/      牛牛引擎 + 房间 + 接口
├── room/        1 桌 1 Actor、视图裁剪、保险、广播
├── service/     钱包、充提、俱乐部、活动、教学、公平性、红包
├── mapper/      MyBatis-Plus
├── model/       实体
└── web/         REST 控制器

web/
├── public/      theme.css、miniapp-native.css、verify.js
└── src/         React 页面与 Telegram 适配层
```

---

## 七、工程约定

这几条是硬约定，后面加代码请照做：

1. **复杂 SQL 一律写在 XML**：`src/main/resources/mapper/*.xml`，接口只留方法签名 + Javadoc。
   注解里塞多行 SQL 拼接不可读，也难让 DBA 审。
   `mybatis-plus.mapper-locations=classpath*:mapper/*.xml` 已经在配置里。
2. **每个字段都要有 COMMENT**：新加表或字段时，同步补 `schema.sql` 与 `db/schema-mysql.sql`。
3. **每个类、每个 public 方法都要有说明**：写"为什么"而不是"是什么"，
   例如"保费从场外钱包扣，因为 All-in 后桌上可能没钱"。
4. **金额只用 long 存 micro-USDT**，禁止 double/BigDecimal 做加减。
5. **状态机保持纯函数**：`TexasEngine` 里不碰 DB、不碰网络，副作用由 `TableActor` 统一执行，
   这样每手牌才能离线重放。
6. **不要用客户端传的 role / 余额 / 输赢**：一切以服务端状态为准。

---

## 八、已知边界（不是 bug，是范围）

| 项 | 现状 | 后续 |
|---|---|---|
| 链上充提 | `BlockchainGateway` 为桩实现，`ready=false` | 换公司平台实现类，业务代码不动 |
| 保险 | 仅支持两人单挑、转牌圈、All-in 亮牌 | 多人池按 2/3/4 人分档赔率 |
| 旁观者 | 已做角色隔离与 0.5s 节流，未做聊天 | 旁观聊天要做敏感词与限频 |
| 前端动画 | DOM + CSS，无 PixiJS | 手感打磨放到二期 |
| 后台管理 | 无 UI，配置走 SQL / 接口 | 提现审核、封号、活动配置 |
| KYC / 音视频 | 未做 | 牌照申请后按监管要求补 |
| 牛牛 | 下注窗口 8s、庄家自动轮换 | 抢庄 / 连庄 / 补庄 / 选庄 |

---

## 九、踩过的坑（再犯会很痛）

1. **initData 的 HMAC 方向**：`secret = HMAC(key="WebAppData", message=bot_token)`。
   写成反过来，本地测不出来，线上所有真实用户登录 401。
2. **不要相信客户端传的 role**：服务端按真实座位判定，客户端只能"降级为旁观"。
3. **短牌的 category 不能复用长牌常量**：长牌 `FLUSH=5/FULL_HOUSE=6`，短牌恰好相反。
4. **MySQL 默认隔离级别是 REPEATABLE READ**：连接初始化要降到 READ COMMITTED，否则间隙锁容易把并发行锁升级成死锁。
5. **同一笔 tx_hash 只能入账一次**：靠唯一索引，不靠业务判断。
6. **`SERVER__PORT` 会覆盖 `server.port`**：启动参数显式指定。
