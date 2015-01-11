package create_sample;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;

import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;
import twitter4j.conf.ConfigurationBuilder;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Pair;

public class SampleCreator {

    private static final int MAX_STREAMED = 18000;
    private static final int MAX_CYCLES = 10;
    private static final int MAX_LOOKUP_SIZE = 100; // Size of the Twitter API Status Lookup method response
    private static final int MIN_RETWEETS = 2; // Minimum amount of retweets necessary to monitor a tweet
    private static final int GET_FOLLOWERS_APP_LIMIT = 15; // Max amount of calls to getFollowersIDs in 15 minutes through app auth
    private static final int GET_FOLLOWERS_USER_LIMIT = 15; // Max amount of calls to getFollowersIDs in 15 minutes through user auth
    private static final int GET_RETWEETS_USER_LIMIT = 15; // Max amount of calls to getRetweets in 15 minutes through user auth
    private static final int GET_RETWEETS_APP_LIMIT = 60; // Max amount of calls to getRetweets in 15 minutes through app auth
    private static final int GET_RETWEETERS_USER_LIMIT = 15; // Max amount of calls to getRetweeterIDs in 15 minutes through user auth
    private static final int GET_RETWEETERS_APP_LIMIT = 60; // Max amount of calls to getRetweeterIDs in 15 minutes through app auth
    // Max amount of calls to getRetweeter functions in 15 minutes
    private static final int MAX_MONITORED = GET_RETWEETS_USER_LIMIT + GET_RETWEETS_APP_LIMIT + GET_RETWEETERS_USER_LIMIT + GET_RETWEETERS_APP_LIMIT; 
    private static final long FIFTEEN_MINUTES = 900000;
    private static final long ONE_SECOND = 1000;
    private static final long TWO_SECONDS = 2000;
    // Amount of 15 minute blocks without retweets before tweet is delcared dead
    private static final int PERIODS_TO_DIE = 4; 
    private static final String[] POSITIVE_EMOTICONS = {":-)", ":)", ";)", ";-)", ":D", ":-D"};
    private static final String[] NEGATIVE_EMOTICONS = {":(", ":-(", "D:", "D-:", ";_;"};
    private static final File SAVED_SENTIMENT_CLASSIFIER = new File("SentimentClassifier.txt");
    private static final File SAVED_TOPIC_CLASSIFIER = new File("TopicClassifier.txt");
    private static final File MONITOR_PROGRESS = new File("MonitorProgress.ser");
    private static final File DEAD_MONITOR_PROGRESS = new File("DeadMonitorProgress.ser");
    private static final File PRINTED_MONITOR_PROGRESS = new File("MonitorProgress.txt");
    private static final File PRINTED_DEAD_MONITOR_PROGRESS = new File("DeadMonitorProgress.txt");
    private static final File MONITOR_FINAL = new File("MonitorFinal.ser");
    private static final File DEAD_MONITOR_FINAL = new File("DeadMonitorFinal.ser");
    private static final File PRINTED_MONITOR_FINAL = new File("MonitorFinal.txt");
    private static final File PRINTED_DEAD_MONITOR_FINAL = new File("DeadMonitorFinal.txt");
    private static final File CLUSTERING_PROGRESS = new File("ClusterProgress.ser");
    private static final File CLUSTERING_PROGRESS_NUMBER = new File("ClusterProgress.txt");
    private static final File DIFFUSION_PROGRESS = new File("DiffusionProgress.ser");
    private static final File DIFFUSION_PROGRESS_NUMBER = new File("DiffusionProgress.txt");
    
    private static int findFollowersRate = 0, getRetweetsRate = 0, secondsStreamed = 0;
    private static Twitter twitter, userAuth, appAuth;
    private static PriorityQueue<Long> checkedRetweetedTweets = new PriorityQueue<Long>();

