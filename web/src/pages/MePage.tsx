import { useEffect, useState } from 'react';
import { api } from '../api.ts';
import { useApp } from '../store.ts';
import { addToHomeScreen, canAddToHomeScreen, haptic, isMock } from '../tg.ts';
import type { HandItem, StatsBlock } from '../types.ts';
import { fmt, fmtSigned } from '../utils.ts';

type Tab = 'LONG' | 'SHORT' | 'NIUNIU';

const TABS: { key: Tab; label: string }[] = [
  { key: 'LONG', label: '长牌' },
  { key: 'SHORT', label: '短牌' },
  { key: 'NIUNIU', label: '牛牛' }
];

export function MePage() {
  const { me, refreshMe, showToast } = useApp();
  const [hands, setHands] = useState<HandItem[]>([]);
  const [stats, setStats] = useState<Record<string, StatsBlock> | null>(null);
  const [tab, setTab] = useState<Tab>('LONG');

  useEffect(() => {
    refreshMe();
    api<HandItem[]>('/api/me/hands?limit=20').then(setHands).catch(() => setHands([]));
    api<Record<string, StatsBlock>>('/api/me/stats').then(setStats).catch(() => setStats(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const s = stats?.[tab];

  return (
    <>
      <div className="topbar">
        <div className="club-name">我的</div>
        <div className="row" style={{ gap: 6 }}>
          <button className="btn sm ghost" onClick={() => { location.hash = '#wallet'; }}>钱包</button>
          <button
            className="btn sm ghost"
            onClick={() => { haptic(); canAddToHomeScreen() ? addToHomeScreen() : showToast('当前客户端不支持添加到桌面'); }}
          >
            桌面
          </button>
        </div>
      </div>

      <div className="card row">
        <div className="row" style={{ gap: 12 }}>
          <div className="avatar">
            {me?.avatar ? <img src={me.avatar} alt="" /> : (me?.nick || '玩')[0]}
          </div>
          <div>
            <div style={{ fontSize: 15, fontWeight: 600 }}>{me?.nick || ''}</div>
            <div className="small muted">ID {me?.userId || ''}{me?.vipLevel ? ' · VIP' + me.vipLevel : ''}</div>
          </div>
        </div>
        <div className="gold mono">{fmt(me?.balance || 0)}</div>
      </div>

      <div className="card">
        <div className="seg" style={{ marginBottom: 12 }}>
          {TABS.map(t => (
            <div key={t.key} className={'seg-item' + (tab === t.key ? ' active' : '')} onClick={() => { haptic(); setTab(t.key); }}>
              {t.label}
            </div>
          ))}
        </div>
        <div className="row">
          <Stat value={s?.netText || '0'} label="总输赢" color={(s?.net || 0) >= 0 ? 'green' : 'red'} />
          <Stat value={String(s?.hands || 0)} label={tab === 'NIUNIU' ? '局数' : '手牌数'} />
          <Stat value={s?.avgText || '0'} label="每局均值" />
        </div>
        <div className="divider" />
        <div className="row">
          {tab === 'NIUNIU' ? (
            <>
              <Stat value={(s?.winRate ?? 0) + '%'} label="胜率" />
              <Stat value={String(s?.bankerRounds || 0)} label="坐庄局数" />
              <Stat value={String((s?.rounds || 0) - (s?.bankerRounds || 0))} label="闲家局数" />
            </>
          ) : (
            <>
              <Stat value={(s?.vpip ?? 0) + '%'} label="入池率 VPIP" />
              <Stat value={(s?.pfr ?? 0) + '%'} label="加注率 PFR" />
              <Stat value={s?.insuranceNetText || '0'} label="保险净额" color={(s?.net || 0) >= 0 ? 'green' : 'red'} />
            </>
          )}
        </div>
        <div className="small muted" style={{ marginTop: 8 }}>
          {tab === 'NIUNIU'
            ? '牛牛按"局 × 人"统计：一局里庄家与每个闲家分别结算，平局不计入胜率。'
            : '总输赢 = 牌局输赢 + 保险赔付 − 保费。小盲补齐计 VPIP，大盲过牌不计。'}
        </div>
      </div>

      <div className="card">
        <div className="card-title">最近牌局</div>
        {hands.length === 0 && <div className="empty">还没有打过牌</div>}
        {hands.map((h, i) => (
          <div className="list-item" key={h.handId + '-' + i}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="small ellipsis">{h.handId}</div>
              <div className="small muted">
                {h.variant === 'SHORT' ? '短牌' : '长牌'} · 座位 {h.seat} · {h.holeCards || '-'}
                {h.board ? ' · ' + h.board : ''}
              </div>
            </div>
            <div className={'mono ' + (h.net >= 0 ? 'green' : 'red')}>{fmtSigned(h.net)}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <div className="card-title">偏好</div>
        <div className="list-item">
          <div style={{ flex: 1 }}>
            <div>参与保险</div>
            <div className="small muted">关闭后牌桌不报价、不能投保</div>
          </div>
          <button
            className={'btn sm' + (me?.insuranceOptIn === false ? '' : ' primary')}
            onClick={async () => {
              try {
                await api('/api/me/insurance-opt', { method: 'POST', body: { on: me?.insuranceOptIn === false } });
                await refreshMe();
                showToast(me?.insuranceOptIn === false ? '已开启保险' : '已关闭保险');
              } catch (e: any) {
                showToast(e.message);
              }
            }}
          >
            {me?.insuranceOptIn === false ? '已关闭' : '已开启'}
          </button>
        </div>
      </div>

      <div className="card">
        <div className="card-title">快捷</div>
        <div className="grid4">
          <Quick label="账单" onClick={() => { location.hash = '#wallet'; }} />
          <Quick label="收藏" onClick={() => { location.hash = '#learn'; }} />
          <Quick label="邀请" onClick={() => { location.hash = '#activity'; }} />
          <Quick label="客服" onClick={() => showToast('客服 Bot 待接入')} />
        </div>
      </div>

      {isMock() && (
        <div className="small muted" style={{ textAlign: 'center', marginTop: 8 }}>
          浏览器调试模式：身份为本地模拟，非 Telegram 账号
        </div>
      )}
    </>
  );
}

function Stat({ value, label, color }: { value: string; label: string; color?: string }) {
  return (
    <div style={{ textAlign: 'center', flex: 1 }}>
      <div className={'mono ' + (color || '')} style={{ fontSize: 16 }}>{value}</div>
      <div className="small muted">{label}</div>
    </div>
  );
}

function Quick({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <div className="quick" onClick={onClick}>
      <div className="quick-dot">{label[0]}</div>
      <div className="small">{label}</div>
    </div>
  );
}
