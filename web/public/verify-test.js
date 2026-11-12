/*
 * verify.js 自测脚本
 * ------------------------------------------------------------
 * 用法：
 *   node verify-test.js
 *
 * 读取同目录 verify-vectors.json 里的 3 组已知输入/输出，
 * 逐项校验 sha256(serverSeed)、牌堆大小、前 10 张、整副牌 hash、
 * 每人底牌与公牌。全部通过退出码 0，否则 1。
 *
 * 为什么要有它：站内"验证通过"是自证，第三方拿到 verify.js 后
 * 必须先能自测脚本本身没被篡改，再谈验证平台。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

const sandbox = {};
new Function('window', 'global', 'module', 'require', 'exports',
  fs.readFileSync(path.join(here, 'verify.js'), 'utf8')
)(undefined, sandbox, undefined, undefined, undefined);
const KK = sandbox.KKVerify;

const vectors = JSON.parse(fs.readFileSync(path.join(here, 'verify-vectors.json'), 'utf8'));
let failed = 0;

function eq(name, actual, expected) {
  const a = JSON.stringify(actual);
  const b = JSON.stringify(expected);
  if (a !== b) {
    console.error(`  x ${name}\n    实际 ${a}\n    期望 ${b}`);
    failed++;
  } else {
    console.log(`  ok ${name}`);
  }
}

for (const c of vectors.cases) {
  console.log(`\n[${c.name}]`);
  const r = KK.deal(c.input.serverSeed, c.input.clientSeeds, c.input.nonce, c.input.variant, c.input.players);
  eq('sha256(serverSeed)', KK.sha256Hex(c.input.serverSeed), c.expected.serverSeedHash);
  eq('牌堆张数', r.deck.length, c.expected.deckSize);
  eq('前 10 张', r.deck.slice(0, 10), c.expected.deckFirst10);
  eq('整副牌 hash', KK.sha256Hex(r.deck.join(' ')), c.expected.deckSha256);
  eq('每人底牌', r.holes, c.expected.holes);
  eq('公牌', r.board, c.expected.board);
}

console.log(failed === 0 ? '\n全部通过' : `\n${failed} 项不通过`);
process.exit(failed === 0 ? 0 : 1);
