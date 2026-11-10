/*
 * KK 德州 · 发牌验证脚本
 * ------------------------------------------------------------
 * 零依赖、可离线运行。用 Node 或浏览器控制台都能跑：
 *
 *   node verify.js <serverSeed> <clientSeeds> <nonce> <LONG|SHORT>
 *
 *   <clientSeeds> 多人用逗号分隔；没有就传空字符串 ""
 *   <nonce>       手号（第几手）
 *
 * 输出：完整牌序 + 前 2*n 张发牌位置（n 由你传入的座位数决定时可在代码里调整）
 *
 * 算法（与服务端一字不差）：
 *   seed = HMAC_SHA256(serverSeed, clientSeeds + ":" + nonce)
 *   deck = FisherYates(标准牌序, HMAC-DRBG(seed))
 *
 * 如果你用这个脚本算出的牌序与平台记录不一致，说明平台作弊，请立即停止游戏。
 */
(function (global) {
  'use strict';

  // ---------------- SHA-256（纯 JS，无依赖） ----------------
  var K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ];

  function utf8(str) {
    if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(str);
    return Buffer.from(str, 'utf8');
  }

  function rotr(x, n) {
    return (x >>> n) | (x << (32 - n));
  }

  function sha256(bytes) {
    var H = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
             0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
    var msg = Array.prototype.slice.call(bytes);
    var bitLen = msg.length * 8;
    msg.push(0x80);
    while (msg.length % 64 !== 56) msg.push(0);
    for (var i = 7; i >= 0; i--) msg.push((bitLen / Math.pow(2, i * 8)) & 0xff);

    var w = new Array(64);
    for (var off = 0; off < msg.length; off += 64) {
      for (var t = 0; t < 16; t++) {
        w[t] = (msg[off + t * 4] << 24) | (msg[off + t * 4 + 1] << 16) |
               (msg[off + t * 4 + 2] << 8) | msg[off + t * 4 + 3];
      }
      for (t = 16; t < 64; t++) {
        var s0 = rotr(w[t - 15], 7) ^ rotr(w[t - 15], 18) ^ (w[t - 15] >>> 3);
        var s1 = rotr(w[t - 2], 17) ^ rotr(w[t - 2], 19) ^ (w[t - 2] >>> 10);
        w[t] = (w[t - 16] + s0 + w[t - 7] + s1) | 0;
      }
      var a = H[0], b = H[1], c = H[2], d = H[3], e = H[4], f = H[5], g = H[6], h = H[7];
      for (t = 0; t < 64; t++) {
        var S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
        var ch = (e & f) ^ (~e & g);
        var t1 = (h + S1 + ch + K[t] + w[t]) | 0;
        var S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
        var maj = (a & b) ^ (a & c) ^ (b & c);
        var t2 = (S0 + maj) | 0;
        h = g; g = f; f = e; e = (d + t1) | 0;
        d = c; c = b; b = a; a = (t1 + t2) | 0;
      }
      H[0] = (H[0] + a) | 0; H[1] = (H[1] + b) | 0; H[2] = (H[2] + c) | 0; H[3] = (H[3] + d) | 0;
      H[4] = (H[4] + e) | 0; H[5] = (H[5] + f) | 0; H[6] = (H[6] + g) | 0; H[7] = (H[7] + h) | 0;
    }
    var out = [];
    for (var k = 0; k < 8; k++) {
      out.push((H[k] >>> 24) & 0xff, (H[k] >>> 16) & 0xff, (H[k] >>> 8) & 0xff, H[k] & 0xff);
    }
    return out;
  }

  function hmac(keyBytes, msgBytes) {
    var block = 64;
    var key = keyBytes.slice();
    if (key.length > block) key = sha256(key);
    while (key.length < block) key.push(0);
    var ipad = [], opad = [];
    for (var i = 0; i < block; i++) {
      ipad.push(key[i] ^ 0x36);
      opad.push(key[i] ^ 0x5c);
    }
    return sha256(opad.concat(sha256(ipad.concat(msgBytes))));
  }

  function hex(bytes) {
    return bytes.map(function (b) { return ('0' + (b & 0xff).toString(16)).slice(-2); }).join('');
  }

  function utf8Bytes(str) { return Array.prototype.slice.call(utf8(str)); }

  function longToBytes(v) {
    var b = [];
    for (var i = 7; i >= 0; i--) {
      b.push(v & 0xff);
      v = Math.floor(v / 256);
    }
    return b.reverse();
  }

  // ---------------- HMAC-DRBG（与服务端一致） ----------------
  function Drbg(seed) {
    this.seed = seed;
    this.counter = 0;
    this.buf = [];
    this.pos = 0;
  }
  Drbg.prototype.nextByte = function () {
    if (this.pos >= this.buf.length) {
      this.buf = hmac(this.seed, longToBytes(this.counter++));
      this.pos = 0;
    }
    return this.buf[this.pos++] & 0xff;
  };
  Drbg.prototype.nextInt = function (bound) {
    var bits, val;
    do {
      bits = (((this.nextByte() << 24) | (this.nextByte() << 16) |
               (this.nextByte() << 8) | this.nextByte()) >>> 1);
      val = bits % bound;
    } while (bits - val + (bound - 1) < 0);
    return val;
  };

  // ---------------- 牌堆 ----------------
  var RANKS = ['2', '3', '4', '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'A'];
  var SUITS = ['s', 'h', 'c', 'd'];

  function buildDeck(variant) {
    var minRank = variant === 'SHORT' ? 6 : 2;
    var deck = [];
    for (var s = 0; s < 4; s++) {
      for (var r = minRank; r <= 14; r++) {
        deck.push(RANKS[r - 2] + SUITS[s]);
      }
    }
    return deck;
  }

  function order(serverSeed, clientSeeds, nonce, variant) {
    var msg = (clientSeeds == null ? '' : clientSeeds) + ':' + nonce;
    var seed = hmac(utf8Bytes(serverSeed), utf8Bytes(msg));
    var deck = buildDeck(variant || 'LONG');
    var drbg = new Drbg(seed);
    for (var i = deck.length - 1; i > 0; i--) {
      var j = drbg.nextInt(i + 1);
      var tmp = deck[i]; deck[i] = deck[j]; deck[j] = tmp;
    }
    return deck;
  }

  var KKVerify = {
    sha256Hex: function (s) { return hex(sha256(utf8Bytes(s))); },
    order: order,
    // 前 n 张发牌：2 张/人按座位顺序发两轮，之后 3 张翻牌 + 1 转牌 + 1 河牌
    deal: function (serverSeed, clientSeeds, nonce, variant, players) {
      var deck = order(serverSeed, clientSeeds, nonce, variant);
      var idx = 0;
      var holes = [];
      for (var round = 0; round < 2; round++) {
        for (var p = 0; p < players; p++) {
          if (!holes[p]) holes[p] = [];
          holes[p][round] = deck[idx++];
        }
      }
      var board = [deck[idx++], deck[idx++], deck[idx++]];
      if (deck.length - idx > 0) board.push(deck[idx++]);
      if (deck.length - idx > 0) board.push(deck[idx++]);
      return { holes: holes, board: board, deck: deck };
    }
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = KKVerify;
  global.KKVerify = KKVerify;

  // Node CLI
  if (typeof require !== 'undefined' && typeof module !== 'undefined' && require.main === module) {
    var a = process.argv.slice(2);
    if (a.length < 3) {
      console.log('用法: node verify.js <serverSeed> <clientSeeds> <nonce> [LONG|SHORT] [players]');
      process.exit(1);
    }
    var res = KKVerify.deal(a[0], a[1], a[2], a[3] || 'LONG', parseInt(a[4] || '6', 10));
    console.log('sha256(serverSeed) 应为公示的 serverSeedHash：');
    console.log('  ' + KKVerify.sha256Hex(a[0]));
    console.log('完整牌序：');
    console.log('  ' + res.deck.join(' '));
    console.log('每人底牌：');
    res.holes.forEach(function (h, i) { console.log('  seat ' + i + ': ' + h.join(' ')); });
    console.log('公牌：' + res.board.join(' '));
  }
})(typeof window !== 'undefined' ? window : global);
