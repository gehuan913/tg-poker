import { useEffect, useState } from 'react';
import { useApp } from '../store.ts';
import type { TableLedgerRow, TableView } from '../types.ts';
import { api } from '../api.ts';
import { fmt, toMicro } from '../utils.ts';
import { CardBack, CardFace } from '../components/CardFace.tsx';
import { Modal } from '../components/Modal.tsx';
import { haptic, openLink } from '../tg.ts';

type Tab = 'log' | 'ledger' | 'insurance' | 'redpacket';

export function TablePage({ tableId }: { tableId: string }) {
  const { view, enterTable, doAction, doSit, doRebuy, doQuote, doInsurance, doSubmitSeed, doChat, me, showToast, quote } = useApp();
  const [sitOpen, setSitOpen] = useState(false);
  const [verifyOpen, setVerifyOpen] = useState(false);
  const [rebuyOpen, setRebuyOpen] = useState(false);
  const [tab, setTab] = useState<Tab>('log');
  const [quick, setQuick] = useState<'' | 'chat' | 'note' | 'rank'>('');
  const [chatText, setChatText] = useState('');
  const [note, setNote] = useState('');
  const [buyIn, setBuyIn] = useState('10');
  const [rebuyAmt, setRebuyAmt] = useState('10');
  const [raiseAmt, setRaiseAmt] = useState(0);
  const [ledger, setLedger] = useState<TableLedgerRow[]>([]);
  const [seed, setSeed] = useState('');
  const [rpTo, setRpTo] = useState(0);
  const [rpAmt, setRpAmt] = useState('1');
  const [, forceTick] = useState(0);

  useEffect(() => { enterTable(tableId); }, [tableId]);
  useEffect(() => {
    const t = window.setInterval(() => forceTick(v => v + 1), 200);
    return () => window.clearInterval(t);
  }, []);

  useEffect(() => {
    if (tab !== 'ledger') return;
    let alive = true;
    const load = () => api<TableLedgerRow[]>(`/api/table/${tableId}/ledger`).then(r => { if (alive) setLedger(r); }).catch(() => undefined);
    load();
    const t = window.setInterval(load, 3000);
    return () => { alive = false; window.clearInterval(t); };
  }, [tab, tableId]);

  // 桌内笔记存本地，换桌各自独立；不进服务端，纯私人备忘
  useEffect(() => {
    setNote(localStorage.getItem('note:' + tableId) || '');
  }, [tableId]);

  if (!view) {
    return (
      <div className="table-page">
        <div className="table-top">
          <button className="btn sm ghost" onClick={() => { location.hash = '#lobby'; }}>‹ 大厅</button>
        </div>
        <div className="empty" style={{ marginTop: 80 }}>正在进入牌桌…</div>
      </div>
    );
  }

  const v = view as TableView;
  const my = v.mySeat >= 0 ? v.seats[v.mySeat] : null;
  const offset = (v.serverTime || Date.now()) - Date.now();
  const left = Math.max(0, v.deadline - (Date.now() + offset));
  const total = (v.actionSeconds || 15) * 1000;
  const leftPct = Math.min(100, (left / total) * 100);
  const isSpectator = v.role === 'SPECTATOR' || !my;

  return (
    <div className="table-page">
      <div className="table-top">
        <button className="btn sm ghost" onClick={() => { location.hash = '#lobby'; }}>‹</button>
        <div className="tt-name">
          {v.name}<span className="id">{v.tableId}</span>
        </div>
        <div className="tt-meta">
          {v.variantLabel} · {v.smallBlind > 0 ? `${fmt(v.smallBlind)}/${fmt(v.bigBlind)}` : v.anteLabel}
          {v.straddle ? ` · Straddle ${fmt(v.straddleAmount || 0)}` : ''}
          {v.runningMinutes !== undefined && <span className="tt-run"> · 已运行 {hm(v.runningMinutes)}</span>}
        </div>
        <div
          className="verify"
          onClick={() => {
            haptic();
            navigator.clipboard?.writeText(v.verifyCode || '');
            showToast(v.verifyCode ? '存证号已复制 ' + v.verifyCode : '等待开局');
            setVerifyOpen(true);
          }}
        >
          {v.verifyCode || '等待开局'}
        </div>
      </div>

      {v.stage === 'PAUSED' && <div className="paused-banner">牌桌已暂停，等待管理员恢复</div>}

      <div className="felt">
        <div className="felt-bar">
          <span>底池 <b className="mono">{fmt(v.pot)}</b></span>
          <span>旁观 {v.spectators}</span>
          {v.anteLabel && <span className="ante-tag">{v.anteLabel}</span>}
        </div>

        <div className="board">
          {[0, 1, 2, 3, 4].map(i => (
            v.board[i] ? <CardFace key={i} code={v.board[i]} /> : <CardBack key={i} small />
          ))}
        </div>

        {seatPositions(v.maxSeats, v.mySeat).map(p => {
          const s = v.seats[p.seat];
          if (!s) return null;
          return (
            <div
              key={p.seat}
              className={'seat' + (s.seated ? '' : ' empty') + (s.acting ? ' acting' : '') + (s.folded ? ' folded' : '')}
              style={{ left: p.x + '%', top: p.y + '%' }}
            >
              <div className="badge">
                {s.seated ? (s.avatar ? <img src={s.avatar} alt="" /> : (s.nick || '玩')[0]) : '+'}
              </div>
              <div className="nick">{s.seated ? s.nick : ''}</div>
              <div className="stack mono">
                {s.seated ? fmt(s.stack) + (s.bet > 0 ? ' · ' + fmt(s.bet) : '') : ''}
              </div>
              {s.lastAction && <div className="action">{s.lastAction}{s.lastAmount ? ' ' + fmt(s.lastAmount) : ''}</div>}
              {s.dealer && <div className="dealer">D</div>}
              <div className="hole-cards">
                {s.cards?.length
                  ? s.cards.map((c, k) => <CardFace key={k} code={c} small />)
                  : (s.seated && !s.folded && v.stage !== 'WAITING'
                    ? <><CardBack small /><CardBack small /></>
                    : null)}
              </div>
              {s.allIn && <div className="allin">ALL IN</div>}
              {s.acting && <div className="timer"><i style={{ width: leftPct + '%' }} /></div>}
            </div>
          );
        })}

        {my && v.myCards.length > 0 && (
          <div className="my-hand">
            {v.myCards.map((c, i) => <CardFace key={i} code={c} />)}
          </div>
        )}
      </div>

      {/* 底部四个快捷：笔记 / 聊天 / 表情 / 牌型参考（技术方案 §2.4） */}
      <div className="quick-row">
        <button className="quick-btn" onClick={() => { haptic(); setQuick(quick === 'note' ? '' : 'note'); }}>
          📝 笔记
        </button>
        <button className="quick-btn" onClick={() => { haptic(); setQuick(quick === 'chat' ? '' : 'chat'); }}>
          💬 聊天{(v.chat?.length || 0) > 0 && <i className="dot" />}
        </button>
        <button
          className="quick-btn"
          onClick={() => {
            haptic();
            setQuick('chat');
            doChat(EMOJIS[Math.floor(Math.random() * EMOJIS.length)]);
          }}
        >
          😀 表情
        </button>
        <button className="quick-btn" onClick={() => { haptic(); setQuick(quick === 'rank' ? '' : 'rank'); }}>
          🃏 牌型
        </button>
      </div>

      {quick === 'chat' && (
        <div className="chat-panel">
          <div className="chat-list">
            {(v.chat || []).length === 0 && <div className="small muted">还没有人说话</div>}
            {(v.chat || []).map((m, i) => (
              <div className={'chat-item' + (m.userId === me?.userId ? ' mine' : '')} key={i}>
                <span className="chat-nick">{m.nick}</span>
                <span className="chat-text">{m.text}</span>
              </div>
            ))}
          </div>
          <div className="chat-input">
            <input
              value={chatText}
              maxLength={60}
              placeholder={isSpectator ? '上桌后才能发言' : '说点什么…'}
              disabled={isSpectator}
              onChange={e => setChatText(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter' && chatText.trim()) {
                  doChat(chatText.trim());
                  setChatText('');
                }
              }}
            />
            <button
              className="btn sm primary"
              disabled={isSpectator || !chatText.trim()}
              onClick={() => { doChat(chatText.trim()); setChatText(''); }}
            >
              发送
            </button>
          </div>
        </div>
      )}

      <div className="tabs">
        {([
          ['log', '日志'],
          ['ledger', '账单'],
          ['insurance', '保险'],
          ['redpacket', '红包']
        ] as [Tab, string][]).map(([k, label]) => (
          <div key={k} className={'tab-item' + (tab === k ? ' active' : '')} onClick={() => { haptic(); setTab(k); }}>
            {label}
          </div>
        ))}
      </div>

      <div className="tab-panel">
        {tab === 'log' && (
          <div className="table-log">
            {(v.log || []).slice(-8).map((l, i) => <div key={i}>{l}</div>)}
            {(!v.log || v.log.length === 0) && <div className="muted">等待开始…</div>}
          </div>
        )}

        {tab === 'ledger' && (
          <div className="ledger">
            <div className="ledger-row head">
              <span>昵称</span><span>买入</span><span>手数</span><span>入池</span><span>输赢</span>
            </div>
            {ledger.length === 0 && <div className="empty">暂无数据</div>}
            {ledger.map(r => (
              <div className="ledger-row" key={r.seat}>
                <span className="ellipsis">{r.nick}</span>
                <span className="mono">{r.buyInText || '—'}</span>
                <span className="mono">{r.hands}</span>
                <span className="mono">{r.vpip}%</span>
                <span className={'mono ' + (r.net >= 0 ? 'green' : 'red')}>{r.netText}</span>
              </div>
            ))}
            <div className="small muted" style={{ marginTop: 8 }}>
              买入仅本人可见；手数、入池率、输赢桌内公开（反伙牌）
            </div>
          </div>
        )}

        {tab === 'insurance' && (
          <div>
            {!v.insuranceEnabled && <div className="empty">本桌未开启保险</div>}
            {v.insuranceEnabled && (
              <>
                <div className="ins-head">
                  <div>
                    <div className="small muted">当前报价</div>
                    <div className="mono">
                      {quote?.available ?? v.insurance?.available
                        ? `${quote?.outs ?? v.insurance?.outs} outs · 赔率 ${quote?.odds ?? v.insurance?.oddsText}`
                        : (quote?.reason || v.insurance?.reason || '仅在转牌圈 All-in 亮牌后可投保')}
                    </div>
                  </div>
                  <button className="btn sm" onClick={() => doQuote()}>刷新报价</button>
                </div>
                {(quote?.available || v.insurance?.available) && (
                  <>
                    <div className="small muted">
                      保额上限 {fmt(quote?.maxCoverage ?? v.insurance?.maxCoverage ?? 0)}，应付保费{' '}
                      {fmt(quote?.premium ?? v.insurance?.premium ?? 0)}
                    </div>
                    <button
                      className="btn primary block"
                      style={{ marginTop: 8 }}
                      onClick={() => doInsurance(quote?.maxCoverage ?? v.insurance?.maxCoverage ?? 0)}
                    >
                      买入保险（满池）
                    </button>
                  </>
                )}
                <div className="divider" />
                <div className="small muted">本桌保险流水</div>
                {(v.insurances || []).length === 0 && <div className="empty">暂无</div>}
                {(v.insurances || []).map((io, i) => (
                  <div className="list-item" key={i}>
                    <div style={{ flex: 1 }}>
                      <div className="small">{io.nick} · {io.handNo}手-{io.street}</div>
                      <div className="small muted">{io.outs} outs · 赔率 {io.odds}</div>
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <div className="mono small">保费 {io.premiumText}</div>
                      <div className={'mono small ' + (io.result === 'WON' ? 'green' : 'muted')}>
                        赔付 {io.payoutText} · {io.result === 'WON' ? '已触发' : '未触发'}
                      </div>
                    </div>
                  </div>
                ))}
              </>
            )}
          </div>
        )}

        {tab === 'redpacket' && (
          <div>
            {isSpectator && <div className="empty">上桌后才能发红包</div>}
            {!isSpectator && (
              <>
                <div className="field">
                  <label>发给同桌玩家</label>
                  <select value={rpTo} onChange={e => setRpTo(Number(e.target.value))}>
                    <option value={0}>请选择</option>
                    {v.seats.filter(s => s.seated && s.userId && s.userId !== me?.userId).map(s => (
                      <option key={s.seat} value={s.userId as number}>{s.nick}（{s.seat} 号位）</option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label>金额（USDT，0.1 ~ 100）</label>
                  <input value={rpAmt} onChange={e => setRpAmt(e.target.value)} inputMode="decimal" />
                </div>
                <button
                  className="btn primary block"
                  onClick={async () => {
                    if (!rpTo) { showToast('请选择收款人'); return; }
                    try {
                      await api(`/api/table/${tableId}/redpacket`, {
                        method: 'POST',
                        body: { receiverId: rpTo, amount: toMicro(rpAmt), memo: '桌内红包' }
                      });
                      showToast('红包已发送');
                    } catch (e: any) {
                      showToast(e.message);
                    }
                  }}
                >
                  发红包
                </button>
                <div className="small muted" style={{ marginTop: 8 }}>
                  红包走同一套账本（TRANSFER_IN/OUT），禁止给官方账号转账
                </div>
              </>
            )}
          </div>
        )}
      </div>

      <div className={'action-bar' + (v.stage === 'PAUSED' ? ' disabled' : '')}>
        {isSpectator && (
          <div className="action-row">
            <button
              className="btn primary"
              onClick={() => { setBuyIn(fmt(v.minBuyIn).replace(/,/g, '')); setSitOpen(true); }}
            >
              入座（{fmt(v.minBuyIn)} ~ {fmt(v.maxBuyIn)}）
            </button>
            {v.mySeat < 0 && v.role === 'SPECTATOR' && (
              <button className="btn ghost" onClick={() => enterTable(tableId)}>我要上桌</button>
            )}
          </div>
        )}

        {!isSpectator && v.actingSeat !== v.mySeat && (
          <div className="action-row">
            <button className="btn ghost" onClick={() => setRebuyOpen(true)}>补码</button>
            <button className="btn ghost" onClick={() => { location.hash = '#lobby'; }}>离桌</button>
          </div>
        )}

        {!isSpectator && v.actingSeat === v.mySeat && my && (
          <ActiveBar v={v} my={my} raiseAmt={raiseAmt} setRaiseAmt={setRaiseAmt} doAction={doAction} />
        )}
      </div>

      {sitOpen && (
        <Modal
          title="入座"
          onClose={() => setSitOpen(false)}
          onOk={() => {
            const amt = toMicro(buyIn);
            if (amt < v.minBuyIn || amt > v.maxBuyIn) { showToast('买入金额超出范围'); return false; }
            doSit(amt);
            enterTable(tableId);
            return true;
          }}
        >
          <div className="field">
            <label>买入金额（USDT）</label>
            <input value={buyIn} onChange={e => setBuyIn(e.target.value)} inputMode="decimal" />
          </div>
          <div className="small muted">
            范围 {fmt(v.minBuyIn)} ~ {fmt(v.maxBuyIn)} USDT，从钱包余额扣除（当前 {fmt(me?.balance || 0)}）
          </div>
        </Modal>
      )}

      {rebuyOpen && (
        <Modal
          title="补码"
          onClose={() => setRebuyOpen(false)}
          onOk={() => { doRebuy(toMicro(rebuyAmt)); return true; }}
        >
          <div className="field">
            <label>补码金额（USDT）</label>
            <input value={rebuyAmt} onChange={e => setRebuyAmt(e.target.value)} inputMode="decimal" />
          </div>
          <div className="small muted">补码后桌上筹码不能超过最大买入 {fmt(v.maxBuyIn)}</div>
        </Modal>
      )}

      {verifyOpen && (
        <Modal title="可存证" onClose={() => setVerifyOpen(false)} okText="关闭" onOk={() => undefined}>
          <div className="small" style={{ lineHeight: 1.9 }}>
            <div>存证号：<b className="mono">{v.verifyCode || '-'}</b></div>
            <div>手牌 ID：{v.handId || '-'}</div>
            <div style={{ marginTop: 8 }}>本手承诺 hash（SHA256 of serverSeed）：</div>
            <div className="mono break">{v.serverSeedHash || '-'}</div>
            {v.lastServerSeed && (
              <>
                <div style={{ marginTop: 8 }}>上一手揭示 seed：</div>
                <div className="mono break">{v.lastServerSeed}</div>
              </>
            )}
            {(v.clientSeeds || []).length > 0 && (
              <>
                <div style={{ marginTop: 8 }}>本手玩家种子：</div>
                <div className="mono break">{(v.clientSeeds || []).join(',')}</div>
              </>
            )}
            <div className="divider" />
            <div className="small muted">提交我的种子（下一手生效，提交后不可改）</div>
            <div className="row" style={{ gap: 8, marginTop: 6 }}>
              <input
                className="seed-input"
                value={seed}
                onChange={e => setSeed(e.target.value)}
                placeholder="任意随机字符串"
              />
              <button className="btn sm" onClick={() => doSubmitSeed(seed || String(Date.now()))}>提交</button>
            </div>
            <button
              className="btn sm ghost block"
              style={{ marginTop: 10 }}
              onClick={() => openLink('/verify.js')}
            >
              下载开源验证脚本 verify.js
            </button>
            <div className="small muted" style={{ marginTop: 6 }}>
              算法：HMAC_SHA256(serverSeed, clientSeeds + ':' + nonce) → HMAC-DRBG → Fisher-Yates。
              站内按钮只是方便，真正可信的是脚本能被第三方离线跑通。
            </div>
          </div>
        </Modal>
      )}

      {quick === 'note' && (
        <Modal
          title="桌内笔记（只存本机）"
          onClose={() => setQuick('')}
          onOk={() => {
            localStorage.setItem('note:' + tableId, note);
            showToast('笔记已保存到本机');
            return true;
          }}
        >
          <textarea
            className="note-box"
            value={note}
            onChange={e => setNote(e.target.value)}
            placeholder="记录对手习惯、下注尺度、可疑行为…"
            maxLength={500}
          />
          <div className="small muted">只保存在当前设备，不上传、不同步</div>
        </Modal>
      )}

      {quick === 'rank' && (
        <Modal title={`牌型大小（${v.variant === 'SHORT' ? '短牌' : '长牌'}）`} onClose={() => setQuick('')} okText="关闭" onOk={() => undefined}>
          <div className="rank-list">
            {(v.variant === 'SHORT'
              ? ['同花顺', '四条', '同花', '葫芦', '三条', '顺子', '两对', '一对', '高牌']
              : ['同花顺', '四条', '葫芦', '同花', '顺子', '三条', '两对', '一对', '高牌']
            ).map((name, i) => (
              <div className="rank-item" key={name}>
                <span className="rank-no">{i + 1}</span>
                <span>{name}</span>
              </div>
            ))}
          </div>
          <div className="small muted" style={{ marginTop: 10 }}>
            {v.variant === 'SHORT'
              ? '短牌 36 张：同花大于葫芦、三条大于顺子，最小顺子 A6789，A 不能当 1。'
              : '长牌 52 张：A2345 是最小顺子，A 可当 1 或 14。'}
          </div>
        </Modal>
      )}
    </div>
  );
}

/** 分钟数转 hh:mm，超过 24h 只显示小时。 */
function hm(min: number) {
  const h = Math.floor(min / 60);
  const m = min % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

const EMOJIS = ['👍', '😂', '😭', '😡', '🃏', '💰', '🎉', '🤝'];

function seatPositions(maxSeats: number, mySeat: number) {
  const n = Math.max(2, maxSeats);
  const rot = mySeat >= 0 ? mySeat : 0;
  const out: { seat: number; x: number; y: number }[] = [];
  for (let i = 0; i < n; i++) {
    const deg = 90 + ((i - rot) * 360) / n;
    const rad = (deg * Math.PI) / 180;
    out.push({
      seat: i,
      x: 50 + 40 * Math.cos(rad),
      y: 52 + 40 * Math.sin(rad)
    });
  }
  return out;
}

function ActiveBar({
  v, my, raiseAmt, setRaiseAmt, doAction
}: {
  v: TableView;
  my: any;
  raiseAmt: number;
  setRaiseAmt: (n: number) => void;
  doAction: (act: string, amt?: number) => void;
}) {
  const toCall = Math.max(0, v.currentBet - my.bet);
  const maxTarget = my.stack + my.bet;
  const minTarget = Math.min(v.currentBet + Math.max(v.minRaise, 1), maxTarget);
  const step = v.bigBlind || v.ante || 100000;
  const amt = raiseAmt >= minTarget && raiseAmt <= maxTarget ? raiseAmt : minTarget;

  return (
    <>
      <div className="action-row">
        <button className="btn red" onClick={() => doAction('FOLD')}>弃牌</button>
        {toCall > 0
          ? <button className="btn" onClick={() => doAction('CALL')}>跟注 {fmt(Math.min(toCall, my.stack))}</button>
          : <button className="btn" onClick={() => doAction('CHECK')}>过牌</button>}
      </div>
      <div className="raise-row">
        <input
          type="range"
          min={minTarget}
          max={maxTarget}
          step={step}
          value={amt}
          onChange={e => setRaiseAmt(Number(e.target.value))}
        />
        <div className="raise-val mono">{fmt(amt)}</div>
        <button
          className="btn primary"
          onClick={() => doAction(amt >= maxTarget ? 'ALLIN' : 'RAISE', amt)}
        >
          {toCall > 0 ? '加注' : '下注'}
        </button>
      </div>
    </>
  );
}
