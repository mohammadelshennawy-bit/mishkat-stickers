# يبني ملفات تطبيق ملصقات واحد (أ، ب، ج…) من «كتالوج» الموقع الذي تحرره لوحة المدير:
#   img/stickers/catalog.json  ← الحزم والتطبيقات
#   img/stickers/index.json    ← الملصقات (النص والتصنيف واسم الملف)
#   img/stickers/categories.json
# الناتج داخل www/stickers:
#   library.json (ملصقات هذا التطبيق) · packs-default.json (حزمه بصيغة واتساب) · all/<id>.webp · tray/<id>.png
# ويكتب app.json (الاسم ومعرّف أندرويد واسم ملف APK) لتستخدمه خطوات البناء.
#
# الاستعمال:  python3 scripts/build-app.py <site/img/stickers> <معرّف التطبيق مثل a>
#             python3 scripts/build-app.py <site/img/stickers> --list   ← يطبع قائمة التطبيقات (JSON) لمصفوفة البناء
# إن لم يوجد catalog.json يُستخدم السلوك القديم (fetch-stickers.py) للتطبيق (أ) فقط.
import json, os, sys, shutil, subprocess
from PIL import Image

SRC = sys.argv[1].rstrip('/') + '/'
ARG = sys.argv[2] if len(sys.argv) > 2 else 'a'
HERE = os.path.dirname(os.path.abspath(__file__))
W = os.path.join(HERE, '..', 'www', 'stickers')
CATF = SRC + 'catalog.json'

DEFAULT_APP = {'id': 'a', 'letter': 'أ', 'name': 'ملصقات مشكاة', 'nameEn': 'Mishkat Stickers', 'appId': 'com.mishkat.stickers', 'packs': []}
EMO = {'occasion': ['🎉', '🌙'], 'greet': ['☀️', '🌙'], 'salam': ['👋', '🤝'], 'thanks': ['🙏', '❤️'], 'congrats': ['🎉', '🥳'], 'religious': ['🌙', '🕌'],
       'dhikr': ['📿', '🤲'], 'dua': ['🤲', '💚'], 'hadith': ['📜', '💚'], 'health': ['🤲', '💐'], 'condolence': ['🤲', '🕊️'], 'love': ['❤️', '🌹'],
       'fun': ['😄', '👍'], 'daily': ['☕', '😊'], 'travel': ['✈️', '🧳'], 'wishes': ['🤲', '💚']}

def apk_name(a):
    return 'mishkat-stickers.apk' if a['appId'] == 'com.mishkat.stickers' else 'mishkat-stickers-' + a['id'] + '.apk'

cat = json.load(open(CATF, encoding='utf-8')) if os.path.exists(CATF) else None
apps = [a for a in (cat or {}).get('apps', []) if a.get('packs')] if cat else []

if ARG == '--list':
    print(json.dumps([a['id'] for a in apps] or ['a']))
    sys.exit(0)

if not cat or not apps:
    print('catalog.json غير موجود أو بلا تطبيقات: البناء بالطريقة القديمة')
    subprocess.check_call([sys.executable, os.path.join(HERE, 'fetch-stickers.py'), SRC])
    app = dict(DEFAULT_APP)
