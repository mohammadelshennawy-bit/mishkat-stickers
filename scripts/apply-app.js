// يطبّق اسم التطبيق ومعرّف أندرويد (من app.json الذي يكتبه build-app.py) قبل «cap add android»:
//   capacitor.config.json (appId، appName) · ملفات Java (package) · scripts/wa-native.js (PKG) · عنوان الصفحة وmanifest
const fs = require('fs');
const app = JSON.parse(fs.readFileSync('app.json', 'utf8'));
const OLD = 'com.mishkat.stickers';
const cfg = JSON.parse(fs.readFileSync('capacitor.config.json', 'utf8'));
cfg.appId = app.appId; cfg.appName = app.name;
fs.writeFileSync('capacitor.config.json', JSON.stringify(cfg));
if (app.appId !== OLD) {
  for (const f of ['native/StickerContentProvider.java', 'native/WaStickersPlugin.java']) {
    fs.writeFileSync(f, fs.readFileSync(f, 'utf8').replace(/^package\s+[\w.]+;/m, `package ${app.appId};`));
  }
  fs.writeFileSync('scripts/wa-native.js', fs.readFileSync('scripts/wa-native.js', 'utf8').replace(`PKG = '${OLD}'`, `PKG = '${app.appId}'`));
}
const esc = s => s.replace(/[<>&]/g, c => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c]));
let h = fs.readFileSync('www/index.html', 'utf8');
h = h.replace(/<title>[^<]*<\/title>/, `<title>${esc(app.name)}</title>`).replace(/(<h1 data-t="title">)[^<]*(<\/h1>)/, `$1${esc(app.name)}$2`);
fs.writeFileSync('www/index.html', h);
const m = JSON.parse(fs.readFileSync('www/manifest.webmanifest', 'utf8')); m.name = m.short_name = app.name;
fs.writeFileSync('www/manifest.webmanifest', JSON.stringify(m));
console.log('App:', app.name, app.appId, '→', app.apk);
