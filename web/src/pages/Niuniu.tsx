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
  const [histOpen, setHistOpen] = useState(false);

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

      <div className="row" style={{ justifyContent: 'flex-end', marginBottom: 6 }}>
        <button className="btn sm ghost" onClick={() => { haptic(); setHistOpen(true); }}>
          牌局回顾
        </button>
      </div>

      <div className="table-log">
        {v.log.slice(-5).map((l, i) => <div key={i}>{l}</div>)}
        {v.log.length === 0 && <div className="muted">等待开始…</div>}
      </div>

      <div className="action-bar">
        {v.selecting && (
          <div className="select-banner">
            <div>
              <b>选庄中</b> · {v.selectNick || '-'} 开出牛牛
              <div className="small muted">8 秒内不操作视为放弃</div>
            </div>
            {my && v.selectNick === my.nick && (
              <div className="row" style={{ gap: 6 }}>
                <button
                  className="btn sm primary"
                  onClick={async () => {
                    try {
                      await api(`/api/niuniu/${tableId}/banker/choose`, { method: 'POST', body: { accept: true } });
                      showToast('已接受上庄');
                    } catch (e: any) {
                      showToast(e.message);
                    }
                  }}
                >
                  上庄
                </button>
                <button
                  className="btn sm ghost"
                  onClick={async () => {
                    try {
                      await api(`/api/niuniu/${tableId}/banker/choose`, { method: 'POST', body: { accept: false } });
                      showToast('已放弃');
                    } catch (e: any) {
                      showToast(e.message);
                    }
                  }}
                >
                  放弃
                </button>
              </div>
            )}
          </div>
        )}

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
          <div>
            <div className="small muted" style={{ marginBottom: 6 }}>
              底注 {fmt(v.anteUnit || 0)} · 本局已下注 {fmt(v.myBet || 0)} · 本局最多可到 {fmt(v.maxBet || 0)}
            </div>
            <div className="row" style={{ gap: 6, marginBottom: 6 }}>
              {[1, 5, 10].map(k => (
                <button
                  className="btn sm"
                  key={k}
                  onClick={() => {
                    haptic();
                    setBet(String(((v.anteUnit || 0) * k) / 1e6));
                  }}
                >
                  {k}× 底注
                </button>
              ))}
            </div>
            <div className="raise-row">
              <input value={bet} onChange={e => setBet(e.target.value)} inputMode="decimal" className="seed-input" />
              <button
                className="btn primary"
                onClick={async () => {
                  haptic();
                  try {
                    await api(`/api/niuniu/${tableId}/bet`, { method: 'POST', body: { amount: toMicro(bet) } });
                    showToast('已追加下注');
                  } catch (e: any) {
                    showToast(e.message);
                  }
                }}
              >
                追加下注
              </button>
            </div>
            <div className="small muted" style={{ marginTop: 4 }}>
              追加金额必须是底注的整数倍
            </div>
          </div>
        )}

        {my && !v.canBet && (
          <div className="row">
            <div className="small muted">
              {my.banker
                ? '你是庄家，等闲家下注'
                : v.stage === 'BETTING'
                  ? (v.myBet ? '等开牌（可继续追加）' : '筹码不足，本局无法下注')
                  : '等待下一局'}
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
      {histOpen && (
        <Modal title="牌局回顾" onClose={() => setHistOpen(false)} okText="关闭" onOk={() => undefined}>
          {(v.history || []).length === 0 && <div className="empty">暂无记录</div>}
          {[...(v.history || [])].reverse().map(h => (
            <div className="pot-card" key={h.roundNo}>
              <div className="row">
                <b>第 {h.roundNo} 局</b>
                <span className="small muted">庄家 {h.bankerNick}</span>
              </div>
              {h.entries.map((e, i) => (
                <div className="row small" key={i} style={{ marginTop: 4 }}>
                  <span>{e.nick} · {e.niuLabel}{e.betText !== '0' ? ` · 下注 ${e.betText}` : ''}</span>
                  <span className={'mono ' + (e.result > 0 ? 'green' : e.result < 0 ? 'red' : 'muted')}>{e.resultText}</span>
                </div>
              ))}
            </div>
          ))}
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
