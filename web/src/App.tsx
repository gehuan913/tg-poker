import { useEffect, useState } from 'react';
import { useApp } from './store.ts';
import { setBackHandler } from './tg.ts';
import { Lobby } from './pages/Lobby.tsx';
import { TablePage } from './pages/TablePage.tsx';
import { Wallet } from './pages/Wallet.tsx';
import { MePage } from './pages/MePage.tsx';
import { Learn } from './pages/Learn.tsx';
import { Club } from './pages/Club.tsx';
import { Activity } from './pages/Activity.tsx';
import { Niuniu } from './pages/Niuniu.tsx';

const TABS = [
  { key: 'lobby', icon: '🏛', label: '大厅' },
  { key: 'learn', icon: '📘', label: '教学' },
  { key: 'club', icon: '👥', label: '俱乐部' },
  { key: 'activity', icon: '🎁', label: '活动' },
  { key: 'me', icon: '👤', label: '我的' }
];

const TABS_KEY = new Set(TABS.map(t => t.key).concat('wallet'));

/**
 * 从地址栏解析路由。
 *
 * <p><b>坑</b>：Telegram 打开 Mini App 时会在 URL 末尾追加
 * {@code #tgWebAppData=query_id%3D...&tgWebAppVersion=8.0&tgWebAppPlatform=...}。
 * 这段不是业务路由，若直接当作路由名，会匹配不到任何页面，
 * 表现就是"只有底部导航、上面一片空白，点一下 tab 才出来"。
 * 浏览器直接访问时没有这段，所以本地看不出问题。</p>
 *
 * <p>所以：空值、含 "=" 的、tgWebApp 开头的，一律回落到大厅。</p>
 */
function routeFromHash(): string {
  const raw = location.hash.replace(/^#/, '');
  if (!raw || raw.includes('=') || raw.startsWith('tgWebApp')) return 'lobby';
  return raw;
}

export function App() {
  const { booted, bootError, boot, toast, leaveTable } = useApp();
  const [hash, setHash] = useState(routeFromHash);

  useEffect(() => { boot(); }, []);

  // 把 Telegram 塞进地址栏的参数清掉，避免刷新/重开时又被当成路由
  useEffect(() => {
    if (location.hash.includes('=')) {
      history.replaceState(null, '', location.pathname + location.search);
    }
  }, []);

  useEffect(() => {
    const onChange = () => setHash(routeFromHash());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  // 未知路由兜底到大厅，保证任何情况下都有内容，而不是一片空白
  const route = hash.startsWith('table/') || hash.startsWith('niuniu/') || TABS_KEY.has(hash)
    ? hash
    : 'lobby';

  useEffect(() => {
    if (!route.startsWith('table/')) leaveTable();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [route]);

  // BackButton 单例：只在二级页显示，回大厅时隐藏
  useEffect(() => {
    const isRoot = TABS.some(t => t.key === route);
    if (isRoot) {
      setBackHandler(null);
    } else {
      setBackHandler(() => { location.hash = '#lobby'; });
    }
    return () => setBackHandler(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [route]);

  if (!booted) {
    return (
      <div className="app">
        <div className="boot">
          <div className="boot-logo">♠ ♥ ♣ ♦</div>
          <div className="boot-text">{bootError ? '连接失败：' + bootError : '正在连接牌桌…'}</div>
          {bootError && (
            <button className="btn" onClick={() => location.reload()}>重试</button>
          )}
        </div>
      </div>
    );
  }

  const isTable = route.startsWith('table/');
  const isNiuniu = route.startsWith('niuniu/');
  const fullscreen = isTable || isNiuniu;

  return (
    <div className="app">
      <div className={'page' + (fullscreen ? ' no-tab' : '')}>
        {isTable && <TablePage tableId={route.split('/')[1]} />}
        {isNiuniu && <Niuniu tableId={route.split('/')[1]} />}
        {!fullscreen && route === 'lobby' && <Lobby />}
        {!fullscreen && route === 'me' && <MePage />}
        {!fullscreen && route === 'wallet' && <Wallet />}
        {!fullscreen && route === 'learn' && <Learn />}
        {!fullscreen && route === 'club' && <Club />}
        {!fullscreen && route === 'activity' && <Activity />}
      </div>

      {!fullscreen && (
        <nav className="tabbar">
          {TABS.map(t => (
            <div
              key={t.key}
              className={'tab' + (route === t.key ? ' active' : '')}
              onClick={() => { location.hash = '#' + t.key; }}
            >
              <i>{t.icon}</i><span>{t.label}</span>
            </div>
          ))}
        </nav>
      )}

      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
