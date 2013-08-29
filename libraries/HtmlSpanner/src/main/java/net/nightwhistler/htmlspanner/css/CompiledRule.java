package net.nightwhistler.htmlspanner.css;

import android.util.Log;
import com.osbcp.cssparser.PropertyValue;
import com.osbcp.cssparser.Rule;
import com.osbcp.cssparser.Selector;
import net.nightwhistler.htmlspanner.HtmlSpanner;
import net.nightwhistler.htmlspanner.style.Style;
import org.htmlcleaner.TagNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A Compiled CSS Rule.
 *
 * A CompiledRule consists of a numbers of matchers which can match TagNodes,
 * and StyleUpdaters which can update a Style object if the rule matches.
 *
 *
 */
public class CompiledRule {

    private List<List<CSSCompiler.TagNodeMatcher>> matchers = new ArrayList<List<CSSCompiler.TagNodeMatcher>>();
    private List<CSSCompiler.StyleUpdater> styleUpdaters = new ArrayList<CSSCompiler.StyleUpdater>();

    private HtmlSpanner spanner;

    private String asText;

    CompiledRule(HtmlSpanner spanner, List<List<CSSCompiler.TagNodeMatcher>> matchers,
                        List<CSSCompiler.StyleUpdater> styleUpdaters, String asText ) {

        this.spanner = spanner;
        this.matchers = matchers;
        this.styleUpdaters = styleUpdaters;
        this.asText = asText;
    }

    public String toString() {
        return asText;
    }

    public Style applyStyle( final Style style ) {

        Style result = style;

       for ( CSSCompiler.StyleUpdater updater: styleUpdaters ) {
           result = updater.updateStyle(result, spanner);
       }

        return result;
    }



    public boolean matches( TagNode tagNode ) {

        for ( List<CSSCompiler.TagNodeMatcher> matcherList: matchers ) {
            if ( matchesChain(matcherList, tagNode)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesChain( List<CSSCompiler.TagNodeMatcher> matchers, TagNode tagNode ) {

        TagNode nodeToMatch = tagNode;

        for ( CSSCompiler.TagNodeMatcher matcher: matchers ) {
            if ( ! matcher.matches(nodeToMatch) ) {
                return false;
            }

            nodeToMatch = nodeToMatch.getParent();
        }

        return true;
    }


}
