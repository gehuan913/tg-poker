import { useEffect, useState } from 'react';
import { api } from '../api.ts';
import { useApp } from '../store.ts';
import { fmt, toMicro } from '../utils.ts';
import { Modal } from '../components/Modal.tsx';
import { haptic } from '../tg.ts';
import type { ClubItem } from '../types.ts';

const VARIANTS = [
  { key: 'ALL', label: '全部' },
  { key: 'LONG', label: '长牌' },
  { key: 'SHORT', label: '短牌' },
  { key: 'NIUNIU', label: '牛牛' }
];

export function Lobby() {
  const { me, tables, loadLobby, refreshMe, showToast } = useApp();
  const [variant, setVariant] = useState('ALL');
  const [modal, setModal] = useState<'dep' | 'wd' | null>(null);
  const [amount, setAmount] = useState('10');
  const [address, setAddress] = useState('');
  const [payPwd, setPayPwd] = useState('');
  const [addressInfo, setAddressInfo] = useState<any>(null);
  const [club, setClub] = useState<ClubItem | null>(null);
  const [clubs, setClubs] = useState<ClubItem[]>([]);
  const [records, setRecords] = useState<any[] | null>(null);
  const [showBalance, setShowBalance] = useState(true);

  useEffect(() => {
    refreshMe();
    loadLobby(variant === 'NIUNIU' ? 'ALL' : variant, club?.id);
    api<ClubItem[]>('/api/club/mine')
      .then(list => {
        setClubs(list);
        if (list.length > 0 && !club) setClub(list[0]);
      })
      .catch(() => { setClubs([]); setClub(null); });
    const t = window.setInterval(() => loadLobby(variant === 'NIUNIU' ? 'ALL' : variant, club?.id), 5000);
    return () => window.clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [variant, club?.id]);

  async function openDeposit() {
    setAmount('10');
    setModal('dep');
    try {
      const info = await api<any>('/api/wallet/deposit-address');
      setAddressInfo(info);
    } catch {
      setAddressInfo(null);
    }
  }

  async function doDeposit() {
    const amt = toMicro(amount);
    if (amt < 10_000_000) { showToast('最小充值 10 USDT'); return false; }
    try {
      await api('/api/wallet/mock-deposit', { method: 'POST', body: { amount: amt } });
      showToast('充值成功');
      await refreshMe();
      return true;
    } catch (e: any) {
      showToast(e.message);
      return false;
    }
  }

  async function doWithdraw() {
    const amt = toMicro(amount);
    if (!address.trim()) { showToast('请填写 TRON 地址'); return false; }
    if (amt < 10_000_000) { showToast('最小提现 10 USDT'); return false; }
    try {
      await api('/api/wallet/withdraw', {
        method: 'POST',
        body: { amount: amt, address: address.trim(), payPwd }
      });
      showToast('提现申请已提交');
      await refreshMe();
      return true;
    } catch (e: any) {
      showToast(e.message);
      return false;
    }
  }

  const visible = tables;

  return (
    <>
      <div className="topbar">
        <div style={{ minWidth: 0 }}>
          {clubs.length > 1 ? (
            <select
              className="club-select"
              value={club?.id || 0}
              onChange={e => {
                haptic();
                const id = Number(e.target.value);
                setClub(clubs.find(c => c.id === id) || null);
              }}
            >
              {clubs.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          ) : (
            <div className="club-name">{club?.name || '永旺德州'}</div>
          )}
          <div className="club-sub">
            ID {club?.id || 88888} · 成员 {club?.members || 0} · {tables.reduce((a, t) => a + t.seated, 0)} 人在桌
          </div>
        </div>
        <div className="row" style={{ gap: 6 }}>
          {club && (
            <button
              className="btn sm ghost"
              onClick={async () => {
                haptic();
                try {
                  setRecords(await api<any[]>(`/api/club/${club.id}/records?limit=20`));
                } catch (e: any) {
                  showToast(e.message);
                }
              }}
            >
              记录
            </button>
          )}
          <button className="btn sm ghost" onClick={() => { haptic(); loadLobby(variant === 'NIUNIU' ? 'ALL' : variant, club?.id); }}>
            刷新
          </button>
        </div>
      </div>

      <div className="wallet-strip">
        <div>
          <div className="label">USDT 余额</div>
          <div className="amount mono" onClick={() => setShowBalance(v => !v)}>
            {showBalance ? fmt(me?.balance || 0) : '******'}
          </div>
        </div>
        <div className="actions">
          <button className="btn sm primary" onClick={openDeposit}>充币</button>
          <button className="btn sm" onClick={() => { setAmount('10'); setModal('wd'); }}>提币</button>
          <button className="btn sm ghost" onClick={() => { location.hash = '#wallet'; }}>红包</button>
        </div>
      </div>

      <div className="banner">🔒 双种子发牌 · 结算后可复算验证 · 存证号可直接复制</div>

      <div className="seg">
        {VARIANTS.map(v => (
          <div
            key={v.key}
            className={'seg-item' + (variant === v.key ? ' active' : '')}
            onClick={() => { haptic(); setVariant(v.key); }}
          >
            {v.label}
          </div>
        ))}
      </div>

      {variant === 'NIUNIU' && (
        <div className="table-item" onClick={() => { location.hash = '#niuniu/N01'; }}>
          <div className="seat-ring" style={{ ['--p' as any]: '0%' }}><span>牛</span></div>
          <div className="table-meta">
            <div className="table-name">牛牛 · 1 庄 3 闲 <span className="id">N01</span></div>
            <div className="table-tags">
              <span className="tag">牛牛 5 倍</span>
              <span className="tag">8 秒下注</span>
            </div>
          </div>
          <div className="btn sm primary">进入</div>
        </div>
      )}

      {variant !== 'NIUNIU' && visible.length === 0 && <div className="empty">暂无牌桌</div>}

      {variant !== 'NIUNIU' && visible.map(t => {
        const pct = Math.round((t.seated / t.maxSeats) * 100);
        return (
          <div key={t.tableId} className="table-item" onClick={() => { location.hash = '#table/' + t.tableId; }}>
            <div className="seat-ring" style={{ ['--p' as any]: pct + '%' }}>
              <span>{t.seated}/{t.maxSeats}</span>
            </div>
            <div className="table-meta">
              <div className="table-name">
                {t.name} <span className="id">{t.tableId}</span>
              </div>
              <div className="table-tags">
                <span className="tag">{t.variant === 'SHORT' ? '短牌' : '长牌'}</span>
                <span className="tag">
                  {t.smallBlind > 0
                    ? `${fmt(t.smallBlind)}/${fmt(t.bigBlind)}${t.straddle ? '/' + fmt(t.bigBlind * 2) : ''}`
                    : `前注 ${fmt(t.ante)}`}
                </span>
                {t.straddle && <span className="tag">Straddle</span>}
                {t.insuranceEnabled && <span className="tag ins">保险</span>}
                <span className="tag">入池 {t.avgVpip}%</span>
                <span className="tag">最小买入 {fmt(t.minBuyIn)}</span>
                <span className="tag">已运行 {t.runningMinutes} 分</span>
              </div>
            </div>
            <div className="btn sm primary">进入</div>
          </div>
        );
      })}

      {modal === 'dep' && (
        <Modal title="充值 USDT（TRON-TRC20）" onClose={() => setModal(null)} onOk={doDeposit}>
          <div className="field">
            <label>我的专属充值地址（一人一址）</label>
            <div className="addr-box mono">{addressInfo?.address || '加载中…'}</div>
            {addressInfo && (
              <button
                className="btn sm"
                style={{ marginTop: 8 }}
                onClick={() => {
                  navigator.clipboard?.writeText(addressInfo.address).then(() => showToast('地址已复制'));
                }}
              >
                复制地址
              </button>
            )}
          </div>
          <div className="field">
            <label>充值金额（USDT，最小 10）</label>
            <input value={amount} onChange={e => setAmount(e.target.value)} inputMode="decimal" />
          </div>
          <div className="small muted">
            网络：TRON-TRC20，最小 10 USDT。
            {addressInfo && !addressInfo.ready && ' 当前为模拟模式，地址不可用于真实充值，点击确认走模拟入账。'}
          </div>
        </Modal>
      )}

      {modal === 'wd' && (
        <Modal title="提现 USDT" onClose={() => setModal(null)} onOk={doWithdraw}>
          <div className="field">
            <label>TRON 收款地址</label>
            <input value={address} onChange={e => setAddress(e.target.value)} placeholder="T 开头地址" />
          </div>
          <div className="field">
            <label>提现金额（USDT，最小 10）</label>
            <input value={amount} onChange={e => setAmount(e.target.value)} inputMode="decimal" />
          </div>
          {me?.hasPayPwd && (
            <div className="field">
              <label>支付密码</label>
              <input
                type="password"
                value={payPwd}
                inputMode="numeric"
                maxLength={6}
                onChange={e => setPayPwd(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="6 位数字"
              />
            </div>
          )}
          <div className="small muted">手续费 1 USDT，到账 1–10 分钟。当前未接链上签名，仅记账。</div>
        </Modal>
      )}

      {records && (
        <Modal title={`${club?.name || ''} · 牌局记录`} onClose={() => setRecords(null)} okText="关闭" onOk={() => undefined}>
          {records.length === 0 && <div className="empty">暂无记录</div>}
          {records.map(r => (
            <div className="list-item" key={r.handId}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="small mono ellipsis">{r.handId}</div>
                <div className="small muted">
                  {r.variant === 'SHORT' ? '短牌' : '长牌'} · {r.blinds} · 底池 {r.potText}
                </div>
                <div className="small muted ellipsis">{r.board || '（无人摊牌）'} · {r.verifyCode}</div>
              </div>
            </div>
          ))}
        </Modal>
      )}
    </>
  );
}
