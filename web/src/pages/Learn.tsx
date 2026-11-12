import { useEffect, useState } from 'react';
import { api } from '../api.ts';
import { Modal } from '../components/Modal.tsx';
import { useApp } from '../store.ts';
import { haptic, openLink } from '../tg.ts';
import type { LessonItem, QuizItem } from '../types.ts';

type Tab = 'knowledge' | 'tool' | 'train' | 'mine';

export function Learn() {
  const { showToast } = useApp();
  const [tab, setTab] = useState<Tab>('knowledge');
  const [lessons, setLessons] = useState<LessonItem[]>([]);
  const [favorites, setFavorites] = useState<any[]>([]);
  const [open, setOpen] = useState<LessonItem | null>(null);
  const [cfg, setCfg] = useState<any>(null);
  const [prefOpen, setPrefOpen] = useState(false);
  const [prefs, setPrefs] = useState<Record<string, boolean>>(() => {
    try {
      return JSON.parse(localStorage.getItem('prefs') || '{}');
    } catch {
      return {};
    }
  });
  const on = (k: string, dft = true) => (prefs[k] === undefined ? dft : prefs[k]);
  const savePref = (k: string, v: boolean) => {
    const p = { ...prefs, [k]: v };
    setPrefs(p);
    localStorage.setItem('prefs', JSON.stringify(p));
    haptic();
  };

  // 工具
  const [hole, setHole] = useState('AsKd');
  const [board, setBoard] = useState('');
  const [opponents, setOpponents] = useState(1);
  const [variant, setVariant] = useState('LONG');
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  // 训练
  const [quiz, setQuiz] = useState<QuizItem[]>([]);
  const [qi, setQi] = useState(0);
  const [picked, setPicked] = useState(-1);
  const [judge, setJudge] = useState<any>(null);
  const [score, setScore] = useState(0);

  useEffect(() => {
    api<LessonItem[]>('/api/teach/lessons').then(setLessons).catch(() => setLessons([]));
    api<any[]>('/api/teach/favorites').then(setFavorites).catch(() => setFavorites([]));
    api<QuizItem[]>('/api/teach/quiz?limit=5').then(setQuiz).catch(() => setQuiz([]));
    api<any>('/api/app/config').then(setCfg).catch(() => setCfg(null));
  }, []);

  async function compute() {
    setLoading(true);
    setResult(null);
    try {
      const q = new URLSearchParams({ hole, opponents: String(opponents), variant, iterations: '20000' });
      if (board.trim()) q.set('board', board.trim());
      const r = await api<any>('/api/teach/equity?' + q.toString());
      setResult(r);
    } catch (e: any) {
      setResult({ error: e.message });
    } finally {
      setLoading(false);
    }
  }

  async function answer(choice: number) {
    const q = quiz[qi];
    if (!q) return;
    setPicked(choice);
    try {
      const r = await api<any>('/api/teach/quiz/answer', { method: 'POST', body: { id: q.id, choice } });
      setJudge(r);
      if (r.correct) setScore(s => s + 1);
    } catch (e: any) {
      setJudge({ explain: e.message });
    }
  }

  function nextQuiz() {
    setPicked(-1);
    setJudge(null);
    setQi(i => Math.min(i + 1, quiz.length));
  }

  const current = quiz[qi];
  const options: string[] = current ? safeParse(current.options) : [];

  return (
    <>
      <div className="topbar">
        <div className="club-name">教学</div>
        <div className="small muted">知识 / 工具 / 训练 / 我的</div>
      </div>

      <div className="seg">
        {([['knowledge', '知识'], ['tool', '工具'], ['train', '训练'], ['mine', '我的']] as [Tab, string][]).map(([k, label]) => (
          <div key={k} className={'seg-item' + (tab === k ? ' active' : '')} onClick={() => { haptic(); setTab(k); }}>
            {label}
          </div>
        ))}
      </div>

      {tab === 'knowledge' && (
        <>
          {lessons.length === 0 && <div className="empty">暂无内容，等运营上传</div>}
          {lessons.map(l => (
            <div className="card-lesson" key={l.id} onClick={() => { haptic(); setOpen(l); }}>
              <div className="lesson-cover">{l.mediaType === 'VIDEO' ? '▶' : '📄'}</div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="lesson-title">{l.title}</div>
                <div className="small muted ellipsis">{l.summary}</div>
                <div className="lesson-meta">
                  {l.mediaType === 'VIDEO' ? fmtDur(l.durationSec) : '图文'} · {l.views} 播放 · {l.likes} 赞
                </div>
              </div>
            </div>
          ))}
        </>
      )}

      {tab === 'tool' && (
        <div className="card">
          <div className="card-title">胜率计算器（Monte Carlo 2 万次）</div>
          <div className="field">
            <label>我的底牌（如 AsKd）</label>
            <input value={hole} onChange={e => setHole(e.target.value)} />
          </div>
          <div className="field">
            <label>公牌（可选，3/4/5 张，如 2c7h9s）</label>
            <input value={board} onChange={e => setBoard(e.target.value)} />
          </div>
          <div className="row" style={{ gap: 8 }}>
            <div className="field" style={{ flex: 1 }}>
              <label>对手数</label>
              <input value={opponents} onChange={e => setOpponents(Number(e.target.value) || 1)} inputMode="numeric" />
            </div>
            <div className="field" style={{ flex: 1 }}>
              <label>玩法</label>
              <select value={variant} onChange={e => setVariant(e.target.value)}>
                <option value="LONG">长牌</option>
                <option value="SHORT">短牌</option>
              </select>
            </div>
          </div>
          <button className="btn primary block" onClick={compute} disabled={loading}>
            {loading ? '计算中…' : '计算胜率'}
          </button>
          {result && !result.error && (
            <div style={{ marginTop: 12 }}>
              <div className="row">
                <ResultBar label="胜" value={result.win} cls="green" />
                <ResultBar label="平" value={result.tie} cls="muted" />
                <ResultBar label="负" value={result.lose} cls="red" />
              </div>
              <div className="small muted" style={{ marginTop: 8 }}>
                当前牌型：{result.handName} · 对手 {result.opponents} · 抽样 {result.iterations} 次
              </div>
            </div>
          )}
          {result?.error && <div className="small red" style={{ marginTop: 8 }}>{result.error}</div>}
        </div>
      )}

      {tab === 'train' && (
        <div className="card">
          <div className="card-title">练习题 {qi + 1} / {quiz.length} · 答对 {score}</div>
          {!current && <div className="empty">已练完，刷新可再来一组</div>}
          {current && (
            <>
              <div style={{ fontSize: 15, lineHeight: 1.6, marginBottom: 12 }}>{current.question}</div>
              {options.map((op, i) => (
                <div
                  key={i}
                  className={'option' + (picked === i ? ' picked' : '') + (judge && judge.answer === i ? ' right' : '')}
                  onClick={() => !judge && answer(i)}
                >
                  {String.fromCharCode(65 + i)}. {op}
                </div>
              ))}
              {judge && (
                <div className="judge">
                  <div className={judge.correct ? 'green' : 'red'}>{judge.correct ? '答对了' : '答错了'}</div>
                  <div className="small muted" style={{ marginTop: 6 }}>{judge.explain}</div>
                  <button className="btn sm primary" style={{ marginTop: 10 }} onClick={nextQuiz}>下一题</button>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {tab === 'mine' && (
        <>
          <div className="card">
            <div className="card-title">我的收藏</div>
            {favorites.length === 0 && <div className="empty">还没有收藏</div>}
            {favorites.map((f: any) => (
              <div className="list-item" key={f.id}>
                <div style={{ flex: 1 }}>
                  <div>{f.title}</div>
                  <div className="small muted ellipsis">{f.summary}</div>
                </div>
              </div>
            ))}
          </div>

          <div className="card">
            <div className="list-item"
              onClick={() => {
                haptic();
                const link = cfg?.botUsername
                  ? `https://t.me/${cfg.botUsername}?startapp=share`
                  : 'https://t.me';
                window.open('https://t.me/share/url?url=' + encodeURIComponent(link) +
                  '&text=' + encodeURIComponent('一起来打德州，发牌可验证：' + link), '_blank');
              }}
            >
              <div style={{ flex: 1 }}>分享给好友</div>
              <div className="small muted">›</div>
            </div>
            <div className="list-item"
              onClick={() => {
                haptic();
                if (cfg?.groupUrl) openLink(cfg.groupUrl);
                else showToast('官方群未配置');
              }}
            >
              <div style={{ flex: 1 }}>官方群</div>
              <div className="small muted">{cfg?.groupUrl ? '加入 ›' : '未配置 ›'}</div>
            </div>
            <div className="list-item" onClick={() => { haptic(); setPrefOpen(true); }}>
              <div style={{ flex: 1 }}>设置</div>
              <div className="small muted">音效 / 震动 ›</div>
            </div>
          </div>
        </>
      )}

      {open && (
        <Modal title={open.title} onClose={() => setOpen(null)} okText="收藏" onOk={() => {
          api(`/api/teach/lessons/${open.id}/favorite`, { method: 'POST', body: { on: true } }).catch(() => undefined);
          return true;
        }}>
          <div className="small" style={{ lineHeight: 1.9 }}>{open.content || open.summary}</div>
          <div className="small muted" style={{ marginTop: 10 }}>
            {open.mediaType === 'VIDEO' ? '视频地址：' + (open.mediaUrl || '待运营上传') : '图文内容'}
          </div>
        </Modal>
      )}

      {prefOpen && (
        <Modal title="教学设置" onClose={() => setPrefOpen(false)} okText="关闭" onOk={() => undefined}>
          <div className="list-item">
            <div style={{ flex: 1 }}>视频自动播放</div>
            <button className={'btn sm' + (on('autoPlay') ? ' primary' : '')} onClick={() => savePref('autoPlay', !on('autoPlay'))}>
              {on('autoPlay') ? '开' : '关'}
            </button>
          </div>
          <div className="list-item">
            <div style={{ flex: 1 }}>训练答对音效</div>
            <button className={'btn sm' + (on('sound') ? ' primary' : '')} onClick={() => savePref('sound', !on('sound'))}>
              {on('sound') ? '开' : '关'}
            </button>
          </div>
          <div className="small muted" style={{ marginTop: 8 }}>设置保存在本机</div>
        </Modal>
      )}
    </>
  );
}

function ResultBar({ label, value, cls }: { label: string; value: number; cls: string }) {
  return (
    <div style={{ flex: 1, textAlign: 'center' }}>
      <div className={'mono ' + cls} style={{ fontSize: 18 }}>{(value * 100).toFixed(1)}%</div>
      <div className="small muted">{label}</div>
    </div>
  );
}

function fmtDur(sec: number) {
  if (!sec) return '—';
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

function safeParse(text: string): string[] {
  try {
    const v = JSON.parse(text);
    return Array.isArray(v) ? v.map(String) : [];
  } catch {
    return [];
  }
}
