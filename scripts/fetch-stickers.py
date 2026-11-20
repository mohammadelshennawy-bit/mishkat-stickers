# Builds www/stickers/all/*.webp (512x512, <=100KB) and www/stickers/tray/*.png (96x96)
# from the Mishkat site's img/stickers folder, so the stickers live in one place only.
# Usage: python3 scripts/fetch-stickers.py <path-to-site-img-stickers>
import json, os, shutil, sys
from PIL import Image

SRC = sys.argv[1].rstrip('/') + '/'
W = os.path.join(os.path.dirname(__file__), '..', 'www', 'stickers')
lib = json.load(open(os.path.join(W, 'library.json'), encoding='utf-8'))
os.makedirs(os.path.join(W, 'all'), exist_ok=True)
os.makedirs(os.path.join(W, 'tray'), exist_ok=True)

def enc(im, p):
    for kw in (dict(lossless=True, method=4), dict(quality=92, method=4),
               dict(quality=86, method=4), dict(quality=78, method=4), dict(quality=65, method=6)):
        im.save(p, 'WEBP', **kw)
        if os.path.getsize(p) <= 100 * 1024:
            return

ok, missing = 0, []
for x in lib['items']:
    sid, src = x['id'], x.get('src') or x['id'] + '.png'
    f = SRC + src
    if not os.path.exists(f):
        alt = os.path.splitext(f)[0] + '.webp'
        if os.path.exists(alt): f = alt
        else: missing.append(sid); continue
    p = os.path.join(W, 'all', sid + '.webp')
    im = Image.open(f).convert('RGBA')
    if im.size != (512, 512):
        im.thumbnail((512, 512), Image.LANCZOS)
        c = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
        c.paste(im, ((512 - im.width) // 2, (512 - im.height) // 2)); im = c
    if f.endswith('.webp') and Image.open(f).size == (512, 512) and os.path.getsize(f) <= 100 * 1024:
        shutil.copy(f, p)
    else:
        enc(im, p)
    t = os.path.join(W, 'tray', sid + '.png')
    im.resize((96, 96), Image.LANCZOS).save(t, optimize=True)
    if os.path.getsize(t) > 50 * 1024:
        im.resize((96, 96), Image.LANCZOS).quantize(128).save(t, optimize=True)
    ok += 1

print('stickers built:', ok, 'missing:', len(missing), missing[:20])
if ok < 30: sys.exit('too few stickers')
# keep default packs valid: drop stickers whose files are missing
pf = os.path.join(W, 'packs-default.json'); P = json.load(open(pf, encoding='utf-8'))
have = set(os.path.splitext(n)[0] for n in os.listdir(os.path.join(W, 'all')))
for pk in P['sticker_packs']:
    pk['stickers'] = [s for s in pk['stickers'] if os.path.splitext(s['image_file'])[0] in have][:30]
    if os.path.splitext(pk['tray_image_file'][5:])[0] not in have and pk['stickers']:
        pk['tray_image_file'] = 'tray_' + os.path.splitext(pk['stickers'][0]['image_file'])[0] + '.png'
P['sticker_packs'] = [pk for pk in P['sticker_packs'] if len(pk['stickers']) >= 3]
json.dump(P, open(pf, 'w', encoding='utf-8'), ensure_ascii=False)
print('packs:', [len(pk['stickers']) for pk in P['sticker_packs']])
