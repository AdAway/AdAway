package net.nightwhistler.htmlspanner.style;

import net.nightwhistler.htmlspanner.FontFamily;

import java.lang.reflect.Field;

/**
 * CSS Style object.
 *
 * A Style is immutable: using a setter creates a new Style object with the
 * changed setings.
 */
public class Style {

    public static enum TextAlignment { LEFT, CENTER, RIGHT };
    public static enum FontWeight {  NORMAL, BOLD }
    public static enum FontStyle { NORMAL, ITALIC }
    public static enum DisplayStyle { BLOCK, INLINE };
    public static enum BorderStyle { SOLID, DASHED, DOTTED, DOUBLE }

    private final FontFamily fontFamily;
    private final TextAlignment textAlignment;
    private final StyleValue fontSize;

    private final FontWeight fontWeight;
    private final FontStyle fontStyle;

    private final Integer color;
    private final Integer backgroundColor;
    private final Integer borderColor;

    private final DisplayStyle displayStyle;
    private final BorderStyle borderStyle;
    private final StyleValue borderWidth;

    private final StyleValue textIndent;

    private final StyleValue marginTop;
    private final StyleValue marginBottom;
    private final StyleValue marginLeft;
    private final StyleValue marginRight;


    public Style() {
        fontFamily = null;
        textAlignment = null;
        fontSize = null;
        fontWeight = null;
        fontStyle = null;
        color = null;
        backgroundColor = null;
        displayStyle = null;
        marginBottom = null;
        textIndent = null;
        marginTop = null;
        marginLeft = null;
        marginRight = null;

        borderColor = null;
        borderStyle = null;
        borderWidth = null;
    }

    public Style(FontFamily family, TextAlignment textAlignment, StyleValue fontSize,
                 FontWeight fontWeight, FontStyle fontStyle, Integer color,
                 Integer backgroundColor, DisplayStyle displayStyle, StyleValue marginTop,
                 StyleValue marginBottom, StyleValue marginLeft, StyleValue marginRight,
                 StyleValue textIndent, Integer borderColor, BorderStyle borderStyle,
                 StyleValue borderWidth) {
        this.fontFamily = family;
        this.textAlignment = textAlignment;
        this.fontSize = fontSize;

        this.fontWeight = fontWeight;
        this.fontStyle = fontStyle;
        this.color = color;
        this.backgroundColor = backgroundColor;
        this.displayStyle = displayStyle;
        this.marginBottom = marginBottom;
        this.textIndent = textIndent;
        this.marginTop = marginTop;
        this.marginLeft = marginLeft;
        this.marginRight = marginRight;

        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
        this.borderStyle = borderStyle;
    }

