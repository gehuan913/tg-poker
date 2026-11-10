const TOKEN_KEY = 'tgp_token';

let token = localStorage.getItem(TOKEN_KEY) || '';

export function setToken(t: string) {
  token = t || '';
  if (t) localStorage.setItem(TOKEN_KEY, t);
  else localStorage.removeItem(TOKEN_KEY);
}

export function getToken() {
  return token;
}

interface ApiOpts {
  method?: string;
  body?: unknown;
  auth?: boolean;
}

/** 后端统一返回 { code, message, data }，code !== 0 视为失败。 */
export async function api<T = any>(path: string, opts: ApiOpts = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = opts;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (auth && token) headers['X-Token'] = token;

  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  if (res.status === 401) {
    setToken('');
    throw new Error('登录已过期，请重新打开');
  }

  let json: any;
  try {
    json = await res.json();
  } catch {
    throw new Error('服务异常（' + res.status + '）');
  }

  if (json.code !== 0) throw new Error(json.message || '请求失败');
  return json.data as T;
}
