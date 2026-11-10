import { create } from 'zustand';
import { api, setToken } from './api.ts';
import { initTelegram, haptic } from './tg.ts';
import { TableSocket } from './ws.ts';
import type { InsuranceQuote, LobbyItem, Me, TableView } from './types.ts';

let socket: TableSocket | null = null;

interface AppState {
  booted: boolean;
  bootError: string;
  me: Me | null;
  tables: LobbyItem[];
  view: TableView | null;
  tableId: string;
  watchOnly: boolean;
  quote: InsuranceQuote | null;
  toast: string;

  boot: () => Promise<void>;
  showToast: (msg: string) => void;
  refreshMe: () => Promise<void>;
  loadLobby: (variant?: string, clubId?: number) => Promise<void>;  enterTable: (id: string, watch?: boolean) => void;
  leaveTable: () => void;
  doAction: (act: string, amt?: number) => void;
  doSit: (buyIn: number) => void;
  doRebuy: (amount: number) => void;
  doQuote: () => void;
  doInsurance: (coverage: number) => void;
  doSubmitSeed: (seed: string) => void;
  doChat: (text: string) => void;
}

export const useApp = create<AppState>((set, get) => ({
  booted: false,
  bootError: '',
  me: null,
  tables: [],
  view: null,
  tableId: '',
  watchOnly: false,
  quote: null,
  toast: '',

  showToast: (msg) => {
    set({ toast: msg });
    window.setTimeout(() => {
      if (get().toast === msg) set({ toast: '' });
    }, 2200);
  },

  boot: async () => {
    const tg = initTelegram();
    try {
      const res = await api<{ token: string; userId: number; nick: string; avatar: string }>(
        '/api/auth/login',
        { method: 'POST', auth: false, body: { initData: tg.initData, devId: tg.devId, devName: tg.devName } }
      );
      setToken(res.token);
      await get().refreshMe();
      // 深链：t.me/bot?startapp=clubId 之类，这里只处理牌桌
      if (tg.startParam && tg.startParam.startsWith('table_')) {
        location.hash = '#table/' + tg.startParam.substring(6);
      }
      set({ booted: true });
    } catch (e: any) {
      set({ bootError: e.message || '登录失败' });
    }
  },

  refreshMe: async () => {
    try {
      const me = await api<Me>('/api/me');
      set({ me });
    } catch {
      // ignore
    }
  },

  loadLobby: async (variant, clubId) => {
    try {
      const q = new URLSearchParams();
      if (variant && variant !== 'ALL') q.set('variant', variant);
      if (clubId && clubId > 0) q.set('clubId', String(clubId));
      const tables = await api<LobbyItem[]>('/api/lobby/tables' + (q.toString() ? '?' + q.toString() : ''));
      set({ tables });
    } catch (e: any) {
      get().showToast(e.message);
    }
  },

  enterTable: (id, watch = false) => {
    if (socket && get().tableId === id && get().watchOnly === watch) return;
    socket?.close();
    set({ tableId: id, view: null, watchOnly: watch, quote: null });
    socket = new TableSocket(
      id,
      (v) => set({ view: v as TableView }),
      (msg) => get().showToast(msg),
      (q) => set({ quote: q }),
      watch
    );
    socket.connect();
  },

  leaveTable: () => {
    socket?.leave();
    socket?.close();
    socket = null;
    set({ tableId: '', view: null, quote: null });
  },

  doAction: (act, amt = 0) => {
    haptic('tap');
    if (!socket?.action(act, amt)) get().showToast('连接已断开，正在重连…');
  },

  doSit: (buyIn) => {
    haptic('tap');
    const me = get().me;
    if (!socket?.sit(buyIn, -1, me?.nick || '玩家', me?.avatar || '')) {
      get().showToast('连接已断开，正在重连…');
    }
  },

  doRebuy: (amount) => {
    haptic('tap');
    if (!socket?.rebuy(amount)) get().showToast('连接已断开');
  },

  doQuote: () => {
    socket?.quoteInsurance();
  },

  doInsurance: (coverage) => {
    haptic('tap');
    if (!socket?.buyInsurance(coverage)) get().showToast('连接已断开');
  },

  doSubmitSeed: (seed) => {
    if (!socket?.submitSeed(seed)) get().showToast('连接已断开');
    else get().showToast('种子已提交，下一手生效');
  },

  doChat: (text) => {
    haptic('tap');
    if (!socket?.chat(text)) get().showToast('连接已断开');
  }
}));
