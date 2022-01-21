/*
Jaeha Choi
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class WebCrawl {

    // Number of total hops to reach
    private final int hops;
    // Number of retries when 5xx response code is encountered
    private final int retries = 3;
    // Stack to keep track of queues, which keeps track of each URLs in page (in order)
    private final Stack<LinkedList<String>> siteQueueStack;
    // Visited url
    private final HashSet<String> visited = new HashSet<>();
    // Current hop count
    private int currHops = 0;

    /**
     * Initialize the web crawler
     *
     * @param hops number of total hops to make. Is not guaranteed to make full hops
     */
    private WebCrawl(int hops) {
        this.hops = hops;
        this.siteQueueStack = new Stack<>();
        this.siteQueueStack.add(new LinkedList<>());
    }

    /**
     * Main function that executes this program
     *
     * @param args args[0] contains the start URL;
     *             args[1] contains the number of hops to make
     */
    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.out.println("You must pass exactly two parameters.");
            System.out.println("Usage: java WebCrawl <url> <hops>");
            System.exit(1);
        }

        String startUrl = null;
        int hops = 0;

        try {
            startUrl = args[0];
            hops = Integer.parseUnsignedInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Second parameter must be an integer.");
            System.exit(1);
        }

        WebCrawl crawler = new WebCrawl(hops);
        crawler.siteQueueStack.peek().add(startUrl);
        crawler.start();
    }

    /**
     * Start crawling the web.
     */
    private void start() {
        String prevUrl = "";
        String currUrl;
        String tmpLink;
        LinkedList<String> currSiteQueue;
        int delay = 0;

        while (this.currHops <= this.hops && !this.siteQueueStack.isEmpty()) {
            currSiteQueue = this.siteQueueStack.peek();
            // If the last website did not contain any URLs, search through previous websites (queues)
            while (currSiteQueue.isEmpty()) {
                this.siteQueueStack.pop();
                if (this.siteQueueStack.isEmpty())
                    return;
                currSiteQueue = this.siteQueueStack.peek();
            }

            // Get next URL to visit
            currUrl = currSiteQueue.poll();

            // tempLink is only used to keep track of visited URL, and may not be identical to the actual url
            // "example.com" and "example.com/" should be considered the same (per spec)
            tmpLink = currUrl;
            if (!tmpLink.endsWith("/"))
                tmpLink += "/";
            this.siteQueueStack.add(new LinkedList<>());

            // If retry cap is reached for the same URL, reset
            if (delay >= this.retries) {
                prevUrl = "";
                delay = 0;
            } else if (!this.visited.contains(tmpLink)) {
                if (!prevUrl.equals(currUrl))
                    delay = 0;
                if (this.visitUrl(currUrl, delay))
                    this.currHops++;

                delay++;
                prevUrl = currUrl;
            }
        }

    }

    /**
     * Visit a page to find "href" links in <a> tag
     *
     * @param urlStr URL of the page to visit
     * @param delay  delay before attempting to connect again
     * @return true if page was successfully visited, false otherwise
     */
    private boolean visitUrl(String urlStr, int delay) {
        boolean result = false;
        // Stores current page's URL in order
        LinkedList<String> currPageQueue = this.siteQueueStack.peek();
        try {
            // Sleep if delay is given by 5xx retry (per spec)
            TimeUnit.SECONDS.sleep(delay);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);

            int respCode = conn.getResponseCode();
            if (respCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                String line;
                int counter = 0;
                Matcher matcher = Pattern.compile("<a\\s(.*?)href=\"http(.*?)\"").matcher("");

                // It's a lot faster to store all URLs in case we need to look for an alternative URL compared to
                // requesting the previous page again to find another URL.
                while ((line = reader.readLine()) != null) {
                    matcher.reset(line);
                    // Use while as there could be more than one match per line
                    while (matcher.find()) {
                        currPageQueue.add("http" + matcher.group(2));
                        counter++;
                    }
                }
                System.out.printf("HOP %d\tVisited %s\tFound %d URLs%n", this.currHops, urlStr, counter);
                result = true;
            } else if (respCode == HttpURLConnection.HTTP_MOVED_TEMP || respCode == HttpURLConnection.HTTP_MOVED_PERM) {
                String newUrl = conn.getHeaderField("Location");
                // Only hop if redirection provides a full url (per spec)
                if (newUrl.trim().startsWith("http")) {
                    System.out.printf("HOP %d\tRedirecting from %s to %s%n", this.currHops, urlStr, newUrl);
                    // Add to first to prioritize this url and visit right after
                    currPageQueue.addFirst(newUrl);
                    this.visited.add(urlStr);
                }
                return false;
            } else if (500 <= respCode && respCode < 600) {
                // Add the site to try again (per spec)
                System.out.printf("HOP %d\tError from %s\tResponse Code: %d\tRetry: %d%n", this.currHops, urlStr, respCode, delay + 1);
                // Add to first to prioritize this url and visit right after
                currPageQueue.addFirst(urlStr);
                // If this wasn't the last retry, don't mark as visited and try again
                if (delay + 1 < this.retries)
                    return false;
            } else {
                System.out.printf("HOP %d\tError from %s\tResponse Code: %d%n", this.currHops, urlStr, respCode);
            }
        } catch (IOException | InterruptedException e) {
            System.out.printf("HOP %d\tError while visiting %s%n", this.currHops, urlStr);
        }

        // "example.com" and "example.com/" should be considered the same (per spec)
        if (!urlStr.endsWith("/"))
            urlStr += "/";
        // Add to visited
        this.visited.add(urlStr);
        return result;
    }
}
