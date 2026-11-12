package com.familymdm.agent.sitepolicy;

/**
 * The family's own page for anything that does not open (and our other small pages): the MDM
 * shield logo large and faded behind everything, the logo badge on top, a title and a short
 * message. One look everywhere: the family Browser, the YouTube add-on, and Chrome with Inspection.
 */
public final class BlockedPage {
    private BlockedPage() {}

    public static final String NOT_ALLOWED = "This site is not allowed";
    public static final String CONTACT = "Contact your administrator if you have any questions.";

    private static final String SHIELD = "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4z";

    /** A complete page. title and lines are plain text; extraHtml (buttons, a form) is trusted HTML. */
    public static String html(String title, String[] lines, String extraHtml) {
        StringBuilder p = new StringBuilder();
        if (lines != null) for (String l : lines) if (l != null && !l.isEmpty()) p.append("<p>").append(esc(l)).append("</p>");
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>" + esc(title) + "</title>"
                + "<style>"
                + ":root{--p:#4F46E5;--bg:#f4f4fb;--ink:#1d1b20;--mute:#5f6368;--card:rgba(255,255,255,.82)}"
                + "@media(prefers-color-scheme:dark){:root{--p:#6366F1;--bg:#111318;--ink:#e6e1e5;--mute:#a8a8b3;--card:rgba(27,29,36,.82)}}"
                + "html,body{margin:0;min-height:100%;background:var(--bg);color:var(--ink);font-family:Roboto,'Segoe UI',system-ui,sans-serif}"
                + ".wm{position:fixed;left:50%;top:50%;width:min(130vw,130vh);height:min(130vw,130vh);transform:translate(-50%,-50%);color:var(--p);opacity:.10;pointer-events:none;z-index:0}"
                + ".wrap{position:relative;z-index:1;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}"
                + ".card{background:var(--card);border-radius:20px;padding:28px 22px;max-width:440px;width:100%;box-shadow:0 6px 24px rgba(0,0,0,.08);text-align:center;backdrop-filter:blur(2px)}"
                + ".badge{width:64px;height:64px;border-radius:18px;background:var(--p);display:flex;align-items:center;justify-content:center;margin:0 auto 16px}"
                + "h1{font-size:22px;margin:0 0 10px}p{color:var(--mute);font-size:15px;line-height:1.5;margin:6px 0;word-break:break-word}"
                + ".b{display:block;text-align:center;margin-top:12px;padding:14px;border-radius:24px;background:var(--p);color:#fff;text-decoration:none;font-size:16px;border:0;width:100%;box-sizing:border-box;font-family:inherit}"
                + ".b.o{background:transparent;color:var(--p);border:1px solid var(--p)}.n{color:#b06000}"
                + "input,textarea{font:inherit;font-size:16px;padding:12px;width:100%;box-sizing:border-box;border:1px solid #999;border-radius:12px;margin-top:10px;background:transparent;color:inherit}"
                + "</style></head><body>"
                + "<svg class=wm viewBox='0 0 24 24'><path fill='currentColor' d='" + SHIELD + "'/></svg>"
                + "<div class=wrap><div class=card>"
                + "<div class=badge><svg width=34 height=34 viewBox='0 0 24 24'><path fill='#fff' d='" + SHIELD + "'/></svg></div>"
                + "<h1>" + esc(title) + "</h1>" + p + (extraHtml == null ? "" : extraHtml)
                + "</div></div></body></html>";
    }

    /** Same page, the body given as HTML (the caller escapes what goes into it). */
    public static String raw(String title, String bodyHtml, String extraHtml) {
        String page = html(title, null, extraHtml);
        return page.replace("<h1>" + esc(title) + "</h1>", "<h1>" + esc(title) + "</h1>" + (bodyHtml == null ? "" : bodyHtml));
    }

    /** "This site is not allowed. Contact your administrator..." with the address and the reason (optional). */
    public static String notAllowed(String address, String reason, String extraHtml) {
        return html(NOT_ALLOWED, new String[]{CONTACT, reason, address}, extraHtml);
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }
}
