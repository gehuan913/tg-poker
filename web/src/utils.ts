export function fmt(micro: number): string {
  const v = (micro || 0) / 1e6;
  return v.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

export function fmtSigned(micro: number): string {
  const v = (micro || 0) / 1e6;
  return (v > 0 ? '+' : '') + v.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

export function toMicro(str: string): number {
  const v = parseFloat(str);
  if (!isFinite(v) || v <= 0) return 0;
  return Math.round(v * 1e6);
}

export const SUIT: Record<string, string> = { s: '♠', h: '♥', c: '♣', d: '♦' };

export function isRed(code: string): boolean {
  return code[1] === 'h' || code[1] === 'd';
}

export function rankText(code: string): string {
  return code[0] === 'T' ? '10' : code[0];
}
