package com.jdbernard.twitter

import com.martiansoftware.nailgun.NGContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import twitter4j.Paging
import twitter4j.Status
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
    private boolean printStatusTimestamps
    private boolean strict
    private boolean warnings

    private Logger log = LoggerFactory.getLogger(getClass())


    /* ============================================= */
    /* ======== MAIN METHODS - Entry points ======== */
    /* ============================================= */

    public static void main(String[] args) {
        TwitterCLI inst = new TwitterCLI(new File(System.getProperty("user.home"),
            ".gritterrc"))

        // trim the last argumnet, not all cli's are well-behaved
        args[-1] = args[-1].trim()
        inst.run((args as List) as LinkedList)
    }

    public static void nailMain(NGContext context) {
        if (nailgunInst == null)
            nailgunInst = new TwitterCLI(new File(
                System.getProperty("user.home"), ".gritterrc"))
        else 
            nailgunInst.stdin = new Scanner(context.in)

        // trim the last argumnet, not all cli's are well-behaved
        context.args[-1] = context.args[-1].trim()

        nailgunInst.run((context.args as List) as LinkedList)
    }

    /* ===================================== */
    /* ======== CONFIGURATION/SETUP ======== */
    /* ===================================== */

    public static void reconfigure(LinkedList args) {
        if (nailgunInst == null) main(args as String[])
        else {
            nailgunInst = null
            nailgunInst = new TwitterCLI(new File(
                System.getProperty("user.home"), ".gritterrc"))

            nailgunInst.run(args)
        }
    }

    public TwitterCLI(File propFile) {

        // load the configuration
        Properties cfg = new Properties()
        propFile.withInputStream { is -> cfg.load(is) }

        // create a twitter instance
        twitter = (new TwitterFactory(new PropertyConfiguration(cfg))).getInstance()

        // configure the colors
        colors.author = new ConsoleColor(cfg.getProperty("colors.author", "CYAN:false"))
        colors.mentioned = new ConsoleColor(cfg.getProperty("colors.mentioned", "GREEN:false"))
        colors.error = new ConsoleColor(cfg.getProperty("colors.error", "RED:true"))
        colors.option = new ConsoleColor(cfg.getProperty("colors.option", "YELLOW:true"))
        colors.even = new ConsoleColor(cfg.getProperty("colors.even", "WHITE"))
        colors.odd = new ConsoleColor(cfg.getProperty("colors.odd", "YELLOW"))

        // configure the terminal width
        terminalWidth = (System.getenv().COLUMNS ?: cfg.terminalWidth ?: 79) as int

        colored = (cfg.colored ?: 'true') as boolean
        strict = (cfg.strict ?: 'false') as boolean
        warnings = (cfg.warnings ?: 'true') as boolean
        printStatusTimestamps = (cfg."timeline.printTimestamps" ?: 'true') as boolean

        stdin = new Scanner(System.in)
    }

    /* =================================== */
    /* ======== PARSING FUNCTIONS ======== */
    /* =================================== */

    /**
     | Main entry to the command line parsing system. In general, the parsing
     | system (this method and the others dedicated to parsing arguments)
     | follow some common conventions:
     | 
     | 1.  An argument is either considered a *command* or a *parameter*.
     | 2.  Arguments are parsed in order.
     | 3.  A command can have aliases to make invokation read naturally.
     | 4.  A command may expect further refining commands, one of which
     |     will be the default. So if a command has possible refining
     |     commands, but the next argument does not match any of them,
     |     the default will be assumed and the current argument will not
     |     be comsumed, but passed on to the refining command.
     |
     | For example: ::
     |
     |     gritter set colored off show mine
     |
     | is parsed: 
     |
     | *  the first argument is expected to be a command
     | *  the ``set`` command consumes two arguments, expecting both to
     |    be parameters
     | *  the set command is finished, but there are still arguments, so the
     |    parsing process starts again, expecting the next argument to be a
     |    command.
     | *  the ``show`` command consumes one argument and expects a command
     | *  ``mine`` does not match any of the possible refinements for ``show`` so
     |    the default command, `timeline`, is assumed.
     | *  the `show timeline` command consumes one argument, expecting a command
     | *  The `show timeline mine` command executes
     | *  No more arguments remain, so execution terminates.
     |
     | Recognized top-level commands are:
     |
     | +-----------------+-------------------+----------------------------+
     | | *Command*       | *Aliases*         | *Description*              |
     | +-----------------+-------------------+----------------------------+
     | | ``delete``      | ``destroy``,      | Delete a post, status, list|
     | |                 | ``remove``        | membership, etc.           |
     | +-----------------+-------------------+----------------------------+
     | | ``get``         | ``show``          | Display a list, timeline,  |
     | |                 |                   | list membership, etc.      |
     | +-----------------+-------------------+----------------------------+
     | | ``help``        |                   | Display help for commands  |
     | +-----------------+-------------------+----------------------------+
     | | ``post``        | `add`, ``create`` | Post a new status, add a   |
     | |                 |                   | new list subscription, etc.|
     | +-----------------+-------------------+----------------------------+
     | | ``reconfigure`` |                   | Cause the tool to reload   |
     | |                 |                   | its configuration file.    |
     | +-----------------+-------------------+----------------------------+
     | | ``set``         |                   | Set a configurable value   |
     | |                 |                   | at runtime.                |
     | +-----------------+-------------------+----------------------------+
     |
     | @param args A {@link java.util.LinkedList} of arguments to parse.
     */
    public void run(LinkedList args) {
        if (args.size() < 1) printUsage()

        log.debug("argument list: {}", args)

        while (args.peek()) {
            def command = args.poll()

            switch (command.toLowerCase()) {
                case ~/delete|destroy|remove/: delete(args); break
                case ~/get|show/: get(args); break            // get|show
                case ~/help/: help(args); break               // help
                case ~/post|add|create/: post(args); break    // post|add|create
                case ~/reconfigure/: reconfigure(args); break // reconfigure
                case ~/set/: set(args); break                 // set
                default:                                      // fallthrough
                    if (strict) {
                        log.error(color("Unrecognized command: '$command'", colors.error))
                    } else {
                        if (warnings) {
                            println "Command '$command' unrecognized: " +
                                "assuming this is a parameter to 'show'"
                        }

                        args.addFirst(command)
                        get(args)
                    }
            }
        }
    }

    /* -------- DELETE Subparsing -------- */

    /**
     | Parse a ``delete`` command. Valid options are:
     |
     | +-------------+--------------------------------------------------+
     | | *Argument*  | *Description*                                    |
     | +-------------+--------------------------------------------------+
     | | ``status``  | Destroy a status given a status id. *This is the |
     | |             | default command.*                                |
     | +-------------+--------------------------------------------------+
     | | ``list``    | Delete a list, remove list members, etc.         |
     | +-------------+--------------------------------------------------+
     |
     | @param args A {@link java.util.LinkedList} of arguments.
     */
    public void delete(LinkedList args) {
        def option = args.poll()

        log.debug("Processing a 'delete' command, option = {}.", option)

        switch(option) {
            case "status": deleteStatus(args); break
            case "list": deleteList(args); break
            default: args.addFirst(option)
                deleteStatus(args)
        }
    }

    /**
     | Parse a ``delete list`` command. Valid options are:
     |
     | +------------------+---------------------------------------------+
     | | *Argument*       | *Description*                               |
     | +------------------+---------------------------------------------+
     | | ``member``       | Remove a member from a list.                |
     | +------------------+---------------------------------------------+
     | | ``subscription`` | Unsubcribe from a given list.               |
     | +------------------+---------------------------------------------+
     | | *list-reference* | Delete the list specified by the reference. |
     | +------------------+---------------------------------------------+
     |
     | @param args A {@link java.util.LinkedList} of arguments.
     */
    public void deleteList(LinkedList args) {
        def option = args.poll()

        log.debig("Processing a 'delete list' command, option = {}.", option)

        switch(option) {
            case "member": deleteListMember(args); break
            case "subscription": deleteListSubscription(args); break
            default: args.addFirst(option)
                doDeleteList(args)
        }
    }

    /* -------- GET/SHOW Subparsing -------- */


    /**
     | Parse a ``get`` command. Valid options are:
     |
     | +-------------------+-------------------------------------------------+
     | | *Argument*        | *Description*                                   |
     | +-------------------+-------------------------------------------------+
     | | ``list``          | Show a list timeline, members, subs, etc.       |
     | +-------------------+-------------------------------------------------+
     | | ``lists``         | Show lists all lists owned by a given user      |
     | +-------------------+-------------------------------------------------+
     | | ``subscriptions`` | Show all of the lists a given user is subcribed |
     | |                   | to.                                             |
     | +-------------------+-------------------------------------------------+
     | | ``timeline``      | Show a timeline of tweets. *This is the default |
     | |                   | command.*                                       |
     | +-------------------+-------------------------------------------------+
     | | ``user``          | Show information about a given users.           |
     | +-------------------+-------------------------------------------------+
     |
     | @param args A {@link java.util.LinkedList} of arguments.
     */
     public void get(LinkedList args) {
        def option = args.poll()

        log.debug("Processing a 'get' command, option = {}.", option)

        switch(option) {
            case "list":  showList(args); break
            case "lists": showLists(args); break
            case ~/subs.*/:   showSubscriptions(args); break
            case "timeline":   showTimeline(args); break
            case "user":   showUser(args); break
            default: args.addFirst(option)
                showTimeline(args)
        }
    }

    /**
     | Parse a ``show list`` command. Valid options are:
     |
     | +--------------------+-----------------------------------------------+
     | | *Argument*         | *Description*                                 |
     | +--------------------+-----------------------------------------------+
     | | ``members``        | Show the members of a given list. This is the |
     | |                    | list of users who's tweets comprise the list. |
     | +--------------------+-----------------------------------------------+
     | | ``subscribers``    | Show the subscribers of a given list. This is |
     | |                    | the list of users who are see the list.       |
     | +--------------------+-----------------------------------------------+
     | | ``subscriptions``  | Show all of the lists a given user is         |
     | |                    | subscribed to.                                |
     | +--------------------+-----------------------------------------------+
     | | *list-reference*   | Show the timeline for a given list.           |
     | +--------------------+-----------------------------------------------+
     |
     | @param args a {@link java.util.LinkedList} of arguments.
     */
    public void showList(LinkedList args) {
        def option = args.poll()

        log.debug("Processing a 'show list' command, option = '{}'", option)

        switch(option) {
            case "members": showListMembers(args); break
            case "subscribers": showListSubscribers(args); break
            case "subscriptions": showListSubscriptions(args); break
            default: args.addFirst(option)
                showListTimeline(args)
        }
    }

    /**
     | Parse a ``show lists`` command. ``show lists`` consumes at most one
     | argument, representing a user reference. It shows all of the lists
     | owned by the given user. If no user reference is given ``show lists``
     | shows the lists owned by the currently logged in user. 
     |
     | @param args a {@link java.util.LinkedList} of arguments.
     */
    public void showLists(LinkedList args) {
        def user = args.poll()

        log.debug("Processing a 'show lists' command, user = '{}'", user)

        if (!user) user = twitter.screenName

        printLists(twitter.getUserLists(user, -1)) // TODO paging
    }

    /**
     | Parse a ``show list members`` command. ``show list members`` consumes
     | one argument, a reference to the list in question.
     |
     | @param args a {@util java.util.LinkedList} of arguments.
     */
    public void showListMembers(LinkedList args) {
        def listRef = parseListReference(args)

        log.debug("Processing a 'show list members' command, list = '{}'",
            listRef)

        if (!listRef) {
            println color("show list members", colors.option) +
                color(" command requires a list reference in the form of " +
                "user/list: ", colors.error) +
                "gritter show list members <user/list>"
            return
        }

        def userList = twitter.getUserListMembers(listRef.username,
            listRef.listId, -1)

        printUserList(userList) // TODO paging
    }

    /**
     | Parse a ``show list subscribers`` command. ``show list subscribers
     | consumes one argument, a reference to the list in question.
     |
     | @param args a {@util java.util.LinkedList} of arguments.
     */
    public void showListSubscribers(LinkedList args) {
        def listRef = parseListReference(args)

        log.debug("Processing a 'show list subscribers' command, list = '{}'",
            listRef)

        if (!listRef) {
            println color("show list subscribers", colors.option) +
                color(" command requires a list reference in the form of " +
                "user/list: ", colors.error) +
                "gritter show list subscribers <user/list>"
            return
        }

        printUserList(twitter.getUserListSubscribers(
            listRef.username, listRef.listId, -1))  // TODO: paging
    }

    /**
     | Parse a ``show list members`` command. ``show list members consumes one
     | argument, a reference to the list in question.
     |
     | @param args a {@util java.util.LinkedList} of arguments.
     */
    public void showListSubscriptions(LinkedList args) {
        def user = args.poll()

        log.debug("Processing a 'show list subscriptions' command, list = '{}'",
            listRef)

        if (!user) user = twitter.screenName

        printLists(twitter.getUserListSubscriptions(user, -1)) // TODO: paging
    }

    public void showListTimeline(LinkedList args) {
        if (args.size() < 1) {
            println color("show list", colors.option) +
                color(" command requires a list reference in the form of " +
                "user/list: ", colors.error) +
                "gritter show list <user/list>"
            return
        }

        def listRef = parseListReference(args)

        log.debug("Showing a list timeline, list = '{}'", listRef)

        if (listRef.listId == -1) {
            println color("show list", colors.option) +
                color(" command requires a list reference in the form of " +
                "user/list: ", colors.error) +
                "gritter show list <user/list>"
            return
        }

        printTimeline(twitter.getUserListStatuses(
            listRef.username, listRef.listId, new Paging()))  // TODO: paging
    }

    public void showTimeline(LinkedList args) {

        String timeline = args.poll() ?: "home"
        
        log.debug("Processing a 'show timeline' command, timeline = '{}'",
            timeline)

        switch (timeline) {
            // friends
            case "friends": printTimeline(twitter.friendsTimeline); break
            // home
            case "home": printTimeline(twitter.homeTimeline); break
            // mine
            case "mine": printTimeline(twitter.userTimeline); break
            // public
            case "public": printTimeline(twitter.publicTimeline); break
            // user
            case "user":
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

    public void showUser(LinkedList args) {
        def user = args.poll()

        log.debug("Processing a 'show user' command, user = '{}'", user)

        println color("show user", colors.option) +
            color(" is not yet implemented.", colors.error)
    }

    public void createList(LinkedList args) {
        def option = args.poll()

        switch(option) {
            case "member": addListMember(args); break
            case "subscription": addListSubscription(args); break
            default: args.addFirst(option)
                createNewList(args); break
        }
    }

    public void help(LinkedList args) {

        log.debug("Processing a 'help' command.")

        // TODO: this being unimplemented really hurts the discoverability and
        // useability of the tool, FIXME ASAP.
        println color("help", colors.option) +
            color(" is not yet implemented.", colors.error)
    }

    public void post(LinkedList args) {
        def option = args.poll()

        log.debug("Processing a 'post' command: option = '{}'", option)

        if (!option) {
            println color("post", colors.option) +
                color(" command requires at least two parameters: ",
                colors.error) + "gritter post <status|retweet|list> " +
                "<options>..."
            return
        }

        switch (option) {
            case "status": postStatus(args.poll()); break
            case "retweet": retweetStatus(args.poll()); break
            case "list": createList(args); break
            default: postStatus(option)
        }
    }

    public void set(LinkedList args) {
        def option = args.poll()
        def value = args.poll()

        log.debug("Processing a 'set' command: option = '{}', value = '{}'",
            option, value)

        if (!value) {   // note: if option is null, value is null
            println color("set", colors.option) +
                color(" command requires two options: ", colors.error) +
                "gritter set <param> <value>"
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

    /* ======== WORKER FUNCTIONS ========*/

    public void deleteListMember(LinkedList args) {
        def listRef = parseListReference(args)
        def user = args.poll()

        log.debug("Deleting a member from a list: list='{}', user='{}'",
            listRef, user)

        if (!user) {
            println color("delete list member", colors.option) +
                color(" requires two parameters: ", colors.error) +
                "gritter delete list member <list-ref> <user>"
            return
        }

        // look up the user id if neccessary
        if (user.isLong()) user = user as long
        else user = twitter.showUser(user).id

        twitter.deleteUserListMember(listRef.listId, user)
    }

    public void deleteListSubscribtion(LinkedList args) {
        def listRef = parseListReference(args)

        log.debug("Unsubscribing from a list: listRef='{}', user='{}'",
            listRef, user)

        if (!listRef) {
            println color("delete list subscription", colors.option) +
                color(" requires a list reference: ", colors.error) +
                "gritter delete list subscription <list-ref>"
            return
        }

        twitter.unsubscribeUserList(listRef.username, listRef.listId)
    }

    public void doDeleteList(LinkedList args) {
        def listRef = parseListReference(args)

        log.debug("Destroying a list: listRef='{}'", listRef)

        if (!listRef) {
            println color("destroy list", colors.option) +
                color(" requries a list reference: ", colors.error) +
                "gritter destroy list <list-ref>"
            return
        }

        twitter.destroyUserList(listRef.listId)
    }

    public void deleteStatus(LinkedList args) {
        def statusId = args.poll()

        log.debug("Destroying a status: id='{}'", statusId)

        if (!statusId || !statusId.isLong()) {
            println color("destroy status", colors.option) +
                color(" requires a status id: ", colors.error) +
                "gritter delete status <status-id>"
            return
        }

        statusId = statusId as long

        twitter.destroyStatus(statusId)
    }

    public void printLists(def lists) {
        int colSize = 0

        // fins largest indentation needed
        lists.each { list ->
            def curColSize = list.user.screenName.length() +
                list.slug.length() + list.id.toString().length() + 3
                
            colSize = Math.max(colSize, curColSize)
            //println colSize //TODO, fix column alignment
        }

        lists.each { list ->
            //println colSize
            def col1 = color("@${list.user.screenName}", colors.author) + "/" +
                color("${list.slug} (${list.id})", colors.option)

            println col1.padLeft(colSize) + ": ${list.memberCount} members " +
                "and ${list.subscriberCount} subscribers"

            println wrapToWidth(list.description, terminalWidth,
                "".padLeft(8), "")
            //println col1.length()
        }
    }

    public void printUserList(def users) {
        int colSize = 0
        colSize = users.inject(0) {
            curMax, user -> Math.max(curMax, user.id.toString().length()) }

        users.each { user ->
            println "${user.id.toString().padLeft(colSize)} - " +
                color(user.screenName, colors.author) + ": ${user.name}"
        }
    }

    public void printTimeline(def timeline) {

        log.debug("Printing a timeline: {}", timeline)

        Map formatOptions = [:]

        formatOptions.authorLength = 0

        timeline.each { status ->
            if (status.user.screenName.length() > formatOptions.authorLength)
                formatOptions.authorLength = status.user.screenName.length()
        }
            
        formatOptions.indent = "".padLeft(formatOptions.authorLength + 2)

        timeline.eachWithIndex { status, rowNum ->
            formatOptions.rowNum = rowNum
            println formatStatus(status, formatOptions)
        }
    }

    public void postStatus(String status) {

        log.debug("Posting a status: '{}'", status)

        if (!status) {
            println color("post status ", colors.option) +
                color("command requires one option: ", colors.error) +
                "gritter post status <status>"
            return
        }

        if (status.length() > 140)  {
            println color("Status exceeds Twitter's 140 character limit.", colors.error)
            return
        }

        println "Update status: '$status'? "
        if (stdin.nextLine() ==~ /yes|y|true|t/) {
            try {
                twitter.updateStatus(status)
                println "Status posted."
            } catch (Exception e) {
                println "An error occurred trying to post the status: '" +
                    e.localizedMessage
                log.error("Error posting status:", e)
            }
        } else println "Status post canceled."
    }

    public void retweetStatus(def statusId) {
        
        log.debug("Retweeting a status: '{}'", statusId)

        if (!statusId.isLong() || !statusId) {
            println color("retweet ", colors.option) +
                color("command requires a status id: ", colors.error) +
                "gritter post retweet <statusId>"
        }

        statusId = statusId as long
        def status = twitter.showStatus(statusId)

        println "Retweet '" + color(status.text, colors.odd) + "'? "
        if (stdin.nextLine() ==~ /yes|y|true|t/)
            twitter.retweetStatus(statusId)
    }

    public void addListMember(LinkedList args) {
        def listRef = parseListReference(args)
        def user = args.poll()

        log.debug("Adding a member to a list: list='{}', user='{}'",
            listRef, user)

        if (!user) {
            println color("add list member", colors.option) +
                color(" requires two parameters: ", colors.error) +
                "gritter add list member <list-ref> <user>"
            return
        }

        if (!listRef) {
            println color("No list found that matches the given description: ",
                colors.error) + color(listRef, colors.option)
            return 
        }

        // look up the user id if neccessary
        if (user.isLong()) user = user as long
        else user = twitter.showUser(user).id

        twitter.addUserListMember(listRef.listId, user)

    }

    public void addListSubscription(LinkedList args) {
        def listRef = parseListReference(args)

        log.debug("Subscribing to a list: list='{}'", listRef)

        if (!listRef) {
            println color("add list subscription ", colors.option) +
                color("expects a list name, user/list: ", colors.error) +
                "gritter add list subscription <user/list>"
            return 
        }

        twitter.subscribeUserList(listRef.username, listRef.listId)
    }
    
    public void createNewList(LinkedList args) {
        def name = args.poll()
        def isPublic = args.poll()
        def desc = args.poll()

        log.debug("Creating a new list: name='{}', isPublic='{}', desc='{}'",
            name, isPublic, desc)

        if (desc == null) {
            println color("create list ", colors.option) +
                color("command requires three arguments: ", colors.error) +
                "gritter create list <listName> <isPublic> <listDescription>"
            return 
        }

        println "Create list '${color(list, colors.option)}'?"
        if (stdin.nextLine() ==~ /yes|y|true|t/)
            twitter.createUserList(name, isPublic ==~ /yes|y|true|t/, desc)
    }

    public def parseListReference(LinkedList args) {
        def username = args.poll()
        def listId

        log.debug("Looking up a list: ref = '{}'", username)
        log.debug("remaining args='{}'", args)

        if (!username) return false

        username = username.split("/")

        if (username.length != 2) {
            listId = username[0]
            username = twitter.screenName
        } else {
            listId = username[1]
            username = username[0]
        }

        if (listId.isInteger()) listId = listId as int
        else listId = findListByName(username, listId)

        // TODO: err if list does not exist

        return [username: username, listId: listId]
    }

    public int findListByName(String userName, String listName) {

        def userLists
        long cursor = -1
        int listId = -1

        while(listId == -1) {
            userLists = twitter.getUserLists(userName, cursor)
            userLists.each { list ->
                if (listName == list.name || listName == list.slug)
                    listId = list.id
            }

            if (!userLists.hasNext()) break

            cursor = userLists.nextCursor
        }

        return listId
    }

    public String formatStatus(Status status, Map formatOptions) {

        def indent = formatOptions.indent ?: ""
        def authorLength = formatOptions.authorLength ?: 0
        def rowNum = formatOptions.rowNum ?: 0

        String result
        def textColor = (rowNum % 2 == 0 ? colors.even : colors.odd)

        // add author's username
        result = color(status.user.screenName.padLeft(
            authorLength) + ": ", colors.author, textColor)

        // format the status text
        String text = status.text

        // if this status takes up more room than we have left on the line
        if (text.length() > terminalWidth - indent.length()) {
            // wrap text to terminal width
            text = wrapToWidth(text, terminalWidth, indent, "").
                substring(indent.length())

            // if we are 
        } 
            
        // color @mentions in the tweet
        text = text.replaceAll(/(@\w+)/, color("\$1", colors.mentioned, textColor))

        result += text

        return result
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
                
                if (lastSpaceIdx == -1) // we haven't seen a space on this line
                    lastSpaceIdx = lineStartIdx + actualWidth - 1

                wrapped += prefix + text[lineStartIdx..<lastSpaceIdx] + suffix + EOL
                curLineLength = 0
                lineStartIdx = lastSpaceIdx + 1
                i = lastSpaceIdx + 1
                lastSpaceIdx = -1
            }

            if (text.charAt(i).isWhitespace())
                lastSpaceIdx = i
        }

        if (i - lineStartIdx > 1)
            wrapped += prefix + text[lineStartIdx..<text.length()]

        return wrapped
    }
}
