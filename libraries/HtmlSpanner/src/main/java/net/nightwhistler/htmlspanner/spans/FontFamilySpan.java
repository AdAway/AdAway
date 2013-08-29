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

package net.nightwhistler.htmlspanner.spans;

import net.nightwhistler.htmlspanner.FontFamily;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;

public class FontFamilySpan extends TypefaceSpan {

	private final FontFamily fontFamily;

	private boolean bold;
	private boolean italic;

	public FontFamilySpan(FontFamily type) {
		super(type.getName());
		this.fontFamily = type;
	}

	public void setBold(boolean bold) {
		this.bold = bold;
	}

	public void setItalic(boolean italic) {
		this.italic = italic;
	}

	public FontFamily getFontFamily() {
		return fontFamily;
	}
	
	public boolean isBold() {
		return bold;
	}
	
	public boolean isItalic() {
		return italic;
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		applyCustomTypeFace(ds, this.fontFamily);
	}

	@Override
	public void updateMeasureState(TextPaint paint) {
		applyCustomTypeFace(paint, this.fontFamily);
	}

    public void updateMeasureState(Paint paint) {
        applyCustomTypeFace(paint, this.fontFamily);
    }


	private void applyCustomTypeFace(Paint paint, FontFamily tf) {

		paint.setAntiAlias(true);
		
		paint.setTypeface(tf.getDefaultTypeface());

		if (bold) {
			if (tf.isFakeBold()) {
				paint.setFakeBoldText(true);
			} else {
				paint.setTypeface(tf.getBoldTypeface());
			}
		}

		if (italic) {
			if (tf.isFakeItalic()) {
				paint.setTextSkewX(-0.25f);
			} else {
				paint.setTypeface(tf.getItalicTypeface());
			}
		}

		if (bold && italic && tf.getBoldItalicTypeface() != null) {
			paint.setTypeface(tf.getBoldItalicTypeface());
		}
	}

    public String toString() {
        StringBuilder builder = new StringBuilder("{\n");
        builder.append( "  font-family: " + fontFamily.getName() + "\n" );
        builder.append( "  bold: " + isBold() + "\n");
        builder.append( "  italic: " + isItalic() + "\n" );
        builder.append( "}");

        return builder.toString();
    }
}
