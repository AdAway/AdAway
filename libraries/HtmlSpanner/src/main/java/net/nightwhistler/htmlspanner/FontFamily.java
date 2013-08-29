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

import android.graphics.Typeface;

public class FontFamily {

	private Typeface defaultTypeface;

	private Typeface boldTypeface;

	private Typeface italicTypeface;

	private Typeface boldItalicTypeface;

	private String name;

	public FontFamily(String name, Typeface defaultTypeFace) {
		this.name = name;
		this.defaultTypeface = defaultTypeFace;
	}

	public String getName() {
		return name;
	}

	public void setBoldItalicTypeface(Typeface boldItalicTypeface) {
		this.boldItalicTypeface = boldItalicTypeface;
	}

	public void setBoldTypeface(Typeface boldTypeface) {
		this.boldTypeface = boldTypeface;
	}

	public void setDefaultTypeface(Typeface defaultTypeface) {
		this.defaultTypeface = defaultTypeface;
	}

	public void setItalicTypeface(Typeface italicTypeface) {
		this.italicTypeface = italicTypeface;
	}

	public Typeface getBoldItalicTypeface() {
		return boldItalicTypeface;
	}

	public Typeface getBoldTypeface() {
		return boldTypeface;
	}

	public Typeface getDefaultTypeface() {
		return defaultTypeface;
	}

	public Typeface getItalicTypeface() {
		return italicTypeface;
	}

	public boolean isFakeBold() {
		return boldTypeface == null;
	}

	public boolean isFakeItalic() {
		return italicTypeface == null;
	}

    public String toString() {
        return name;
    }

}
