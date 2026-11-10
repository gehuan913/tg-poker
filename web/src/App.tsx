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

export function App() {
  const { booted, bootError, boot, toast, leaveTable } = useApp();
  const [hash, setHash] = useState(() => location.hash.replace(/^#/, '') || 'lobby');

  useEffect(() => { boot(); }, []);

  useEffect(() => {
    const onChange = () => setHash(location.hash.replace(/^#/, '') || 'lobby');
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  useEffect(() => {
    if (!hash.startsWith('table/')) leaveTable();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hash]);

  // BackButton 单例：只在二级页显示，回大厅时隐藏
  useEffect(() => {
    const isRoot = TABS.some(t => t.key === hash);
    if (isRoot) {
      setBackHandler(null);
    } else {
      setBackHandler(() => { location.hash = '#lobby'; });
    }
    return () => setBackHandler(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hash]);

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

  const isTable = hash.startsWith('table/');
  const isNiuniu = hash.startsWith('niuniu/');
  const fullscreen = isTable || isNiuniu;

  return (
    <div className="app">
      <div className={'page' + (fullscreen ? ' no-tab' : '')}>
        {isTable && <TablePage tableId={hash.split('/')[1]} />}
        {isNiuniu && <Niuniu tableId={hash.split('/')[1]} />}
        {!fullscreen && hash === 'lobby' && <Lobby />}
        {!fullscreen && hash === 'me' && <MePage />}
        {!fullscreen && hash === 'wallet' && <Wallet />}
        {!fullscreen && hash === 'learn' && <Learn />}
        {!fullscreen && hash === 'club' && <Club />}
        {!fullscreen && hash === 'activity' && <Activity />}
      </div>

      {!fullscreen && (
        <nav className="tabbar">
          {TABS.map(t => (
            <div
              key={t.key}
              className={'tab' + (hash === t.key ? ' active' : '')}
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
