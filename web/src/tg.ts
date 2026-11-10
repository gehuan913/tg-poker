/**
 * Telegram Mini App 适配层。
 *
 * 三条硬规则（踩过坑，别改）：
 * 1. 不在 Telegram 里（没有 window.Telegram）时，业务降级为"浏览器本地账号"，不假装是 TG 用户。
 * 2. 高度一律用 viewportStableHeight 写进 --app-height，绝不用 100vh（键盘弹出会跳）。
 * 3. BackButton 只允许一个 handler，注册前必须先 offClick，否则返回键会连点多次。
 */

type TgWebApp = any;

declare global {
  interface Window {
    Telegram?: { WebApp: TgWebApp };
  }
}

const tg = (): TgWebApp | undefined => window.Telegram?.WebApp;

/** 只要拿到 initData，就说明真的在 Telegram 里（浏览器不会注入这个字段）。 */
function reallyInTelegram(t: TgWebApp | undefined): boolean {
  if (!t) return false;
  return Boolean(t.initData);
}

export interface TgInfo {
  initData: string;
  devId: number;
  devName: string;
  inTelegram: boolean;
  startParam: string;
  platform: string;
  version: string;
  colorScheme: 'light' | 'dark';
}

/** 主题色：竞品是深色牌桌，这里固定深色，但把 Telegram 的变量同步过去。 */
const DARK = {
  bg: '#0d1117',
  surface: '#161b22',
  text: '#e6edf3',
  hint: '#8b949e',
  button: '#2f81f7',
  buttonText: '#ffffff'
};

export function initTelegram(): TgInfo {
  const t = tg();
  const inTelegram = reallyInTelegram(t);

  if (t) {
    try {
      t.ready();
      t.expand();
      // 头部/底部与页面背景一致，避免上下出现浅色条
      t.setHeaderColor?.(DARK.bg);
      t.setBackgroundColor?.(DARK.bg);
      // Bot API 7.7+：禁止纵向滑动手势关闭，牌桌里必须禁，否则下注时容易误关
      if (t.isVersionAtLeast?.('7.7')) t.disableVerticalSwipes?.();
    } catch {
      /* 老客户端没有这些方法 */
    }
    applyViewport(t);
    applyTheme(t);
    bindViewportEvents(t);
    nudgeLayout(t);
  }

  const startParam: string = (inTelegram && (t?.initDataUnsafe?.start_param as string)) || '';

  if (inTelegram && t.initData) {
    return {
      initData: t.initData,
      devId: 0,
      devName: '',
      inTelegram: true,
      startParam,
      platform: t.platform || 'unknown',
      version: t.version || '0',
      colorScheme: (t.colorScheme as 'light' | 'dark') || 'dark'
    };
  }

  // 浏览器调试：稳定的 devId，刷新不换号
  let devId = Number(localStorage.getItem('tgp_dev_id') || '0');
  if (!devId) {
    devId = 900000000 + Math.floor(Math.random() * 999999);
    localStorage.setItem('tgp_dev_id', String(devId));
  }
  const devName = localStorage.getItem('tgp_dev_name') || '游客' + String(devId).slice(-4);

  return {
    initData: '',
    devId,
    devName,
    inTelegram: false,
    startParam,
    platform: 'web',
    version: '0',
    colorScheme: 'dark'
  };
}

/** 把稳定尺寸写进 CSS 变量，供 .app / .table-page 使用。 */
function applyViewport(t: TgWebApp) {
  const h = t.viewportStableHeight || t.viewportHeight || window.innerHeight;
  const w = t.viewportWidth || window.innerWidth || document.documentElement.clientWidth;
  const root = document.documentElement.style;
  root.setProperty('--app-height', h + 'px');
  if (w > 0) {
    root.setProperty('--app-width', w + 'px');
  }
  const top = t.contentSafeAreaInset?.top ?? t.safeAreaInset?.top ?? 0;
  const bottom = t.contentSafeAreaInset?.bottom ?? t.safeAreaInset?.bottom ?? 0;
  root.setProperty('--app-inset-top', top + 'px');
  root.setProperty('--app-inset-bottom', bottom + 'px');
}

/**
 * Telegram 的部分 WebView（尤其桌面版）首帧按 0 尺寸排版，
 * 之后窗口展开却不重排，表现是整页黑屏、只剩底部导航的一两个字，
 * 必须手动点一下才恢复。这里在启动后主动踢几次布局，让 WebView 重新测量。
 */
