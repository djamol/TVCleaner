package com.patilweb.hguclients;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the HGU Wi-Fi station table and joins it with DHCP device names. */
public final class Gateway {

    static final String HOST = "http://192.168.1.200";
    private static final String USER = "adminubiq";
    private static final String PASSWORD = "adminubiq";

    private static final Pattern DEVICES = Pattern.compile(
            "new it\\(\"devname\", \"([^\"]*)\"\\), new it\\(\"macAddr\", \"([^\"]*)\"\\), new it\\(\"ipAddr\", \"([^\"]*)\"\\)");
    private static final Pattern RADIO = Pattern.compile(
            "band\\[(\\d+)\\]=(\\d+);\\s*ssid_drv\\[\\1\\]='([^']*)';");
    private static final Pattern CHANNEL = Pattern.compile("channel_drv\\[(\\d+)\\]='([^']*)'");
    private static final Pattern CSRF = Pattern.compile("csrftoken['\"][^>]*value=['\"]([^'\"]+)");
    private static final Pattern ROW = Pattern.compile("<tr class=\"tbl_body\">(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    private final CookieManager cookies = new CookieManager();

    static final class Client {
        String name = "—";
        String ip = "—";
        String mac = "";
        String vendor = "—";
        String ssid = "—";
        String band = "DHCP only";
        String channel = "—";
        String tx = "—";
        String rx = "—";
        String rate = "—";
        String rssi = "—";
        int rssiN = 1000;
        String quality = "";
        String powerSave = "—";
        String timeout = "—";
        boolean dhcpOnly = true;
    }

    List<Client> load() throws Exception {
        CookieHandler.setDefault(cookies);
        String eth = get("/status_ethernet_info.asp");
        if (eth.contains("formLogin")) {
            login();
            eth = get("/status_ethernet_info.asp");
        }
        if (eth.contains("formLogin")) {
            logout();
            login();
            eth = get("/status_ethernet_info.asp");
        }
        if (eth.contains("formLogin")) {
            throw new Exception("Gateway login failed");
        }

        Map<String, String[]> dhcp = new LinkedHashMap<>();
        Matcher devices = DEVICES.matcher(eth);
        while (devices.find()) {
            dhcp.put(devices.group(2).toLowerCase(), new String[]{devices.group(1), devices.group(3)});
        }

        String wlan = get("/status_wlan_info_11n.asp");
        Map<Integer, String[]> radios = new LinkedHashMap<>();
        Matcher radio = RADIO.matcher(wlan);
        while (radio.find()) {
            radios.put(Integer.parseInt(radio.group(1)), new String[]{radio.group(2), radio.group(3), "?"});
        }
        Matcher channel = CHANNEL.matcher(wlan);
        while (channel.find()) {
            String[] info = radios.get(Integer.parseInt(channel.group(1)));
            if (info != null) {
                info[2] = channel.group(2);
            }
        }

        String sta = get("/wlstatbl.asp");
        String token = csrf(sta);
        List<Client> clients = new ArrayList<>();
        for (Map.Entry<Integer, String[]> entry : radios.entrySet()) {
            String page = post("/boaform/admin/formWirelessTbl",
                    "wlan_idx=" + entry.getKey()
                            + "&submit-url=" + enc("/wlstatbl.asp")
                            + "&refresh=" + enc("Refresh")
                            + "&csrftoken=" + enc(token));
            token = csrf(page).isEmpty() ? token : csrf(page);
            String[] info = entry.getValue();
            String band = bandName(info[2]);
            Matcher rows = ROW.matcher(page);
            while (rows.find()) {
                List<String> cells = cells(rows.group(1));
                if (cells.isEmpty() || cells.get(0).isEmpty() || "None".equals(cells.get(0))) {
                    continue;
                }
                Client client = new Client();
                client.mac = cells.get(0).toLowerCase();
                client.dhcpOnly = false;
                String[] named = dhcp.get(client.mac);
                if (named != null) {
                    client.name = deviceName(client.mac, named[0]);
                    client.ip = named[1];
                } else {
                    client.name = deviceName(client.mac, "");
                }
                client.vendor = vendor(client.mac);
                client.ssid = info[1].isEmpty() ? "—" : info[1];
                client.band = band;
                client.channel = info[2];
                client.tx = at(cells, 1);
                client.rx = at(cells, 2);
                client.rate = at(cells, 3);
                client.rssi = at(cells, 4);
                client.powerSave = at(cells, 5);
                client.timeout = at(cells, 6);
                try {
                    client.rssiN = Integer.parseInt(client.rssi);
                } catch (NumberFormatException ignored) {
                    client.rssiN = 999;
                }
                client.quality = quality(client.rssiN);
                clients.add(client);
            }
        }

        for (Map.Entry<String, String[]> entry : dhcp.entrySet()) {
            boolean seen = false;
            for (Client client : clients) {
                if (client.mac.equals(entry.getKey())) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                continue;
            }
            Client client = new Client();
            client.mac = entry.getKey();
            client.name = deviceName(client.mac, entry.getValue()[0]);
            client.ip = entry.getValue()[1];
            client.vendor = vendor(client.mac);
            clients.add(client);
        }

        clients.sort(Comparator
                .comparing((Client c) -> c.dhcpOnly)
                .thenComparingInt(c -> c.dhcpOnly ? 0 : -c.rssiN)
                .thenComparing(c -> c.ip));
        return clients;
    }

    private void login() throws Exception {
        String page = get("/admin/login.asp");
        String token = csrf(page);
        String result = post("/boaform/admin/formLogin",
                "username=" + enc(USER)
                        + "&psd=" + enc(PASSWORD)
                        + "&sec_lang=0&loginSelinit=&ismobile=&csrftoken=" + enc(token));
        if (result.contains("you have logined")) {
            logout();
            page = get("/admin/login.asp");
            token = csrf(page);
            post("/boaform/admin/formLogin",
                    "username=" + enc(USER)
                            + "&psd=" + enc(PASSWORD)
                            + "&sec_lang=0&loginSelinit=&ismobile=&csrftoken=" + enc(token));
        }
    }

    private void logout() throws Exception {
        post("/boaform/admin/formLogout", "submit-url=" + enc("/admin/login.asp"));
    }

    /** The gateway DHCP name for this MAC is stale. The box is an X9 Turbo. */
    private static String deviceName(String mac, String dhcpName) {
        if ("88:5e:b7:5c:59:70".equals(mac)) {
            return "X9 Turbo";
        }
        return dhcpName == null || dhcpName.isEmpty() ? "—" : dhcpName;
    }

    private static String vendor(String mac) {
        String[] parts = mac.split(":");
        if (parts.length < 3) {
            return "—";
        }
        int first = Integer.parseInt(parts[0], 16);
        if ((first & 0x02) != 0) {
            return "Private MAC";
        }
        String oui = parts[0] + ":" + parts[1] + ":" + parts[2];
        if ("48:3f:da".equals(oui)) {
            return "Espressif";
        }
        return "—";
    }

    private static String quality(int rssi) {
        if (rssi >= 100) {
            return "";
        }
        if (rssi >= -50) {
            return "Excellent";
        }
        if (rssi >= -60) {
            return "Good";
        }
        if (rssi >= -70) {
            return "Fair";
        }
        return "Weak";
    }

    private static String bandName(String channel) {
        try {
            int ch = Integer.parseInt(channel);
            if (ch >= 1 && ch <= 14) {
                return "2.4 GHz";
            }
            if (ch >= 36) {
                return "5 GHz";
            }
        } catch (NumberFormatException ignored) {
            // leave unknown
        }
        return "Wi-Fi";
    }

    private static String csrf(String page) {
        Matcher matcher = CSRF.matcher(page);
        String token = "";
        while (matcher.find()) {
            token = matcher.group(1);
        }
        return token;
    }

    private static List<String> cells(String row) {
        List<String> cells = new ArrayList<>();
        Matcher matcher = CELL.matcher(row);
        while (matcher.find()) {
            String text = matcher.group(1)
                    .replaceAll("<[^>]+>", " ")
                    .replace("&nbsp;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            cells.add(text);
        }
        return cells;
    }

    private static String at(List<String> cells, int index) {
        return index < cells.size() && !cells.get(index).isEmpty() ? cells.get(index) : "—";
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    private String get(String path) throws Exception {
        return exchange(path, null);
    }

    private String post(String path, String body) throws Exception {
        return exchange(path, body);
    }

    private String exchange(String path, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(HOST + path).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "hgu-clients");
        conn.setRequestProperty("Referer", HOST + "/admin/login.asp");
        if (body != null) {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            throw new Exception("Empty response " + code + " from " + path);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
