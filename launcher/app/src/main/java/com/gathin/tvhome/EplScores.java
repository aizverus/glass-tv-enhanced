package com.gathin.tvhome;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * This gameweek's Premier League fixtures and results (Fantasy Premier League's public API) and the
 * league table (ESPN's public standings), neither needing a key. Fetched off the main thread at most every {@link #REFRESH_MS} while Home is in
 * front; the card above the dock shows the newest matches, OK opens the table.
 */
final class EplScores {
    private static final String TAG = "TvHome.EPL";
    private static final String TABLE = "https://site.api.espn.com/apis/v2/sports/soccer/eng.1/standings";
    static final long REFRESH_MS = 30 * 60_000L;

    static final class Match {
        final String home, away, homeAbbr, awayAbbr, homeScore, awayScore;
        /** "pre", "in" or "post". */
        final String state;
        /** The match clock or status while it is on ("67'", "HT"). */
        final String detail;
        final long kickoff;

        Match(String home, String away, String homeAbbr, String awayAbbr, String homeScore, String awayScore,
              String state, String detail, long kickoff) {
            this.home = home;
            this.away = away;
            this.homeAbbr = homeAbbr;
            this.awayAbbr = awayAbbr;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.state = state;
            this.detail = detail;
            this.kickoff = kickoff;
        }
    }

    /** {rank, team, played, goal difference, points}. */
    static final class Row {
        final String rank, team, abbr, played, diff, points;

        Row(String rank, String team, String abbr, String played, String diff, String points) {
            this.rank = rank;
            this.team = team;
            this.abbr = abbr;
            this.played = played;
            this.diff = diff;
            this.points = points;
        }
    }

    static volatile List<Match> matches = Collections.emptyList();
    static volatile List<Row> table = Collections.emptyList();
    /** The next match of each followed team, soonest first. */
    static volatile List<Match> next = Collections.emptyList();
    /** The Premier League's own YouTube channel. */
    static final String CHANNEL = "UCG5qGWdu8nIRZqJ_GgDwQ-w";
    static volatile long fetchedAt;

    private EplScores() {}

    static boolean due() {
        // While a match is on, every two minutes so the score and minute keep up; else every half hour.
        long every = REFRESH_MS;
        long now = System.currentTimeMillis();
        for (Match m : matches) {
            if (m.state.equals("in") || (m.state.equals("pre") && m.kickoff > 0 && m.kickoff <= now)) every = 2 * 60_000L;
        }
        return now - fetchedAt > every;
    }

    /** The gameweek on the card: the first one not yet finished (Fantasy Premier League's numbering). */
    static volatile int week;
    /** FPL team id to {short name, name}, from bootstrap-static; refreshed every few hours. */
    private static final java.util.Map<Integer, String[]> TEAMS = new java.util.HashMap<>();
    private static long weekCheckedAt;
    private static final String FPL = "https://fantasy.premierleague.com/api/";

    /** Fetches this gameweek's fixtures and the table; call off the main thread. */
    static boolean refresh(Set<String> teams) {
        fetchedAt = System.currentTimeMillis();
        boolean any = false;
        try {
            if (TEAMS.isEmpty() || week == 0 || fetchedAt - weekCheckedAt > 3 * 3600_000L) {
                JSONObject boot = new JSONObject(get(FPL + "bootstrap-static/"));
                JSONArray ts = boot.getJSONArray("teams");
                for (int i = 0; i < ts.length(); i++) {
                    JSONObject t = ts.getJSONObject(i);
                    TEAMS.put(t.getInt("id"), new String[]{t.optString("short_name"), t.optString("name")});
                }
                JSONArray events = boot.getJSONArray("events");
                // The week being played: the current one until it is finished, then the next.
                int current = 0;
                for (int i = 0; i < events.length() && current == 0; i++) {
                    JSONObject e = events.getJSONObject(i);
                    if (!e.optBoolean("finished")) current = e.getInt("id");
                }
                if (current > 0) week = current;
                weekCheckedAt = fetchedAt;
            }
            List<Match> found = fixtures(new JSONArray(get(FPL + "fixtures/?event=" + week)));
            matches = found;
            any = true;
            // Each followed team's next match: this week's if not yet played, else the first one after.
            List<Match> ahead = new ArrayList<>();
            for (Match m : found) if (m.state.equals("pre")) ahead.add(m);
            try {
                ahead.addAll(fixtures(new JSONArray(get(FPL + "fixtures/?future=1"))));
            } catch (Exception ex) {
                Log.w(TAG, "future: " + ex);
            }
            List<Match> nexts = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Match m : ahead) {
                boolean mine = false;
                for (String t : new String[]{m.homeAbbr, m.awayAbbr}) {
                    if (teams.contains(t) && seen.add(t)) mine = true;
                }
                if (mine && !nexts.contains(m)) nexts.add(m);
            }
            next = nexts;
        } catch (Exception ex) {
            Log.w(TAG, "fixtures: " + ex);
        }
        try {
            JSONArray entries = new JSONObject(get(TABLE)).getJSONArray("children").getJSONObject(0)
                    .getJSONObject("standings").getJSONArray("entries");
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject x = entries.getJSONObject(i);
                JSONObject team = x.getJSONObject("team");
                String rank = "", played = "", diff = "", points = "";
                JSONArray stats = x.getJSONArray("stats");
                for (int s = 0; s < stats.length(); s++) {
                    JSONObject st = stats.getJSONObject(s);
                    switch (st.optString("name")) {
                        case "rank": rank = st.optString("displayValue"); break;
                        case "gamesPlayed": played = st.optString("displayValue"); break;
                        case "pointDifferential": diff = st.optString("displayValue"); break;
                        case "points": points = st.optString("displayValue"); break;
                        default: break;
                    }
                }
                rows.add(new Row(rank, team.optString("shortDisplayName"), team.optString("abbreviation"), played, diff, points));
            }
            Collections.sort(rows, (a, b) -> Integer.compare(num(a.rank), num(b.rank)));
            table = rows;
        } catch (Exception ex) {
            Log.w(TAG, "table: " + ex);
        }
        Log.i(TAG, "matches " + matches.size() + ", table " + table.size());
        // Offline (just after the TV starts, before Wi-Fi is up): try again in a minute, not in half an hour.
        if (!any) fetchedAt = System.currentTimeMillis() - REFRESH_MS + 60_000L;
        return any;
    }

    /** FPL fixtures as matches, in kick-off order. */
    private static List<Match> fixtures(JSONArray fx) throws Exception {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        List<Match> found = new ArrayList<>();
        for (int i = 0; i < fx.length(); i++) {
            JSONObject f = fx.getJSONObject(i);
            String[] h = TEAMS.get(f.optInt("team_h")), a = TEAMS.get(f.optInt("team_a"));
            if (h == null || a == null) continue;
            long kickoff = 0;
            try {
                kickoff = iso.parse(f.optString("kickoff_time")).getTime();
            } catch (Exception ignored) {
            }
            boolean started = f.optBoolean("started"), finished = f.optBoolean("finished") || f.optBoolean("finished_provisional");
            String state = finished ? "post" : started ? "in" : "pre";
            String hs = f.isNull("team_h_score") ? "" : String.valueOf(f.optInt("team_h_score"));
            String as = f.isNull("team_a_score") ? "" : String.valueOf(f.optInt("team_a_score"));
            found.add(new Match(h[1], a[1], h[0], a[0], hs, as, state, f.optInt("minutes") + "'", kickoff));
        }
        Collections.sort(found, (x, y) -> Long.compare(x.kickoff, y.kickoff));
        return found;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 99;
        }
    }

    /** The card's lines: this week's matches of the followed teams, in kick-off order. */
    static List<Match> latest(Set<String> teams, int max) {
        List<Match> out = new ArrayList<>();
        for (Match m : matches) {
            if (teams.contains(m.homeAbbr) || teams.contains(m.awayAbbr)) out.add(m);
        }
        return out.subList(0, Math.min(max, out.size()));
    }

    /**
     * Three tab-separated columns so every line lines up: the match, its date, and the kick-off time
     * (or FT, or the minute while it is on). "CHE \u2013 MUN\tSat 31.10.26\t8.30PM", "LIV 2\u20130 BHA\tSat 25.10.26\tFT".
     */
    static String line(Match m, boolean is24) {
        SimpleDateFormat date = new SimpleDateFormat("EEE dd.MM.yy", Locale.ENGLISH);
        String teams = m.state.equals("pre") ? m.homeAbbr + " \u2013 " + m.awayAbbr
                : m.homeAbbr + " " + m.homeScore + "\u2013" + m.awayScore + " " + m.awayAbbr;
        String last;
        if (m.state.equals("pre")) {
            last = new SimpleDateFormat(is24 ? "HH.mm" : "h.mma", Locale.ENGLISH).format(m.kickoff);
        } else if (m.state.equals("in")) {
            last = "\u25CF " + m.detail;
        } else {
            last = "FT";
        }
        return teams + "\t" + date.format(m.kickoff) + "\t" + last;
    }

    /** 1 when the home side won a finished match, -1 when the away side did, else 0 (a draw, or not over). */
    static int winner(Match m) {
        if (!m.state.equals("post")) return 0;
        try {
            return Integer.signum(Integer.parseInt(m.homeScore) - Integer.parseInt(m.awayScore));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > 2 * 1024 * 1024) throw new IllegalStateException("response too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
