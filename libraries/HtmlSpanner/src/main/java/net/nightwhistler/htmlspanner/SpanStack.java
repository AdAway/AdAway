package net.nightwhistler.htmlspanner;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import net.nightwhistler.htmlspanner.css.CompiledRule;
import net.nightwhistler.htmlspanner.style.Style;
import org.htmlcleaner.TagNode;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: alex
 * Date: 5/6/13
 * Time: 2:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpanStack {

    private Stack<SpanCallback> spanItemStack = new Stack<SpanCallback>();

    private Set<CompiledRule> rules = new HashSet<CompiledRule>();

    private Map<TagNode, List<CompiledRule>> lookupCache = new HashMap<TagNode, List<CompiledRule>>();

    public void registerCompiledRule(CompiledRule rule) {
        this.rules.add( rule );
    }

    public Style getStyle( TagNode node, Style baseStyle ) {

        if ( ! lookupCache.containsKey(node) ) {

            Log.d("SpanStack", "Looking for matching CSS rules for node: "
                    + "<" + node.getName() + " id='" + option(node.getAttributeByName("id"))
                    + "' class='" + option(node.getAttributeByName("class")) + "'>");

            List<CompiledRule> matchingRules = new ArrayList<CompiledRule>();
            for ( CompiledRule rule: rules ) {
                if ( rule.matches(node)) {
                    matchingRules.add(rule);
                }
            }

            Log.d("SpanStack", "Found " + matchingRules.size() + " matching rules.");
            lookupCache.put(node, matchingRules);
        }

        Style result = baseStyle;

        for ( CompiledRule rule: lookupCache.get(node) ) {

            Log.d( "SpanStack", "Applying rule " + rule );

            Style original = result;
            result = rule.applyStyle(result);

            Log.d("SpanStack", "Original style: " + original );
            Log.d("SpanStack", "Resulting style: " + result);
        }

        return result;
    }

    private static String option( String s ) {
        if ( s == null ) {
            return "";
        } else {
            return s;
        }
    }

    public void pushSpan( final Object span, final int start, final int end ) {

        SpanCallback callback = new SpanCallback() {
            @Override
            public void applySpan(HtmlSpanner spanner, SpannableStringBuilder builder) {
                builder.setSpan(span, start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        };

        spanItemStack.push(callback);
    }

    public void pushSpan( SpanCallback callback ) {
        spanItemStack.push(callback);
    }

    public void applySpans(HtmlSpanner spanner, SpannableStringBuilder builder ) {
        while ( ! spanItemStack.isEmpty() ) {
            spanItemStack.pop().applySpan(spanner, builder);
        }
    }



}