    public static void main(String[] args) throws ClassNotFoundException, InterruptedException, IOException, TwitterException {
        // Don't want the Twitter4J logger cluttering up the console
        System.setProperty("twitter4j.loggerFactory", "twitter4j.NullLoggerFactory");
        
        // Connect to Twitter
        try {
            twitter = connectionSetup();
        } catch (TwitterException e) {
            System.out.println("Fatal: Could not connect to Twitter.");
            e.printStackTrace();
            throw e;
        }
        
        HashSet<MonitoredStatus> monitor = new HashSet<MonitoredStatus>(), dead = new HashSet<MonitoredStatus>();
        LinkedList<Status> newSample = new LinkedList<Status>();
        boolean finished = false;
        int cycles = 0;
        
        // Load the classifiers
        SentimentClassifier sentimentClassifier = null;
        TopicClassifier topicClassifier = null;
        try {
            sentimentClassifier = new SentimentClassifier(SAVED_SENTIMENT_CLASSIFIER);
            topicClassifier = new TopicClassifier(SAVED_TOPIC_CLASSIFIER);
        } catch (ClassNotFoundException e) {
            System.out.println("Fatal: Topic or sentiment classifier files did not contain a valid classifier.");
            e.printStackTrace();
            throw e;
        } catch (IOException e) {
            System.out.println("Fatal: Could not read topic or sentiment classifier file.");
            e.printStackTrace();
            throw e;
        }

        // Create the sample
        while (!finished) {
            // Update the tweets
            updateMonitor(monitor, dead);

            // Add previous sample
            try {
                addSampleToMonitor(monitor, newSample, sentimentClassifier, topicClassifier);
            } catch (TwitterException e) {
                System.out.println("Fatal: Status Lookup failed.");
                e.printStackTrace();
                throw e;
            }

            printMonitors(monitor, dead);

            // Get new sample
            try {
            	newSample = streamTweets(MAX_STREAMED);
            } catch (InterruptedException e) {
                System.out.println("Fatal: Stream sleep interrupted.");
                e.printStackTrace();
                throw e;
            }

            try {
            	saveMonitorProgress(monitor, dead);
            } catch (IOException e) {
            	System.out.println("Fatal: Could not write to monitor progress files.");
            	e.printStackTrace();
            	throw e;
            }

            // End after a number of cycles
            if (cycles == MAX_CYCLES) {
                finished = true;
            }

            // Refresh rate limit window
            if (!finished) {
                try {
                	System.out.println("Cycle done: " + cycles);
                	if (FIFTEEN_MINUTES - (secondsStreamed * 1000) > 0) {
                		System.out.println("Sleeping " + (FIFTEEN_MINUTES - (secondsStreamed * 1000)) + " ms to refresh rate limit.");
                    	System.out.println(new Date());
                		Thread.sleep(FIFTEEN_MINUTES - (secondsStreamed * 1000));
                	}
                    getRetweetsRate = 0;
                } catch (InterruptedException e) {
                    System.out.println("Fatal: Rate limit refresh sleep interrupted.");
                    e.printStackTrace();
                    throw e;
                }
            }
            
            cycles++;
        }

        // Save monitors
        LinkedList<MonitoredStatus> orderedMonitor = turnSetIntoLinkedList(monitor);
        LinkedList<MonitoredStatus> orderedDead = turnSetIntoLinkedList(dead);
        
        try {
        	saveFinalMonitors(orderedMonitor, orderedDead);
        } catch (IOException e) {
        	System.out.println("Fatal: Could not write to monitor final files.");
        	e.printStackTrace();
        	throw e;
        }
        
        // Make files for clustering and calculate diffusion graphs
        try {
        	startFilesForClustering(orderedDead, "MCL");
        } catch (InterruptedException e) {
        	System.out.println("Fatal: Sleep to refresh findFollowers interrupted.");
        	e.printStackTrace();
        	throw e;
        } catch (IOException e) {
        	System.out.println("Fatal: Could not write to cluster files.");
        	e.printStackTrace();
        	throw e;
        }
        
        try {
        	startDiffusionTreeDepths(orderedDead, DEAD_MONITOR_FINAL);
        } catch (InterruptedException e) {
        	System.out.println("Fatal: Sleep to refresh findFollowers interrupted.");
        	e.printStackTrace();
        	throw e;
        } catch (IOException e) {
        	System.out.println("Fatal: Could not access diffusion files.");
        	e.printStackTrace();
        	throw e;
        }

		/*LinkedList<String> dirs = new LinkedList<String>();
	    dirs.add("Test Sample Processed/Miercoles");
	    dirs.add("Test Sample Processed/Jueves");
	    dirs.add("Test Sample Processed/Sabado");
	    dirs.add("Test Sample Processed/Domingo");
	    makeSampleForPythonDC(dirs, "TestSample6");

		LinkedList<String> dirs2 = new LinkedList<String>();
	    dirs2.add("Complete Sample Processed/Lunes");
	    dirs2.add("Complete Sample Processed/Martes");
	    dirs2.add("Complete Sample Processed/Miercoles");
	    dirs2.add("Complete Sample Processed/Jueves");
	    dirs2.add("Complete Sample Processed/Viernes");
	    dirs2.add("Complete Sample Processed/Sabado");
	    dirs2.add("Complete Sample Processed/Domingo");
	    makeSampleForPythonDC(dirs2, "CompleteSample6");
	    */
        System.out.println("Finished.");
    }
    
    public static Twitter connectionSetup() throws TwitterException {
        userAuth = TwitterFactory.getSingleton();
        ConfigurationBuilder builder;
        builder = new ConfigurationBuilder();
        builder.setApplicationOnlyAuthEnabled(true);
        appAuth = new TwitterFactory(builder.build()).getInstance();
        appAuth.getOAuth2Token();
        return userAuth;
    }
    
    public static void updateMonitor(HashSet<MonitoredStatus> monitor, HashSet<MonitoredStatus> dead) {
        MonitoredStatus tweet;
        Status updated;
        Iterator<MonitoredStatus> iMonitor;
        List<User> retweeters;
        
        iMonitor = monitor.iterator();
        while (iMonitor.hasNext()) {
            tweet = (MonitoredStatus)iMonitor.next();
            try {
                updated = twitter.showStatus(tweet.getId());
                retweeters = getSomeRetweeters(updated, updated.getRetweetCount() - tweet.getRetweetCount().peekLast());
                for (User u : retweeters)
                    tweet.getRetweeters().add(u);
                tweet.addObservation(updated.getRetweetCount(), getRetweetLikelihood(updated, retweeters));
                if (tweet.getInactivePeriods() == PERIODS_TO_DIE) { // Inactive for an hour
                    dead.add(tweet);
                    iMonitor.remove();
                }
            } catch (TwitterException e) { // Tweet deleted
                iMonitor.remove();
            }
        }
    }
    
