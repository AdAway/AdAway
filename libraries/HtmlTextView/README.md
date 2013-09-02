# HtmlTextView for Android

This HtmlTextView supports all HTML tags supported by Android's Html class (see [The CommonsBlog](http://commonsware.com/blog/Android/2010/05/26/html-tags-supported-by-textview.html) and [history of Html class](https://github.com/android/platform_frameworks_base/commits/master/core/java/android/text/Html.java) for newer additions).
Additionally, list tags are supported (``<ul>``, ``<ol>``, ``<dd>``) and code tags with ``<code>``.

This also includes a workaround to prevent TextView crashing on [specific Android versions](http://code.google.com/p/android/issues/detail?id=35466).

This library is kept very tiny with no external dependencies.
I am using it to provide Help/About Activities in my apps.

## Example

```
HtmlTextView text = new HtmlTextView(this);

// loads html from string
text.setHtmlFromString("<b>Hello</b><ul><li>world</li><li>cats</li></ul>");
```
or
```
HtmlTextView text = new HtmlTextView(this);

// loads html from raw resource, i.e., a html file in res/raw/, this allows translatable resource (e.g., res/raw-de/ for german)
text.setHtmlFromRawResource(this, R.raw.help);
```

## Use library as Gradle dependency (Android library project)

1. Copy this folder to your project and include it in ``settings.gradle`` with ``include ':html-textview'``
2. Add dependency ``compile project(':html-textview')`` to your project's ``build.gradle``.

## License

Apache License v2

## Authors
- This library was hacked together by Dominik Schürmann
- Original [TagHandler](https://gist.github.com/mlakkadshaw/5983704) developed by [Mohammed Lakkadshaw](http://blog.mohammedlakkadshaw.com/)
- Original [UrlImageGetter](https://gist.github.com/Antarix/4167655) developed by Antarix Tandon
- [JellyBeanSpanFixTextView](https://gist.github.com/pyricau/3424004) (with fix from comment) developed by Pierre-Yves Ricau

## Contributions

Feel free to fork and do pull requests. I am more than happy to merge them.
Please do not introduce external dependencies.