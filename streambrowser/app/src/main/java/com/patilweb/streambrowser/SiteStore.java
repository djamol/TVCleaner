package com.patilweb.streambrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Built-in sites plus user-added URLs. Supports hide/remove, edit, and custom order.
 */
final class SiteStore {
    private static final String PREFS = "sites";
    private static final String KEY_CUSTOM = "custom_json";
    private static final String KEY_HIDDEN = "hidden_ids";
    private static final String KEY_ORDER = "order_json";
    private static final String KEY_OVERRIDES = "overrides_json";
    private static final int MAX_CUSTOM = 24;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final int[] accents;

    SiteStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.accents = new int[]{
                ctx.getColor(R.color.tile_tbcpl),
                ctx.getColor(R.color.tile_kartoons),
                ctx.getColor(R.color.tile_nightflix),
                ctx.getColor(R.color.tile_cinegram),
                ctx.getColor(R.color.tile_cinejoy),
                ctx.getColor(R.color.tile_movy),
                ctx.getColor(R.color.tile_redflix),
                ctx.getColor(R.color.tile_speech),
                ctx.getColor(R.color.tile_dictation),
                ctx.getColor(R.color.tile_kick),
        };
    }

    List<Site> defaults() {
        List<Site> list = new ArrayList<>();
        list.add(new Site("kick", ctx.getString(R.string.site_kick), ctx.getString(R.string.site_kick_desc),
                "https://kick.com/", accents[9], false, false));
        list.add(new Site("tbcpl", ctx.getString(R.string.site_tbcpl), ctx.getString(R.string.site_tbcpl_desc),
                "https://tbcpl.lol/", accents[0], false, false));
        list.add(new Site("kartoons", ctx.getString(R.string.site_kartoons), ctx.getString(R.string.site_kartoons_desc),
                "https://kartoons.to/home", accents[1], false, false));
        list.add(new Site("nightflix", ctx.getString(R.string.site_nightflix), ctx.getString(R.string.site_nightflix_desc),
                "https://nightflix.to/", accents[2], false, false));
        list.add(new Site("cinegram", ctx.getString(R.string.site_cinegram), ctx.getString(R.string.site_cinegram_desc),
                "https://cinegram.tv/", accents[3], true, false));
        list.add(new Site("cinejoy", ctx.getString(R.string.site_cinejoy), ctx.getString(R.string.site_cinejoy_desc),
                "https://cinejoy.pk/", accents[4], false, false));
        list.add(new Site("movy", ctx.getString(R.string.site_movy), ctx.getString(R.string.site_movy_desc),
                "https://www.movy.sx/", accents[5], false, false));
        list.add(new Site("redflix", ctx.getString(R.string.site_redflix), ctx.getString(R.string.site_redflix_desc),
                "http://redflix.biz/", accents[6], false, false));
        // Audio → text websites (need mic / WebView audio capture)
        list.add(new Site("speechtexter", ctx.getString(R.string.site_speechtexter),
                ctx.getString(R.string.site_speechtexter_desc),
                "https://www.speechtexter.com/", accents[7], false, false));
        list.add(new Site("dictation", ctx.getString(R.string.site_dictation),
                ctx.getString(R.string.site_dictation_desc),
                "https://dictation.io/speech", accents[8], false, false));
        return list;
    }

    /** Visible sites in saved order (defaults + custom, with edits applied). */
    List<Site> allVisible() {
        Map<String, Site> byId = catalog();
        Set<String> hidden = hiddenIds();
        List<String> order = loadOrder();
        List<Site> out = new ArrayList<>();

        if (order.isEmpty()) {
            for (Site s : defaults()) {
                if (!hidden.contains(s.id)) out.add(applyOverride(s));
            }
            out.addAll(loadCustom());
            return out;
        }

        Set<String> seen = new HashSet<>();
        for (String id : order) {
            if (hidden.contains(id) || !seen.add(id)) continue;
            Site s = byId.get(id);
            if (s != null) out.add(s);
        }
        // Append anything new not yet in the saved order.
        for (Site s : byId.values()) {
            if (hidden.contains(s.id) || seen.contains(s.id)) continue;
            out.add(s);
        }
        return out;
    }

    boolean addCustom(String title, String url) {
        String cleanUrl = normalizeUrl(url);
        if (cleanUrl == null) return false;
        String name = title == null ? "" : title.trim();
        if (name.isEmpty()) {
            Uri u = Uri.parse(cleanUrl);
            name = u.getHost() != null ? u.getHost() : "Site";
        }
        if (name.length() > 40) name = name.substring(0, 40);
        List<Site> custom = loadCustom();
        if (custom.size() >= MAX_CUSTOM) return false;
        for (Site s : allVisible()) {
            if (s.url.equalsIgnoreCase(cleanUrl)) return false;
        }
        boolean aggressive = cleanUrl.toLowerCase(Locale.US).contains("cinegram");
        int accent = accents[custom.size() % accents.length];
        String id = "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        custom.add(new Site(id, name, cleanUrl, cleanUrl, accent, aggressive, true));
        if (!saveCustom(custom)) return false;
        List<String> order = currentOrderIds();
        if (!order.contains(id)) order.add(id);
        return saveOrder(order);
    }

    /** Edit name/URL for a custom site or override a built-in. */
    boolean update(String id, String title, String url) {
        if (id == null) return false;
        String cleanUrl = normalizeUrl(url);
        if (cleanUrl == null) return false;
        String name = title == null ? "" : title.trim();
        if (name.isEmpty()) {
            Uri u = Uri.parse(cleanUrl);
            name = u.getHost() != null ? u.getHost() : "Site";
        }
        if (name.length() > 40) name = name.substring(0, 40);

        if (id.startsWith("c_")) {
            List<Site> custom = loadCustom();
            boolean found = false;
            for (int i = 0; i < custom.size(); i++) {
                Site s = custom.get(i);
                if (!id.equals(s.id)) continue;
                boolean aggressive = cleanUrl.toLowerCase(Locale.US).contains("cinegram")
                        || s.aggressiveAdBlock;
                custom.set(i, new Site(s.id, name, cleanUrl, cleanUrl, s.accentColor, aggressive, true));
                found = true;
                break;
            }
            return found && saveCustom(custom);
        }

        // Built-in override
        JSONObject overrides = loadOverrides();
        try {
            JSONObject o = new JSONObject();
            o.put("title", name);
            o.put("url", cleanUrl);
            o.put("description", cleanUrl);
            overrides.put(id, o);
            return prefs.edit().putString(KEY_OVERRIDES, overrides.toString()).commit();
        } catch (Exception e) {
            return false;
        }
    }

    /** Move site left (−1) or right (+1) in the visible list. */
    boolean move(String id, int delta) {
        if (id == null || delta == 0) return false;
        List<String> order = currentOrderIds();
        int idx = order.indexOf(id);
        if (idx < 0) return false;
        int dest = idx + delta;
        if (dest < 0 || dest >= order.size()) return false;
        order.remove(idx);
        order.add(dest, id);
        return saveOrder(order);
    }

    /** Hide a built-in or delete a custom site. */
    boolean remove(String id) {
        if (id == null) return false;
        List<String> order = currentOrderIds();
        order.remove(id);
        saveOrder(order);

        if (id.startsWith("c_")) {
            List<Site> custom = loadCustom();
            boolean changed = false;
            for (Iterator<Site> it = custom.iterator(); it.hasNext(); ) {
                if (id.equals(it.next().id)) {
                    it.remove();
                    changed = true;
                    break;
                }
            }
            return changed && saveCustom(custom);
        }
        Set<String> hidden = hiddenIds();
        hidden.add(id);
        JSONObject overrides = loadOverrides();
        overrides.remove(id);
        return prefs.edit()
                .putStringSet(KEY_HIDDEN, hidden)
                .putString(KEY_OVERRIDES, overrides.toString())
                .commit();
    }

    static String normalizeUrl(String raw) {
        if (raw == null) return null;
        String u = raw.trim();
        if (u.isEmpty()) return null;
        if (!u.contains("://")) u = "https://" + u;
        Uri uri = Uri.parse(u);
        String scheme = uri.getScheme();
        if (scheme == null) return null;
        scheme = scheme.toLowerCase(Locale.US);
        if (!scheme.equals("http") && !scheme.equals("https")) return null;
        if (uri.getHost() == null || uri.getHost().isEmpty()) return null;
        return u;
    }

    private Map<String, Site> catalog() {
        Map<String, Site> map = new LinkedHashMap<>();
        for (Site s : defaults()) {
            if (!hiddenIds().contains(s.id)) map.put(s.id, applyOverride(s));
        }
        for (Site s : loadCustom()) map.put(s.id, s);
        return map;
    }

    private List<String> currentOrderIds() {
        List<String> order = loadOrder();
        if (!order.isEmpty()) {
            // Refresh: drop missing, keep extras for append by allVisible
            Map<String, Site> byId = catalog();
            List<String> cleaned = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String id : order) {
                if (byId.containsKey(id) && seen.add(id)) cleaned.add(id);
            }
            for (String id : byId.keySet()) {
                if (seen.add(id)) cleaned.add(id);
            }
            return cleaned;
        }
        List<String> ids = new ArrayList<>();
        for (Site s : allVisible()) ids.add(s.id);
        return ids;
    }

    private Site applyOverride(Site base) {
        JSONObject overrides = loadOverrides();
        JSONObject o = overrides.optJSONObject(base.id);
        if (o == null) return base;
        String title = o.optString("title", base.title);
        String url = o.optString("url", base.url);
        if (normalizeUrl(url) == null) url = base.url;
        String desc = o.optString("description", base.description);
        return new Site(base.id, title, desc, url, base.accentColor, base.aggressiveAdBlock, false);
    }

    private Set<String> hiddenIds() {
        Set<String> set = prefs.getStringSet(KEY_HIDDEN, null);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    private List<String> loadOrder() {
        List<String> out = new ArrayList<>();
        String json = prefs.getString(KEY_ORDER, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String id = arr.optString(i, "");
                if (!id.isEmpty()) out.add(id);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private boolean saveOrder(List<String> order) {
        JSONArray arr = new JSONArray();
        for (String id : order) arr.put(id);
        return prefs.edit().putString(KEY_ORDER, arr.toString()).commit();
    }

    private JSONObject loadOverrides() {
        try {
            return new JSONObject(prefs.getString(KEY_OVERRIDES, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private List<Site> loadCustom() {
        List<Site> out = new ArrayList<>();
        String json = prefs.getString(KEY_CUSTOM, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "");
                String title = o.optString("title", "Site");
                String url = o.optString("url", "");
                if (id.isEmpty() || normalizeUrl(url) == null) continue;
                int accent = o.optInt("accent", accents[i % accents.length]);
                boolean aggressive = o.optBoolean("aggressive", url.toLowerCase(Locale.US).contains("cinegram"));
                out.add(new Site(id, title, url, url, accent, aggressive, true));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private boolean saveCustom(List<Site> custom) {
        JSONArray arr = new JSONArray();
        try {
            for (Site s : custom) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", s.title);
                o.put("url", s.url);
                o.put("accent", s.accentColor);
                o.put("aggressive", s.aggressiveAdBlock);
                arr.put(o);
            }
            return prefs.edit().putString(KEY_CUSTOM, arr.toString()).commit();
        } catch (Exception e) {
            return false;
        }
    }
}