function nudgeLayout(t: TgWebApp) {
  const kick = () => {
    applyViewport(t);
    // 读一次布局属性，强制同步 reflow（不读的话浏览器会合并掉）
    void document.body.offsetWidth;
    void document.documentElement.clientWidth;
  };
  window.requestAnimationFrame(kick);
  [80, 200, 400, 800, 1500].forEach(ms => window.setTimeout(kick, ms));
}

function applyTheme(t: TgWebApp) {
  const p = t.themeParams || {};
  const root = document.documentElement.style;
  // Telegram 注入的是 --tg-theme-*，这里额外映射一套自己用的变量，
  // 保证深色牌桌在浅色系统主题下也不会出现白底黑字的割裂。
  root.setProperty('--app-bg', DARK.bg);
  root.setProperty('--app-surface', p.secondary_bg_color || DARK.surface);
  root.setProperty('--app-text', DARK.text);
  root.setProperty('--app-hint', p.hint_color || DARK.hint);
  root.setProperty('--app-button', p.button_color || DARK.button);
  root.setProperty('--app-button-text', p.button_text_color || DARK.buttonText);
}

function bindViewportEvents(t: TgWebApp) {
  t.onEvent?.('viewportChanged', () => applyViewport(t));
  t.onEvent?.('themeChanged', () => applyTheme(t));
  t.onEvent?.('safeAreaChanged', () => applyViewport(t));
  t.onEvent?.('contentSafeAreaChanged', () => applyViewport(t));
}

export function haptic(type: 'tap' | 'ok' | 'err' = 'tap') {
  const hf = tg()?.HapticFeedback;
  if (!hf) return;
  try {
    if (type === 'tap') hf.impactOccurred('light');
    else if (type === 'ok') hf.notificationOccurred('success');
    else hf.notificationOccurred('error');
  } catch {
    /* ignore */
  }
}

export function showAlert(message: string) {
  const t = tg();
  if (t?.showAlert) t.showAlert(message);
  else window.alert(message);
}

/* ------------------------------------------------------------------ 返回键 */

let backHandler: (() => void) | null = null;

/**
 * 单一 BackButton handler。
 * 传 null 隐藏。必须在页面切换时调用，否则会叠加（每次返回触发多次）。
 */
export function setBackHandler(fn: (() => void) | null) {
  const t = tg();
  if (!t?.BackButton) return;
  try {
    if (backHandler) t.BackButton.offClick(backHandler);
    backHandler = fn;
    if (fn) {
      t.BackButton.onClick(fn);
      t.BackButton.show();
    } else {
      t.BackButton.hide();
    }
  } catch {
    /* ignore */
  }
}

/* ------------------------------------------------------------------ 存储 */

export async function cloudGet(key: string): Promise<string | null> {
  const cs = tg()?.CloudStorage;
  if (!cs) return localStorage.getItem('cloud:' + key);
  return new Promise(resolve => {
    try {
      cs.getItem(key, (_e: unknown, v?: string) => resolve(v ?? null));
    } catch {
      resolve(null);
    }
  });
}

export async function cloudSet(key: string, value: string): Promise<void> {
  const cs = tg()?.CloudStorage;
  if (!cs) {
    localStorage.setItem('cloud:' + key, value);
    return;
  }
  return new Promise(resolve => {
    try {
      cs.setItem(key, value, () => resolve());
    } catch {
      resolve();
    }
  });
}

/* ------------------------------------------------------------------ 能力门控 */

/** 添加到桌面：Bot API 8.0+，老客户端静默失败。 */
export function canAddToHomeScreen(): boolean {
  const t = tg();
  return Boolean(t?.isVersionAtLeast?.('8.0') && t.addToHomeScreen);
}

export function addToHomeScreen() {
  const t = tg();
  if (!canAddToHomeScreen()) {
    showAlert('当前 Telegram 版本不支持，请升级到最新版');
    return;
  }
  try {
    t.addToHomeScreen();
  } catch {
    showAlert('添加失败，请稍后再试');
  }
}

/** openLink 必须由用户手势直接触发，不能在 setTimeout / await 之后调用。 */
export function openLink(url: string) {
  const t = tg();
  if (t?.openLink) t.openLink(url, { try_instant_view: false });
  else window.open(url, '_blank');
}

export function closeApp() {
  tg()?.close?.();
}

export function isMock(): boolean {
  // 没有真实 initData 就说明是普通浏览器（本地联调），不是 Telegram 环境
  return !Boolean(tg()?.initData);
}
