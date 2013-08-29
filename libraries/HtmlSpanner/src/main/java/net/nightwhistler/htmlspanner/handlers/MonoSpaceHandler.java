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
import net.nightwhistler.htmlspanner.spans.FontFamilySpan;
import net.nightwhistler.htmlspanner.style.Style;
import org.htmlcleaner.TagNode;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;
import net.nightwhistler.htmlspanner.TagNodeHandler;

/**
 * Sets monotype font.
 * 
 * @author Alex Kuiper
 * 
 */
public class MonoSpaceHandler extends StyledTextHandler {

    @Override
    public Style getStyle() {
        return new Style().setFontFamily(
                getSpanner().getFontResolver().getMonoSpaceFont() );
    }
}
