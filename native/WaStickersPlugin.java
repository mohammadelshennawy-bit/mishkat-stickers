package com.mishkat.stickers;

/*
 * إضافة Capacitor: ربط واجهة التطبيق بواتساب.
 *  status({id})      → { whatsapp, business, added }   هل واتساب مثبّت؟ وهل الحزمة مضافة؟
 *  add({id, name})   → يفتح نافذة واتساب «إضافة حزمة ملصقات» ويعيد { ok, cancelled, error }
 *  savePacks({json}) → يحفظ «حزمي» (بصيغة contents.json) ليقرأها مزوّد الملصقات
 */

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "WaStickers")
public class WaStickersPlugin extends Plugin {
    private static final String WA = "com.whatsapp", WB = "com.whatsapp.w4b";

    private String authority() { return getContext().getPackageName() + ".stickercontentprovider"; }

    private boolean installed(String pkg) {
        try {
            getContext().getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** يسأل واتساب: هل أُضيفت هذه الحزمة؟ */
    private boolean added(String pkg, String id) {
        Uri u = new Uri.Builder().scheme("content").authority(pkg + ".provider.sticker_whitelist_check")
                .appendPath("is_whitelisted").appendQueryParameter("authority", authority())
                .appendQueryParameter("identifier", id).build();
        Cursor c = null;
        try {
            c = getContext().getContentResolver().query(u, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex("result");
                return i >= 0 && c.getInt(i) == 1;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return false;
    }

    @PluginMethod
    public void status(PluginCall call) {
        String id = call.getString("id", "");
        boolean wa = installed(WA), wb = installed(WB);
        JSObject r = new JSObject();
        r.put("whatsapp", wa);
        r.put("business", wb);
        r.put("added", (!wa || added(WA, id)) && (!wb || added(WB, id)) && (wa || wb));
        r.put("addedWa", wa && added(WA, id));
        r.put("addedWb", wb && added(WB, id));
        call.resolve(r);
    }

    @PluginMethod
    public void savePacks(PluginCall call) {
        String json = call.getString("json");
        if (json == null || !json.contains("sticker_packs")) { call.reject("json غير صالح"); return; }
        try {
            File tmp = new File(getContext().getFilesDir(), StickerContentProvider.USER_FILE + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(json.getBytes("UTF-8")); }
            File f = new File(getContext().getFilesDir(), StickerContentProvider.USER_FILE);
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
            StickerContentProvider.dirty = true;
            getContext().getContentResolver().notifyChange(new Uri.Builder().scheme("content").authority(authority()).appendPath("metadata").build(), null);
            JSObject r = new JSObject(); r.put("ok", true); call.resolve(r);
        } catch (Exception e) {
            call.reject("تعذّر الحفظ: " + e.getMessage());
        }
    }

    @PluginMethod
    public void add(PluginCall call) {
        String id = call.getString("id"), name = call.getString("name", "");
        String target = call.getString("target", "");
        if (id == null) { call.reject("id مطلوب"); return; }
        boolean wa = installed(WA), wb = installed(WB);
        if (!wa && !wb) { call.reject("واتساب غير مثبّت", "NO_WHATSAPP"); return; }
        Intent i = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        i.putExtra("sticker_pack_id", id);
        i.putExtra("sticker_pack_authority", authority());
        i.putExtra("sticker_pack_name", name);
        // واتساب العادي أولًا، أو واتساب الأعمال إن طُلب أو كان وحده المثبّت
        if (WB.equals(target) && wb) i.setPackage(WB);
        else if (WA.equals(target) && wa) i.setPackage(WA);
        else if (wa && !added(WA, id)) i.setPackage(WA);
        else if (wb && !added(WB, id)) i.setPackage(WB);
        else i.setPackage(wa ? WA : WB);
        try {
            startActivityForResult(call, i, "addResult");
        } catch (ActivityNotFoundException e) {
            call.reject("نسخة واتساب لا تدعم إضافة الملصقات — حدّث واتساب", "OLD_WHATSAPP");
        }
    }

    @ActivityCallback
    private void addResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject r = new JSObject();
        Intent data = result.getData();
        if (result.getResultCode() == Activity.RESULT_CANCELED) {
            String err = data != null ? data.getStringExtra("validation_error") : null;
            if (err != null) { r.put("ok", false); r.put("error", err); }
            else { r.put("ok", false); r.put("cancelled", true); }
        } else {
            r.put("ok", true);
        }
        call.resolve(r);
    }
}
