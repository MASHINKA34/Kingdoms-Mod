package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class EmblemUrls {
    public static final List<String> DEFAULT_HOSTS = List.of(
            "i.imgur.com",
            "imgur.com",
            "raw.githubusercontent.com",
            "cdn.discordapp.com",
            "media.discordapp.net",
            "i.ibb.co");

    public static String sanitize(String url) {
        if (url == null) {
            return "";
        }
        String cleaned = url.strip();
        if (cleaned.isEmpty()) {
            return "";
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            if (cleaned.contains("://") || !cleaned.contains(".")) {
                return "";
            }
            cleaned = "https://" + cleaned;
        }
        if (cleaned.length() > Faction.MAX_EMBLEM_URL_LENGTH) {
            return "";
        }
        return isAllowed(cleaned) ? cleaned : "";
    }

    public static boolean isAllowed(String url) {
        String host = hostOf(url);
        if (host.isEmpty()) {
            return false;
        }
        List<? extends String> allowed = allowedHosts();
        if (allowed.isEmpty()) {
            return false;
        }
        for (String entry : allowed) {
            String candidate = entry == null ? "" : entry.strip().toLowerCase(Locale.ROOT);
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.equals("*")) {
                return true;
            }
            if (host.equals(candidate) || host.endsWith("." + candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<? extends String> allowedHosts() {
        try {
            return ModConfigSpec.EMBLEM_ALLOWED_HOSTS.get();
        } catch (RuntimeException exception) {
            return DEFAULT_HOSTS;
        }
    }

    private static String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || scheme == null) {
                return "";
            }
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            if (!lowerScheme.equals("http") && !lowerScheme.equals("https")) {
                return "";
            }
            if (uri.getUserInfo() != null) {
                return "";
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private EmblemUrls() {
    }
}
