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
package net.nightwhistler.htmlspanner.handlers;

import android.util.Log;
import net.nightwhistler.htmlspanner.SpanStack;

import net.nightwhistler.htmlspanner.style.Style;
import net.nightwhistler.htmlspanner.style.StyleValue;
import org.htmlcleaner.TagNode;

import android.text.SpannableStringBuilder;

/**
 * Handles Headers, by assigning a relative text-size.
 * 
 * Note that which header is handled (h1, h2, etc) is determined by the tag this
 * handler is registered for.
 * 
 * Example:
 * 
 * spanner.registerHandler("h1", new HeaderHandler(1.5f));
 * spanner.registerHandler("h2", new HeaderHandler(1.4f));
 * 
 * @author Alex Kuiper
 * 
 */
public class HeaderHandler extends StyledTextHandler {

	private final StyleValue size;
    private final StyleValue margin;

	/**
	 * Creates a HeaderHandler which gives
	 * 
	 * @param size
	 */
	public HeaderHandler(float size, float margin) {
        this.size = new StyleValue(size, StyleValue.Unit.EM);
        this.margin = new StyleValue(margin, StyleValue.Unit.EM);
	}

    @Override
    public Style getStyle() {
        return super.getStyle().setFontSize(size)
                .setFontWeight(Style.FontWeight.BOLD)
                .setDisplayStyle(Style.DisplayStyle.BLOCK)
                .setMarginBottom(margin)
                .setMarginTop(margin);

    }

}
