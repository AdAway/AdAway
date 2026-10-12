package org.adaway.model.source;

import org.junit.Test;

import java.util.regex.Matcher;

import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.adaway.model.source.SourceLoader.UNBOUND_LOCAL_ZONE;
import static org.adaway.model.source.SourceLoader.UNBOUND_LOCAL_DATA;
import static org.adaway.model.source.SourceLoader.RPZ_CNAME_DOT;
import static org.adaway.model.source.SourceLoader.SURGE_DOMAIN_RULE;
import static org.adaway.model.source.SourceLoader.SURGE_DOMAIN_SUFFIX_RULE;
import static org.adaway.model.source.SourceLoader.BIND_ZONE_STMT;
import static org.junit.Assert.*;

/**
 * Tests for the 4 new parser patterns added to SourceLoader:
 *  - UNBOUND_LOCAL_ZONE  (Unbound DNS config format)
 *  - UNBOUND_LOCAL_DATA  (Unbound DNS data record format)
 *  - RPZ_CNAME_DOT       (BIND Response Policy Zone format)
 *  - SURGE_DOMAIN_RULE   (Surge / Quantumult / Clash domain rule format)
 *  - BIND_ZONE_STMT      (BIND zone statement format)
 *
 * Also tests ABP/uBO $options rule handling in extractHostnameFromNonHostsSyntax.
 */
public class SourceLoaderParserPatternsTest {

