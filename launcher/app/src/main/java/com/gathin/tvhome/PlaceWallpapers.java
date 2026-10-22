package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.FaceDetector;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Wallpapers of places to visit: once a week, takes the pictures Bing has shown on its home page over
 * the past days (landmarks, landscapes, wildlife), each with the place's name and a few lines about
 * it, keeps those with nobody in them, and deletes the week before. Needs no key and no account. The
 * launcher's only network use; "places": false in config.json turns it off.
 *
 * Runs on a background thread; every failure just leaves the previous pictures in place.
 */
final class PlaceWallpapers {
    private static final String TAG = "TvHome.Places";
    static final String DIR = "place-wallpapers";
    private static final long REFRESH_MS = 7L * 24 * 60 * 60 * 1000;
    /**
     * Words in a picture's name or headline that mean someone is in it. Only those two are read:
     * they describe the picture, where the paragraph under them tells the place's story, and
     * mentions locals or hikers far more often than it shows them. Faces are looked for as well.
     */
    private static final Pattern PEOPLE = Pattern.compile("\\b(people|persons?|man|men|woman|women|girls?|boys?"
            + "|child|children|kids?|famil(y|ies)|crowds?|festivals?|parades?|carnivals?|danc(er|ers|ing)"
            + "|surf(er|ers|ing)|hik(er|ers|ing)|climb(er|ers)|ski(er|ers)|swimm(er|ers)|fisherm[ae]n|monks?"
            + "|pilgrims?|tourists?|visitors?|weddings?|brides?|processions?|performers?|athletes?|runners?"
            + "|cyclists?|worshipp?ers?|spectators?|audiences?|musicians?|farmers?|riders?|couples?|locals"
            + "|lad(y|ies)|portraits?|selfies?|residents?|travell?ers?|crew|pedestrians?|sailors?|divers?"
            + "|kayakers?|soldiers?|guards?|vendors?|shoppers?|players?|fans)\\b", Pattern.CASE_INSENSITIVE);

    /** Raised whenever the rules for which pictures are kept change. */
    private static final int RULES = 2;

    /** Wikimedia asks every client to name itself. */
    static final String WIKIMEDIA_UA = "GlassTV/1.0 (https://github.com/taqinzai/glass-tv)";
    private static final String C = "https://upload.wikimedia.org/wikipedia/commons/";
    private static final String T = "https://thumb.wikimedia.org/wikipedia/commons/thumb/";

