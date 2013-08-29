/*
 * Copyright (C) 2011 Alex Kuiper <http://www.nightwhistler.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nightwhistler.htmlspanner;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import net.nightwhistler.htmlspanner.handlers.*;
import net.nightwhistler.htmlspanner.handlers.attributes.AlignmentAttributeHandler;

import net.nightwhistler.htmlspanner.handlers.attributes.BorderAttributeHandler;
import net.nightwhistler.htmlspanner.handlers.attributes.StyleAttributeHandler;
import net.nightwhistler.htmlspanner.style.Style;
import net.nightwhistler.htmlspanner.handlers.StyledTextHandler;
import net.nightwhistler.htmlspanner.style.StyleValue;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import android.text.Spannable;
import android.text.SpannableStringBuilder;

/**
 * HtmlSpanner provides an alternative to Html.fromHtml() from the Android
 * libraries.
 *
 * In its simplest form, just call new HtmlSpanner().fromHtml() to get a similar
 * result. The real strength is in being able to register custom NodeHandlers.
 *
 * @author work
 *
 */
public class HtmlSpanner {

    /**
     * Temporary constant for the width of 1 horizontal em
     * Used for calculating margins.
     */
    public static final int HORIZONTAL_EM_WIDTH = 10;


    private Map<String, TagNodeHandler> handlers;

    private boolean stripExtraWhiteSpace = false;

    private HtmlCleaner htmlCleaner;

    private FontResolver fontResolver;

    /**
     * Switch to determine if CSS is used
     */
    private boolean allowStyling = true;

    /**
     * If CSS colours are used
     */
    private boolean useColoursFromStyle = true;


    /**
     * Creates a new HtmlSpanner using a default HtmlCleaner instance.
     */
    public HtmlSpanner() {
        this(createHtmlCleaner(), new SystemFontResolver());
    }

    /**
     * Creates a new HtmlSpanner using the given HtmlCleaner instance.
     *
     * This allows for a custom-configured HtmlCleaner.
     *
     * @param cleaner
     */
    public HtmlSpanner(HtmlCleaner cleaner, FontResolver fontResolver) {
        this.htmlCleaner = cleaner;
        this.fontResolver = fontResolver;
        this.handlers = new HashMap<String, TagNodeHandler>();

        registerBuiltInHandlers();
    }

    public FontResolver getFontResolver() {
        return this.fontResolver;
    }

    public void setFontResolver( FontResolver fontResolver ) {
        this.fontResolver = fontResolver;
    }

    public FontFamily getFont( String name ) {
        return this.fontResolver.getFont(name);
    }

    /**
     * Switch to specify whether excess whitespace should be stripped from the
     * input.
     *
     * @param stripExtraWhiteSpace
     */
    public void setStripExtraWhiteSpace(boolean stripExtraWhiteSpace) {
        this.stripExtraWhiteSpace = stripExtraWhiteSpace;
    }

    /**
     * Returns if whitespace is being stripped.
     *
     * @return
     */
    public boolean isStripExtraWhiteSpace() {
        return stripExtraWhiteSpace;
    }

    /**
     * Indicates whether the text style may be updated.
     *
     * If this is set to false, all CSS is ignored
     * and the basic built-in style is used.
     *
     * @return
     */
    public boolean isAllowStyling() {
        return allowStyling;
    }

    /**
     * Switch to specify is CSS style should be used.
     *
     * @param value
     */
    public void setAllowStyling( boolean value ) {
        this.allowStyling = value;
    }

    /**
     * Switch to specify if the colours from CSS
     * should override user-specified colours.
     *
     * @param value
     */
    public void setUseColoursFromStyle( boolean value ) {
        this.useColoursFromStyle = value;
    }

    public boolean isUseColoursFromStyle() {
        return this.useColoursFromStyle;
    }

    /**
     * Registers a new custom TagNodeHandler.
     *
     * If a TagNodeHandler was already registered for the specified tagName it
     * will be overwritten.
     *
     * @param tagName
     * @param handler
     */
    public void registerHandler(String tagName, TagNodeHandler handler) {
        this.handlers.put(tagName, handler);
        handler.setSpanner(this);
    }

    /**
     * Removes the handler for the given tag.
     *
     * @param tagName the tag to remove handlers for.
     */
    public void unregisterHandler(String tagName) {
        this.handlers.remove(tagName);
    }

    /**
     * Parses the text in the given String.
     *
     * @param html
     *
     * @return a Spanned version of the text.
     */
    public Spannable fromHtml(String html) {
        return fromTagNode(this.htmlCleaner.clean(html));
    }

    /**
     * Parses the text in the given Reader.
     *
     * @param reader
     * @return
     * @throws IOException
     */
    public Spannable fromHtml(Reader reader) throws IOException {
        return fromTagNode(this.htmlCleaner.clean(reader));
    }

    /**
     * Parses the text in the given InputStream.
     *
     * @param inputStream
     * @return
     * @throws IOException
     */
    public Spannable fromHtml(InputStream inputStream) throws IOException {
        return fromTagNode(this.htmlCleaner.clean(inputStream));
    }

    /**
     * Gets the currently registered handler for this tag.
     *
     * Used so it can be wrapped.
     *
     * @param tagName
     * @return the registed TagNodeHandler, or null if none is registered.
     */
    public TagNodeHandler getHandlerFor(String tagName) {
        return this.handlers.get(tagName);
    }

    /**
     * Creates spanned text from a TagNode.
     *
     * @param node
     * @return
     */
    public Spannable fromTagNode(TagNode node) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        SpanStack stack = new SpanStack();
        handleContent(result, node, stack);

        stack.applySpans(this, result);

