const fs = require('fs');
// 用独立上下文执行，避免触发脚本自带的 CLI 分支
const sandbox = {};
new Function('window', 'global', 'module', 'require', 'exports',
  fs.readFileSync(__dirname + '/verify.js', 'utf8')
)(undefined, sandbox, undefined, undefined, undefined);
const KKVerify = sandbox.KKVerify;

const cases = [
  { name: 'LONG-单人种子', serverSeed: '0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0', clientSeeds: '', nonce: '1', variant: 'LONG', players: 6 },
  { name: 'LONG-双人种子', serverSeed: 'aa11bb22cc33dd44ee55ff6600778899aabbccddeeff00112233445566778899', clientSeeds: 'deadbeefcafebabe,saltsalt12345678', nonce: '12', variant: 'LONG', players: 6 },
  { name: 'SHORT-短牌', serverSeed: '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef', clientSeeds: 'p1seed', nonce: '7', variant: 'SHORT', players: 4 }
];
const out = cases.map(c => {
  const r = KKVerify.deal(c.serverSeed, c.clientSeeds, c.nonce, c.variant, c.players);
  return {
    name: c.name,
    input: c,
    expected: {
      serverSeedHash: KKVerify.sha256Hex(c.serverSeed),
      deckSize: r.deck.length,
      deckFirst10: r.deck.slice(0, 10),
      deckSha256: KKVerify.sha256Hex(r.deck.join(' ')),
      holes: r.holes,
      board: r.board
    }
  };
});
fs.writeFileSync(__dirname + '/verify-vectors.json', JSON.stringify({ version: 1, note: '用于第三方自测 verify.js；expected 由同一算法生成，仅作回归基线', cases: out }, null, 2));
console.log('cases', out.length, '| first10:', out[0].expected.deckFirst10.join(' '), '| hash:', out[0].expected.serverSeedHash.slice(0, 16));
