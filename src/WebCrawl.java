/*
Jaeha Choi
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class WebCrawl {

    private final int hops;
    private final int retries = 3;
    private final Queue<Page> siteQueue;
    private final HashSet<String> visited = new HashSet<>();
    private final Pattern urlPattern = Pattern.compile("<a href=\"http(.*?)\"");
    private int currHops = 0;

    /**
     * Initialize the web crawler
     *
     * @param hops number of total hops to make. Is not guaranteed to make full hops
     */
    private WebCrawl(int hops) {
        this.hops = hops;
        // Give the default size of hops, and change to max queue
        this.siteQueue = new PriorityQueue<>(this.hops, Collections.reverseOrder());
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
        crawler.siteQueue.add(new Page(startUrl, 0)); // initial hop count is 0
        crawler.start();
    }

    /**
     * Start crawling the web.
     */
    private void start() {
        Page currPage;
        String prevUrl = "";
        String tmpLink;
        int delay = 0;

        while (this.currHops <= this.hops && !this.siteQueue.isEmpty()) {
            currPage = this.siteQueue.poll();
            tmpLink = currPage.url;
            if (!tmpLink.endsWith("/")) tmpLink += "/";


            // If retry cap is reached for the same URL, reset
            if (delay >= this.retries) {
                prevUrl = "";
                delay = 0;
            } else if (!this.visited.contains(tmpLink)) {
                if (!prevUrl.equals(currPage.url)) {
                    delay = 0;
                }
                if (this.visitUrl(currPage.url, delay)) {
                    this.currHops++;
                }
                delay++;
                prevUrl = currPage.url;
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
                Matcher matcher = this.urlPattern.matcher("");
                int counter = 0;
                while ((line = reader.readLine()) != null) {
                    matcher.reset(line);
                    if (matcher.find()) {
                        this.siteQueue.add(new Page("http" + matcher.group(1), this.currHops + 1));
                        counter++;
                    }
                }
                System.out.printf("HOP %d\tVisited %s\tFound %d URLs%n", this.currHops, urlStr, counter);
                result = true;
            } else if (respCode == HttpURLConnection.HTTP_MOVED_PERM || respCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                String newUrl = conn.getHeaderField("Location");
                System.out.printf("HOP %d\tRedirecting from %s to %s%n", this.currHops, urlStr, newUrl);
                this.siteQueue.add(new Page(newUrl, this.currHops + 1));
                return false;
            } else if (500 <= respCode && respCode < 600) {
                // Add the site to try again
                System.out.printf("HOP %d\tError from %s\tResponse Code: %d\tRetry: %d%n", this.currHops, urlStr, respCode, delay + 1);
                this.siteQueue.add(new Page(urlStr, this.currHops + 1));
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

    /**
     * Page record store url with a value to be used for priority queue
     */
    private record Page(String url, int priority) implements Comparable<Page> {

        @Override
        public int compareTo(Page p) {
            return Integer.compare(this.priority, p.priority);
        }
    }
}