    /**
     * places_theme "islamic": mosques, each a picture from Wikimedia Commons under a free licence,
     * credited on the shelf. {picture, name, description, where, credit}. Fetched once, not weekly.
     */
    static final String[][] ISLAMIC = {
            {T + "b/b8/Edited_Great_Mosque_of_Mecca1_5-2019-ccsa4.0_%28cropped%29.jpg/1920px-Edited_Great_Mosque_of_Mecca1_5-2019-ccsa4.0_%28cropped%29.jpg",
                    "Masjidil Haram", "Masjid paling suci dalam Islam, mengelilingi Kaabah, kiblat umat Islam di seluruh dunia.",
                    "Makkah, Arab Saudi", "Saudipics.com, CC BY-SA 4.0"},
            {T + "0/0c/Masjid_Nabawi_The_Prophet%27s_Mosque%2C_Madina.jpg/1920px-Masjid_Nabawi_The_Prophet%27s_Mosque%2C_Madina.jpg",
                    "Masjid Nabawi", "Masjid yang dibina oleh Rasulullah SAW, tempat baginda dimakamkan di bawah Kubah Hijau.",
                    "Madinah, Arab Saudi", "Muhammad Mahdi Karim, GFDL 1.2"},
            {T + "8/87/Jerusalem-2013-Temple_Mount-Al-Aqsa_Mosque_%28NE_exposure%29.jpg/1920px-Jerusalem-2013-Temple_Mount-Al-Aqsa_Mosque_%28NE_exposure%29.jpg",
                    "Masjid al-Aqsa", "Kiblat pertama umat Islam dan tempat Israk Rasulullah SAW.",
                    "Baitulmaqdis, Palestin", "Godot13, CC BY-SA 4.0"},
            {T + "1/1b/Jerusalem_DomeoftheRock_J65_%28cropped%29.JPG/1920px-Jerusalem_DomeoftheRock_J65_%28cropped%29.JPG",
                    "Kubah as-Sakhrah", "Kubah emas di perkarangan Masjid al-Aqsa, dibina pada tahun 691 Masihi.",
                    "Baitulmaqdis, Palestin", "Ludvig14, CC BY-SA 4.0"},
            {C + "8/85/Cristal_Mosque_in_Kuala_Terengganu.jpg",
                    "Masjid Kristal", "Masjid berbingkai keluli dan kaca di Taman Tamadun Islam, Pulau Wan Man.",
                    "Kuala Terengganu, Malaysia", "emrank (Flickr), CC BY 2.0"},
            {C + "3/3f/Floating_Mosque.jpg",
                    "Masjid Tengku Tengah Zaharah", "Dikenali sebagai Masjid Terapung, di muara Sungai Ibai.",
                    "Kuala Ibai, Terengganu", "bbbsheep, CC BY 2.0"},
            {T + "1/16/Masjid_Putra%2C_Putrajaya%2C_Malaysia.jpg/1920px-Masjid_Putra%2C_Putrajaya%2C_Malaysia.jpg",
                    "Masjid Putra", "Masjid berkubah merah jambu di tepi Tasik Putrajaya.",
                    "Putrajaya, Malaysia", "CC BY 4.0"},
            {T + "1/1f/National_Mosque_of_Malaysia%2C_Kuala_Lumpur_2026.jpg/1920px-National_Mosque_of_Malaysia%2C_Kuala_Lumpur_2026.jpg",
                    "Masjid Negara", "Masjid nasional Malaysia dengan bumbung berbentuk payung, dirasmikan pada tahun 1965.",
                    "Kuala Lumpur, Malaysia", "Santaria Fi, CC BY 4.0"},
            {C + "e/e3/Ubudiah_Mosque%2C_circa_2023.jpg",
                    "Masjid Ubudiah", "Masjid diraja berkubah keemasan, siap dibina pada tahun 1917.",
                    "Kuala Kangsar, Perak", "Linken-nightmare, CC0"},
            {C + "4/4b/SA_Blue_Mosque.jpg",
                    "Masjid Sultan Salahuddin Abdul Aziz", "Dikenali sebagai Masjid Biru, antara masjid terbesar di Malaysia.",
                    "Shah Alam, Selangor", "Lerdsuwa, CC BY-SA 3.0"},
            {T + "0/03/Istanbul_%2834223582516%29_%28cropped%29.jpg/1920px-Istanbul_%2834223582516%29_%28cropped%29.jpg",
                    "Masjid Sultan Ahmed", "Masjid Biru Istanbul dengan enam menara, siap pada tahun 1616.",
                    "Istanbul, Turki", "Pedro Szekely, CC BY-SA 2.0"},
            {T + "c/c8/Badshahi_Mosque_front_picture.jpg/1920px-Badshahi_Mosque_front_picture.jpg",
                    "Masjid Badshahi", "Masjid zaman Mughal yang siap dibina pada tahun 1673.",
                    "Lahore, Pakistan", "Romero Maia, CC BY-SA 4.0"},
            {T + "e/e0/Ali_Mujtaba_WLM2017_FAISAL_MOSQUE_019.jpg/1920px-Ali_Mujtaba_WLM2017_FAISAL_MOSQUE_019.jpg",
                    "Masjid Faisal", "Masjid berbentuk khemah di kaki Bukit Margalla, siap pada tahun 1986.",
                    "Islamabad, Pakistan", "Ali Mujtaba, CC BY-SA 4.0"},
            {C + "b/b2/Great_Mosque_of_Kairouan_Panorama_-_Grande_Mosqu%C3%A9e_de_Kairouan_Panorama.jpg",
                    "Masjid Agung Kairouan", "Antara masjid tertua di Afrika Utara, diasaskan pada tahun 670 Masihi.",
                    "Kairouan, Tunisia", "Marek Szarejko, CC BY-SA 2.0"},
    };
    private static final long RETRY_MS = 30 * 60_000L;

