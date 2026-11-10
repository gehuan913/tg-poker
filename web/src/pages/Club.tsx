import { useEffect, useState } from 'react';
import { api } from '../api.ts';
import { Modal } from '../components/Modal.tsx';
import { haptic } from '../tg.ts';
import type { ClubItem } from '../types.ts';

export function Club() {
  const [mine, setMine] = useState<ClubItem[]>([]);
  const [all, setAll] = useState<ClubItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [notice, setNotice] = useState('');
  const [detail, setDetail] = useState<any>(null);
  const [toast, setToast] = useState('');

  useEffect(() => { reload(); }, []);

  function reload() {
    api<ClubItem[]>('/api/club/mine').then(setMine).catch(() => setMine([]));
    api<ClubItem[]>('/api/club/search').then(setAll).catch(() => setAll([]));
  }

  function showToast(m: string) {
    setToast(m);
    window.setTimeout(() => setToast(''), 2000);
  }

  async function search() {
    const list = await api<ClubItem[]>('/api/club/search?keyword=' + encodeURIComponent(keyword));
    setAll(list);
  }

  async function join(id: number) {
    try {
      await api(`/api/club/${id}/join`, { method: 'POST' });
      showToast('已加入');
      reload();
    } catch (e: any) {
      showToast(e.message);
    }
  }

  return (
    <>
      <div className="topbar">
        <div className="club-name">俱乐部</div>
        <button className="btn sm primary" onClick={() => { haptic(); setCreateOpen(true); }}>创建</button>
      </div>

      <div className="card">
        <div className="card-title">我的俱乐部</div>
        {mine.length === 0 && <div className="empty">还没有加入任何俱乐部</div>}
        {mine.map(c => (
          <div className="list-item" key={c.id} onClick={() => { haptic(); api(`/api/club/${c.id}`).then(setDetail); }}>
            <div className="avatar">{c.name[0]}</div>
            <div style={{ flex: 1 }}>
              <div>{c.name} <span className="small muted">ID {c.id}</span></div>
              <div className="small muted">{c.members} 成员 · {c.treasuryText} U 金库</div>
            </div>
            <div className="small muted">{c.role || ''}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <div className="card-title">发现俱乐部</div>
        <div className="row" style={{ gap: 8, marginBottom: 10 }}>
          <input className="seed-input" value={keyword} onChange={e => setKeyword(e.target.value)} placeholder="搜索名称" />
          <button className="btn sm" onClick={search}>搜索</button>
        </div>
        {all.length === 0 && <div className="empty">暂无</div>}
        {all.map(c => {
          const joined = mine.some(m => m.id === c.id);
          return (
            <div className="list-item" key={c.id}>
              <div className="avatar">{c.name[0]}</div>
              <div style={{ flex: 1 }}>
                <div>{c.name}</div>
                <div className="small muted">{c.members} 成员 · 最小买入 {c.minBuyInText} U</div>
              </div>
              <button className="btn sm" disabled={joined} onClick={() => join(c.id)}>
                {joined ? '已加入' : '加入'}
              </button>
            </div>
          );
        })}
      </div>

      {createOpen && (
        <Modal
          title="创建俱乐部"
          onClose={() => setCreateOpen(false)}
          onOk={async () => {
            if (!name.trim()) { showToast('请填写名称'); return false; }
            try {
              await api('/api/club/create', { method: 'POST', body: { name, notice, joinType: 'OPEN' } });
              setCreateOpen(false);
              setName('');
              setNotice('');
              reload();
              showToast('创建成功');
              return true;
            } catch (e: any) {
              showToast(e.message);
              return false;
            }
          }}
        >
          <div className="field">
            <label>俱乐部名称</label>
            <input value={name} onChange={e => setName(e.target.value)} />
          </div>
          <div className="field">
            <label>公告</label>
            <input value={notice} onChange={e => setNotice(e.target.value)} />
          </div>
          <div className="small muted">创建后你会成为盟主，负责金库与保险承保</div>
        </Modal>
      )}

      {detail && (
        <Modal title={detail.name} onClose={() => setDetail(null)} okText="关闭" onOk={() => undefined}>
          <div className="small" style={{ lineHeight: 1.9 }}>
            <div>成员：{detail.members} 人</div>
            <div>金库余额：{detail.treasury?.balanceText} U</div>
            <div>最低保证金：{detail.treasury?.minReserveText} U</div>
            <div>保险：{detail.insuranceEnabled ? '开启' : '关闭'}</div>
            <div className="divider" />
            <div className="muted">公告：{detail.notice || '无'}</div>
          </div>
        </Modal>
      )}

      {toast && <div className="toast">{toast}</div>}
    </>
  );
}
