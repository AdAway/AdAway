package net.nightwhistler.htmlspanner;

import com.osbcp.cssparser.CSSParser;
import com.osbcp.cssparser.Rule;

import net.nightwhistler.htmlspanner.css.CSSCompiler;
import net.nightwhistler.htmlspanner.css.CompiledRule;
import org.htmlcleaner.TagNode;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static junit.framework.Assert.*;

public class RuleMatchingTest {

    /*
    Yes, commenting out code is EVIL :)

    This test now runs into the dreaded Stub! error,
    so to get it working again I probably need to use Robolectric,
    but that means transforming the whole project into an
    Android library project.... which is kind of lame, since
    it's perfectly fine as a compiled jar.

    Stay tuned for the resolution... :)
     */

    /*
    @Test
    public void straightTagNameMatch() throws Exception {

        List<Rule> rules = CSSParser.parse( "a { text-size: 3;}" );
        CompiledRule rule = CSSCompiler.compile(rules.get(0), new HtmlSpanner());

        TagNode nodeA = new TagNode( "a" );
        TagNode nodeB = new TagNode( "b" );

        assertTrue( rule.matches( nodeA ) );
        assertFalse( rule.matches( nodeB ) );
    }

    @Test
    public void tagClassMatch() throws Exception {
        List<Rule> rules = CSSParser.parse( ".red {text-size: 3; }" );
        CompiledRule rule = CSSCompiler.compile(rules.get(0), new HtmlSpanner());

        TagNode nodeA = new TagNode( "a" );
        nodeA.setAttribute("class", "red");

        TagNode nodeB = new TagNode( "b" );
        nodeB.setAttribute("class", "blue");

        assertTrue( rule.matches( nodeA ) );
        assertFalse( rule.matches( nodeB ) );
    }

    @Test
    public void tagClassAndNameMatch() throws Exception {
        List<Rule> rules = CSSParser.parse( "a.red { text-size: 3; }" );
        CompiledRule rule = CSSCompiler.compile(rules.get(0), new HtmlSpanner());

        TagNode nodeA = new TagNode( "a" );
        nodeA.setAttribute("class", "red");

        TagNode nodeB = new TagNode( "b" );
        nodeB.setAttribute("class", "red");

        assertTrue( rule.matches( nodeA ) );
        assertFalse( rule.matches( nodeB ) );
    }

    @Test
    public void tagMatchById() throws Exception {
        List<Rule> rules = CSSParser.parse( "#red { text-size: 3;}" );
        CompiledRule rule = CSSCompiler.compile(rules.get(0), new HtmlSpanner());

        TagNode nodeA = new TagNode( "a" );
        nodeA.setAttribute("id", "red");

        TagNode nodeB = new TagNode( "b" );
        nodeB.setAttribute("class", "red");

        assertTrue( rule.matches( nodeA ) );
        assertFalse( rule.matches( nodeB ) );
    }


    @Test
    public void tagMatchMultiRule() throws Exception {
        List<Rule> rules = CSSParser.parse( "div .red { text-size: 3;}" );
        CompiledRule rule = CSSCompiler.compile(rules.get(0), new HtmlSpanner());

        TagNode divNode = new TagNode("div");

        TagNode nodeA = new TagNode( "a" );
        nodeA.setAttribute("class", "red");

        divNode.addChild( nodeA );

        TagNode spanNode = new TagNode("span");

        TagNode nodeB = new TagNode( "b" );
        nodeB.setAttribute("class", "red");
        spanNode.addChild(nodeB);

        assertTrue( rule.matches( nodeA ) );
        assertFalse( rule.matches( nodeB ) );

    }
    */

}
