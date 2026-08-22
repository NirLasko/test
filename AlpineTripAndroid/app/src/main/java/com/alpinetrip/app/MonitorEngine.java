package com.alpinetrip.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MonitorEngine {
    private static final String PREFS = "alpine_native";
    private static final Pattern COMMENT = Pattern.compile("<[^>]*value[^>]*lang=\\\"(?:de-CH|fr-CH|it-CH|en-EN)\\\"[^>]*>(.*?)</[^>]*value>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private MonitorEngine() {}

    public static final class Result {
        public int dayIndex;
        public int level; // 0 good, 1 warning, 2 problem
        public String title;
        public String summary;
        public String details;
        public long checkedAt;

        public String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("dayIndex", dayIndex);
                o.put("level", level);
                o.put("title", title);
                o.put("summary", summary);
                o.put("details", details);
                o.put("checkedAt", checkedAt);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
    }

    private static final class WeatherOutcome {
        int level = 0;
        String text = "";
    }

    private static final class RoadOutcome {
        int level = 0;
        String text = "";
    }

    public static Result check(Context context, int dayIndex) {
        Result r = new Result();
        r.dayIndex = Math.max(0, Math.min(dayIndex, TripData.DAYS.size()-1));
        r.checkedAt = System.currentTimeMillis();
        TripData.Day d = TripData.DAYS.get(r.dayIndex);

        WeatherOutcome weather;
        RoadOutcome roads;
        try { weather = checkWeather(d); }
        catch (Exception e) {
            weather = new WeatherOutcome();
            weather.level = 1;
            weather.text = "מזג אוויר: הבדיקה נכשלה – " + safeMessage(e);
        }
        try { roads = checkRoads(context, d); }
        catch (Exception e) {
            roads = new RoadOutcome();
            roads.level = 1;
            roads.text = "כבישים: הבדיקה נכשלה – " + safeMessage(e);
        }

        r.level = Math.max(weather.level, roads.level);
        if (r.level >= 2) {
            r.title = "🔴 Alpine Route Watch – נדרשת תשומת לב";
            r.summary = "זוהתה בעיה אפשרית במסלול של היום";
        } else if (r.level == 1) {
            r.title = "🟡 Alpine Route Watch – בדיקה נוספת מומלצת";
            r.summary = "יש תנאי מזג אוויר/דרך שכדאי לבדוק";
        } else {
            r.title = "🟢 Alpine Route Watch – הכל נראה תקין";
            r.summary = "לא זוהה שינוי משמעותי במסלול של היום";
        }
        r.details = d.title + "\n\n" + weather.text + "\n\n" + roads.text;

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("last_monitor_json", r.toJson()).apply();
        return r;
    }

    private static WeatherOutcome checkWeather(TripData.Day day) throws Exception {
        WeatherOutcome out = new WeatherOutcome();
        List<String> rows = new ArrayList<>();
        StringBuilder lats = new StringBuilder(), lons = new StringBuilder();
        for (int i=0;i<day.weatherPoints.size();i++) {
            if(i>0){ lats.append(','); lons.append(','); }
            lats.append(String.format(Locale.US,"%.4f",day.weatherPoints.get(i).lat));
            lons.append(String.format(Locale.US,"%.4f",day.weatherPoints.get(i).lon));
        }
        String u = "https://api.open-meteo.com/v1/forecast?latitude=" + lats + "&longitude=" + lons +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_gusts_10m_max&timezone=Europe%2FZurich&forecast_days=1";
        String raw = httpGet(u);
        Object parsed = raw.trim().startsWith("[") ? new JSONArray(raw) : new JSONObject(raw);
        for (int i=0;i<day.weatherPoints.size();i++) {
            JSONObject j = parsed instanceof JSONArray ? ((JSONArray)parsed).getJSONObject(i) : (JSONObject)parsed;
            JSONObject daily = j.getJSONObject("daily");
            int code = daily.getJSONArray("weather_code").getInt(0);
            double tmax = daily.getJSONArray("temperature_2m_max").getDouble(0);
            double tmin = daily.getJSONArray("temperature_2m_min").getDouble(0);
            int rain = daily.getJSONArray("precipitation_probability_max").getInt(0);
            double gust = daily.getJSONArray("wind_gusts_10m_max").getDouble(0);
            int sev = weatherSeverity(code, rain, gust);
            out.level = Math.max(out.level, sev);
            TripData.Point p = day.weatherPoints.get(i);
            rows.add(String.format(Locale.US, "%s: %s, %.0f–%.0f°C, גשם עד %d%%, משבים עד %.0f קמ״ש",
                    p.name, weatherName(code), tmin, tmax, rain, gust));
        }
        String prefix = out.level == 0 ? "מזג אוויר: ללא דגל משמעותי." :
                out.level == 1 ? "מזג אוויר: קיימת אזהרה מתונה." : "מזג אוויר: תנאים שעלולים להשפיע על המסלול.";
        out.text = prefix + "\n" + join(rows, "\n");
        return out;
    }

    private static RoadOutcome checkRoads(Context context, TripData.Day day) throws Exception {
        RoadOutcome out = new RoadOutcome();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString("road_api_token", "").trim();
        if (token.isEmpty()) {
            out.level = 1;
            out.text = "כבישים: מפתח API שווייצרי עדיין לא הוגדר. מזג האוויר ייבדק, אך חסימות כבישים לא ייבדקו אוטומטית עד להזנת המפתח בהגדרות.";
            return out;
        }

        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" +
                "<d2LogicalModel xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" modelBaseVersion=\"2\" xmlns=\"http://datex2.eu/schema/2/2_0\">" +
                "<exchange><supplierIdentification><country>ch</country><nationalIdentifier>FEDRO</nationalIdentifier></supplierIdentification>" +
                "<subscription><operatingMode>operatingMode1</operatingMode><subscriptionStartTime>" + ZonedDateTime.now(TripData.ZURICH).minusMinutes(1) + "</subscriptionStartTime>" +
                "<subscriptionState>active</subscriptionState><updateMethod>singleElementUpdate</updateMethod><target><address></address><protocol>http</protocol></target></subscription>" +
                "</exchange></d2LogicalModel></soap:Body></soap:Envelope>";

        URL url = new URL("https://api.opentransportdata.swiss/TDP/Soap_Datex2/TrafficSituations/Pull");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(18000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("SOAPAction", "http://opentransportdata.swiss/TDP/Soap_Datex2/Pull/v1/pullTrafficMessages");
        c.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        c.setRequestProperty("User-Agent", "AlpineTrip2026-Android/1.0");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String xml = readAll(is);
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

        List<String> hits = new ArrayList<>();
        Matcher m = COMMENT.matcher(xml);
        while (m.find()) {
            String s = unescapeXml(m.group(1)).replaceAll("\\s+", " ").trim();
            String low = s.toLowerCase(Locale.ROOT);
            if (low.contains("aufgehoben:") || low.contains("révoqué:") || low.contains("revocato:")) continue;
            boolean relevant = false;
            for (String k : day.roadKeywords) if (low.contains(k.toLowerCase(Locale.ROOT))) { relevant = true; break; }
            if (!relevant) continue;
            if (!hits.contains(s)) hits.add(s);
            if (containsAny(low, "gesperrt","geschlossen","sperrung","blocked","closed","chiuso","fermé","interrotto")) out.level = Math.max(out.level, 2);
            else out.level = Math.max(out.level, 1);
            if (hits.size() >= 5) break;
        }
        if (hits.isEmpty()) {
            out.text = "כבישים: לא נמצאה הודעת תנועה פעילה שמכילה את נקודות המסלול של היום במאגר השווייצרי הרשמי.";
            out.level = 0;
        } else {
            out.text = "כבישים: נמצאו דיווחים רלוונטיים אפשריים:\n• " + join(hits, "\n• ");
        }
        return out;
    }

    private static String httpGet(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "AlpineTrip2026-Android/1.0");
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String s = readAll(is); c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return s;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static int weatherSeverity(int code, int rain, double gust) {
        if (gust >= 80 || rain >= 80 || code == 75 || code == 82 || code >= 95) return 2;
        if (gust >= 60 || rain >= 60 || (code >= 61 && code <= 73) || (code >= 80 && code <= 81)) return 1;
        return 0;
    }

    private static String weatherName(int code) {
        switch (code) {
            case 0: return "בהיר"; case 1: return "בהיר ברובו"; case 2: return "מעונן חלקית"; case 3: return "מעונן";
            case 45: case 48: return "ערפל";
            case 51: case 53: case 55: return "טפטוף";
            case 61: return "גשם קל"; case 63: return "גשם"; case 65: return "גשם חזק";
            case 71: return "שלג קל"; case 73: return "שלג"; case 75: return "שלג כבד";
            case 80: case 81: return "ממטרים"; case 82: return "ממטרים חזקים";
            case 95: case 96: case 99: return "סופות רעמים";
            default: return "תנאים משתנים";
        }
    }

    private static boolean containsAny(String s, String... vals) {
        for (String v : vals) if (s.contains(v)) return true;
        return false;
    }
    private static String unescapeXml(String s) {
        return s.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#39;","'").replace("&amp;","&");
    }
    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder(); for (int i=0;i<list.size();i++){ if(i>0) sb.append(sep); sb.append(list.get(i)); } return sb.toString();
    }
    private static String safeMessage(Exception e) {
        String s=e.getMessage(); return (s==null||s.length()>120)?e.getClass().getSimpleName():s;
    }
}
