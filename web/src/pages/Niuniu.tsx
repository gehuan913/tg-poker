import { useEffect, useState } from 'react';
import { api } from '../api.ts';
import { useApp } from '../store.ts';
import { CardFace, CardBack } from '../components/CardFace.tsx';
import { Modal } from '../components/Modal.tsx';
import { fmt, toMicro } from '../utils.ts';
import { haptic } from '../tg.ts';
import type { NiuniuView } from '../types.ts';

export function Niuniu({ tableId }: { tableId: string }) {
  const { me, refreshMe, showToast } = useApp();
  const [v, setV] = useState<NiuniuView | null>(null);
  const [sitOpen, setSitOpen] = useState(false);
  const [buyIn, setBuyIn] = useState('10');
  const [bet, setBet] = useState('0.2');
  const [, tick] = useState(0);

  useEffect(() => {
    let alive = true;
    const load = () => api<NiuniuView>(`/api/niuniu/${tableId}`)
      .then(r => { if (alive) setV(r); })
      .catch(() => undefined);
    load();
    const t = window.setInterval(load, 1500);
    const t2 = window.setInterval(() => tick(x => x + 1), 200);
    return () => { alive = false; window.clearInterval(t); window.clearInterval(t2); };
  }, [tableId]);

  if (!v) {
    return (
      <div className="table-page">
        <div className="table-top">
          <button className="btn sm ghost" onClick={() => { location.hash = '#lobby'; }}>‹ 大厅</button>
        </div>
        <div className="empty" style={{ marginTop: 80 }}>正在进入牛牛桌…</div>
      </div>
    );
  }

  const my = v.mySeat >= 0 ? v.seats[v.mySeat] : null;
  const offset = v.serverTime - Date.now();
  const left = Math.max(0, v.deadline - (Date.now() + offset));
  const pct = v.stage === 'BETTING' ? Math.min(100, (left / (v.betSeconds * 1000)) * 100) : 0;

  return (
    <div className="table-page">
      <div className="table-top">
        <button className="btn sm ghost" onClick={() => { location.hash = '#lobby'; }}>‹</button>
        <div className="tt-name">{v.name}<span className="id">{v.tableId}</span></div>
        <div className="tt-meta">第 {v.roundNo} 局 · {stageLabel(v.stage)}</div>
      </div>

      <div className="felt nn-felt">
        <div className="felt-bar">
          <span>庄家 {v.seats[v.bankerSeat]?.nick || '-'}</span>
          <span>上庄资金 ≥ {fmt(v.bankerMin || 0)} U · 排队 {v.queueCount || 0}</span>
        </div>

        {v.stage === 'BETTING' && (
          <div className="bet-timer"><i style={{ width: pct + '%' }} /></div>
        )}

        <div className="nn-seats">
          {v.seats.map(s => (
            <div className={'nn-seat' + (s.seat === v.mySeat ? ' me' : '') + (s.banker ? ' banker' : '')} key={s.seat}>
              <div className="row" style={{ gap: 6 }}>
                <div className="badge sm">{s.seated ? (s.nick || '玩')[0] : '+'}</div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="nn-nick ellipsis">
                    {s.seated ? s.nick : '空位'}
                    {s.banker && <span className="tag ins" style={{ marginLeft: 4 }}>庄</span>}
                  </div>
                  <div className="small muted">{s.seated ? s.stackText + ' U' : ''}</div>
                </div>
                {s.betPlaced && <div className="small gold mono">{s.betText}</div>}
              </div>
              <div className="nn-cards">
                {s.cards?.length
                  ? s.cards.map((c, i) => <CardFace key={i} code={c} small />)
                  : (s.seated && v.stage !== 'WAITING' ? <><CardBack small /><CardBack small /><CardBack small /></> : null)}
              </div>
              {s.niuLabel && (
                <div className="row" style={{ marginTop: 4 }}>
                  <span className="small gold">{s.niuLabel} ×{s.multiplier}</span>
                  <span className={'small mono ' + (s.result >= 0 ? 'green' : 'red')}>{s.resultText}</span>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="table-log">
        {v.log.slice(-5).map((l, i) => <div key={i}>{l}</div>)}
        {v.log.length === 0 && <div className="muted">等待开始…</div>}
      </div>

      <div className="action-bar">
        {!my && (
          <button className="btn primary block" onClick={() => { setBuyIn('10'); setSitOpen(true); }}>
            入座（牛牛 1 庄 3 闲）
          </button>
        )}

        {my && (
          <div className="action-row" style={{ marginBottom: 6 }}>
            {my.banker ? (
              <button
                className="btn sm ghost"
                onClick={async () => {
                  try {
                    await api(`/api/niuniu/${tableId}/banker`, { method: 'POST', body: { on: false } });
                    showToast('已下庄，本局结束后生效');
                  } catch (e: any) {
                    showToast(e.message);
                  }
                }}
              >
                下庄
              </button>
            ) : v.wantBanker ? (
              <button
                className="btn sm"
                onClick={async () => {
                  try {
                    await api(`/api/niuniu/${tableId}/banker`, { method: 'POST', body: { on: false } });
                    showToast('已取消上庄申请');
                  } catch (e: any) {
                    showToast(e.message);
                  }
                }}
              >
                取消申请（排队 {v.queueCount || 0}）
              </button>
            ) : (
              <button
                className="btn sm"
                onClick={async () => {
                  try {
                    await api(`/api/niuniu/${tableId}/banker`, { method: 'POST', body: { on: true } });
                    showToast('已申请上庄');
                  } catch (e: any) {
                    showToast(e.message);
                  }
                }}
              >
                申请上庄（需 {fmt(v.bankerMin || 0)} U）
                {v.lastNiuNiuSeat === my.seat ? ' · 牛牛优先' : ''}
              </button>
            )}
          </div>
        )}

        {my && v.canBet && (
          <div className="raise-row">
            <input value={bet} onChange={e => setBet(e.target.value)} inputMode="decimal" className="seed-input" />
            <button
              className="btn primary"
              onClick={async () => {
                haptic();
                try {
                  await api(`/api/niuniu/${tableId}/bet`, { method: 'POST', body: { amount: toMicro(bet) } });
                  showToast('已下注');
                } catch (e: any) {
                  showToast(e.message);
                }
              }}
            >
              下注（上限 {fmt(v.maxBet)}）
            </button>
          </div>
        )}

        {my && !v.canBet && (
          <div className="row">
            <div className="small muted">
              {my.banker ? '你是庄家，等闲家下注' : v.stage === 'BETTING' ? '已下注，等开牌' : '等待下一局'}
            </div>
            <button
              className="btn ghost sm"
              onClick={async () => {
                try {
                  await api(`/api/niuniu/${tableId}/leave`, { method: 'POST' });
                  await refreshMe();
                  showToast('已离桌');
                } catch (e: any) {
                  showToast(e.message);
                }
              }}
            >
              离桌
            </button>
          </div>
        )}
      </div>

      {sitOpen && (
        <Modal
          title="入座牛牛"
          onClose={() => setSitOpen(false)}
          onOk={async () => {
            try {
              await api(`/api/niuniu/${tableId}/sit`, {
                method: 'POST',
                body: { buyIn: toMicro(buyIn), nick: me?.nick || '玩家', avatar: me?.avatar || '' }
              });
              await refreshMe();
              showToast('入座成功');
              return true;
            } catch (e: any) {
              showToast(e.message);
              return false;
            }
          }}
        >
          <div className="field">
            <label>买入金额（USDT，钱包 {fmt(me?.balance || 0)}）</label>
            <input value={buyIn} onChange={e => setBuyIn(e.target.value)} inputMode="decimal" />
          </div>
          <div className="small muted">庄家最多赔 5 倍，下注上限会按庄家筹码自动限制</div>
        </Modal>
      )}
    </div>
  );
}

function stageLabel(s: string) {
  if (s === 'BETTING') return '下注中';
  if (s === 'SETTLED') return '已开牌';
  return '等待中';
}
