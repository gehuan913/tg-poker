import { getToken } from './api.ts';

type ViewHandler = (v: any) => void;
type ErrorHandler = (msg: string) => void;
type QuoteHandler = (q: any) => void;

/**
 * 牌桌长连接。
 *
 * - 断线指数退避 + 25s 心跳（蜂窝 NAT 最短 30s，代理默认 60s）
 * - 重连后服务端直接推全量视图，等价于 snapshot
 * - role 只是"我想旁观"的声明，服务端按真实座位判定，客户端无法靠参数提权
 */
export class TableSocket {
  private ws: WebSocket | null = null;
  private closed = false;
  private retry = 0;
  private heartbeat: number | null = null;
  private pongAt = 0;
  private reconnectTimer: number | null = null;

  constructor(
    private tableId: string,
    private onView: ViewHandler,
    private onError: ErrorHandler,
    private onQuote?: QuoteHandler,
    private watchOnly = false
  ) {}

  private url() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const role = this.watchOnly ? 'SPECTATOR' : 'SEATED';
    return `${proto}://${location.host}/ws/table?tableId=${encodeURIComponent(this.tableId)}`
      + `&token=${encodeURIComponent(getToken())}&role=${role}`;
  }

  connect() {
    if (this.closed) return;
    try {
      this.ws = new WebSocket(this.url());
    } catch {
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.retry = 0;
      this.pongAt = Date.now();
      this.startHeartbeat();
    };

    this.ws.onmessage = (ev) => {
      let msg: any;
      try {
        msg = JSON.parse(ev.data);
      } catch {
        return;
      }
      if (msg.t === 'table') this.onView(msg.data);
      else if (msg.t === 'error') this.onError(msg.message);
      else if (msg.t === 'insuranceQuote') this.onQuote?.(msg);
      else if (msg.t === 'pong') this.pongAt = Date.now();
    };

    this.ws.onclose = () => {
      this.stopHeartbeat();
      this.scheduleReconnect();
    };

    this.ws.onerror = () => {
      try { this.ws?.close(); } catch { /* ignore */ }
    };
  }

  private scheduleReconnect() {
    if (this.closed) return;
    this.retry = Math.min(this.retry + 1, 6);
    const delay = Math.min(1000 * Math.pow(2, this.retry - 1), 8000);
    if (this.reconnectTimer) window.clearTimeout(this.reconnectTimer);
    this.reconnectTimer = window.setTimeout(() => this.connect(), delay);
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeat = window.setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        // 连续 3 次没有 pong 判死，主动断开重连
        if (Date.now() - this.pongAt > 75_000) {
          try { this.ws.close(); } catch { /* ignore */ }
          return;
        }
        this.ws.send(JSON.stringify({ t: 'ping' }));
      }
    }, 25000);
  }

  private stopHeartbeat() {
    if (this.heartbeat) window.clearInterval(this.heartbeat);
    this.heartbeat = null;
  }

  send(obj: Record<string, unknown>) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(obj));
      return true;
    }
    return false;
  }

  action(act: string, amt = 0) {
    return this.send({ t: 'action', act, amt });
  }

  sit(buyIn: number, seat: number, nick: string, avatar: string) {
    return this.send({ t: 'sit', buyIn, seat, nick, avatar });
  }

  rebuy(amount: number) {
    return this.send({ t: 'rebuy', amount });
  }

  leave() {
    return this.send({ t: 'leave' });
  }

  start() {
    return this.send({ t: 'start' });
  }

  submitSeed(seed: string) {
    return this.send({ t: 'clientSeed', seed });
  }

  /** 桌内发言（服务端 2 秒限频、60 字上限）。 */
  chat(text: string) {
    return this.send({ t: 'chat', text });
  }

  quoteInsurance() {
    return this.send({ t: 'insuranceQuote' });
  }

  buyInsurance(coverage: number) {
    return this.send({ t: 'insurance', coverage });
  }

  close() {
    this.closed = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) window.clearTimeout(this.reconnectTimer);
    try { this.ws?.close(); } catch { /* ignore */ }
  }
}