else:
    app = next((a for a in apps if a['id'] == ARG), None)
    if not app:
        sys.exit('تطبيق غير موجود في catalog.json: ' + ARG)
    idx = json.load(open(SRC + 'index.json', encoding='utf-8'))
    by = {i['id']: i for i in idx}
    cats = json.load(open(SRC + 'categories.json', encoding='utf-8')) if os.path.exists(SRC + 'categories.json') else {}
    old = {}
    try:
        old = {i['id']: i for i in json.load(open(os.path.join(W, 'library.json'), encoding='utf-8'))['items']}
    except Exception:
        pass
    packs = [p for p in (next((x for x in cat['packs'] if x['id'] == pid), None) for pid in app['packs']) if p]
    ids = []
    for p in packs:
        p['ids'] = [i for i in p['ids'] if i in by][:30]
        ids += [i for i in p['ids'] if i not in ids]

    def em(i):
        return (old.get(i) or {}).get('em') or EMO.get(by[i].get('cat'), ['😊'])

    lib = {'cats': {k: v for k, v in cats.items() if any(by[i].get('cat') == k for i in ids)},
           'items': [{'id': i, 'src': by[i].get('src') or i + '.png', 'cat': by[i].get('cat', 'occasion'), 'ar': by[i].get('ar', ''),
                      'en': by[i].get('en') or by[i].get('ar', ''), 'em': em(i), 'acc': (by[i].get('en') or by[i].get('ar') or '')[:120]} for i in ids]}

    # الصور
    for d in ('all', 'tray'):
        shutil.rmtree(os.path.join(W, d), ignore_errors=True)
        os.makedirs(os.path.join(W, d))

    def enc(im, p):
        for kw in (dict(lossless=True, method=4), dict(quality=92, method=4), dict(quality=86, method=4), dict(quality=78, method=4), dict(quality=65, method=6), dict(quality=50, method=6)):
            im.save(p, 'WEBP', **kw)
            if os.path.getsize(p) <= 100 * 1024:
                return

    have, missing = set(), []
    for it in lib['items']:
        f = SRC + it['src']
        if not os.path.exists(f):
            alt = [SRC + it['id'] + e for e in ('.webp', '.png') if os.path.exists(SRC + it['id'] + e)]
            if not alt:
                missing.append(it['id']); continue
            f = alt[0]
        im = Image.open(f).convert('RGBA')
        if im.size != (512, 512):
            im.thumbnail((512, 512), Image.LANCZOS)
            c = Image.new('RGBA', (512, 512), (0, 0, 0, 0)); c.paste(im, ((512 - im.width) // 2, (512 - im.height) // 2)); im = c
        p = os.path.join(W, 'all', it['id'] + '.webp')
        if f.endswith('.webp') and Image.open(f).size == (512, 512) and os.path.getsize(f) <= 100 * 1024:
            shutil.copy(f, p)
        else:
            enc(im, p)
        t = os.path.join(W, 'tray', it['id'] + '.png')
        im.resize((96, 96), Image.LANCZOS).save(t, optimize=True)
        if os.path.getsize(t) > 50 * 1024:
            im.resize((96, 96), Image.LANCZOS).quantize(128).save(t, optimize=True)
        have.add(it['id'])
    lib['items'] = [i for i in lib['items'] if i['id'] in have]

    out = []
    for p in packs:
        s = [i for i in p['ids'] if i in have]
        if len(s) < 3:
            print('تخطي حزمة فيها أقل من 3 ملصقات:', p['id']); continue
        tray = p.get('tray') if p.get('tray') in s else s[0]
        out.append({'identifier': p['id'], 'name': p['name'][:120], 'name_en': p.get('nameEn', ''), 'publisher': 'مشكاة · Mishkat',
                    'tray_image_file': 'tray_' + tray + '.png', 'image_data_version': str(p.get('v', 1)), 'avoid_cache': False,
                    'publisher_email': 'mishkatapps@gmail.com', 'publisher_website': 'https://mishkatapps.github.io/',
                    'privacy_policy_website': '', 'license_agreement_website': '', 'animated_sticker_pack': False,
                    'stickers': [{'image_file': i + '.webp', 'emojis': em(i)[:3], 'accessibility_text': (by[i].get('en') or by[i].get('ar') or '')[:120]} for i in s]})
    if not out:
        sys.exit('لا توجد حزمة صالحة في ' + app['name'])
    json.dump(lib, open(os.path.join(W, 'library.json'), 'w', encoding='utf-8'), ensure_ascii=False)
    json.dump({'android_play_store_link': '', 'ios_app_store_link': '', 'catalog_version': cat.get('version', ''), 'app_name': app['name'], 'app_name_en': app.get('nameEn', ''),
               'sticker_packs': out}, open(os.path.join(W, 'packs-default.json'), 'w', encoding='utf-8'), ensure_ascii=False)
    print(app['name'], '· الحزم:', [len(p['stickers']) for p in out], '· الملصقات:', len(have), '· مفقود:', missing[:20])

info = {'id': app['id'], 'name': app['name'], 'nameEn': app.get('nameEn', ''), 'appId': app['appId'], 'apk': apk_name(app)}
json.dump(info, open(os.path.join(HERE, '..', 'app.json'), 'w', encoding='utf-8'), ensure_ascii=False)
print('app.json:', info)