    public static void addSampleToMonitor(HashSet<MonitoredStatus> monitor, LinkedList<Status> newSample, 
            SentimentClassifier sentimentClassifier, TopicClassifier topicClassifier) throws TwitterException {
        long[] ids;
        int i, lookupUpperLimit;
        MonitoredStatus addedTweet;
        ResponseList<Status> updatedSample;
        List<User> retweeters;
        
        ids = new long[newSample.size()];
        i = 0;
        for (Status status: newSample) {
            if (!status.isRetweet()) { // Skip retweets
                ids[i] = status.getId();
                i++;
            }
        }
        ids = Arrays.copyOfRange(ids, 0, i); // Shortens ids to account for skipped retweets
        
        i = 0;
        while (i * MAX_LOOKUP_SIZE < ids.length) {
            if ((i + 1) * MAX_LOOKUP_SIZE > ids.length) {
                lookupUpperLimit = ids.length;
            } else {
                lookupUpperLimit = (i + 1) * MAX_LOOKUP_SIZE;
            }
            updatedSample = twitter.lookup(Arrays.copyOfRange(ids, i * MAX_LOOKUP_SIZE, lookupUpperLimit));
            for (Status status: updatedSample) {
                if (status.getLang().equals("en") && status.getRetweetCount() >= MIN_RETWEETS && getRetweetsRate < MAX_MONITORED) {
                    addedTweet = new MonitoredStatus(status.getId(), status.getText(), status.getCreatedAt(),
                            (status.getUserMentionEntities().length > 0) && status.getText().startsWith("@"), 
                            status.getUserMentionEntities().length > 0, status.getHashtagEntities().length > 0, 
                            status.getText().contains("http://") || status.getText().contains("https://"), status.getText().contains("!"), 
                            status.getText().contains("?"), containsArray(status.getText(), POSITIVE_EMOTICONS), 
                            containsArray(status.getText(), NEGATIVE_EMOTICONS), status.getUser().getFollowersCount(), 
                            sentimentClassifier.classify(status.getText()), topicClassifier.classify(status.getText()));
                    if (status.getRetweetCount() > 0) {
                        retweeters = getSomeRetweeters(status, status.getRetweetCount());
                        for (User u : retweeters)
                            addedTweet.getRetweeters().add(u);
                        addedTweet.addObservation(status.getRetweetCount(), getRetweetLikelihood(status, retweeters));
                    } else {
                        addedTweet.addObservation(0, 0);
                    }
                    monitor.add(addedTweet);
                }
            }
            i++;
        }
    }
    
    public static void addSampleToMonitorWithRetweets(HashSet<MonitoredStatus> monitor, LinkedList<Status> newSample, 
            SentimentClassifier sentimentClassifier, TopicClassifier topicClassifier) throws TwitterException {
        long[] ids;
        int i, lookupUpperLimit;
        MonitoredStatus addedTweet;
        ResponseList<Status> updatedSample;
        List<User> retweeters;
        Iterator<Status> iSample;
        LinkedList<Status> replace;
        Status tweet, originalTweet;
        
        iSample = newSample.iterator();
        replace = new LinkedList<Status>();
        while (iSample.hasNext()) {
            tweet = iSample.next();
            if (tweet.isRetweet()) {
                originalTweet = tweet.getRetweetedStatus();
                if (!checkedRetweetedTweets.contains(originalTweet.getId())) {
                    checkedRetweetedTweets.add(originalTweet.getId());
                    replace.add(originalTweet);
                }
                iSample.remove();
                } else {
                    checkedRetweetedTweets.add(tweet.getId());
            }
        }
        for (Status status: replace) {
            newSample.add(status);
        }
        
        ids = new long[newSample.size()];
        i = 0;
        for (Status status: newSample) {
            ids[i] = status.getId();
            i++;
        }
        
        i = 0;
        while (i * MAX_LOOKUP_SIZE < ids.length) {
            if ((i + 1) * MAX_LOOKUP_SIZE > ids.length) {
                lookupUpperLimit = ids.length;
            } else {
                lookupUpperLimit = (i + 1) * MAX_LOOKUP_SIZE;
            }
            updatedSample = twitter.lookup(Arrays.copyOfRange(ids, i * MAX_LOOKUP_SIZE, lookupUpperLimit));
            for (Status status: updatedSample) {
                if (status.getLang().equals("en") && status.getRetweetCount() >= MIN_RETWEETS && monitor.size() < 150) {
                    addedTweet = new MonitoredStatus(status.getId(), status.getText(), status.getCreatedAt(),
                            (status.getUserMentionEntities().length > 0) && status.getText().startsWith("@"), 
                            status.getUserMentionEntities().length > 0, status.getHashtagEntities().length > 0, 
                            status.getText().contains("http://") || status.getText().contains("https://"), status.getText().contains("!"), 
                            status.getText().contains("?"), containsArray(status.getText(), POSITIVE_EMOTICONS), 
                            containsArray(status.getText(), NEGATIVE_EMOTICONS), status.getUser().getFollowersCount(), 
                            sentimentClassifier.classify(status.getText()), topicClassifier.classify(status.getText()));
                    if (status.getRetweetCount() > 0) {
                        retweeters = getSomeRetweeters(status, status.getRetweetCount());
                        for (User u : retweeters)
                            addedTweet.getRetweeters().add(u);
                        addedTweet.addObservation(status.getRetweetCount(), getRetweetLikelihood(status, retweeters));
                    } else {
                        addedTweet.addObservation(0, 0);
                    }
                    monitor.add(addedTweet);
                }
            }
            i++;
        }
    }
    
