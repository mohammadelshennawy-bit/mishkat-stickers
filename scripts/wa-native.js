// يُجهّز مشروع أندرويد لملصقات واتساب بعد «cap add/sync» (آمن عند تكراره):
// 1) ينسخ ملفات Java (المزوّد + إضافة Capacitor)  2) يسجّل الإضافة في MainActivity
// 3) يضيف المزوّد و<queries> إلى AndroidManifest  4) يمنع ضغط webp/png داخل الحزمة (يطلبه openFd)
const fs = require('fs'), path = require('path');
const APP = 'android/app', PKG = 'com.mishkat.stickers';
if (!fs.existsSync(APP)) process.exit(0);
const javaDir = path.join(APP, 'src/main/java', ...PKG.split('.'));
fs.mkdirSync(javaDir, { recursive: true });
for (const f of ['StickerContentProvider.java', 'WaStickersPlugin.java']) fs.copyFileSync(path.join('native', f), path.join(javaDir, f));

const ma = path.join(javaDir, 'MainActivity.java');
fs.writeFileSync(ma, `package ${PKG};

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(WaStickersPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
`);

const mf = path.join(APP, 'src/main/AndroidManifest.xml');
let x = fs.readFileSync(mf, 'utf8');
if (!x.includes('.StickerContentProvider')) {
  x = x.replace('</application>', `    <provider
            android:name=".StickerContentProvider"
            android:authorities="\${applicationId}.stickercontentprovider"
            android:enabled="true"
            android:exported="true"
            android:readPermission="com.whatsapp.sticker.READ" />
    </application>`);
}
if (!x.includes('<package android:name="com.whatsapp" />')) {
  x = x.replace('</manifest>', `    <queries>
        <package android:name="com.whatsapp" />
        <package android:name="com.whatsapp.w4b" />
    </queries>
</manifest>`);
}
fs.writeFileSync(mf, x);

const bg = path.join(APP, 'build.gradle');
let g = fs.readFileSync(bg, 'utf8');
if (!g.includes("noCompress 'webp'")) {
  g = g.replace(/android\s*\{/, "android {\n    androidResources {\n        noCompress 'webp', 'png'\n    }");
  fs.writeFileSync(bg, g);
}
console.log('WhatsApp stickers native setup OK');
