package ir.flicker.flanner;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@CapacitorPlugin(name = "Flanner")
public class FlannerPlugin extends Plugin {

    private Context ctx() { return getContext(); }

    @PluginMethod
    public void openUrl(PluginCall call) {
        String u = call.getString("url");
        if (u == null) { call.reject("no url"); return; }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject("fail"); }
    }

    @PluginMethod
    public void openApp(PluginCall call) {
        String p = call.getString("pkg");
        Intent i = p == null ? null : ctx().getPackageManager().getLaunchIntentForPackage(p);
        if (i == null) { call.reject("not found"); return; }
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject("fail"); }
    }

    @PluginMethod
    public void listApps(PluginCall call) {
        final PackageManager pm = ctx().getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN, null);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> l = pm.queryIntentActivities(q, 0);
        Collections.sort(l, (x, y) -> x.loadLabel(pm).toString().compareToIgnoreCase(y.loadLabel(pm).toString()));
        JSArray a = new JSArray();
        for (ResolveInfo r : l) {
            if (r.activityInfo.packageName.equals(ctx().getPackageName())) continue;
            JSObject o = new JSObject();
            o.put("pkg", r.activityInfo.packageName);
            o.put("label", r.loadLabel(pm).toString());
            a.put(o);
        }
        JSObject res = new JSObject();
        res.put("apps", a);
        call.resolve(res);
    }

    @PluginMethod
    public void pickFile(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, i, "pickDone");
    }

    @ActivityCallback
    private void pickDone(PluginCall call, ActivityResult r) {
        if (call == null) return;
        if (r.getResultCode() != Activity.RESULT_OK || r.getData() == null || r.getData().getData() == null) {
            call.reject("cancel");
            return;
        }
        Uri u = r.getData().getData();
        try {
            ctx().getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) { /* ignore */ }
        String name = "file";
        Cursor cu = ctx().getContentResolver().query(u, null, null, null, null);
        try {
            if (cu != null && cu.moveToFirst()) {
                int ix = cu.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) name = cu.getString(ix);
            }
        } finally { if (cu != null) cu.close(); }
        String m = ctx().getContentResolver().getType(u);
        JSObject o = new JSObject();
        o.put("uri", u.toString());
        o.put("name", name);
        o.put("mime", m == null ? "*/*" : m);
        call.resolve(o);
    }

    @PluginMethod
    public void openFile(PluginCall call) {
        try {
            Uri u = Uri.parse(call.getString("uri"));
            String m = call.getString("mime", "*/*");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, m);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject("fail"); }
    }

    @PluginMethod
    public void saveFile(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(call.getString("mime", "application/octet-stream"));
        i.putExtra(Intent.EXTRA_TITLE, call.getString("name", "flanner.txt"));
        startActivityForResult(call, i, "saveDone");
    }

    @ActivityCallback
    private void saveDone(PluginCall call, ActivityResult r) {
        if (call == null) return;
        if (r.getResultCode() != Activity.RESULT_OK || r.getData() == null || r.getData().getData() == null) {
            call.reject("cancel");
            return;
        }
        Uri u = r.getData().getData();
        try (OutputStream os = ctx().getContentResolver().openOutputStream(u, "wt")) {
            String b = call.getString("base64");
            byte[] d = b != null ? Base64.decode(b, Base64.DEFAULT)
                    : call.getString("text", "").getBytes(StandardCharsets.UTF_8);
            os.write(d);
            call.resolve();
        } catch (Exception e) { call.reject("fail"); }
    }

    private NotificationManager nm() {
        return (NotificationManager) ctx().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @PluginMethod
    public void hasDnd(PluginCall call) {
        JSObject o = new JSObject();
        o.put("granted", nm().isNotificationPolicyAccessGranted());
        call.resolve(o);
    }

    @PluginMethod
    public void requestDnd(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void setDnd(PluginCall call) {
        NotificationManager n = nm();
        if (!n.isNotificationPolicyAccessGranted()) { call.reject("no permission"); return; }
        SharedPreferences sp = ctx().getSharedPreferences("flanner", Context.MODE_PRIVATE);
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        if (on) {
            if (!sp.contains("prev")) sp.edit().putInt("prev", n.getCurrentInterruptionFilter()).apply();
            n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);
        } else if (sp.contains("prev")) {
            n.setInterruptionFilter(sp.getInt("prev", NotificationManager.INTERRUPTION_FILTER_ALL));
            sp.edit().remove("prev").apply();
        }
        call.resolve();
    }

    @PluginMethod
    public void openNotifSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx().getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void getIcons(PluginCall call) {
        PackageManager pm = ctx().getPackageManager();
        JSArray arr = call.getArray("pkgs");
        JSObject out = new JSObject();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try {
                    String p = arr.getString(i);
                    Drawable d = pm.getApplicationIcon(p);
                    Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(b);
                    d.setBounds(0, 0, 96, 96);
                    d.draw(c);
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    b.compress(Bitmap.CompressFormat.PNG, 100, os);
                    out.put(p, Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP));
                } catch (Exception e) { /* skip this app */ }
            }
        }
        JSObject res = new JSObject();
        res.put("icons", out);
        call.resolve(res);
    }

    @PluginMethod
    public void pickContact(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(call, i, "contactDone");
    }

    @ActivityCallback
    private void contactDone(PluginCall call, ActivityResult r) {
        if (call == null) return;
        if (r.getResultCode() != Activity.RESULT_OK || r.getData() == null || r.getData().getData() == null) {
            call.reject("cancel");
            return;
        }
        Uri u = r.getData().getData();
        Cursor c = ctx().getContentResolver().query(u, new String[]{
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null);
        JSObject o = new JSObject();
        try {
            if (c != null && c.moveToFirst()) {
                o.put("number", c.getString(0));
                o.put("name", c.getString(1));
            }
        } finally { if (c != null) c.close(); }
        call.resolve(o);
    }
}