    @Test
    public void abpSeparatorFollowedByPathIsNotAWholeDomainBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^/advert"));
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^banner"));
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("example.com^/advert"));
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("example.com^banner"));
        assertEquals("example.com",
                SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^"));
        assertEquals(SUFFIX,
                SourceLoader.extractRuleKindFromNonHostsSyntax("||example.com^"));
    }

    // -----------------------------------------------------------------------
    // UNBOUND_LOCAL_ZONE tests
    // -----------------------------------------------------------------------

    @Test
    public void unboundLocalZone_matchesAlwaysRefuse() {
        String line = "local-zone: \"ads.example.com\" always_refuse";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertTrue("UNBOUND_LOCAL_ZONE should match always_refuse line", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void unboundLocalZone_matchesAlwaysNxdomain() {
        String line = "local-zone: \"tracker.evil.net\" always_nxdomain";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertTrue("UNBOUND_LOCAL_ZONE should match always_nxdomain line", m.matches());
        assertEquals("tracker.evil.net", m.group(1));
    }

    @Test
    public void unboundLocalZone_matchesWithLeadingWhitespace() {
        String line = "  local-zone: \"ads.example.com\" static";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertTrue("UNBOUND_LOCAL_ZONE should match with leading whitespace", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void unboundLocalZone_alwaysNxdomainTrailingRootDot_isExactBlock() {
        String line = "local-zone: \"ads.example.com.\" always_nxdomain";

        assertEquals("ads.example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(EXACT, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void unboundLocalZone_transparent_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "local-zone: \"cdn.example.com\" transparent"));
    }

    @Test
    public void unboundLocalZone_inform_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "local-zone: \"telemetry.example.com\" inform"));
    }

    @Test
    public void unboundLocalZone_noMatchOnPlainDomain() {
        String line = "ads.example.com";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertFalse("UNBOUND_LOCAL_ZONE should not match plain domain", m.matches());
    }

    @Test
    public void unboundLocalZone_noMatchOnLocalData() {
        String line = "local-data: \"ads.example.com A 0.0.0.0\"";
        Matcher m = UNBOUND_LOCAL_ZONE.matcher(line);
        assertFalse("UNBOUND_LOCAL_ZONE should not match local-data lines", m.matches());
    }

    // -----------------------------------------------------------------------
    // UNBOUND_LOCAL_DATA tests
    // -----------------------------------------------------------------------

    @Test
    public void unboundLocalData_matchesARecord() {
        String line = "local-data: \"tracker.com A 0.0.0.0\"";
        Matcher m = UNBOUND_LOCAL_DATA.matcher(line);
        assertTrue("UNBOUND_LOCAL_DATA should match A record line", m.matches());
        assertEquals("tracker.com", m.group(1));
    }

    @Test
    public void unboundLocalData_nullIpv4_isExactBlock() {
        String line = "local-data: \"tracker.com A 0.0.0.0\"";

        assertEquals("tracker.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(EXACT, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void unboundLocalData_nullIpv6_isExactBlock() {
        String line = "local-data: \"tracker.com AAAA ::\"";

        assertEquals("tracker.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(EXACT, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void unboundLocalData_publicAddress_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "local-data: \"cdn.example.com A 1.2.3.4\""));
    }

    @Test
    public void unboundLocalData_privateAddress_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "local-data: \"router.example.com A 192.168.1.1\""));
    }

    @Test
    public void unboundLocalData_matchesWithLeadingWhitespace() {
        String line = "  local-data: \"ads.evil.org A 127.0.0.1\"";
        Matcher m = UNBOUND_LOCAL_DATA.matcher(line);
        assertTrue("UNBOUND_LOCAL_DATA should match with leading whitespace", m.matches());
        assertEquals("ads.evil.org", m.group(1));
    }

    @Test
    public void unboundLocalData_noMatchOnLocalZone() {
        String line = "local-zone: \"ads.example.com\" always_refuse";
        Matcher m = UNBOUND_LOCAL_DATA.matcher(line);
        assertFalse("UNBOUND_LOCAL_DATA should not match local-zone lines", m.matches());
    }

    // -----------------------------------------------------------------------
    // RPZ_CNAME_DOT tests
    // -----------------------------------------------------------------------

    @Test
    public void rpzCnameDot_matchesSimpleCname() {
        String line = "ads.example.com CNAME .";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertTrue("RPZ_CNAME_DOT should match simple CNAME . line", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void rpzCnameDot_matchesWithTtl() {
        String line = "tracking.site.com 60 CNAME .";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertTrue("RPZ_CNAME_DOT should match line with TTL", m.matches());
        assertEquals("tracking.site.com", m.group(1));
    }

    @Test
    public void rpzCnameDot_matchesWithInClass() {
        String line = "malware.host.ru 60 IN CNAME .";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertTrue("RPZ_CNAME_DOT should match line with TTL and IN class", m.matches());
        assertEquals("malware.host.ru", m.group(1));
    }

    @Test
    public void rpzCnameDot_matchesWithInClassNoTtl() {
        String line = "bad.domain.com IN CNAME .";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertTrue("RPZ_CNAME_DOT should match line with IN class and no TTL", m.matches());
        assertEquals("bad.domain.com", m.group(1));
    }

    @Test
    public void rpzCnameDot_trailingOwnerRootDot_isExactBlock() {
        String line = "ads.example.com. CNAME .";

        assertEquals("ads.example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(EXACT, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void rpzCnameDot_noMatchOnSoaRecord() {
        // @ SOA rpz.urlhaus.abuse.ch. ... should not match — starts with @
        String line = "@ SOA rpz.urlhaus.abuse.ch. abuse.ch. 2020060501 1 1 604800 300";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertFalse("RPZ_CNAME_DOT should not match SOA records (@ start)", m.matches());
    }

    @Test
    public void rpzCnameDot_noMatchOnNsRecord() {
        // NS record should not match — has NS not CNAME
        String line = "rpz.urlhaus.abuse.ch. NS localhost.";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertFalse("RPZ_CNAME_DOT should not match NS records", m.matches());
    }

    @Test
    public void rpzCnameDot_noMatchOnCnameToNonDot() {
        // CNAME to a real hostname (not ".") should not match
        String line = "ads.example.com CNAME real.redirect.com.";
        Matcher m = RPZ_CNAME_DOT.matcher(line);
        assertFalse("RPZ_CNAME_DOT should not match CNAME to non-dot target", m.matches());
    }

    // -----------------------------------------------------------------------
    // SURGE_DOMAIN_RULE tests
    // -----------------------------------------------------------------------

    @Test
    public void surgeDomainRule_matchesDomainSuffix() {
        String line = "DOMAIN-SUFFIX,ads.example.com";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertFalse("SURGE_DOMAIN_RULE must not flatten suffix rules into exact hosts",
                m.matches());
        assertEquals("ads.example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void surgeDomainRule_matchesDomainFull() {
        String line = "DOMAIN-FULL,tracking.example.com";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertTrue("SURGE_DOMAIN_RULE should match DOMAIN-FULL rule", m.matches());
        assertEquals("tracking.example.com", m.group(1));
        assertEquals(EXACT, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void surgeDomainRule_matchesDomainNoSuffix() {
        // bare DOMAIN, rule
        String line = "DOMAIN,exact.domain.com";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertTrue("SURGE_DOMAIN_RULE should match bare DOMAIN rule", m.matches());
        assertEquals("exact.domain.com", m.group(1));
    }

    @Test
    public void surgeDomainRule_noMatchOnDomainKeyword() {
        // DOMAIN-KEYWORD is a substring match, not a hostname — must be excluded
        String line = "DOMAIN-KEYWORD,ad";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertFalse("SURGE_DOMAIN_RULE should not match DOMAIN-KEYWORD", m.matches());
    }

    @Test
    public void surgeDomainRule_noMatchOnIpCidr() {
        String line = "IP-CIDR,192.168.0.0/24,REJECT";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertFalse("SURGE_DOMAIN_RULE should not match IP-CIDR rules", m.matches());
    }

    @Test
    public void surgeDomainRule_matchesWithTrailingWhitespace() {
        String line = "DOMAIN-SUFFIX,0z5jn.cn  ";
        Matcher m = SURGE_DOMAIN_RULE.matcher(line);
        assertFalse("SURGE_DOMAIN_RULE must not flatten suffix rules with trailing whitespace",
                m.matches());
        assertEquals("0z5jn.cn", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void surgeDomainSuffixReject_isSuffixBlock() {
        String line = "DOMAIN-SUFFIX,ads.example.com,REJECT";

        assertEquals("ads.example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void surgeDomainSuffixRejectNoResolve_isSuffixBlock() {
        String line = "DOMAIN-SUFFIX,ads.example.com,REJECT,no-resolve";

        assertEquals("ads.example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void surgeDomainSuffixDirect_isSkipped() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "DOMAIN-SUFFIX,cdn.example.com,DIRECT"));
    }

    @Test
    public void surgeDomainReject_isExactBlock() {
        String line = "DOMAIN,exact.example.com,REJECT";

        assertEquals("exact.example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(EXACT, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void surgeDomainKeywordReject_isSkipped() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("DOMAIN-KEYWORD,ad,REJECT"));
    }

    @Test(timeout = 1000)
    public void surgeDomainRules_skipHostileCommaOptionRunsWithoutBacktracking() {
        String exact = repeatedOptionRun("DOMAIN,0-");
        String suffix = repeatedOptionRun("DOMAIN-SUFFIX,0-");

        assertTrue("Exact Surge rule pattern must still finish and match the rule shape.",
                SURGE_DOMAIN_RULE.matcher(exact).matches());
        assertTrue("Suffix Surge rule pattern must still finish and match the rule shape.",
                SURGE_DOMAIN_SUFFIX_RULE.matcher(suffix).matches());
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(exact));
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(suffix));
    }

    // -----------------------------------------------------------------------
    // BIND_ZONE_STMT tests
    // -----------------------------------------------------------------------

    @Test
    public void bindZoneStmt_matchesTypeMaster() {
        String line = "zone \"ads.example.com\" { type master; notify no; file \"null.zone.file\"; };";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertTrue("BIND_ZONE_STMT should match zone statement", m.matches());
        assertEquals("ads.example.com", m.group(1));
    }

    @Test
    public void bindZoneStmt_matchesWithLeadingWhitespace() {
        String line = "  zone \"tracker.evil.net\" { type master; file \"/etc/bind/block.zone\"; };";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertTrue("BIND_ZONE_STMT should match with leading whitespace", m.matches());
        assertEquals("tracker.evil.net", m.group(1));
    }

    @Test
    public void bindZoneStmt_matchesOpenBraceOnly() {
        // Minimal valid form — just zone name and opening brace
        String line = "zone \"ads.tracker.net\" {";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertTrue("BIND_ZONE_STMT should match minimal form with just opening brace", m.matches());
        assertEquals("ads.tracker.net", m.group(1));
    }

    @Test
    public void bindZoneStmt_noMatchOnRootZone() {
        // zone "." is the DNS root zone — sanitizeHostname will reject "." as invalid hostname
        // (this test validates the pattern matches, but the caller must skip it)
        String line = "zone \".\" { type hint; file \"named.ca\"; };";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertTrue("BIND_ZONE_STMT pattern matches root zone (caller must skip '.')", m.matches());
        assertEquals(".", m.group(1));
    }

    @Test
    public void bindZoneStmt_noMatchOnPlainDomain() {
        String line = "ads.example.com";
        Matcher m = BIND_ZONE_STMT.matcher(line);
        assertFalse("BIND_ZONE_STMT should not match plain domain", m.matches());
    }

    @Test
    public void dnsmasqAddressNullIpv4_isSuffixBlock() {
        String line = "address=/example.com/0.0.0.0";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqAddressNullIpv6_isSuffixBlock() {
        String line = "address=/example.com/::";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqAddressNullHash_isSuffixBlock() {
        String line = "address=/example.com/#";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqAddressNullIpv4WithInlineComment_isSuffixBlock() {
        String line = "address=/example.com/0.0.0.0 # comment";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqAddressNullIpv6WithInlineComment_isSuffixBlock() {
        String line = "address=/example.com/:: # comment";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqAddressNullHashWithInlineComment_isSuffixBlock() {
        String line = "address=/example.com/# # comment";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqAddressTrailingRootDot_isSuffixBlock() {
        String line = "address=/example.com./0.0.0.0";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqAddressLeadingDot_isSuffixBlock() {
        String line = "address=/.example.com/0.0.0.0";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqAddressPublicRedirect_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("address=/example.com/8.8.8.8"));
    }

    @Test
    public void dnsmasqAddressPrivateRedirect_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "address=/example.com/192.168.1.1"));
    }

    @Test
    public void dnsmasqAddressLoopbackRedirect_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "address=/example.com/127.0.0.1"));
    }

    @Test
    public void dnsmasqAddressLoopbackIpv6Redirect_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "address=/example.com/::1"));
    }

    @Test
    public void dnsmasqAddressEmptyTarget_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("address=/example.com/"));
    }

    @Test
    public void dnsmasqAddressMultiDomainShape_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(
                "address=/one.example/two.example/0.0.0.0"));
    }

    @Test
    public void dnsmasqLocalRule_isSuffixBlock() {
        String line = "local=/example.com/";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqLocalRuleWithoutTrailingSlash_isSuffixBlock() {
        String line = "local=/example.com";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqLocalRuleWithInlineComment_isSuffixBlock() {
        String line = "local=/example.com/ # comment";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqLocalRuleWithoutTrailingSlashWithInlineComment_isSuffixBlock() {
        String line = "local=/example.com # comment";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqLocalTrailingRootDot_isSuffixBlock() {
        String line = "local=/example.com./";

        assertEquals("example.com", SourceLoader.extractHostnameFromNonHostsSyntax(line));
        assertEquals(SUFFIX, SourceLoader.extractRuleKindFromNonHostsSyntax(line));
    }

    @Test
    public void dnsmasqServerRule_notExtractedAsBlock() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("server=/example.com/8.8.8.8"));
    }

    @Test
    public void bindZoneStatement_notExtractedAsBlock() {
        String line = "zone \"ads.example.com\" { type master; file \"null.zone.file\"; };";
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax(line));
    }

    // -----------------------------------------------------------------------
    // ABP/uBO $options rules
    //
    // Content-type options ($script, $image, etc.) make a rule browser-only — skip at DNS level.
    // Context-only options ($third-party, $important) are still browser-context rules.
    // They are unsafe to flatten into DNS-level blocks without modelling the option semantics.
    // -----------------------------------------------------------------------

    @Test
    public void abpRule_thirdPartyAlone_skipped() {
        // ALL rules with $options are skipped at DNS level — even $third-party.
        // ||youtube.com^$third-party means "block YT as third-party embed" not "block youtube.com".
        // Ad networks (taboola.com, etc.) are covered by OISD hosts-format entries instead.
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||google.com^$third-party"));
    }

    @Test
    public void abpRule_taboolaThirdParty_skipped() {
        // $third-party context modifier → skip. OISD Full list covers taboola.com directly.
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||taboola.com^$third-party"));
    }

    @Test
    public void abpRule_scriptOption_notExtracted() {
        // $script is a content-type option → browser-only rule → skip
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^$script"));
    }

    @Test
    public void abpRule_imageOption_notExtracted() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^$image"));
    }

    @Test
    public void abpRule_stylesheetOption_notExtracted() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^$stylesheet"));
    }

    @Test
    public void abpRule_xmlhttprequestOption_notExtracted() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^$xmlhttprequest"));
    }

    @Test
    public void abpRule_multipleContentTypeOptions_notExtracted() {
        // $script,image,third-party — contains content-type options → skip
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com^$script,image,third-party"));
    }

    @Test
    public void abpRule_thirdPartyWithContentType_notExtracted() {
        // $third-party,image — image IS a content-type option → skip
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||taboola.com^$third-party,image"));
    }

    @Test
    public void abpRule_dollarNoCaretThirdParty_skipped() {
        // ||example.com$third-party (no ^ separator) — has $options → skip, same as with ^
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||example.com$third-party"));
    }

    @Test
    public void abpRule_unanchoredDollarOptions_notExtracted() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("example.com$third-party"));
    }

    @Test
    public void abpRule_unanchoredPath_notExtracted() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("example.com/path/ad.js"));
    }

    @Test
    public void abpRule_noOptions_isSuffixRule() {
        // Browser-anchor rules are suffix semantics, not exact-host entries.
        assertEquals("ads.example.com",
                SourceLoader.extractHostnameFromNonHostsSyntax("||ads.example.com^"));
        assertEquals(SUFFIX,
                SourceLoader.extractRuleKindFromNonHostsSyntax("||ads.example.com^"));
    }

    @Test
    public void abpRule_noCaretNoOptions_isSuffixRule() {
        // Browser-anchor rules without a caret are still not exact-host entries.
        assertEquals("tracker.net",
                SourceLoader.extractHostnameFromNonHostsSyntax("||tracker.net"));
        assertEquals(SUFFIX,
                SourceLoader.extractRuleKindFromNonHostsSyntax("||tracker.net"));
    }

    // -----------------------------------------------------------------------
    // ABP/uBO path-specific rules — must NOT become DNS blocks
    //
    // "||domain/path" is a URL-path filter — DNS cannot filter by path.
    // e.g. ||youtube.com/pagead/ means "block YouTube's ad-serving path", NOT "block youtube.com"
    // -----------------------------------------------------------------------

    @Test
    public void abpRule_pathRule_notExtracted() {
        // ||youtube.com/pagead/ — blocks a specific path, not the whole domain
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||youtube.com/pagead/"));
    }

    @Test
    public void abpRule_pathRuleWithParams_notExtracted() {
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("||youtube.com/youtubei/v1/player/ad_break"));
    }

    @Test
    public void abpRule_domainWithCaret_isSuffixRule() {
        // Browser-anchor rules are now stored as suffix rules, not flattened exact hosts.
        assertEquals("ads.youtube.com",
                SourceLoader.extractHostnameFromNonHostsSyntax("||ads.youtube.com^"));
        assertEquals(SUFFIX,
                SourceLoader.extractRuleKindFromNonHostsSyntax("||ads.youtube.com^"));
    }

    // -----------------------------------------------------------------------
    // ABP/uBO cosmetic (element-hiding) rules — must NOT be treated as DNS blocks
    //
    // "domain##selector" means "hide CSS selector on domain" — browser-only.
    // Bug: the old code stripped everything after '#', leaving a bare hostname
    // which then got blocked at DNS level. Fix: check ## BEFORE hash-stripping.
    // -----------------------------------------------------------------------

    @Test
    public void cosmeticRule_doubleHash_notExtracted() {
        // m.youtube.com##lazy-list > ad-slot-renderer — hides YT ads in browser, NOT a DNS block
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("m.youtube.com##lazy-list > ad-slot-renderer"));
    }

    @Test
    public void cosmeticRule_exception_notExtracted() {
        // domain#@#selector — cosmetic exception rule
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("ads.google.com,youtube.com#@#.video-ads"));
    }

    @Test
    public void cosmeticRule_scriptlet_notExtracted() {
        // domain#$#scriptlet — scriptlet injection rule
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("example.com#$#abort-on-property-read ads"));
    }

    @Test
    public void cosmeticRule_uboProc_notExtracted() {
        // domain#?#selector — uBO procedural cosmetic
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("example.com#?#div:has(> .ad)"));
    }

    @Test
    public void cosmeticRule_wildcardStar_notExtracted() {
        // domain##* — EasyList Hebrew "hide all" cosmetic rule
        assertNull(SourceLoader.extractHostnameFromNonHostsSyntax("atardrushim.com##*"));
    }

    private static String repeatedOptionRun(String prefix) {
        StringBuilder builder = new StringBuilder(prefix);
        for (int i = 0; i < 5000; i++) {
            builder.append(",!");
        }
        return builder.toString();
    }

}
