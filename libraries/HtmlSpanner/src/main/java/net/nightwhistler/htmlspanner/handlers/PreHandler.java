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

import net.nightwhistler.htmlspanner.FontFamily;
import net.nightwhistler.htmlspanner.SpanStack;
import net.nightwhistler.htmlspanner.TagNodeHandler;
import net.nightwhistler.htmlspanner.TextUtil;

import net.nightwhistler.htmlspanner.spans.FontFamilySpan;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.TagNode;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;

/**
 * Handles pre tags, setting the style to monospace and preserving the
 * formatting.
 * 
 * @author Alex Kuiper
 * 
 */
public class PreHandler extends TagNodeHandler {

	private void getPlainText(StringBuffer buffer, Object node) {
		if (node instanceof ContentNode) {

			ContentNode contentNode = (ContentNode) node;
			String text = TextUtil.replaceHtmlEntities(contentNode.getContent()
					.toString(), true);

			buffer.append(text);

		} else if (node instanceof TagNode) {
			TagNode tagNode = (TagNode) node;
			for (Object child : tagNode.getChildren()) {
				getPlainText(buffer, child);
			}
		}
	}

	@Override
	public void handleTagNode(TagNode node, SpannableStringBuilder builder,
			int start, int end, SpanStack spanStack) {

		StringBuffer buffer = new StringBuffer();
		getPlainText(buffer, node);

		builder.append(buffer.toString());

        FontFamily monoSpace = getSpanner().getFontResolver().getMonoSpaceFont();
		spanStack.pushSpan(new FontFamilySpan(monoSpace), start, builder.length());
		appendNewLine(builder);
		appendNewLine(builder);
	}

	@Override
	public boolean rendersContent() {
		return true;
	}

}