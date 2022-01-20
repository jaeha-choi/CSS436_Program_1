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

    private final int hops;
    private final int retries = 3;
    private final Stack<LinkedList<String>> siteQueueStack;
    private final HashSet<String> visited = new HashSet<>();
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

        if (!startUrl.endsWith("/")) {
            startUrl += "/";
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
            while (currSiteQueue.isEmpty()) {
                this.siteQueueStack.pop();
                if (this.siteQueueStack.isEmpty()) {
                    return;
                }
                currSiteQueue = this.siteQueueStack.peek();
            }

            currUrl = currSiteQueue.poll();
            tmpLink = currUrl;
            if (!tmpLink.endsWith("/")) tmpLink += "/";
            this.siteQueueStack.add(new LinkedList<>());

            // If retry cap is reached for the same URL, reset
            if (delay >= this.retries) {
                prevUrl = "";
                delay = 0;
            } else if (!this.visited.contains(tmpLink)) {
                if (!prevUrl.equals(currUrl)) {
                    delay = 0;
                }
                if (this.visitUrl(currUrl, delay)) {
                    this.currHops++;
                }
                delay++;
                prevUrl = currUrl;
            }
        }

    }

    /**
     * Visit a page to find "href" links
     *
     * @param urlStr URL of the page to visit
     * @param delay delay before attempting to connect again
     * @return true if page was successfully visited, false otherwise
     */
    private boolean visitUrl(String urlStr, int delay) {
        boolean result = false;
        LinkedList<String> currPageQueue = this.siteQueueStack.peek();
        try {
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
                Matcher matcher = Pattern.compile("<a\s(.*?)href=\"http(.*?)\"").matcher("");
                while ((line = reader.readLine()) != null) {
                    matcher.reset(line);
                    while (matcher.find()) {
                        currPageQueue.add("http" + matcher.group(2));
                        counter++;
                    }
                }
                System.out.printf("HOP %d\tVisited %s\tFound %d URLs%n", this.currHops, urlStr, counter);
                result = true;
            } else if (respCode == HttpURLConnection.HTTP_MOVED_PERM || respCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl.trim().startsWith("http")) {
                    System.out.printf("HOP %d\tRedirecting from %s to %s%n", this.currHops, urlStr, newUrl);
                    currPageQueue.addFirst(newUrl);
                    this.visited.add(urlStr);
                }
                return false;
            } else if (500 <= respCode && respCode < 600) {
                // Add the site to try again
                System.out.printf("HOP %d\tError from %s\tResponse Code: %d\tRetry: %d%n", this.currHops, urlStr, respCode, delay + 1);
                currPageQueue.addFirst(urlStr);
                // If this wasn't the last retry, don't mark as visited and try again
                if (delay + 1 < this.retries) return false;
            } else {
                System.out.printf("HOP %d\tError from %s\tResponse Code: %d%n", this.currHops, urlStr, respCode);
            }
        } catch (IOException | InterruptedException e) {
            System.out.printf("HOP %d\tError while visiting %s%n", this.currHops, urlStr);
        }

        // Add to visited
        if (!urlStr.endsWith("/")) urlStr += "/";
        this.visited.add(urlStr);
        return result;
    }
}
