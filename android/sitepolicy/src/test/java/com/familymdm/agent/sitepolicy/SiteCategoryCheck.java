package com.familymdm.agent.sitepolicy;

public final class SiteCategoryCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        System.out.println("ok:   " + what);
    }

    public static void main(String[] args) {
        check("News".equals(SiteCategory.of("www.foxnews.com")), "Fox News is News (a www. host)");
        check("News".equals(SiteCategory.of("foxnews.com")), "foxnews.com is News");
        check("News".equals(SiteCategory.of("FOXNEWS.COM.")), "case and a trailing dot do not matter");
        check("Social media".equals(SiteCategory.of("m.facebook.com")), "a sub-domain of a listed site has the same kind");
        check("Shopping".equals(SiteCategory.of("smile.amazon.com")), "amazon sub-domains are Shopping");
        check(SiteCategory.of("notfoxnews.com") == null, "a look-alike name is not News");
        check(SiteCategory.of("foxnews.com.attacker.example") == null, "a look-alike longer name is not News");
        check("Search".equals(SiteCategory.of("www.google.com")), "www.google.com is Search");
        check(SiteCategory.of("docs.google.com") == null, "docs.google.com is NOT Search (exact entries only)");
        check("Music".equals(SiteCategory.of("music.youtube.com")), "YouTube Music is Music");
        check("Video and streaming".equals(SiteCategory.of("www.youtube.com")), "YouTube is Video and streaming");
        check("Email".equals(SiteCategory.of("mail.google.com")), "Gmail is Email");
        check(SiteCategory.of(null) == null && SiteCategory.of("") == null, "null and empty never throw");

        check("News".equals(SiteCategory.labelFor("https://www.foxnews.com/politics/some-article")), "labelFor reads the host of an address");
        check(SiteCategory.MISC.equals(SiteCategory.labelFor("https://some-small-site.example/page")), "an unknown site is Miscellaneous");
        check(SiteCategory.MISC.equals(SiteCategory.labelFor(null)), "no address is Miscellaneous");

        String page = BlockedPage.notAllowed("https://www.foxnews.com/politics/x", "Only the sites that were approved open on this phone.", null);
        check(page.contains("This site is not allowed") && page.contains(">News<"), "the blocked page shows the category");
        check(page.contains("Only the sites that were approved open on this phone.") && page.contains("https://www.foxnews.com/politics/x"), "and still the reason and the address");
        check(page.contains(BlockedPage.CONTACT), "and still the contact line");
        String misc = BlockedPage.notAllowed("https://some-small-site.example/", null, "<a class=b href='x'>Ask for this site</a>");
        check(misc.contains(">Miscellaneous<") && misc.contains("Ask for this site"), "an unknown site says Miscellaneous and keeps the buttons it was given");
        String none = BlockedPage.notAllowed("not-an-address", "why", null);
        check(!none.contains("Miscellaneous"), "no category label when there is no web address");
        System.out.println("SiteCategoryCheck OK");
    }
}
