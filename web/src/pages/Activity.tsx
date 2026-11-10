import { useEffect, useState } from 'react';
import { api } from '../api.ts';
import { useApp } from '../store.ts';
import { haptic, openLink } from '../tg.ts';
import type { ActivityItem } from '../types.ts';

export function Activity() {
  const { refreshMe } = useApp();
  const [list, setList] = useState<ActivityItem[]>([]);
  const [invite, setInvite] = useState<any>(null);
  const [msg, setMsg] = useState('');

  useEffect(() => {
    load();
    api<any>('/api/activity/invite-code').then(setInvite).catch(() => setInvite(null));
  }, []);

  function load() {
    api<ActivityItem[]>('/api/activity').then(setList).catch(() => setList([]));
  }

  function toast(m: string) {
    setMsg(m);
    window.setTimeout(() => setMsg(''), 2400);
  }

  async function claim(item: ActivityItem) {
    haptic();
    if (item.code === 'VIP_GROUP' && item.link) {
      openLink(item.link);
    }
    try {
      const r = await api<any>('/api/activity/claim', { method: 'POST', body: { code: item.code } });
      toast(r.message || '已领取');
      await refreshMe();
      load();
    } catch (e: any) {
      toast(e.message);
    }
  }

  return (
    <>
      <div className="topbar">
        <div className="club-name">活动</div>
        <div className="small muted">四个活动全部配置化</div>
      </div>

      {list.length === 0 && <div className="empty">暂无活动</div>}

      {list.map(a => (
        <div className="card" key={a.code}>
          <div className="row">
            <div className="row" style={{ gap: 10 }}>
              <div className="act-icon">{a.icon || '🎁'}</div>
              <div>
                <div style={{ fontWeight: 600 }}>{a.name}</div>
                <div className="small muted">{a.description}</div>
              </div>
            </div>
            <button className="btn sm primary" disabled={a.claimed} onClick={() => claim(a)}>
              {a.claimed ? '今日已领' : a.code === 'VIP_GROUP' ? '进入' : '领取'}
            </button>
          </div>
        </div>
      ))}

      {invite && (
        <div className="card">
          <div className="card-title">我的邀请码</div>
          <div className="row">
            <div className="mono gold" style={{ fontSize: 20 }}>{invite.code}</div>
            <button
              className="btn sm"
              onClick={() => {
                navigator.clipboard?.writeText(invite.code).then(() => toast('邀请码已复制'));
              }}
            >
              复制
            </button>
          </div>
          <div className="small muted" style={{ marginTop: 8 }}>
            好友注册时填你的邀请码，并打过一手牌，你可领 1 USDT 推荐红包
          </div>
        </div>
      )}

      {msg && <div className="toast">{msg}</div>}
    </>
  );
}
