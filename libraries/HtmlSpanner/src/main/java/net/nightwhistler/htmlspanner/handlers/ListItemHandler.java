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
import net.nightwhistler.htmlspanner.TagNodeHandler;
import net.nightwhistler.htmlspanner.spans.ListItemSpan;

import org.htmlcleaner.TagNode;

import android.text.Spannable;
import android.text.SpannableStringBuilder;

/**
 * Handles items in both numbered and unordered lists.
 * 
 * @author Alex Kuiper
 * 
 */
public class ListItemHandler extends TagNodeHandler {

	private int getMyIndex(TagNode node) {
		if (node.getParent() == null) {
			return -1;
		}

		int i = 1;

		for (Object child : node.getParent().getChildren()) {
			if (child == node) {
				return i;
			}

			if (child instanceof TagNode) {
				TagNode childNode = (TagNode) child;
				if ("li".equals(childNode.getName())) {
					i++;
				}
			}
		}

		return -1;
	}

	private String getParentName(TagNode node) {
		if (node.getParent() == null) {
			return null;
		}

		return node.getParent().getName();
	}

	@Override
	public void handleTagNode(TagNode node, SpannableStringBuilder builder,
			int start, int end, SpanStack spanStack) {

		if (builder.length() > 0
				&& builder.charAt(builder.length() - 1) != '\n') {
			builder.append("\n");
		}

		if ("ol".equals(getParentName(node))) {
			ListItemSpan bSpan = new ListItemSpan(getMyIndex(node));
            spanStack.pushSpan(bSpan, start, end);
		} else if ("ul".equals(getParentName(node))) {
			// Unicode bullet character.
			ListItemSpan bSpan = new ListItemSpan();
            spanStack.pushSpan(bSpan, start, end);
		}

	}
}