package com.mishkat.stickers;

/*
 * مزوّد ملصقات واتساب لتطبيق «ملصقات مشكاة».
 * يقرأ الحزم من ملف «حزمي» الذي يحفظه التطبيق (files/wa_packs.json) — يعدّلها المستخدم من الواجهة —
 * وإن لم يوجد فمن الحزم الافتراضية assets/public/stickers/packs-default.json.
 * الملصقات في assets/public/stickers/all/<id>.webp وأيقونات الحزم في assets/public/stickers/tray/<id>.png
 * ويقدّمها لواتساب بالواجهة التي حدّدتها واتساب في تطبيقها النموذجي:
 *   <authority>/metadata                 كل الحزم
 *   <authority>/metadata/<id>            حزمة واحدة
 *   <authority>/stickers/<id>            ملصقات حزمة
 *   <authority>/stickers_asset/<id>/<f>  ملف الملصق أو أيقونة الحزمة
 * أسماء الأعمدة لا تُغيَّر لأن واتساب يعتمد عليها.
 */

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class StickerContentProvider extends ContentProvider {
    static final String BASE = "public/stickers";
    private static final String TAG = "MishkatStickers";

    static class Sticker { String file, emojis, text; }
    static class Pack {
        String id, name, publisher, tray, version, email, website, privacy, license;
        boolean avoidCache, animated;
        List<Sticker> stickers = new ArrayList<>();
    }

    static final String USER_FILE = "wa_packs.json";
    private List<Pack> packs;
    private long packsStamp = -1;
    static volatile boolean dirty = false;   // يضبطه WaStickersPlugin بعد حفظ «حزمي»
    private String playLink = "", iosLink = "";

    @Override public boolean onCreate() { return true; }

    private synchronized List<Pack> packs() {
        File uf = new File(getContext().getFilesDir(), USER_FILE);
        long stamp = uf.exists() ? uf.lastModified() : 0;
        if (packs != null && stamp == packsStamp && !dirty) return packs;
        packsStamp = stamp; dirty = false;
        List<Pack> out = new ArrayList<>();
        try (InputStream in = uf.exists() ? new FileInputStream(uf) : getContext().getAssets().open(BASE + "/packs-default.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject root = new JSONObject(bo.toString("UTF-8"));
            playLink = root.optString("android_play_store_link", "");
            iosLink = root.optString("ios_app_store_link", "");
            JSONArray arr = root.getJSONArray("sticker_packs");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Pack p = new Pack();
                p.id = o.getString("identifier");
                p.name = o.getString("name");
                p.publisher = o.getString("publisher");
                p.tray = o.getString("tray_image_file");
                p.version = o.optString("image_data_version", "1");
                p.avoidCache = o.optBoolean("avoid_cache", false);
                p.animated = o.optBoolean("animated_sticker_pack", false);
                p.email = o.optString("publisher_email", "");
                p.website = o.optString("publisher_website", "");
                p.privacy = o.optString("privacy_policy_website", "");
                p.license = o.optString("license_agreement_website", "");
                JSONArray st = o.getJSONArray("stickers");
                for (int j = 0; j < st.length(); j++) {
                    JSONObject s = st.getJSONObject(j);
                    Sticker k = new Sticker();
                    k.file = s.getString("image_file");
                    JSONArray em = s.optJSONArray("emojis");
                    StringBuilder sb = new StringBuilder();
                    if (em != null) for (int e = 0; e < em.length(); e++) { if (e > 0) sb.append(','); sb.append(em.getString(e)); }
                    k.emojis = sb.toString();
                    k.text = s.optString("accessibility_text", "");
                    p.stickers.add(k);
                }
                out.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "contents.json", e);
        }
        packs = out;
        return packs;
    }

    private Pack find(String id) {
        if (id == null) return null;
        for (Pack p : packs()) if (id.equals(p.id)) return p;
        return null;
    }

    private Cursor packCursor(Uri uri, List<Pack> list) {
        MatrixCursor c = new MatrixCursor(new String[]{
                "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
                "android_play_store_link", "ios_app_download_link", "sticker_pack_publisher_email",
                "sticker_pack_publisher_website", "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website", "image_data_version",
                "whatsapp_will_not_cache_stickers", "animated_sticker_pack"});
        for (Pack p : list) {
            c.addRow(new Object[]{p.id, p.name, p.publisher, p.tray, playLink, iosLink, p.email, p.website,
                    p.privacy, p.license, p.version, p.avoidCache ? 1 : 0, p.animated ? 1 : 0});
        }
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        List<String> seg = uri.getPathSegments();
        if (seg.size() == 1 && "metadata".equals(seg.get(0))) return packCursor(uri, packs());
        if (seg.size() == 2 && "metadata".equals(seg.get(0))) {
            List<Pack> one = new ArrayList<>();
            Pack p = find(seg.get(1));
            if (p != null) one.add(p);
            return packCursor(uri, one);
        }
        if (seg.size() == 2 && "stickers".equals(seg.get(0))) {
            MatrixCursor c = new MatrixCursor(new String[]{"sticker_file_name", "sticker_emoji", "sticker_accessibility_text"});
            Pack p = find(seg.get(1));
            if (p != null) for (Sticker s : p.stickers) c.addRow(new Object[]{s.file, s.emojis, s.text});
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private boolean allowed(Pack p, String file) {
        if (p == null || file == null) return false;
        if (file.equals(p.tray)) return true;
        for (Sticker s : p.stickers) if (file.equals(s.file)) return true;
        return false;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 3 || !"stickers_asset".equals(seg.get(0))) return null;
        Pack p = find(seg.get(1));
        String file = seg.get(2);
        if (!allowed(p, file)) return null;
        try {
            String path = file.startsWith("tray_") ? BASE + "/tray/" + file.substring(5) : BASE + "/all/" + file;
            return getContext().getAssets().openFd(path);
        } catch (Exception e) {
            Log.e(TAG, "openFd " + file, e);
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        String a = getContext().getPackageName() + ".stickercontentprovider";
        List<String> seg = uri.getPathSegments();
        if (seg.isEmpty()) return null;
        switch (seg.get(0)) {
            case "metadata": return (seg.size() == 1 ? "vnd.android.cursor.dir/vnd." : "vnd.android.cursor.item/vnd.") + a + ".metadata";
            case "stickers": return "vnd.android.cursor.dir/vnd." + a + ".stickers";
            case "stickers_asset": return seg.size() == 3 && seg.get(2).endsWith(".png") ? "image/png" : "image/webp";
            default: return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Not supported"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Not supported"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Not supported"); }
}
