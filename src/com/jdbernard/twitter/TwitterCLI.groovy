package com.jdbernard.twitter

import com.martiansoftware.nailgun.NGContext
import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.conf.Configuration
import twitter4j.conf.PropertyConfiguration

public class TwitterCLI {

    private static String EOL = System.getProperty("line.separator")

    private static TwitterCLI nailgunInst
    private Twitter twitter

    private Scanner stdin

    private Map colors = [:]

    private int terminalWidth
    private boolean colored

    public static void main(String[] args) {
        TwitterCLI inst = new TwitterCLI(new File(System.getProperty("user.home"),
            ".groovy-twitter-cli-rc"))

        inst.run((args as List) as Queue)
    }

    public static void nailMain(NGContext context) {
        if (nailgunInst == null)
            nailgunInst = new TwitterCLI(new File(
                System.getProperty("user.home"), ".groovy-twitter-cli-rc"))
        else 
            nailgunInst.stdin = new Scanner(context.in)

        nailgunInst.
        nailgunInst.run((context.args as List) as Queue)
    }

    public static void reconfigure(Queue args) {
        if (nailgunInst == null) main(args as String[])
        else {
            nailgunInst = null
            nailgunInst = new TwitterCLI(new File(
                System.getProperty("user.home"), ".groovy-twitter-cli-rc"))

            nailgunInst.run(args)
        }
    }

    static String wrapToWidth(String text, int width, String prefix, String suffix) {
        int lastSpaceIdx = 0;
        int curLineLength = 0;
        int lineStartIdx = 0;
        int i = 0;
        int actualWidth = width - prefix.length() - suffix.length()
        String wrapped = ""

        text = text.replaceAll("[\n\r]", " ")

        for (i = 0; i < text.length(); i++) {

            curLineLength++
            if (curLineLength > actualWidth) {
                wrapped += prefix + text[lineStartIdx..<lastSpaceIdx] + suffix + EOL
                curLineLength = 0
                lineStartIdx = lastSpaceIdx + 1
                i = lastSpaceIdx
            }

            if (text.charAt(i).isWhitespace())
            lastSpaceIdx = i

        }

        if (i - lineStartIdx > 1)
            wrapped += prefix + text[lineStartIdx..<text.length()]

        return wrapped
    }
    
    public TwitterCLI(File propFile) {

        // load the configuration
        Properties cfg = new Properties()
        propFile.withInputStream { is -> cfg.load(is) }

        // create a twitter instance
        twitter = (new TwitterFactory(new PropertyConfiguration(cfg))).getInstance()

        // configure the colors
        colors.author = new ConsoleColor(cfg.getProperty("colors.author", "BLUE:false"))
        colors.mentioned = new ConsoleColor(cfg.getProperty("colors.mentioned", "GREEN:false"))
        colors.error = new ConsoleColor(cfg.getProperty("colors.error", "RED:true"))
        colors.option = new ConsoleColor(cfg.getProperty("colors.option", "YELLOW:true"))
        colors.even = new ConsoleColor(cfg.getProperty("colors.even", "WHITE"))
        colors.odd = new ConsoleColor(cfg.getProperty("colors.odd", "YELLOW"))

        // configure the terminal width
        terminalWidth = (System.getenv().COLUMNS ?: cfg.terminalWidth ?: 79) as int

        colored = (cfg.colored ?: 'true') as boolean

        stdin = new Scanner(System.in)
    }

    public void run(Queue args) {
        if (args.size() < 1) printUsage()

        while (args.peek()) {
            def command = args.poll()

            switch (command.toLowerCase()) {
                case ~/h.*/: help(args); break
                case ~/p.*/: post(args); break 
                case ~/r.*/: reconfigure(args); break
                case ~/se.*/: set(args); break
                case ~/sh.*/: show(args); break
                case ~/t.*/: timeline(args); break

                default:
                    printUsage()
            }
        }
    }

    public void help(Queue args) {

    }

    public void post(Queue args) {
        def status = args.poll()

        if (!status) {
            println color("post ", colors.option) +
                color("command requires one option: ", colors.error) +
                "twitter post <status>"
            return
        }

        if (status.length() > 140)  {
            println color("Status exceeds Twitter's 140 character limit.", colors.error)
            return
        }

        print "Update status: '$status'? "
        if (stdin.nextLine() ==~ /yes|y|true|t/)
            twitter.updateStatus(status)
    }

    public void set(Queue args) {
        def option = args.poll()
        def value = args.poll()

        if (!value) {   // note: if option is null, value is null
            println color("set", colors.option) +
                color(" command requires two options: ", colors.error) +
                "twitter set <param> <value>"
            return
        }

        switch (option) {
            case "terminalWidth": terminalWidth = value as int; break
            case "colored": colored = value.toLowerCase() ==~ /true|t|on|yes|y/
                break

            default:
                println color("No property named ", colors.error) +
                    color(option, colors.option) +
                    color(" exists.", colors.error)
        }
    }

    public void show(Queue args) {

    }

    public void timeline(Queue args) {

        String timeline = args.poll() ?: "friends"
        
        switch (timeline) {
            // friends
            case ~/f.*/: printTimeline(twitter.friendsTimeline); break
            // mine
            case ~/m.*/: printTimeline(twitter.userTimeline); break
            // user
            case ~/u.*/:
                String user = args.poll()
                if (user) {
                    if (user.isNumber())
                        printTimeline(twitter.getUserTimeline(user as int))
                    else printTimeline(twitter.getUserTimeline(user))
                } else println color("No user specified.", colors.error)
                break;
            default:
                println color("Unknown timeline: ", colors.error) +
                    color(timeline, colors.option)
                break;
        }
    }

    void printTimeline(def timeline) {

        int authorLen = 0, textLen
        String statusIndent
        def textColor = colors.even

        timeline.each { status ->
            if (status.user.screenName.length() > authorLen)
                authorLen = status.user.screenName.length()
        }
            
        timeline.eachWithIndex { status, rowNum ->
            String text = status.text
            print color(status.user.screenName.padLeft(authorLen), colors.author)
            print ": "
            statusIndent = "".padLeft(authorLen + 2)
            textLen = terminalWidth - statusIndent.length()

            if (text.length() > textLen) {
                text = wrapToWidth(text, terminalWidth, statusIndent, "").
                    substring(statusIndent.length())
            } 
                
            textColor = (rowNum % 2 == 0 ? colors.even : colors.odd)
            text = text.replaceAll(/(@\w+)/, color("\$1", colors.mentioned, textColor))
            println color(text, textColor)
        }
    }

    public static void printUsage() {
        // TODO
    }

    public String resetColor() { colored ? "\u001b[m" : "" }

    public String color(def message, ConsoleColor color,
    ConsoleColor existing = null) {
        if (!colored) return message
        return color.toString() + message + (existing ?: resetColor())
    }

}