    public Style setFontFamily(FontFamily fontFamily) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, this.fontStyle, this.color, this.backgroundColor, this.displayStyle,
                this.marginTop, this.marginBottom, this.marginLeft, this.marginRight, this.textIndent,
                this.borderColor, this.borderStyle, this.borderWidth );
    }


    public Style setTextAlignment(TextAlignment alignment) {
        return new Style(this.fontFamily, alignment, this.fontSize, this.fontWeight,
                this.fontStyle, this.color, this.backgroundColor, this.displayStyle,
                this.marginTop, this.marginBottom, this.marginLeft, this.marginRight, this.textIndent,
                this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setFontSize(StyleValue fontSize) {
        return new Style(this.fontFamily, this.textAlignment, fontSize, this.fontWeight,
                this.fontStyle, this.color, this.backgroundColor, this.displayStyle,
                this.marginTop, this.marginBottom, this.marginLeft, this.marginRight, this.textIndent,
                this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setFontWeight(FontWeight fontWeight) {
        return new Style(fontFamily, this.textAlignment, this.fontSize, fontWeight,
                this.fontStyle, this.color, this.backgroundColor, this.displayStyle,
                this.marginTop, this.marginBottom, this.marginLeft, this.marginRight, this.textIndent,
                this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setFontStyle(FontStyle fontStyle) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, this.marginTop, this.marginBottom, this.marginLeft, this.marginRight,
                this.textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setColor(Integer color) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, color, this.backgroundColor,
                this.displayStyle, this.marginTop, this.marginBottom, this.marginLeft, this.marginRight,
                this.textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setBackgroundColor( Integer bgColor ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, bgColor,
                this.displayStyle, this.marginTop, this.marginBottom, this.marginLeft, this.marginRight,
                this.textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setDisplayStyle( DisplayStyle displayStyle ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                displayStyle, this.marginTop, this.marginBottom, this.marginLeft, this.marginRight,
                this.textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setMarginBottom( StyleValue marginBottom ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, this.marginTop, marginBottom, this.marginLeft,
                this.marginRight,this.textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setMarginTop( StyleValue marginTop ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, marginTop, this.marginBottom, this.marginLeft,
                this.marginRight,this.textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setMarginLeft( StyleValue marginLeft ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, this.marginTop, this.marginBottom, marginLeft,
                this.marginRight,this.textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setMarginRight( StyleValue marginRight ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, this.marginTop, this.marginBottom, this.marginLeft,
                marginRight,this.textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setTextIndent( StyleValue textIndent ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, this.marginTop, this.marginBottom, this.marginLeft, this.marginRight,
                textIndent, this.borderColor, this.borderStyle, this.borderWidth );
    }

    public Style setBorderStyle( BorderStyle borderStyle ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, this.marginTop, this.marginBottom, this.marginLeft, this.marginRight,
                textIndent, this.borderColor, borderStyle, this.borderWidth );
    }

    public Style setBorderColor( Integer borderColor ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, this.marginTop, this.marginBottom, this.marginLeft, this.marginRight,
                textIndent, borderColor, borderStyle, this.borderWidth );
    }

    public Style setBorderWidth( StyleValue borderWidth ) {
        return new Style(fontFamily, this.textAlignment, this.fontSize,
                this.fontWeight, fontStyle, this.color, this.backgroundColor,
                this.displayStyle, this.marginTop, this.marginBottom, this.marginLeft, this.marginRight,
                textIndent, this.borderColor, this.borderStyle, borderWidth );
    }

    public Integer getBackgroundColor() {
        return this.backgroundColor;
    }

    public FontFamily getFontFamily() {
        return this.fontFamily;
    }

    public TextAlignment getTextAlignment() {
        return textAlignment;
    }

    public StyleValue getFontSize() {
        return this.fontSize;
    }

    public FontWeight getFontWeight() {
        return fontWeight;
    }

    public FontStyle getFontStyle() {
        return fontStyle;
    }

    public Integer getColor() {
        return this.color;
    }

    public DisplayStyle getDisplayStyle() {
        return this.displayStyle;
    }

    public StyleValue getMarginBottom() {
        return this.marginBottom;
    }

    public StyleValue getMarginTop() {
        return this.marginTop;
    }

    public StyleValue getMarginLeft() {
        return this.marginLeft;
    }

    public StyleValue getMarginRight() {
        return this.marginRight;
    }

    public StyleValue getTextIndent() {
        return this.textIndent;
    }

    public Integer getBorderColor() {
        return this.borderColor;
    }

    public BorderStyle getBorderStyle() {
        return this.borderStyle;
    }

    public StyleValue getBorderWidth() {
        return this.borderWidth;
    }

    public String toString() {

        StringBuilder result = new StringBuilder( "{\n" );

        if ( fontFamily != null  ) {
            result.append( "  font-family: " + fontFamily.getName() + "\n");
        }

        if ( textAlignment != null ) {
            result.append( "  text-alignment: " + textAlignment + "\n");
        }

        if ( fontSize != null ) {
            result.append( "  font-size: " + fontSize + "\n");
        }

        if ( fontWeight != null ) {
            result.append( "  font-weight: " + fontWeight + "\n" );
        }

        if ( fontStyle != null ) {
            result.append( "  font-style: " + fontStyle + "\n" );
        }

        if ( color != null ) {
            result.append("  color: " + color + "\n");
        }

        if ( backgroundColor != null ) {
            result.append("  background-color: " + backgroundColor + "\n");
        }

        if ( displayStyle != null ) {
            result.append("  display: " + displayStyle + "\n");
        }

        if ( marginTop != null ) {
            result.append("  margin-top: " + marginTop + "\n" );
        }

        if ( marginBottom != null ) {
            result.append("  margin-bottom: " + marginBottom + "\n" );
        }

        if ( marginLeft != null ) {
            result.append("  margin-left: " + marginLeft + "\n" );
        }

        if ( marginRight != null ) {
            result.append("  margin-right: " + marginRight + "\n" );
        }

        if ( textIndent != null ) {
            result.append("  text-indent: " + textIndent + "\n" );
        }

        if ( borderStyle != null ) {
            result.append("  border-style: " + borderStyle + "\n" );
        }

        if ( borderColor != null ) {
            result.append("  border-color: " + borderColor + "\n" );
        }

        if ( borderWidth != null ) {
            result.append("  border-style: " + borderWidth + "\n" );
        }

        result.append("}\n");

        return result.toString();
    }

}
