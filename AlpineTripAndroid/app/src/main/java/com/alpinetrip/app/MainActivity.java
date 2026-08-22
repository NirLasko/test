package com.alpinetrip.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "alpine_native";
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.ensureChannel(this);

        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowUniversalAccessFromFileURLs(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBridge(), "NativeBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "geo".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
                    catch (Exception e) { Toast.makeText(MainActivity.this, "לא ניתן לפתוח את הקישור", Toast.LENGTH_SHORT).show(); }
                    return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) { pushLastMonitorToWeb(); }
        });
        webView.loadUrl("file:///android_asset/index.html");

        requestNotificationPermission();
        AlarmScheduler.scheduleTripChecks(this);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 501);
        }
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
        if (am.canScheduleExactAlarms()) return;
        new AlertDialog.Builder(this)
                .setTitle("אישור בדיקות בשעות המדויקות")
                .setMessage("כדי שהאפליקציה תבצע בדיקות ב־07:00, 11:00 ו־15:00 גם כשהיא סגורה, יש לאפשר לה 'התראות ותזכורות' במסך הבא.")
                .setNegativeButton("אחר כך", null)
                .setPositiveButton("פתח הגדרות", (d,w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                }).show();
    }

    @Override protected void onResume() {
        super.onResume();
        AlarmScheduler.scheduleTripChecks(this);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void showMonitorSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad,pad,pad,0);

        TextView info = new TextView(this);
        info.setText("מזג האוויר עובד ללא מפתח. כדי לבדוק חסימות ותקלות בכבישי שווייץ יש להזין Token של Traffic Situations API מ־opentransportdata.swiss.");
        layout.addView(info);

        EditText token = new EditText(this);
        token.setHint("Traffic API Token");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        token.setSingleLine(false);
        token.setText(prefs.getString("road_api_token", ""));
        layout.addView(token);

        String exact = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
            exact = am.canScheduleExactAlarms() ? "✓ תזמון מדויק מאושר" : "⚠ תזמון מדויק עדיין לא מאושר";
        } else exact = "✓ תזמון מדויק נתמך";
        TextView status = new TextView(this);
        status.setPadding(0,pad/2,0,0);
        status.setText(exact + "\nבדיקות מתוזמנות: 17–22/09/2026 בשעות 07:00, 11:00, 15:00 לפי שעון שווייץ.");
        layout.addView(status);

        new AlertDialog.Builder(this)
                .setTitle("הגדרות Alpine Route Watch")
                .setView(layout)
                .setNegativeButton("סגור", null)
                .setNeutralButton("אישור תזמון", (d,w) -> requestExactAlarmPermissionIfNeeded())
                .setPositiveButton("שמור", (d,w) -> {
                    prefs.edit().putString("road_api_token", token.getText().toString().trim()).apply();
                    AlarmScheduler.scheduleTripChecks(this);
                    Toast.makeText(this, "ההגדרות נשמרו", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void runCheckNow() {
        Toast.makeText(this, "מבצע בדיקת מזג אוויר וכבישים...", Toast.LENGTH_SHORT).show();
        int idx = TripData.dayIndexFor(LocalDate.now(TripData.ZURICH));
        if (idx < 0) idx = LocalDate.now(TripData.ZURICH).isBefore(TripData.DAYS.get(0).date) ? 0 : TripData.DAYS.size()-1;
        final int day = idx;
        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.execute(() -> {
            MonitorEngine.Result r = MonitorEngine.check(getApplicationContext(), day);
            NotificationHelper.notify(getApplicationContext(), r);
            runOnUiThread(() -> {
                pushLastMonitorToWeb();
                Toast.makeText(this, "הבדיקה הסתיימה", Toast.LENGTH_SHORT).show();
            });
            ex.shutdown();
        });
    }

    private void pushLastMonitorToWeb() {
        if (webView == null) return;
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString("last_monitor_json", "");
        if (raw.isEmpty()) return;
        String js = "window.applyNativeMonitor && window.applyNativeMonitor(" + JSONObject.quote(raw) + ");";
        webView.evaluateJavascript(js, null);
    }

    private void openBundledDocument(String key) {
        String asset, mime, filename;
        switch (key) {
            case "confirmations": asset="docs/confirmations.pdf"; mime="application/pdf"; filename="Travel_Confirmations.pdf"; break;
            case "emergency": asset="docs/emergency.pdf"; mime="application/pdf"; filename="Emergency_Contacts.pdf"; break;
            case "itinerary": asset="docs/itinerary.docx"; mime="application/vnd.openxmlformats-officedocument.wordprocessingml.document"; filename="Alpine_Trip_Itinerary.docx"; break;
            default: return;
        }
        try {
            File dir = new File(getCacheDir(), "docs"); if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, filename);
            try (InputStream in=getAssets().open(asset); FileOutputStream fos=new FileOutputStream(out)) {
                byte[] buf=new byte[16384]; int n; while((n=in.read(buf))>0) fos.write(buf,0,n);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName()+".files", out);
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "לא נמצאה אפליקציה מתאימה לפתיחת המסמך", Toast.LENGTH_LONG).show();
        }
    }

    public final class NativeBridge {
        @JavascriptInterface public void openSettings() { runOnUiThread(MainActivity.this::showMonitorSettings); }
        @JavascriptInterface public void runCheckNow() { runOnUiThread(MainActivity.this::runCheckNow); }
        @JavascriptInterface public void requestExactAlarmPermission() { runOnUiThread(MainActivity.this::requestExactAlarmPermissionIfNeeded); }
        @JavascriptInterface public void openDocument(String key) { runOnUiThread(() -> openBundledDocument(key)); }
        @JavascriptInterface public String getLastMonitorJson() { return getSharedPreferences(PREFS, MODE_PRIVATE).getString("last_monitor_json", ""); }
    }
}
