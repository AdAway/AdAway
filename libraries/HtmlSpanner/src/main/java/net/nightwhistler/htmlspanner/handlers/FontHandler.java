/*
 * Copyright (C) 2013 Alex Kuiper <http://www.nightwhistler.net>
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

package net.nightwhistler.htmlspanner.handlers;

import net.nightwhistler.htmlspanner.FontFamily;
import net.nightwhistler.htmlspanner.SpanStack;

import net.nightwhistler.htmlspanner.css.CSSCompiler;
import net.nightwhistler.htmlspanner.style.Style;
import org.htmlcleaner.TagNode;

import android.text.SpannableStringBuilder;

/**
 * Handler for font-tags
 */
public class FontHandler extends StyledTextHandler {

    public FontHandler() {
        super(new Style());
    }

	@Override
	public void handleTagNode(TagNode node, SpannableStringBuilder builder,
			int start, int end, Style style, SpanStack spanStack) {

        if ( getSpanner().isAllowStyling() ) {

            String face = node.getAttributeByName("face");
            String size = node.getAttributeByName("size");
            String color = node.getAttributeByName("color");

            FontFamily family = getSpanner().getFont(face);

            style = style.setFontFamily(family);

            if ( size != null ) {
                CSSCompiler.StyleUpdater updater = CSSCompiler.getStyleUpdater("font-size", size);

                if ( updater != null ) {
                    style = updater.updateStyle(style, getSpanner() );
                }
            }

            if ( color != null && getSpanner().isUseColoursFromStyle() ) {

                CSSCompiler.StyleUpdater updater = CSSCompiler.getStyleUpdater("color", color);

                if ( updater != null ) {
                    style = updater.updateStyle(style, getSpanner() );
                }
            }
        }

        super.handleTagNode(node, builder, start, end, style, spanStack);
	}
	

	
}
