# Translating to your language

You can help us with your translation efforts!  

Please have a look at ticket No. [AdAway/AdAway#1050](https://github.com/AdAway/AdAway/issues/1050) for more detailed information, questions and discussions!
We apreciate new contributors and translations are perfect for getting started with contributing at Github in general.

Here is the gist:
Translations are managed via the **transifex.com website**! (and Transifex' website alone, that is)

Unfortunately, we cannot merge translations via Github directly. Please follow the steps below instead.  
Sorry, but this causes some major synchronization issues if not followed.  We have to consolidate many contributions by translators and sync them up to the latest state. That is just not possible via Github.  

1. Please go to **https://www.transifex.com/free-software-for-android/adaway/**
1. Login or create a new account (you can conveniently login via Github as well)
1. Enroll into the language you want to contribute to or even submit a request for a new language.
   * Please keep in mind that we want to stick to the basic languages where possible (e.g. `sr` for Serbian).  
   Please refrain to request regional localizations (like `sr_RS`).
1. In your language section, you can browse all available resources and start **translating strings right in your browser**.
   * You don't have to download anything. Just click "Translate". The downloads are meant for more advanced use cases.

## Here are some tips for using Transifex:
* Make sure to have an eye on the **"Suggestions", "History", "Context" and "Glossary" tabs** on every entry. 
   * Mind translation efforts that were already done or suggested.  
   Basically, »*stand on the shoulders of giants*« where possible.
   * Sometimes source strings change only marginally, but their translations get cleared anyway.  
   You can easily **recover their previous translations** by looking at the "Suggestions" tab. Just make sure they really fit the new source text and edit the translation if needed.
* Help us validate translations by **reviewing others'**. 
* Some strings contain **placeholders** - like for HTML tags or numbers.  
You can click on them to add them or use keyboard shortcuts (see the page settings for an overview).
* You can point out issues to the Translation Organizers via the **"Comments" tab** or just start a discussion.
* Don't forget to save your work!
* Please don't create any Translation Pull Request here on Github.  
   * We will integrate your contributions from time to time into the code by exporting from Transifex directly. No need to provide any files from your side. ;-) 
* For more information about how to use Transifex, see https://docs.transifex.com/


We will add all Transifex translations from time to time to the app.  
For our Translation Organizers: You can use the CLI tool for this to retrieve translated resources from the Transifex server and add them to the Github repo. (See https://docs.transifex.com/client/ for more details about the client tool.)
