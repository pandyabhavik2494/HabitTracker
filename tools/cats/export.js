// Turns the mock's SVG cat stickers into a small JSON draw list the app renders natively.
// Usage: node export.js <mock index.html> <extra.js> <out.json>
const { chromium } = require('playwright');
const fs = require('fs');
const [,, mockPath, extraPath, outPath] = process.argv;
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  page.on('pageerror', e => console.error('page error', e.message));
  await page.goto('file://' + require('path').resolve(mockPath));
  await page.addScriptTag({ content: fs.readFileSync(extraPath, 'utf8') });
  const out = await page.evaluate(() => {
    const ANIM = ['bob','eyes','earR','whisk','tail','breathe','lick','wave','zz','peek','wig','sway','shiver','tw'];
    const FILLBOX = { bob:[.5,.9], eyes:[.5,.5], earR:[.3,1], whisk:[.5,.5] };
    const r2 = v => Math.round(v * 100) / 100;
    const host = document.createElement('div'); document.body.appendChild(host);
    const hex = (c, op = 1) => {
      const m = c.match(/rgba?\(([^)]+)\)/); if (!m) return null;
      const p = m[1].split(',').map(s => parseFloat(s));
      const a = Math.round(((p.length > 3 ? p[3] : 1) * op) * 255);
      return '#' + [p[0], p[1], p[2], a].map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
    };
    function paint(el, val, op) {
      if (!val || val === 'none') return null;
      const u = val.match(/url\("?#([^")]+)"?\)/);
      if (!u) return hex(val, op);
      const g = el.ownerSVGElement.getElementById(u[1]);
      const bb = el.getBBox();
      const stops = [...g.querySelectorAll('stop')].map(s => {
        const cs = getComputedStyle(s);
        return [parseFloat(s.getAttribute('offset')), hex(cs.stopColor, parseFloat(cs.stopOpacity) * op)];
      });
      const num = (a, d) => g.hasAttribute(a) ? parseFloat(g.getAttribute(a)) : d;
      if (g.tagName === 'linearGradient')
        return { lin: [bb.x + num('x1', 0) * bb.width, bb.y + num('y1', 0) * bb.height, bb.x + num('x2', 1) * bb.width, bb.y + num('y2', 0) * bb.height].map(r2), st: stops };
      return { rad: [bb.x + num('cx', .5) * bb.width, bb.y + num('cy', .5) * bb.height, num('r', .5) * bb.width, num('r', .5) * bb.height].map(r2), st: stops };
    }
    function shapeD(el) {
      const n = a => parseFloat(el.getAttribute(a) || 0);
      switch (el.tagName) {
        case 'path': return el.getAttribute('d');
        case 'circle': { const cx = n('cx'), cy = n('cy'), r = n('r'); return `M${cx - r} ${cy}A${r} ${r} 0 1 0 ${cx + r} ${cy}A${r} ${r} 0 1 0 ${cx - r} ${cy}Z`; }
        case 'ellipse': { const cx = n('cx'), cy = n('cy'), rx = n('rx'), ry = n('ry'); return `M${cx - rx} ${cy}A${rx} ${ry} 0 1 0 ${cx + rx} ${cy}A${rx} ${ry} 0 1 0 ${cx - rx} ${cy}Z`; }
        case 'rect': { const x = n('x'), y = n('y'), w = n('width'), h = n('height'); let r = Math.min(n('rx'), w / 2, h / 2);
          if (!r) return `M${x} ${y}H${x + w}V${y + h}H${x}Z`;
          return `M${x + r} ${y}H${x + w - r}A${r} ${r} 0 0 1 ${x + w} ${y + r}V${y + h - r}A${r} ${r} 0 0 1 ${x + w - r} ${y + h}H${x + r}A${r} ${r} 0 0 1 ${x} ${y + h - r}V${y + r}A${r} ${r} 0 0 1 ${x + r} ${y}Z`; }
      }
    }
    function walk(el) {
      const tag = el.tagName;
      if (['defs', 'linearGradient', 'radialGradient', 'clipPath', 'stop'].includes(tag)) return null;
      const cs = getComputedStyle(el);
      const node = {};
      const tl = el.transform && el.transform.baseVal.length ? el.transform.baseVal.consolidate().matrix : null;
      if (tl) node.m = [tl.a, tl.b, tl.c, tl.d, tl.e, tl.f].map(v => Math.round(v * 1e4) / 1e4);
      const op = parseFloat(el.getAttribute('opacity') || 1);
      const cls = (el.getAttribute('class') || '').split(/\s+/).find(c => ANIM.includes(c));
      if (cls) {
        let o = [0, 0];
        if (FILLBOX[cls]) { const bb = el.getBBox(); o = [bb.x + FILLBOX[cls][0] * bb.width, bb.y + FILLBOX[cls][1] * bb.height]; }
        else if (cls === 'breathe') o = [50, 93];
        else { const st = el.style.transformOrigin; if (st) o = st.split(' ').map(parseFloat); }
        node.a = [cls, r2(o[0]), r2(o[1])];
      }
      const cp = el.getAttribute('clip-path');
      if (cp) { const id = cp.match(/#([^)]+)/)[1]; const c = el.ownerSVGElement.getElementById(id).firstElementChild; node.clip = shapeD(c); }
      if (tag === 'g' || tag === 'svg') {
        node.t = 'g';
        if (op !== 1) node.o = op;
        node.c = [...el.children].map(walk).filter(Boolean);
        return node;
      }
      if (tag === 'text') {
        node.t = 'x'; node.s = el.textContent;
        node.x = parseFloat(el.getAttribute('x')); node.y = parseFloat(el.getAttribute('y'));
        node.fs = parseFloat(cs.fontSize); node.fw = parseInt(cs.fontWeight); node.ff = cs.fontFamily.split(',')[0].replace(/"/g, '');
        node.an = cs.textAnchor; node.f = paint(el, cs.fill, op * parseFloat(cs.fillOpacity));
        return node;
      }
      node.t = 'p'; node.d = shapeD(el);
      // The mock blurs Midnight's eye glow with CSS; here that becomes a soft radial gradient.
      if (cs.filter && cs.filter.includes('blur')) {
        const bb = el.getBBox(), c = hex(cs.fill, 1), r = bb.width / 2 + 4;
        node.d = shapeD({ tagName: 'circle', getAttribute: a => ({ cx: bb.x + bb.width / 2, cy: bb.y + bb.height / 2, r })[a] });
        node.f = { rad: [bb.x + bb.width / 2, bb.y + bb.height / 2, r, r].map(r2), st: [[0, c], [0.45, c.slice(0, 7) + '99'], [1, c.slice(0, 7) + '00']] };
        return node;
      }
      const f = paint(el, cs.fill, op * parseFloat(cs.fillOpacity)); if (f) node.f = f;
      const sc = paint(el, cs.stroke, op * parseFloat(cs.strokeOpacity));
      if (sc) {
        node.k = { c: sc, w: parseFloat(cs.strokeWidth) };
        if (cs.strokeLinecap !== 'butt') node.k.cap = cs.strokeLinecap;
        if (cs.strokeLinejoin !== 'miter') node.k.join = cs.strokeLinejoin;
        if (cs.strokeDasharray && cs.strokeDasharray !== 'none') node.k.dash = cs.strokeDasharray.split(/[ ,]+/).map(parseFloat);
      }
      return node;
    }
    const RAR = r => r.split(' ')[0];
    const result = {};
    for (const [id, k] of Object.entries(IDS)) {
      const m = BREEDS[k], pose = POSE[k];
      const rar = id === 'maneki' ? 'Legendary' : RAR(m.r);
      const holo = rar === 'Holo' || rar === 'Legendary';
      const one = sil => {
        host.innerHTML = stickerSvg(k, sil ? { fur: '#2a2f40', ...m.c } : m.c, sil ? { ...pose, bg: ['#1d2130', '#12141c'] } : pose, 100, holo && !sil, sil);
        return walk(host.firstElementChild);
      };
      result[id] = { name: m.n, breed: m.b, rarity: rar, line: (m.line || '').replace(/"/g, ''), bg: pose.bg, art: one(false), sil: one(true) };
    }
    return result;
  });
  fs.writeFileSync(outPath, JSON.stringify(out));
  console.log('cats', Object.keys(out).length, 'bytes', fs.statSync(outPath).size);
  await browser.close();
})();
