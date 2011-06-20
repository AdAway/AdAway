package org.adaway;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Log;

public class HostsParser {
    private Context mContext;
    private static final String TAG = "AdAway HostsParser";
    private HashSet<String> hostnames;
    private LinkedList<String> comments;

    public HostsParser(BufferedReader input, Context context) throws IOException {
        mContext = context;
        parse(input);
    }
    
    public HashSet<String> getHostnames() {
        return hostnames;
    }
    
    public LinkedList<String> getComments() {
        return comments;
    }

    private void parse(BufferedReader reader) throws IOException {
        String nextLine = null;
        hostnames = new HashSet<String>();
        comments = new LinkedList<String>();

        // I could not find any android class that provides checking of an hostname, thus i am using regex
        // http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address/3824105#3824105
        // added underscore to match more hosts
        String hostnameRegex = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-\\_]{0,61}[a-zA-Z0-9])\\.)+([a-zA-Z0-9]{2,5})$";
        Pattern hostnamePattern = Pattern.compile(hostnameRegex);

        // check for comment only line
        String commentRegex = "^#";
        Pattern commentPattern = Pattern.compile(commentRegex);

        // get preference on checking syntax
        boolean checkSyntax = SharedPrefs.getCheckSyntax(mContext);

        Matcher hostnameMatcher = null;
        Matcher commentMatcher = null;
        int index = -1;
        while ((nextLine = reader.readLine()) != null) {

            // check for comment only line
            commentMatcher = commentPattern.matcher(nextLine);
            if (commentMatcher.find()) {
                Log.d(TAG, nextLine + " is a comment only line");
                comments.add(nextLine);
            } else { // other line
                // check if there is any comment in that line
                index = nextLine.indexOf('#');
                if (index != -1) {
                    // strip comment from line and go on
                    nextLine = nextLine.substring(0, index);
                }

                // strip whitespaces from begin and end
                nextLine = nextLine.trim();

                // strip ip from line
                index = nextLine.indexOf(' ');
                if (index != -1) {
                    nextLine = nextLine.substring(index);
                }

                Log.d(TAG, "remaining line: " + nextLine);

                // strip whitespaces from begin and end
                nextLine = nextLine.trim();

                // check preferences: should we check syntax?
                if (checkSyntax) {
                    hostnameMatcher = hostnamePattern.matcher(nextLine);
                    if (hostnameMatcher.find()) {
                        // Log.d(TAG, nextLine + " matched, adding to hostnames");
                        hostnames.add(nextLine);
                    } else {
                        Log.d(TAG, nextLine + " NOT matched");
                    }
                } else {
                    // add without checking
                    hostnames.add(nextLine);
                }
            }
        }
        
        // strip localhost entry
        hostnames.remove("localhost");
    }
}
