package de.blinkt.openvpn.freevpn;

import java.util.Locale;

public final class Node {
    public final String id, ip, country, code, config;
    public final int ping, sessions;
    public final long speed, score;
    public Node(String id, String ip, String country, String code, int ping, long speed,
                int sessions, long score, String config) {
        this.id=id; this.ip=ip; this.country=country; this.code=code;
        this.ping=ping; this.speed=speed; this.sessions=sessions; this.score=score; this.config=config;
    }
    public String flag() {
        if (code.length()!=2) return "◎";
        return new String(Character.toChars(0x1F1E6+code.charAt(0)-'A'))
                + new String(Character.toChars(0x1F1E6+code.charAt(1)-'A'));
    }
    public String displayCountry() {
        switch (code) {
            case "JP": return "日本"; case "US": return "美国"; case "KR": return "韩国";
            case "SG": return "新加坡"; case "DE": return "德国"; case "GB": return "英国";
            case "FR": return "法国"; case "CA": return "加拿大"; case "AU": return "澳大利亚";
            case "NL": return "荷兰"; case "VN": return "越南"; case "TH": return "泰国";
            default: return country;
        }
    }
    public String speedLabel() { return String.format(Locale.ROOT, "%.1f Mbps", speed/1_000_000.0); }
    public boolean matches(String query) {
        String q=query.trim().toLowerCase(Locale.ROOT);
        return (country+" "+code+" "+ip+" "+id+" "+displayCountry()).toLowerCase(Locale.ROOT).contains(q);
    }
}