    public static final void printMonitors(HashSet<MonitoredStatus> monitor, HashSet<MonitoredStatus> dead) {
        int tweetNumber = 0;
        System.out.println("Printing monitors.");
        for (MonitoredStatus status: monitor) {
            System.out.println(tweetNumber);
            status.print();
            System.out.println();
            tweetNumber++;
        }
        if (dead.size() > 0) {
	        System.out.println("===============DEAD===============");
	        for (MonitoredStatus status: dead) {
	            System.out.println(tweetNumber);
	            status.print();
	            System.out.println();
	            tweetNumber++;
	        }
        }
        System.out.println("Finished printing monitors.");
    }
    
    public static HashSet<MonitoredStatus> readMonitorProgressFile(File file) throws ClassNotFoundException, IOException {
        ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
        @SuppressWarnings("unchecked") // The file should contain a HashSet<MonitoredStatus>.
        HashSet<MonitoredStatus> monitor = (HashSet<MonitoredStatus>)input.readObject();
        input.close();
        return monitor;
    }

    /* Prints sample.
     * Row 0: 0:RetweetCount, 1:Clusters, 2:TreeLength, 3:AuthorsFollowers, 4:isDirect, 5:isMention, 6:isExclamation, 7:isHashtag, 8:isNegativeE, 9:isPositiveE,
     * 10:isQuestion, 11:isUrl, 12:Sentiment, 13:Topic, 14:TotalViews, 15:FollowerAverage, 16:MaxFollowers, 17:RowSize
     * Row 1: Retweet number history
     * Row 2: Retweet number delta
     * Row 3: Retweet likelihood history
     * Row 4: Retweet likelihood delta
     * Row 5: TotalViews history
     * Row 6: TotalViews delta
     * Row 7: FollowerAverage history
     * Row 8: FollowerAverage delta
     */
    public static void makeSampleForPythonDC(List<String> dirs, String sampleName) {
        ObjectInputStream input, input2;
        int i = 0, size = 0, clusters;
        LinkedList<double[]> res;
        double[] followerStats;
        try {
            LinkedList<MonitoredStatus> s, s2;
            PrintWriter output = new PrintWriter(new BufferedWriter(new FileWriter(sampleName + ".txt")));
            for (String dir : dirs) {
                input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(dir + "/Final/MonitorTestC.ser")));
                input2 = new ObjectInputStream(new BufferedInputStream(new FileInputStream(dir + "/Final/MonitorTestDif.ser")));
                s = (LinkedList<MonitoredStatus>)input.readObject();
                s2 = (LinkedList<MonitoredStatus>)input2.readObject();
                size = s.size();
                for (MonitoredStatus m : s) {
                    if (m.getRetweetCount().getLast() > 0) {
                        clusters = readClusters(dir + "/MCL/out." + i + ".mci.I20");
                    } else {
                        clusters = 0;
                    }
                    res = followerStats(m.getRetweeters(), m.getRetweetCount(), m.getRetweetLikelihood(), m.getFollowerNumber());
                    followerStats = res.remove();
                    output.println(m.getRetweetCount().peekLast() + " " + clusters + " "  + s2.get(i).getTreeDepth() + " " + m.getFollowerNumber() + " " + 
                            m.isDirect() + " " + m.hasMention() + " " + m.isExclamation() + " " + m.hasHashtag() + " " + m.hasNegativeEmoticon() + " " + m.hasPositiveEmoticon() + 
                            " " + m.isQuestion() + " " + m.hasURL() + " " + m.getSentiment() + " " + m.getTopic() + " " + followerStats[0] + " " + followerStats[1] + 
                            " " + followerStats[2] + " " + m.getRetweetCount().size());
                    for (int j = 0; j < 8; j++) {
                        followerStats = res.remove();
                        for (double d: followerStats) {
                            output.print(d + " ");
                        }
                        output.println();
                    }
                    i++;
                }
                s = (LinkedList<MonitoredStatus>)input.readObject();
                s2 = (LinkedList<MonitoredStatus>)input2.readObject();
                for (MonitoredStatus m : s) {
                    if (m.getRetweetCount().getLast() > 0) {
                        clusters = readClusters(dir + "/MCLD/out." + i + ".mci.I20");
                    } else {
                        clusters = 0;
                    }
                    res = followerStats(m.getRetweeters(), m.getRetweetCount(), m.getRetweetLikelihood(), m.getFollowerNumber());
                    followerStats = res.remove();
                    output.println(m.getRetweetCount().peekLast() + " " + clusters + " "  + s2.get(i - size).getTreeDepth() + " " + m.getFollowerNumber() + " " + 
                            m.isDirect() + " " + m.hasMention() + " " +  m.isExclamation() + " " + m.hasHashtag() + " " + m.hasNegativeEmoticon() + " " + m.hasPositiveEmoticon() + 
                            " " + m.isQuestion() + " " + m.hasURL() + " " + m.getSentiment() + " " + m.getTopic() + " " + followerStats[0] + " " + followerStats[1] + 
                            " " + followerStats[2] + " " + m.getRetweetCount().size());
                    for (int j = 0; j < 8; j++) {
                        followerStats = res.remove();
                        for (double d: followerStats) {
                            output.print(d + " ");
                        }
                        output.println();
                    }
                    i++;
                }
                input.close();
                s = null;
                System.gc();
                i = 0;
            }
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static LinkedList<double[]> followerStats(HashSet<User> users, LinkedList<Integer> retweets, LinkedList<Double> likelihood, int authorFollowers) {
        LinkedList<double[]> result = new LinkedList<double[]>();
        double finalStats[] = new double[3], rtH[] = new double[retweets.size()], rtD[] = new double[retweets.size()], liH[] = new double[retweets.size()],
                liD[] = new double[retweets.size()], viewsH[] = new double[retweets.size()], viewsD[] = new double[retweets.size()], prevLi = 0, prevAvg = 0,
                avgH[] = new double[retweets.size()], avgD[] = new double[retweets.size()];
        int index = 0, prevRt = 0, prevViews = 0;
        Iterator<Integer> itRe = retweets.iterator();
        Iterator<Double> itLi = likelihood.iterator();
        while (itRe.hasNext()) {
            rtH[index] = itRe.next();
            rtD[index] = rtH[index] - prevRt;
            prevRt = (int)rtH[index];
            liH[index] = itLi.next();
            if (Double.isInfinite(liH[index]))
                liH[index] = 0;
            liD[index] = liH[index] - prevLi;
            prevLi = liH[index];
            if (liH[index] > 0) 
                viewsD[index] = (int)Math.floor(rtH[index] / liH[index]);
            else
                viewsD[index] = 0;
            finalStats[0] += viewsD[index];
            viewsH[index] = prevViews + viewsD[index];
            prevViews = (int)viewsH[index];
            if (rtH[index] == 0)
                avgH[index] = 0;
            else
                avgH[index] = viewsH[index] / rtH[index];
            avgD[index] = avgH[index] - prevAvg;
            prevAvg = avgH[index];
            index++;
        }
        finalStats[0] = authorFollowers;
        finalStats[2] = authorFollowers;
        for (User u: users) {
            finalStats[0] += u.getFollowersCount();
            if (finalStats[2] < u.getFollowersCount())
                finalStats[2] = u.getFollowersCount();
        }
        finalStats[1] = finalStats[0] / (users.size() + 1);
        result.add(finalStats);
        result.add(rtH);
        result.add(rtD);
        result.add(liH);
        result.add(liD);
        result.add(viewsH);
        result.add(viewsD);
        result.add(avgH);
        result.add(avgD);
        return result;
    }