    private PlaceWallpapers() {
    }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base != null ? new File(base, DIR) : null;
    }

    /** The pictures on disk, in the order Bing showed them; empty when none. */
    static List<File> list(Context ctx) {
        List<File> out = new ArrayList<>();
        File d = dir(ctx);
        File[] files = d != null ? d.listFiles() : null;
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.length() > 0 && f.getName().endsWith(".jpg")) out.add(f);
        }
        Collections.sort(out);
        return out;
    }

    /**
     * True when the pictures should be fetched: none yet, another theme or older rules, or (Bing) a
     * week has passed. A failed fetch waits half an hour before the next try.
     */
    static boolean due(Context ctx, String theme) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        if (now - p.getLong("failed_at", 0) < RETRY_MS) return false;
        if (p.getInt("rules", 0) < RULES || !theme.equals(p.getString("theme", "bing"))) return true;
        if (list(ctx).isEmpty()) return true;
        return !"islamic".equals(theme) && now - p.getLong("refreshed_at", 0) >= REFRESH_MS;
    }

    /** Fetches the pictures for that theme in place of the ones on disk. Blocking. */
    static boolean refresh(Context ctx, String theme) {
        boolean ok = "islamic".equals(theme) ? refreshIslamic(ctx) : refresh(ctx);
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (ok) e.putString("theme", theme).putLong("failed_at", 0);
        else e.putLong("failed_at", System.currentTimeMillis());
        e.apply();
        return ok;
    }

    /** The mosques in ISLAMIC, each with its name, a line about it and the photo's credit. */
    private static boolean refreshIslamic(Context ctx) {
        File d = dir(ctx);
        if (d == null) return false;
        File fresh = new File(d.getParentFile(), DIR + ".new");
        deleteAll(fresh);
        if (!fresh.mkdirs()) return false;
        int n = 0;
        for (String[] p : ISLAMIC) {
            try {
                byte[] jpg = get(p[0], 4 * 1024 * 1024, WIKIMEDIA_UA);
                String base = String.format(Locale.ROOT, "%02d", n++);
                write(new File(fresh, base + ".jpg"), jpg);
                write(new File(fresh, base + ".txt"), (p[1] + "\n" + p[2] + "  ·  Foto: " + p[4] + "\n" + p[3])
                        .getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.w(TAG, "skipped " + p[1] + ": " + e);
            }
        }
        // Most of them in is good enough; the rest come with the next theme change or rules bump.
        if (n < ISLAMIC.length / 2) {
            deleteAll(fresh);
            return false;
        }
        deleteAll(d);
        if (!fresh.renameTo(d)) return false;
        prefs(ctx).edit().putLong("refreshed_at", System.currentTimeMillis()).putInt("rules", RULES).apply();
        return true;
    }

    /**
     * Downloads this week's places and replaces last week's with them. Blocking: call from a
     * background thread. Returns true when the pictures changed.
     */
    static boolean refresh(Context ctx) {
        File d = dir(ctx);
        if (d == null) return false;
        File fresh = new File(d.getParentFile(), DIR + ".new");
        try {
            deleteAll(fresh);
            if (!fresh.mkdirs()) return false;
            String mkt = Locale.getDefault().toLanguageTag();
            JSONObject model = new JSONObject(new String(get("https://www.bing.com/hp/api/model?mkt="
                    + URLEncoder.encode(mkt, "UTF-8"), 512 * 1024), StandardCharsets.UTF_8));
            JSONArray items = model.optJSONArray("MediaContents");
            int n = 0;
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                JSONObject about = item != null ? item.optJSONObject("ImageContent") : null;
                JSONObject image = about != null ? about.optJSONObject("Image") : null;
                if (image == null) continue;
                String place = about.optString("Title", "").trim();
                String headline = about.optString("Headline", "").trim();
                String text = about.optString("Description", "").trim();
                String url = image.optString("Url", "");
                if (place.isEmpty() || url.isEmpty()) continue;
                if (PEOPLE.matcher(place + " " + headline).find()) {
                    Log.i(TAG, "skipped, names people: " + place);
                    continue;
                }
                // The 1920 x 1080 picture, as a JPEG rather than the WebP Bing names first.
                byte[] jpg = get("https://www.bing.com" + url.replace(".webp", ".jpg"), 3 * 1024 * 1024);
                int faces = faces(jpg);
                if (faces != 0) {
                    Log.i(TAG, "skipped, " + (faces < 0 ? "unreadable" : faces + " face(s)") + ": " + place);
                    continue;
                }
                // Only somewhere: a picture whose caption names no country (fossils, say) is left out.
                String[] named = nameAndCountry(place);
                if (named[1].isEmpty()) {
                    Log.i(TAG, "skipped, no country: " + place);
                    continue;
                }
                String base = String.format(Locale.ROOT, "%02d", n++);
                write(new File(fresh, base + ".jpg"), jpg);
                // One line each, so a stray newline in the text cannot be read back as a name:
                // the thing's name, the description, the country.
                write(new File(fresh, base + ".txt"), (named[0].replace('\n', ' ') + "\n"
                        + text.replace('\n', ' ') + "\n" + named[1].replace('\n', ' ')).getBytes(StandardCharsets.UTF_8));
            }
            if (n == 0) {
                deleteAll(fresh);
                return false;
            }
            // Last week's pictures go once this week's are all in, and so do the film pictures
            // earlier versions kept.
            deleteAll(d);
            deleteAll(new File(d.getParentFile(), "movie-wallpapers"));
            if (!fresh.renameTo(d)) return false;
            prefs(ctx).edit().putLong("refreshed_at", System.currentTimeMillis()).putInt("rules", RULES).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "refresh failed: " + e);
            deleteAll(fresh);
            return false;
        }
    }

    private static final Pattern LEAD = Pattern.compile(
            "^((aerial|panoramic|close-up|night|winter|autumn|summer|spring)\\s+)?(views?|replicas?|life-size replica)\\s+of\\s+(the\\s+)?|^(the|a|an)\\s+",
            Pattern.CASE_INSENSITIVE);
    private static final java.util.Set<String> SMALL = new java.util.HashSet<>(java.util.Arrays.asList(
            "a", "an", "the", "of", "in", "on", "at", "to", "for", "and", "or", "by", "with", "de", "del", "la", "le", "du"));
    private static final java.util.Set<String> US_STATES = new java.util.HashSet<>(java.util.Arrays.asList(
            "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida",
            "Georgia", "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine",
            "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska",
            "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio",
            "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota", "Tennessee", "Texas",
            "Utah", "Vermont", "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming"));

    /**
     * Splits Bing's caption ("Mount Sneffels in the San Juan Mountains near Ridgway, Colorado") into
     * the thing's name ("Mount Sneffels") and its country ("United States"), the country empty
     * when the caption names none ("Ammonite fossils from the Jurassic Period").
     */
    static String[] nameAndCountry(String caption) {
        String c = caption.replaceAll("\\s*\\([^)]*\\)", "").replaceAll("\\s+", " ").trim();
        int comma = c.indexOf(", ");
        String country = comma >= 0 ? c.substring(c.lastIndexOf(", ") + 2).trim() : "";
        if (US_STATES.contains(country)) country = "United States";
        // The name runs up to where the caption starts saying where or when.
        String name = comma >= 0 ? c.substring(0, comma) : c;
        java.util.regex.Matcher cut = Pattern.compile("\\s+(in|near|at|on|from|over|above|under|along|off|across)\\s+",
                Pattern.CASE_INSENSITIVE).matcher(name);
        if (cut.find() && cut.start() > 0) name = name.substring(0, cut.start());
        name = LEAD.matcher(name).replaceFirst("").trim();
        if (name.isEmpty()) name = c;
        return new String[]{titleCase(name), country};
    }

    /** Capitals for each word but the small ones, and for each part of a hyphenated word. */
    private static String titleCase(String s) {
        StringBuilder out = new StringBuilder();
        String[] words = s.split(" ");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i > 0) out.append(' ');
            if (i > 0 && SMALL.contains(w.toLowerCase(Locale.ROOT))) {
                out.append(w.toLowerCase(Locale.ROOT));
                continue;
            }
            String[] parts = w.split("-", -1);
            for (int j = 0; j < parts.length; j++) {
                if (j > 0) out.append('-');
                String p = parts[j];
                out.append(p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1));
            }
        }
        return out.toString();
    }

    /**
     * How many faces can be seen in the picture, or -1 when it cannot be read. Android's own
     * detector, on a half-size copy: no download, no model, and run only when the week's pictures
     * arrive.
     */
    private static int faces(byte[] jpg) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;   // what the detector reads
        o.inSampleSize = 2;
        Bitmap b = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, o);
        if (b == null) return -1;
        try {
            if (b.getWidth() % 2 != 0) {   // the detector needs an even width
                Bitmap even = Bitmap.createBitmap(b, 0, 0, b.getWidth() - 1, b.getHeight());
                b.recycle();
                b = even;
            }
            FaceDetector.Face[] faces = new FaceDetector.Face[4];
            return new FaceDetector(b.getWidth(), b.getHeight(), faces.length).findFaces(b, faces);
        } finally {
            b.recycle();
        }
    }

    /**
     * The thing's name, its description and its country for a picture saved here, or null when
     * there is none: one line each in the matching .txt.
     */
    static String[] describe(File picture) {
        if (picture == null) return null;
        String name = picture.getName();
        if (!name.endsWith(".jpg")) return null;
        File text = new File(picture.getParentFile(), name.substring(0, name.length() - 4) + ".txt");
        if (!text.isFile() || text.length() == 0 || text.length() > 8192) return null;
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(text), StandardCharsets.UTF_8))) {
            String title = r.readLine();
            if (title == null || title.trim().isEmpty()) return null;
            String overview = r.readLine(), country = r.readLine();
            return new String[]{title.trim(), overview == null ? "" : overview.trim(),
                    country == null ? "" : country.trim()};
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(File out, byte[] data) throws Exception {
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(data);
        }
    }

    private static void deleteAll(File d) {
        File[] files = d.listFiles();
        if (files != null) for (File f : files) f.delete();
        d.delete();
    }

    private static byte[] get(String url, int maxBytes) throws Exception {
        return get(url, maxBytes, null);
    }

    private static byte[] get(String url, int maxBytes, String userAgent) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json, image/jpeg");
        // Asked as Android's own HTTP client, Bing answers with today's picture only; as a browser,
        // with the whole week.
        c.setRequestProperty("User-Agent", userAgent != null ? userAgent
                : "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > maxBytes) throw new IllegalStateException("response too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("place_wallpapers", Context.MODE_PRIVATE);
    }
}
