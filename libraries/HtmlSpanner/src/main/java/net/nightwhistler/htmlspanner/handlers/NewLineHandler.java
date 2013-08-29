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

import net.nightwhistler.htmlspanner.SpanStack;
import org.htmlcleaner.TagNode;

import android.text.SpannableStringBuilder;
import net.nightwhistler.htmlspanner.TagNodeHandler;

/**
 * Adds a specified number of newlines.
 * 
 * Used to implement p and br tags.
 * 
 * @author Alex Kuiper
 * 
 */
public class NewLineHandler extends WrappingHandler {

	private int numberOfNewLines;

	/**
	 * Creates this handler for a specified number of newlines.
	 * 
	 * @param howMany
	 */
	public NewLineHandler(int howMany, TagNodeHandler wrappedHandler) {
        super(wrappedHandler);
		this.numberOfNewLines = howMany;
	}

	public void handleTagNode(TagNode node, SpannableStringBuilder builder,
			int start, int end, SpanStack spanStack) {

        super.handleTagNode(node, builder, start, end, spanStack);

		for (int i = 0; i < numberOfNewLines; i++) {
			appendNewLine(builder);
		}
	}
}