        return result;
    }



    private static HtmlCleaner createHtmlCleaner() {
        HtmlCleaner result = new HtmlCleaner();
        CleanerProperties cleanerProperties = result.getProperties();

        cleanerProperties.setAdvancedXmlEscape(true);

        cleanerProperties.setOmitXmlDeclaration(true);
        cleanerProperties.setOmitDoctypeDeclaration(false);

        cleanerProperties.setTranslateSpecialEntities(true);
        cleanerProperties.setTransResCharsToNCR(true);
        cleanerProperties.setRecognizeUnicodeChars(true);

        cleanerProperties.setIgnoreQuestAndExclam(true);
        cleanerProperties.setUseEmptyElementTags(false);

        cleanerProperties.setPruneTags("script,title");

        return result;
    }

    private void handleContent(SpannableStringBuilder builder, Object node,
                               SpanStack stack) {
        if (node instanceof ContentNode) {

            ContentNode contentNode = (ContentNode) node;

            if (builder.length() > 0) {
                char lastChar = builder.charAt(builder.length() - 1);
                if (lastChar != ' ' && lastChar != '\n') {
                    builder.append(' ');
                }
            }

            String text = TextUtil.replaceHtmlEntities(
                    contentNode.getContent().toString(), false);

            if ( isStripExtraWhiteSpace() ) {
                //Replace unicode non-breaking space with normal space.
                text = text.replace( '\u00A0', ' ' );
            }

            text = text.trim();

            builder.append(text);

        } else if (node instanceof TagNode) {
            applySpan(builder, (TagNode) node, stack);
        }
    }

    private void applySpan(SpannableStringBuilder builder, TagNode node, SpanStack stack) {

        TagNodeHandler handler = this.handlers.get(node.getName());

        if ( handler == null ) {
            handler = new StyledTextHandler();
            handler.setSpanner(this);
        }

        int lengthBefore = builder.length();


        handler.beforeChildren(node, builder, stack);


        if ( !handler.rendersContent() ) {

            for (Object childNode : node.getChildren()) {
                handleContent(builder, childNode, stack);
            }
        }

        int lengthAfter = builder.length();
        handler.handleTagNode(node, builder, lengthBefore, lengthAfter, stack);
    }


    private static StyledTextHandler wrap( StyledTextHandler handler ) {
        return new StyleAttributeHandler(new AlignmentAttributeHandler(handler));
    }

    private void registerBuiltInHandlers() {

        TagNodeHandler italicHandler = new StyledTextHandler(
                new Style().setFontStyle(Style.FontStyle.ITALIC));

        registerHandler("i", italicHandler);
        registerHandler("em", italicHandler);
        registerHandler("cite", italicHandler);
        registerHandler("dfn", italicHandler);

        TagNodeHandler boldHandler = new StyledTextHandler(
                new Style().setFontWeight(Style.FontWeight.BOLD));

        registerHandler("b", boldHandler);
        registerHandler("strong", boldHandler);

        TagNodeHandler marginHandler = new StyledTextHandler(
                new Style().setMarginLeft(new StyleValue(2.0f, StyleValue.Unit.EM)));

        registerHandler("blockquote", marginHandler);
        registerHandler("ul", marginHandler);
        registerHandler("ol", marginHandler);

        TagNodeHandler monSpaceHandler = wrap(new MonoSpaceHandler());

        registerHandler("tt", monSpaceHandler);
        registerHandler("code", monSpaceHandler);

        registerHandler("style", new StyleNodeHandler() );

        //We wrap an alignment-handler to support
        //align attributes

        StyledTextHandler inlineAlignment = wrap(new StyledTextHandler());
        TagNodeHandler brHandler = new NewLineHandler(1, inlineAlignment);

        registerHandler("br", brHandler);

        Style paragraphStyle = new Style()
                .setDisplayStyle(Style.DisplayStyle.BLOCK)
                .setMarginBottom(
                        new StyleValue(1.0f, StyleValue.Unit.EM));


        TagNodeHandler pHandler = new BorderAttributeHandler(wrap(new StyledTextHandler(paragraphStyle)));

        registerHandler("p", pHandler);
        registerHandler("div", pHandler);

        registerHandler("h1", wrap(new HeaderHandler(1.5f, 0.5f)));
        registerHandler("h2", wrap(new HeaderHandler(1.4f, 0.6f)));
        registerHandler("h3", wrap(new HeaderHandler(1.3f, 0.7f)));
        registerHandler("h4", wrap(new HeaderHandler(1.2f, 0.8f)));
        registerHandler("h5", wrap(new HeaderHandler(1.1f, 0.9f)));
        registerHandler("h6", wrap(new HeaderHandler(1f, 1f)));

        TagNodeHandler preHandler = new PreHandler();
        registerHandler("pre", preHandler);

        TagNodeHandler bigHandler = new StyledTextHandler(
                new Style().setFontSize(
                        new StyleValue(1.25f, StyleValue.Unit.EM)));

        registerHandler("big", bigHandler);

        TagNodeHandler smallHandler = new StyledTextHandler(
                new Style().setFontSize(
                        new StyleValue(0.8f, StyleValue.Unit.EM)));

        registerHandler("small", smallHandler);

        TagNodeHandler subHandler = new SubScriptHandler();
        registerHandler("sub", subHandler);

        TagNodeHandler superHandler = new SuperScriptHandler();
        registerHandler("sup", superHandler);

        TagNodeHandler centerHandler = new StyledTextHandler(new Style().setTextAlignment(Style.TextAlignment.CENTER));
        registerHandler("center", centerHandler);

        registerHandler("li", new ListItemHandler());

        registerHandler("a", new LinkHandler());
        registerHandler("img", new ImageHandler());

        registerHandler("font", new FontHandler() );

    }

}