    public static int readClusters(String path) {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            input.readLine();
            input.readLine();
            input.readLine();
            String dim = input.readLine();
            input.close();
            return Integer.parseInt(dim.substring(dim.indexOf('x') + 1));
        } catch (FileNotFoundException e) {
            return -1;
        } catch (IOException e) {
            System.out.println("Fatal error");
            return -2;
        }
    }

    public static void makeFilesForClustering(LinkedList<MonitoredStatus> monitor, String dir, int startNumber) throws InterruptedException, IOException {	
        Status updatedTweet;
        User updatedUser;
        int tweetNumber = startNumber;
        DirectedSparseGraph<Long, Pair<Long>> graph;
        MonitoredStatus tweet;
        Iterator<MonitoredStatus> iMonitor = monitor.iterator();
        while (iMonitor.hasNext()) {
            try {
                graph = null;
                tweet = iMonitor.next();
                if (tweet.getRetweetCount().peekLast() != 0) {
                    updatedTweet = twitter.showStatus(tweet.getId());
                    updatedUser = updatedTweet.getUser();
                    graph = getRetweeterFollowerGraph(updatedUser, tweet.getRetweeters());
                }
                printGraphForMCL(graph, dir + "\\" + tweetNumber + ".abc");
            } catch (TwitterException e) {
                e.printStackTrace();
                System.out.println("Tweet deleted, skipping.");
            } finally {
                tweetNumber++;
                iMonitor.remove();
                saveObjectToFile(monitor, CLUSTERING_PROGRESS);
                PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(CLUSTERING_PROGRESS_NUMBER)));
                writer.println(tweetNumber);
                writer.close();
            }
        }
    }
    
    public static void startFilesForClustering(LinkedList<MonitoredStatus> monitor, String dir) throws InterruptedException, IOException {
        makeFilesForClustering(monitor, dir, 0);
    }
    
    public static void continueFilesForClustering(String dir) throws InterruptedException, IOException, ClassNotFoundException {
        ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(CLUSTERING_PROGRESS)));
        @SuppressWarnings("unchecked") // There should be a linked list in the file.
		LinkedList<MonitoredStatus> monitor = (LinkedList<MonitoredStatus>)input.readObject();
        input.close();
        Scanner scanner = new Scanner(CLUSTERING_PROGRESS_NUMBER);
        int tweetNumber = scanner.nextInt();
        scanner.close();
        makeFilesForClustering(monitor, dir, tweetNumber);
    }

    public static void setDiffusionTreeDepths(LinkedList<MonitoredStatus> monitor, File output, int startNumber) throws InterruptedException, IOException {
        int tweetNumber = startNumber;
        Iterator<MonitoredStatus> iMonitor = monitor.iterator();
        MonitoredStatus tweet;
        while (iMonitor.hasNext()) {
            tweet = iMonitor.next();
            System.out.println(tweetNumber);
            System.out.println(tweet.getRetweetCount().peekLast());
            if (tweet.getRetweetCount().peekLast() != 0) {
            	try {
            		makeDiffusionGraph(tweet);
            	} catch (TwitterException e) {
            		System.out.println("Method: setDiffusionTreeDepths. Tweet no longer exists. Setting tree depth to 0.");
            		tweet.setTreeDepth(0);
            	}
            } else {
                tweet.setTreeDepth(1);
            }
            tweetNumber++;
            saveObjectToFile(monitor, DIFFUSION_PROGRESS);
            PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(DIFFUSION_PROGRESS_NUMBER)));
            writer.println(tweetNumber);
            writer.close();
        }
        saveObjectToFile(monitor, output);     
    }
    
    public static void startDiffusionTreeDepths(LinkedList<MonitoredStatus> monitor, File output) throws InterruptedException, IOException {
        setDiffusionTreeDepths(monitor, output, 0);
    }
    
    public static void continueDiffusionTreeDepths(File output) throws InterruptedException, IOException, ClassNotFoundException {
        ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(DIFFUSION_PROGRESS)));
        @SuppressWarnings("unchecked") // There should be a linked list in the file.
		LinkedList<MonitoredStatus> monitor = (LinkedList<MonitoredStatus>)input.readObject();
        input.close();
        Scanner scanner = new Scanner(DIFFUSION_PROGRESS_NUMBER);
        int tweetNumber = scanner.nextInt();
        scanner.close();
        setDiffusionTreeDepths(monitor, output, tweetNumber);
    }

    public static boolean containsArray(String s, String[] a) {
        for (int i = 0; i < a.length; i++)
            if (s.contains(a[i]))
                return true;
        return false;
    }

    public static void saveMonitorProgress(HashSet<MonitoredStatus> monitor, HashSet<MonitoredStatus> dead) throws IOException {
        System.out.println("Saving monitor progress.");
        saveObjectToFile(monitor, MONITOR_PROGRESS);
        saveObjectToFile(dead, DEAD_MONITOR_PROGRESS);
        printMonitorToFile(monitor, PRINTED_MONITOR_PROGRESS);
        printMonitorToFile(dead, PRINTED_DEAD_MONITOR_PROGRESS);
        System.out.println("Progress has been saved.");
    }

    public static void saveFinalMonitors(LinkedList<MonitoredStatus> monitor, LinkedList<MonitoredStatus> dead) throws IOException {
        System.out.println("Saving final monitors.");
        saveObjectToFile(monitor, MONITOR_FINAL);
        saveObjectToFile(dead, DEAD_MONITOR_FINAL);
        printMonitorToFile(monitor, PRINTED_MONITOR_FINAL);
        printMonitorToFile(dead, PRINTED_DEAD_MONITOR_FINAL);
        System.out.println("Final monitors have been saved.");
    }
    
    public static <T> LinkedList<T> turnSetIntoLinkedList(Set<T> set) {
        LinkedList<T> list = new LinkedList<T>();
        for (T element : set)
            list.add(element);
        return list;
    }
    
    public static void saveObjectToFile(Serializable obj, File file) throws IOException {
        ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        output.writeObject(obj);
        output.close();
    }
    
    public static void printMonitorToFile(Iterable<MonitoredStatus> monitor, File file) throws IOException {
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
        writer.println(new Date());
        writer.close();
        for (MonitoredStatus tweet : monitor)
            tweet.printToFile(file);
    }

    public static LinkedList<Status> streamTweets(int count) throws InterruptedException {
        TwitterStream stream;
        Listener listener;
        secondsStreamed = 0;
        System.out.println("Started streaming.");
        listener = new Listener(count);
        stream = TwitterStreamFactory.getSingleton();
        stream.addListener(listener);
        stream.sample();
        while (!listener.limitHit()) {
        	Thread.sleep(ONE_SECOND);
        	secondsStreamed++;
        }
        stream.cleanUp();
        stream.shutdown();
        Thread.sleep(TWO_SECONDS);
        System.out.println("Finished streaming.");
        return listener.getTweets();
    }

    public static void setUserAuth(boolean auth) {
        if (auth)
            twitter = userAuth;
        else
            twitter = appAuth;
    }

    public static DirectedSparseGraph<Long, Pair<Long>> makeDiffusionGraph(MonitoredStatus tweet) throws InterruptedException, TwitterException {
        int i = 0, treeSize = 0;
        long[] retweeters = new long[tweet.getRetweeters().size()];
        for (User user : tweet.getRetweeters()) {
            retweeters[i] = user.getId();
            i++;
        }
        Arrays.sort(retweeters);
        DirectedSparseGraph<Long, Pair<Long>> graph = new DirectedSparseGraph<Long, Pair<Long>>();
        Status updatedTweet;
        updatedTweet = twitter.showStatus(tweet.getId());
        long currentUser = updatedTweet.getUser().getId();
        graph.addVertex(currentUser);
        LinkedList<Long> queue = new LinkedList<Long>();
        queue.add(currentUser);
        LinkedList<Long> followerList;
        ArrayList<Long> checked = new ArrayList<Long>();
        Long[] followers;
        treeSize = 1;
        int currentGen = 1, nextGen = 0;
        while (!queue.isEmpty()) {
            currentUser = queue.pop();
            currentGen--; // Reduce the amount of tweets in the current tree level
            if (currentGen < 0) { // If we've moved into the next tree level
                treeSize++;
                currentGen = nextGen; // Change tree level size counters
                nextGen = 0;
            }
            checked.add(currentUser);
            followerList = findFollowers(currentUser);
            followers = followerList.toArray(new Long[followerList.size()]);
            Arrays.sort(followers);
            for (long retweeter : retweeters) {
            	// If the retweeter is the current user's follower
                if (!checked.contains(retweeter) && Arrays.binarySearch(followers, retweeter) >= 0) {
                	// Add retweeter to the next tree level
                    graph.addVertex(retweeter);
                    graph.addEdge(new Pair<Long>(currentUser, retweeter), currentUser, retweeter);
                    queue.add(retweeter);
                    nextGen++; // Increase the amount of tweets in the next tree level
                }
            }
        }
        tweet.setTreeDepth(treeSize);
        return graph;
    }

    public static void drawGraphForGraphViz(DirectedSparseGraph<Long, Pair<Long>> g, File file) throws FileNotFoundException {
        Collection<Pair<Long>> s = g.getEdges();
        PrintWriter p = new PrintWriter(file);
        p.println("digraph g {");
        p.println("graph [splines = spline];");
        for (Pair<Long> e : s) {
            p.println("\"" + e.getFirst() + "\" -> \"" + e.getSecond() + "\";");
        }
        p.println("}");
        p.close();
    }

    /*public static long[] getRetweeters(Status status) {
		try {
			List<Status> statuses = twitter.getRetweets(status.getId());
			long[] ids = new long[statuses.size()];
			int i = 0;
			for (Status tweet: statuses) {
				ids[i] = tweet.getUser().getId();
				i++;
			}
			return ids;
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Error");
		return null;
	}*/

    public static List<User> getSomeRetweeters(Status status, int retweeterCount) {
        Twitter auth;
        boolean limit;
        if (getRetweetsRate <= GET_RETWEETS_USER_LIMIT) {
            auth = userAuth;
            limit = false;
        } else if (getRetweetsRate <= GET_RETWEETS_USER_LIMIT + GET_RETWEETS_APP_LIMIT) {
            auth = appAuth;
            limit = false;
        } else if (getRetweetsRate <= GET_RETWEETS_USER_LIMIT + GET_RETWEETS_APP_LIMIT + GET_RETWEETERS_USER_LIMIT) {
            auth = userAuth;
            limit = true;
        } else {
            auth = appAuth;
            limit = true;
        }
        if (retweeterCount < 0)
            retweeterCount = 0;
        if (!limit) {
            try {
                List<Status> statuses = auth.getRetweets(status.getId());
                getRetweetsRate++;
                LinkedList<User> retweeters = new LinkedList<User>();
                Status tweet;
                if (statuses.size() < retweeterCount)
                    retweeterCount = statuses.size();
                for (int i = 0; i < retweeterCount; i++) {
                    tweet = statuses.remove(0);
                    retweeters.add(tweet.getUser());
                }
                return retweeters;
            } catch (TwitterException e) {
                System.out.println("Method: getSomeRetweeters. No retweeters for this user.");
                return new LinkedList<User>();
            }
        } else {
            try {
                long[] statuses = auth.getRetweeterIds(status.getId(), -1l).getIDs();
                getRetweetsRate++;
                ResponseList<User> retweeters = userAuth.lookupUsers(statuses);
                if (retweeters.size() < retweeterCount)
                    retweeterCount = retweeters.size();
                List<User> retweetersList = retweeters.subList(0, retweeterCount);
                return retweetersList;
            } catch (TwitterException e) {
                System.out.println("Method: getSomeRetweeters. No retweeters for this user.");
                return new LinkedList<User>();
            }
        }
    }

    //sUntil format: YYYY-MM-DD
    public static List<Status> searchTweetsUntil(String sQuery, String sUntil) {
        Query query = new Query(sQuery);
        query.setUntil(sUntil);
        QueryResult result;
        try {
            result = twitter.search(query);
            for (Status status : result.getTweets()) {
                System.out.println("@" + status.getUser().getScreenName() + ":" + status.getText() + " /// Date: " + status.getCreatedAt());
            }
            return result.getTweets();
        } catch (TwitterException e) {
            e.printStackTrace();
        }
        System.out.println("Error");
        return null;
    }

    public static void reconnect() throws InterruptedException, TwitterException {
        System.setProperty("twitter4j.loggerFactory", "twitter4j.NullLoggerFactory");
        findFollowersRate = 0;
        getRetweetsRate = 0;
        connectionSetup();
        System.out.println("Sleeping because of reconnection.");
        System.out.println(new Date());
        Thread.sleep(FIFTEEN_MINUTES);
    }

    public static LinkedList<Long> findFollowers(long userID) throws InterruptedException, TwitterException {
        long cursor = -1;
        IDs followerIDs;
        LinkedList<Long> followerIDList = new LinkedList<Long>();
        int localFindFollowersRate = 0;
        followerIDs = null;
        do {
            if (findFollowersRate == GET_FOLLOWERS_USER_LIMIT) {
                setUserAuth(false);
            }
            if (findFollowersRate == GET_FOLLOWERS_USER_LIMIT + GET_FOLLOWERS_APP_LIMIT) {
                System.out.println("Sleeping to refresh rate for findFollowers");
                System.out.println(new Date());
                Thread.sleep(FIFTEEN_MINUTES);
                findFollowersRate = 0;
                setUserAuth(true);
            }
            try {
                findFollowersRate++;
                localFindFollowersRate++;
                followerIDs = twitter.getFollowersIDs(userID, cursor);
                for (Long one : followerIDs.getIDs()) {
                    followerIDList.add(one);
                }
                cursor = followerIDs.getNextCursor();
            } catch (TwitterException e) {
                e.printStackTrace();
                if (e.getStatusCode() != -1) {
                    followerIDs = null;
                    System.out.println("Method: findFollowers. Protected user, skipping.");
                    return followerIDList; 
                } else {
                    System.out.println("Connection error. Reconnecting.");
                    reconnect();
                    localFindFollowersRate--;
                }
            }
        } while ((cursor != 0) && (localFindFollowersRate < 30));
        return followerIDList;
    }

    //sUntil format: YYYY-MM-DD
    public static List<Status> getTimelineSince(String user, Date dSince) {
        try {
            List<Status> statuses, statuseses;
            statuseses = new LinkedList<Status>();
            Paging paging = new Paging(1, 100);
            statuses = twitter.getUserTimeline(user, paging);
            int page = 1;
            boolean flag = true;
            while (flag) {
                statuseses.addAll(statuses);
                if (((Status)statuseses.get(statuseses.size() - 1)).getCreatedAt().before(dSince)) {
                    flag = false;
                } else {
                    page++;
                    paging = new Paging(page, 100);
                    statuses = twitter.getUserTimeline(user, paging);
                }
            }
            return statuseses;
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get timeline: " + te.getMessage());
            return null;
        }
    }

    public static List<Status> getEntireTimeline(String user) {
        try {
            List<Status> statuses, statuseses;
            statuseses = new LinkedList<Status>();
            Paging paging = new Paging(1, 100);
            statuses = twitter.getUserTimeline(user, paging);
            int page = 1;
            while (!statuses.isEmpty()) {
                statuseses.addAll(statuses);
                page++;
                paging = new Paging(page, 100);
                statuses = twitter.getUserTimeline(user, paging);
            }
            for (Status status : statuseses) {
                if (status.isRetweet()) {
                    status = status.getRetweetedStatus();
                }
            }
            return statuseses;
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get timeline: " + te.getMessage());
            return null;
        }
    }

    public static List<Status> getFirstPageTimeline(String user) {
        try {
            List<Status> statuses;
            Paging paging = new Paging(1, 100);
            statuses = twitter.getUserTimeline(user, paging);
            return statuses;
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get timeline: " + te.getMessage());
            return null;
        }
    }

    public static long getIdFromUsername(String username) {
        try {
            return twitter.showUser(username).getId();
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get user: " + te.getMessage());
            return 0;
        }
    }

    public static String getUsernameFromId(long id) {
        try {
            return twitter.showUser(id).getScreenName();
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get user: " + te.getMessage());
            return null;
        }
    }

    public static double getRetweetLikelihood(Status status, List<User> retweeters) {
        if (!retweeters.isEmpty()) {
            try {
                int totalFollowers;
                if (status.getRetweetCount() == retweeters.size())
                    totalFollowers = status.getUser().getFollowersCount();
                else
                    totalFollowers = 0;
                for (User rter : retweeters) {
                    totalFollowers += rter.getFollowersCount();
                }
                return retweeters.size() / (double) totalFollowers;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return -1;
        } else {
            return 0;
        }
    }

    public static DirectedSparseGraph<Long, Pair<Long>> getRetweeterFollowerGraph(User author, HashSet<User> retweeters) throws InterruptedException, TwitterException {
        DirectedSparseGraph<Long, Pair<Long>> graph = new DirectedSparseGraph<Long, Pair<Long>>();
        LinkedList<Long> followerList = new LinkedList<Long>();
        User current = author;
        graph.addVertex(current.getId());
        followerList = findFollowers(current.getId());
        for (long authorFollower : followerList) {
            graph.addVertex(authorFollower);
            graph.addEdge(new Pair<Long>(current.getId(), authorFollower), current.getId(), authorFollower);
        }
        for (User retweeter : retweeters) {
            System.out.println("Method: getRtFollowerGraph. Retweeters size: " + retweeters.size());
            System.out.println("Method: getRtFollowerGraph. Retweeter follow count: " + retweeter.getFollowersCount());
            graph.addVertex(retweeter.getId());
            followerList = findFollowers(retweeter.getId());
            for (long follower : followerList) {
                graph.addVertex(follower);
                graph.addEdge(new Pair<Long>(retweeter.getId(), follower), retweeter.getId(), follower);
            }
        }
        return graph;
    }

    public static void printGraphForMCL(DirectedSparseGraph<Long, Pair<Long>> graph, String filepath) throws FileNotFoundException {
        PrintWriter p = new PrintWriter(filepath);
        if (graph == null)
            p.println("empty");
        else {
            for (Pair<Long> ed : graph.getEdges()) {
                p.println(ed.getFirst() + " " + ed.getSecond());
            }
        }
        p.close();
    }
}
