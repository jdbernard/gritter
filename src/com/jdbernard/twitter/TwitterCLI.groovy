package com.jdbernard.twitter

import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.conf.Configuration
import twitter4j.conf.PropertyConfiguration

public class TwitterCLI {

    private static TwitterCLI nailgunInst
    private Twitter twitter

    public static void main(String[] args) {
        TwitterCLI inst = new TwitterCLI(new File(System.getProperty("user.home"),
            ".groovy-twitter-cli-rc"))

        inst.run(args as List)
    }

    public static void nailMain(String[] args) {

        if (nailgunInst == null) nailgunInst = new TwitterCLI(new File(
            System.getProperty("user.home"), ".groovy-twitter-cli-rc"))

        nailgunInst.run(args as List)
    }

    public static void setColor(boolean bright, int code) {
        print "\u001b[${bright?'1':'0'};${code}m"
    }

    public static void resetColor() { print "\u001b[m" }

    public static void colorPrint(def message, boolean bright, int color) {
        setColor(bright, color)
        print message
        resetColor()
    }

    public static void colorPrintln(def message, boolean bright, int color) {
        setColor(bright, color)
        println message
        resetColor()
    }

    public TwitterCLI(File propFile) {

        // load the configuration
        Configuration conf
        propFile.withInputStream { is -> conf = new PropertyConfiguration(is) }

        // create a twitter instance
        twitter = (new TwitterFactory(conf)).getInstance()

    }

    public void run(List args) {
        if (args.size() < 1) printUsage()

        switch (args[0].toLowerCase()) {
            case ~/t.*/: timeline(args.tail()); break
            case ~/p.*/: post(args.tail()); break 

            default:
                printUsage()
        }

    }

    public void timeline(List args) {

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

    public static void printUsage() {
        // TODO
    }
}
