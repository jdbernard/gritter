package com.jdbernard.twitter

import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.conf.Configuration
import twitter4j.conf.PropertyConfiguration

public class TwitterCLI {

    static def twitter

    public static void main(String[] args) {
        if (args.length < 1) printUsage()

        Configuration conf
        File propFile = new File(System.getProperty("user.home"), ".groovy-twitter-cli-rc")

        propFile.withInputStream { is -> conf = new PropertyConfiguration(is) }

        twitter = (new TwitterFactory(conf)).getInstance()

        args = args as List

        switch (args[0].toLowerCase()) {
            case ~/t.*/: timeline(args.tail()); break
            case ~/p.*/: post(args.tail()); break 

            default:
                printUsage()
        }
    }

    void setColor(boolean bright, int code) {
        print "\u001b[${bright?'1':'0'};${code}m"
    }

    void resetColor() { print "\u001b[m" }

    void colorPrint(def message, boolean bright, int color) {
        setColor(bright, color)
        print message
        resetColor()
    }

    void colorPrintln(def message, boolean bright, int color) {
        setColor(bright, color)
        println message
        resetColor()
    }

    void timeline(List args) {

        // default to showing my friends timeline
        if (args.size() == 0) args = ["friends"]

        while (args.size() > 0) {
            def option = args.pop()
            switch (option) {
                // friends
                case ~/f.*/: printTimeline(twitter.friendsTimeline); break
                // mine
                case ~/m.*/: printTimeline(twitter.userTimeline); break
                // user
                case ~/u.*/:
                    if (args.size() == 0)
                        colorPrintln("No user specificied.", true, 31)
                    else printTimeline(twitter.getUserTimeline(args.pop()))
                    break;
                default:
                    colorPrint("Unknown timeline: ", true, 31)
                    colorPrintln(option, true, 33)
                    break;
            }
        }
    }

    void printTimeline(def timeline) {
        timeline.each { status ->
            colorPrint(status.user.screenName, true, 34)
            print ": "
            println status.text
            println ""
        }
    }

    void printUsage() {
        // TODO
    }
}
