package create_sample;

import java.util.LinkedList;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;

/**
 * Retrieves the tweets being streamed from the Streaming API.
 * 
 * @author José Parada
 * @version 1.0
 */
public class Listener implements StatusListener {
    private int count, maxCount;
    private LinkedList<Status> tweets;
    
    /**
     * Constructor with an upper limit of <code>Integer.MAX_VALUE - 1</code> 
     * tweets.
     */
    public Listener() {
        count = 0;
        maxCount = Integer.MAX_VALUE - 1;
        tweets = new LinkedList<Status>();
    }

    /**
     * Constructor with a given upper limit.
     * 
     * @param max Amount of tweets that will be streamed.
     */
    public Listener(int max) {
        count = 0;
        maxCount = max;
        tweets = new LinkedList<Status>();
    }

    /**
     * Returns whether or not the limit has been hit.
     * 
     * @return True if more or the same amount of tweets as the maximum
     * established in the constructor have been streamed, false otherwise.
     */
    public boolean limitHit() {
        return (count >= maxCount);
    }

    /**
     * If an exception occurs, prints its stack trace.
     * 
     * @param ex The exception being handled.
     */
    public void onException(Exception ex) {
        System.out.println("Exception on Listener.");
        ex.printStackTrace();	
    }

    /**
     * When a new tweet is streamed, if less tweets than the limit have been 
     * streamed, saves the tweet.
     * 
     * @param arg0 The status being streamed.
     */
    public void onStatus(Status arg0) {
        count++;
        if (count <= maxCount)
            tweets.add(arg0);
    }

    /**
     * Gets the list of tweets that have been streamed so far.
     * 
     * @return The list of tweets that have been streamed so far.
     */
    public LinkedList<Status> getTweets() {
        return tweets;
    }

    /** Empty onDeletionNotice to fulfill the StatusListener interface. 
     * 
     * @param arg0 Unused.
     */
    public void onDeletionNotice(StatusDeletionNotice arg0) { }
    
    /** Empty onScrubGeo to fulfill the StatusListener interface. 
     * 
     * @param arg0 Unused.
     * @param arg1 Unused.
     */
    public void onScrubGeo(long arg0, long arg1) { }
    
    /** Empty onStallWarning to fulfill the StatusListener interface. 
     * 
     * @param arg0 Unused.
     */
    public void onStallWarning(StallWarning arg0) { }
    
    /** Empty onTrackLimitationNotice to fulfill the StatusListener interface. 
     * 
     * @param arg0 Unused.
     */
    public void onTrackLimitationNotice(int arg0) { }
}
