package net.nightwhistler.htmlspanner;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtil {

	private static Pattern SPECIAL_CHAR_WHITESPACE = Pattern
			.compile("(\t| +|&[a-z]*;|&#x?([a-f]|[A-F]|[0-9])*;|\n)");

	private static Pattern SPECIAL_CHAR_NO_WHITESPACE = Pattern
			.compile("(&[a-z]*;|&#x?([a-f]|[A-F]|[0-9])*;)");

	private static Map<String, String> REPLACEMENTS = new HashMap<String, String>();

	static {

		REPLACEMENTS.put("&nbsp;", "\u00A0");
		REPLACEMENTS.put("&amp;", "&");
		REPLACEMENTS.put("&quot;", "\"");
		REPLACEMENTS.put("&cent;", "¢");
		REPLACEMENTS.put("&lt;", "<");
		REPLACEMENTS.put("&gt;", ">");
		REPLACEMENTS.put("&sect;", "§");

        REPLACEMENTS.put("&ldquo;", "“");
        REPLACEMENTS.put("&rdquo;", "”");
        REPLACEMENTS.put("&lsquo;", "‘");
        REPLACEMENTS.put("&rsquo;", "’");

	}

	/**
	 * Replaces all HTML entities ( &lt;, &amp; ), with their Unicode
	 * characters.
	 * 
	 * @param aText
	 *            text to replace entities in
	 * @return the text with entities replaced.
	 */
	public static String replaceHtmlEntities(String aText,
			boolean preserveFormatting) {
		StringBuffer result = new StringBuffer();

		Map<String, String> replacements = new HashMap<String, String>(
				REPLACEMENTS);
		Matcher matcher;

		if (preserveFormatting) {
			matcher = SPECIAL_CHAR_NO_WHITESPACE.matcher(aText);
		} else {
			matcher = SPECIAL_CHAR_WHITESPACE.matcher(aText);
			replacements.put("", " ");
			replacements.put("\n", " ");
		}

		while (matcher.find()) {
            try {
			    matcher.appendReplacement(result,
					getReplacement(matcher, replacements));
            } catch ( ArrayIndexOutOfBoundsException i ) {
                //Ignore, seems to be a matcher bug
            }
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static String getReplacement(Matcher aMatcher,
			Map<String, String> replacements) {

		String match = aMatcher.group(0).trim();
		String result = replacements.get(match);

		if (result != null) {
			return result;
		} else if ( match.startsWith("&#")) {
			
			Integer code;
			
			// Translate to unicode character.
			try {
				
				//Check if it's hex or normal
				if ( match.startsWith("&#x") ) {
					code = Integer.decode( "0x" + match.substring(3, match.length() -1));
				} else {				
					code = Integer.parseInt(match.substring(2,
						match.length() - 1));
				}
				
				return "" + (char) code.intValue();
			} catch (NumberFormatException nfe) {
				return "";
			}
		} else {
			return "";
		}
	}

}
