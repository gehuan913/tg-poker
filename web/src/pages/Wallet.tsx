import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { api } from '../api.ts';
import { useApp } from '../store.ts';
import type { LedgerItem } from '../types.ts';
import { fmt, toMicro } from '../utils.ts';
import { haptic } from '../tg.ts';

type Tab = 'ledger' | 'deposit' | 'withdraw' | 'redpacket';

export function Wallet() {
  const { me, refreshMe, showToast } = useApp();
  const [tab, setTab] = useState<Tab>('ledger');
  const [ledger, setLedger] = useState<LedgerItem[]>([]);
  const [addr, setAddr] = useState<any>(null);
  const [withdraws, setWithdraws] = useState<any[]>([]);
  const [deposits, setDeposits] = useState<any[]>([]);
  const [packets, setPackets] = useState<any[]>([]);
  const [rq, setRq] = useState('');
  const [results, setResults] = useState<any[]>([]);
  const [toUser, setToUser] = useState<any>(null);
  const [rpAmt, setRpAmt] = useState('1');
  const [rpMemo, setRpMemo] = useState('');
  const [qr, setQr] = useState('');
  const [wAddr, setWAddr] = useState('');
  const [wAmt, setWAmt] = useState('10');
  const [wPwd, setWPwd] = useState('');

  useEffect(() => {
    refreshMe();
    api<LedgerItem[]>('/api/wallet/ledger').then(setLedger).catch(() => setLedger([]));
    api<any>('/api/wallet/deposit-address').then(setAddr).catch(() => setAddr(null));
    api<any[]>('/api/wallet/withdraw-orders').then(setWithdraws).catch(() => setWithdraws([]));
    api<any[]>('/api/wallet/deposit-orders').then(setDeposits).catch(() => setDeposits([]));
    api<any[]>('/api/wallet/redpackets').then(setPackets).catch(() => setPackets([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 地址变化时重新生成二维码；失败就不显示图，只留复制
  useEffect(() => {
    if (!addr?.address) {
      setQr('');
      return;
    }
    QRCode.toDataURL(String(addr.address), { width: 220, margin: 1, errorCorrectionLevel: 'M' })
      .then(setQr)
      .catch(() => setQr(''));
  }, [addr?.address]);

  return (
    <>
      <div className="topbar">
        <div className="club-name">钱包</div>
        <button className="btn sm ghost" onClick={() => { location.hash = '#me'; }}>返回</button>
      </div>

      <div className="card">
        <div className="card-title">USDT 余额（TRON-TRC20）</div>
        <div className="big gold mono">{fmt(me?.balance || 0)}</div>
        <div className="divider" />
        <div className="row small muted">
          <span>最小充提 10 USDT</span>
          <span>提现手续费 1 USDT</span>
        </div>
      </div>

      <div className="seg">
        {([['ledger', '账单'], ['deposit', '充值'], ['withdraw', '提现'], ['redpacket', '红包']] as [Tab, string][]).map(([k, label]) => (
          <div key={k} className={'seg-item' + (tab === k ? ' active' : '')} onClick={() => { haptic(); setTab(k); }}>
            {label}
          </div>
        ))}
      </div>

      {tab === 'ledger' && (
        <div className="card">
          {ledger.length === 0 && <div className="empty">暂无记录</div>}
          {ledger.map(l => (
            <div className="list-item" key={l.id}>
              <div style={{ flex: 1 }}>
                <div>{l.memo || l.bizType}</div>
                <div className="small muted">{(l.createdAt || '').replace('T', ' ').slice(0, 16)}</div>
              </div>
              <div className={'mono ' + (l.direction === 2 ? 'green' : 'red')}>
                {l.direction === 2 ? '+' : '-'}{fmt(l.amount)}
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'deposit' && (
        <>
          <div className="card">
            <div className="card-title">扫码充值</div>
            {qr
              ? <img className="qr-img" src={qr} alt="充值地址二维码" />
              : <div className="empty">二维码生成中…</div>}
            <div className="addr-box mono" style={{ marginTop: 10 }}>{addr?.address || '加载中…'}</div>
            <div className="row" style={{ marginTop: 10 }}>
              <span className="small muted">{addr?.network} · 最小 {addr?.minDepositText} U</span>
              <span className="row" style={{ gap: 6 }}>
                <button
                  className="btn sm"
                  onClick={() => {
                    haptic();
                    navigator.clipboard?.writeText(addr?.address || '');
                    showToast('地址已复制');
                  }}
                >
                  复制
                </button>
                <button
                  className="btn sm ghost"
                  onClick={() => {
                    haptic();
                    const text = `我的 USDT 充值地址（TRON-TRC20）：${addr?.address || ''}`;
                    window.open('https://t.me/share/url?url=' + encodeURIComponent(addr?.address || '') +
                      '&text=' + encodeURIComponent(text), '_blank');
                  }}
                >
                  分享
                </button>
              </span>
            </div>
            <div className="small muted" style={{ marginTop: 8 }}>
              只支持 TRON-TRC20 的 USDT，其他链转账不可找回
            </div>
            {addr && !addr.ready && (
              <div className="small" style={{ color: '#f0b429', marginTop: 8 }}>
                当前为模拟模式：地址不可用于真实充值，充值请用大厅的「模拟入账」。
              </div>
            )}
          </div>
          <div className="card">
            <div className="card-title">充值记录</div>
            {deposits.length === 0 && <div className="empty">暂无</div>}
            {deposits.map(d => (
              <div className="list-item" key={d.id}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="small ellipsis">{d.txHash}</div>
                  <div className="small muted">{d.confirmations} 确认 · {(d.createdAt || '').slice(0, 16)}</div>
                </div>
                <div className="mono green">+{fmt(d.amount)}</div>
              </div>
            ))}
          </div>
        </>
      )}

      {tab === 'withdraw' && (
        <>
          <div className="card">
            <div className="card-title">提现 USDT</div>
            <div className="row" style={{ marginBottom: 8 }}>
              <span className="small muted">可提资产</span>
              <span className="mono">{fmt(me?.balance || 0)} USDT</span>
            </div>
            <div className="row" style={{ marginBottom: 10 }}>
              <span className="small muted">可到账（扣 1 U 手续费）</span>
              <span className="mono gold">
                {fmt(Math.max(0, (me?.balance || 0) - 1_000_000))} USDT
              </span>
            </div>
            <div className="field">
              <label>TRON 收款地址</label>
              <input value={wAddr} onChange={e => setWAddr(e.target.value)} placeholder="T 开头地址" />
            </div>
            <div className="field">
              <label>提现金额（USDT，最小 10）</label>
              <input value={wAmt} onChange={e => setWAmt(e.target.value)} inputMode="decimal" />
            </div>
            {me?.hasPayPwd && (
              <div className="field">
                <label>支付密码</label>
                <input
                  type="password"
                  value={wPwd}
                  onChange={e => setWPwd(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  inputMode="numeric"
                  placeholder="6 位数字"
                />
              </div>
            )}
            <button
              className="btn primary block"
              onClick={async () => {
                haptic();
                try {
                  await api('/api/wallet/withdraw', {
                    method: 'POST',
                    body: { address: wAddr.trim(), amount: toMicro(wAmt), payPwd: wPwd }
                  });
                  showToast('提现申请已提交');
                  await refreshMe();
                  setWithdraws(await api<any[]>('/api/wallet/withdraw-orders'));
                  setWAddr('');
                  setWPwd('');
                } catch (e: any) {
                  showToast(e.message);
                }
              }}
            >
              提交提现
            </button>
            <div className="warn-bar">
              ⚠️ 平台不会以任何理由索要密码或验证码；提现到账 1–10 分钟，请核对地址前 6 位与后 6 位。
            </div>
          </div>

          <div className="card">
            <div className="card-title">提现工单</div>
            {withdraws.length === 0 && <div className="empty">暂无</div>}
            {withdraws.map(w => (
              <div className="list-item" key={w.id}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="mono small">{fmt(w.amount)} USDT</div>
                  <div className="small muted ellipsis">{w.address}</div>
                  <div className="small muted">{w.status} · {(w.createdAt || '').slice(0, 16)}</div>
                </div>
                <div className="small muted">手续费 {fmt(w.fee)}</div>
              </div>
            ))}
            <div className="small muted" style={{ marginTop: 8 }}>
              真实提现由公司区块链平台签名广播；未接入前只记账，状态停在 PENDING_REVIEW。
            </div>
          </div>
        </>
      )}

      {tab === 'redpacket' && (
        <>
          <div className="card">
            <div className="card-title">发红包</div>
            <div className="row" style={{ gap: 8, marginBottom: 8 }}>
              <input
                className="seed-input"
                value={rq}
                placeholder="搜昵称或用户 ID"
                onChange={e => setRq(e.target.value)}
                onKeyDown={async e => {
                  if (e.key !== 'Enter' || !rq.trim()) return;
                  try {
                    setResults(await api<any[]>('/api/user/search?q=' + encodeURIComponent(rq.trim())));
                  } catch (err: any) {
                    showToast(err.message);
                  }
                }}
              />
              <button
                className="btn sm"
                onClick={async () => {
                  if (!rq.trim()) return;
                  try {
                    setResults(await api<any[]>('/api/user/search?q=' + encodeURIComponent(rq.trim())));
                  } catch (err: any) {
                    showToast(err.message);
                  }
                }}
              >
                搜索
              </button>
            </div>

            {results.length > 0 && !toUser && (
              <div className="search-list">
                {results.map(u => (
                  <div className="list-item" key={u.userId} onClick={() => { setToUser(u); setResults([]); }}>
                    <div className="avatar">{u.avatar ? <img src={u.avatar} alt="" /> : (u.nick || '玩')[0]}</div>
                    <div style={{ flex: 1 }}>
                      <div>{u.nick}</div>
                      <div className="small muted">ID {u.userId}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {toUser && (
              <>
                <div className="row" style={{ marginBottom: 8 }}>
                  <span className="small">收件人：{toUser.nick}（ID {toUser.userId}）</span>
                  <button className="btn sm ghost" onClick={() => setToUser(null)}>换人</button>
                </div>
                <div className="field">
                  <label>金额（USDT，0.1 ~ 100）</label>
                  <input value={rpAmt} onChange={e => setRpAmt(e.target.value)} inputMode="decimal" />
                </div>
                <div className="field">
                  <label>备注（可选）</label>
                  <input value={rpMemo} onChange={e => setRpMemo(e.target.value)} maxLength={30} />
                </div>
                <button
                  className="btn primary block"
                  onClick={async () => {
                    try {
                      await api('/api/wallet/redpacket', {
                        method: 'POST',
                        body: { receiverId: toUser.userId, amount: toMicro(rpAmt), memo: rpMemo || '红包', scene: 'CHAT' }
                      });
                      showToast('红包已发送');
                      await refreshMe();
                      setToUser(null);
                      setRq('');
                      setRpAmt('1');
                      setRpMemo('');
                      setPackets(await api<any[]>('/api/wallet/redpackets'));
                    } catch (e: any) {
                      showToast(e.message);
                    }
                  }}
                >
                  发送
                </button>
              </>
            )}
            <div className="small muted" style={{ marginTop: 8 }}>
              只能发给玩家，不能发给官方账号；单笔 0.1~100 USDT，对方每日收款有上限
            </div>
          </div>

          <div className="card">
            <div className="card-title">收到的红包</div>
            {packets.length === 0 && <div className="empty">暂无</div>}
            {packets.map(p => (
              <div className="list-item" key={p.id}>
                <div style={{ flex: 1 }}>
                  <div>{p.senderNick} 的红包</div>
                  <div className="small muted">{p.memo || '红包'} · {(String(p.createdAt) || '').slice(0, 16)}</div>
                </div>
                <div className="mono green">+{fmt(p.amount)}</div>
              </div>
            ))}
            <div className="small muted" style={{ marginTop: 8 }}>
              桌内可在牌桌「红包」Tab 直接发给同桌玩家
            </div>
          </div>
        </>
      )}
    </>
  );
}
