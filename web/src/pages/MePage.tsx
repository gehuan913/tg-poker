import { useEffect, useState } from 'react';
import { api } from '../api.ts';
import { useApp } from '../store.ts';
import { addToHomeScreen, canAddToHomeScreen, haptic, isMock, openLink } from '../tg.ts';
import type { HandItem, StatsBlock } from '../types.ts';
import { fmt, fmtSigned } from '../utils.ts';
import { Modal } from '../components/Modal.tsx';

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

  const [panel, setPanel] = useState<'' | 'notify' | 'desktop' | 'help' | 'pwd'>('');
  const [cfg, setCfg] = useState<any>(null);
  const [pwd, setPwd] = useState('');
  const [prefs, setPrefs] = useState<Record<string, boolean>>(() => {
    try {
      return JSON.parse(localStorage.getItem('prefs') || '{}');
    } catch {
      return {};
    }
  });

  useEffect(() => {
    api<any>('/api/app/config').then(setCfg).catch(() => setCfg(null));
  }, []);

  const savePref = (k: string, v: boolean) => {
    const p = { ...prefs, [k]: v };
    setPrefs(p);
    localStorage.setItem('prefs', JSON.stringify(p));
    haptic();
  };
  const on = (k: string, dft = true) => (prefs[k] === undefined ? dft : prefs[k]);

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
          <Quick
            label="客服"
            onClick={() => {
              haptic();
              if (cfg?.supportUrl) openLink(cfg.supportUrl);
              else showToast('客服地址未配置');
            }}
          />
        </div>
      </div>

      <div className="card">
        <div className="card-title">更多</div>
        <div className="list-item" onClick={() => { haptic(); setPanel('notify'); }}>
          <div style={{ flex: 1 }}>通知</div>
          <div className="small muted">活动与开局提醒 ›</div>
        </div>
        <div className="list-item" onClick={() => { haptic(); setPanel('desktop'); }}>
          <div style={{ flex: 1 }}>桌面设置</div>
          <div className="small muted">音效 / 震动 / 添加到桌面 ›</div>
        </div>
        <div className="list-item" onClick={() => { haptic(); setPanel('pwd'); }}>
          <div style={{ flex: 1 }}>支付密码</div>
          <div className="small muted">{me?.hasPayPwd ? '已设置 ›' : '未设置 ›'}</div>
        </div>
        <div className="list-item" onClick={() => { haptic(); setPanel('help'); }}>
          <div style={{ flex: 1 }}>帮助中心</div>
          <div className="small muted">常见问题 ›</div>
        </div>
      </div>

      {panel === 'notify' && (
        <Modal title="通知" onClose={() => setPanel('')} okText="关闭" onOk={() => undefined}>
          <div className="list-item">
            <div style={{ flex: 1 }}>
              <div>活动通知</div>
              <div className="small muted">大牌奖励、幸运骰子可领时提醒</div>
            </div>
            <button className={'btn sm' + (on('notifyActivity') ? ' primary' : '')} onClick={() => savePref('notifyActivity', !on('notifyActivity'))}>
              {on('notifyActivity') ? '已开启' : '已关闭'}
            </button>
          </div>
          <div className="list-item">
            <div style={{ flex: 1 }}>
              <div>开局提醒</div>
              <div className="small muted">常坐的牌桌人齐时提醒</div>
            </div>
            <button className={'btn sm' + (on('notifyStart') ? ' primary' : '')} onClick={() => savePref('notifyStart', !on('notifyStart'))}>
              {on('notifyStart') ? '已开启' : '已关闭'}
            </button>
          </div>
          <div className="small muted" style={{ marginTop: 8 }}>
            当前仅在本机生效；接入 Bot 推送后改为服务端下发。
          </div>
        </Modal>
      )}

      {panel === 'desktop' && (
        <Modal title="桌面设置" onClose={() => setPanel('')} okText="关闭" onOk={() => undefined}>
          <div className="list-item">
            <div style={{ flex: 1 }}>音效</div>
            <button className={'btn sm' + (on('sound') ? ' primary' : '')} onClick={() => savePref('sound', !on('sound'))}>
              {on('sound') ? '开' : '关'}
            </button>
          </div>
          <div className="list-item">
            <div style={{ flex: 1 }}>震动反馈</div>
            <button className={'btn sm' + (on('vibrate') ? ' primary' : '')} onClick={() => savePref('vibrate', !on('vibrate'))}>
              {on('vibrate') ? '开' : '关'}
            </button>
          </div>
          <div className="list-item">
            <div style={{ flex: 1 }}>
              <div>添加到桌面</div>
              <div className="small muted">Android 需 Telegram 8.0+</div>
            </div>
            <button
              className="btn sm"
              disabled={!canAddToHomeScreen()}
              onClick={() => { haptic(); addToHomeScreen(); }}
            >
              {canAddToHomeScreen() ? '添加' : '不支持'}
            </button>
          </div>
        </Modal>
      )}

      {panel === 'pwd' && (
        <Modal
          title={me?.hasPayPwd ? '重置支付密码' : '设置支付密码'}
          onClose={() => setPanel('')}
          onOk={async () => {
            try {
              await api('/api/me/pay-password', { method: 'POST', body: { pwd } });
              await refreshMe();
              showToast('支付密码已设置');
              setPwd('');
              return true;
            } catch (e: any) {
              showToast(e.message);
              return false;
            }
          }}
        >
          <div className="field">
            <label>6 位数字（不能是连续或重复数字）</label>
            <input
              type="password"
              value={pwd}
              inputMode="numeric"
              maxLength={6}
              onChange={e => setPwd(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="******"
            />
          </div>
          <div className="small muted">设置后，提现需要输入该密码。忘记只能重置。</div>
        </Modal>
      )}

      {panel === 'help' && (
        <Modal title="帮助中心" onClose={() => setPanel('')} okText="关闭" onOk={() => undefined}>
          <div className="help-item">
            <b>买入什么时候扣钱？</b>
            <div className="small muted">上桌时从钱包扣，离桌自动退回；手牌进行中离桌会先弃牌，筹码等本手结束再退。</div>
          </div>
          <div className="help-item">
            <b>短牌和长牌有什么区别？</b>
            <div className="small muted">短牌 36 张（去掉 2~5）：同花大于葫芦、三条大于顺子，最小顺子 A6789。</div>
          </div>
          <div className="help-item">
            <b>保险怎么买？</b>
            <div className="small muted">只在转牌圈（河牌前）且有人 All-in 亮牌时提供，由当前领先方投保，保费从钱包扣。</div>
          </div>
          <div className="help-item">
            <b>怎么验证发牌没被改？</b>
            <div className="small muted">牌桌右上角复制存证号，结算后用 verify.js 离线复算；站内「可存证」也能看 seed 与 hash。</div>
          </div>
          <div className="help-item">
            <b>提现多久到账？</b>
            <div className="small muted">1–10 分钟，手续费 1 USDT，最小 10 USDT。当前未接链上签名，状态停在审核中。</div>
          </div>
        </Modal>
      )}

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
