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

    private static final int MAX_STREAMED = 18000; // 18000 max due to status lookup rate limit
    private static final int MAX_CYCLES = 6;
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
    private static final int MILLISECONDS_IN_A_SECOND = 1000;
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
    private static final File CLASSIFIER_FILE = new File("SampleForClassifier.txt");
    private static final String CLUSTER_DIR = "MCL";
    
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
        long toSleep;
        
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
                	toSleep = FIFTEEN_MINUTES - (secondsStreamed * MILLISECONDS_IN_A_SECOND);
                	if (toSleep > 0) {
                		System.out.println("Sleeping " + toSleep + " ms to refresh rate limit.");
                    	System.out.println(new Date());
                		Thread.sleep(toSleep);
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
        	System.out.println("Starting cluster files.");
        	startFilesForClustering(orderedDead, CLUSTER_DIR);
        	System.out.println("Cluster files finished.");
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
        	System.out.println("Starting diffusion depths.");
        	startDiffusionTreeDepths(orderedDead);
        	System.out.println("Diffusion depths finished.");
        	saveObjectToFile(orderedDead, DEAD_MONITOR_FINAL);
        	printMonitorToFile(orderedDead, PRINTED_DEAD_MONITOR_FINAL);
        } catch (InterruptedException e) {
        	System.out.println("Fatal: Sleep to refresh findFollowers interrupted.");
        	e.printStackTrace();
        	throw e;
        } catch (IOException e) {
        	System.out.println("Fatal: Could not access diffusion files.");
        	e.printStackTrace();
        	throw e;
        }

        try {
        	System.out.println("Making sample for classifiers.");
        	makeSampleForClassifiers(orderedDead, CLUSTER_DIR);
        	System.out.println("Sample for classifiers finished.");
        } catch (ClassNotFoundException e) {
        	System.out.println("Fatal: Monitor file to convert for classifiers did not contain a valid monitor.");
        	e.printStackTrace();
        	throw e;
        } catch (FileNotFoundException e) {
        	System.out.println("Fatal: Could not find monitor file to convert for classifiers.");
        	e.printStackTrace();
        	throw e;
        } catch (IOException e) {
        	System.out.println("Fatal: Could not access monitor file to convert for classifiers.");
        	e.printStackTrace();
        	throw e;
        }
        
        System.out.println("Finished.");
    }
    
    private static Twitter connectionSetup() throws TwitterException {
        userAuth = TwitterFactory.getSingleton();
        ConfigurationBuilder builder;
        builder = new ConfigurationBuilder();
        builder.setApplicationOnlyAuthEnabled(true);
        appAuth = new TwitterFactory(builder.build()).getInstance();
        appAuth.getOAuth2Token();
        return userAuth;
    }
    
    private static void updateMonitor(HashSet<MonitoredStatus> monitor, HashSet<MonitoredStatus> dead) {
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
    
    private static void addSampleToMonitor(HashSet<MonitoredStatus> monitor, LinkedList<Status> newSample, 
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
    
    @SuppressWarnings("unused")
    private static void addSampleToMonitorWithRetweets(HashSet<MonitoredStatus> monitor, LinkedList<Status> newSample, 
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
    
    private static final void printMonitors(HashSet<MonitoredStatus> monitor, HashSet<MonitoredStatus> dead) {
        int tweetNumber = 1;
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
    
    @SuppressWarnings("unused")
    private static HashSet<MonitoredStatus> readMonitorProgressFile(File file) throws ClassNotFoundException, IOException {
        ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
        @SuppressWarnings("unchecked") // The file should contain a HashSet<MonitoredStatus>.
        HashSet<MonitoredStatus> monitor = (HashSet<MonitoredStatus>)input.readObject();
        input.close();
        return monitor;
    }
    
    @SuppressWarnings("unused")
    private static LinkedList<MonitoredStatus> readMonitorFinalFile(File file) throws ClassNotFoundException, IOException {
        ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
        @SuppressWarnings("unchecked") // The file should contain a HashSet<MonitoredStatus>.
        LinkedList<MonitoredStatus> monitor = (LinkedList<MonitoredStatus>)input.readObject();
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
    private static void makeSampleForClassifiers(LinkedList<MonitoredStatus> monitor, String clusterDirPath) throws ClassNotFoundException, FileNotFoundException, IOException {
        LinkedList<double[]> statList;
        double[] followerStats;
        int clusters, i = 0;
        PrintWriter output = new PrintWriter(new BufferedWriter(new FileWriter(CLASSIFIER_FILE)));
        for (MonitoredStatus tweet : monitor) {
            if (tweet.getRetweetCount().getLast() > 0) {
                clusters = readClusters(clusterDirPath + "/out." + i + ".mci.I20");
            } else {
                clusters = 0;
            }
            statList = followerStats(tweet.getRetweeters(), tweet.getRetweetCount(), tweet.getRetweetLikelihood(), tweet.getFollowerNumber());
            followerStats = statList.remove();
            output.println(tweet.getRetweetCount().peekLast() + " " + clusters + " "  + tweet.getTreeDepth() + " " + tweet.getFollowerNumber() + " " + 
                    tweet.isDirect() + " " + tweet.hasMention() + " " + tweet.isExclamation() + " " + tweet.hasHashtag() + " " + tweet.hasNegativeEmoticon() + " " + tweet.hasPositiveEmoticon() + 
                    " " + tweet.isQuestion() + " " + tweet.hasURL() + " " + tweet.getSentiment() + " " + tweet.getTopic() + " " + followerStats[0] + " " + followerStats[1] + 
                    " " + followerStats[2] + " " + tweet.getRetweetCount().size());
            for (int j = 0; j < 8; j++) {
                followerStats = statList.remove();
                for (double d: followerStats) {
                    output.print(d + " ");
                }
                output.println();
            }
            i++;
        }
        output.close();
    }

    private static LinkedList<double[]> followerStats(HashSet<User> users, LinkedList<Integer> retweets, LinkedList<Double> likelihood, int authorFollowers) {
        LinkedList<double[]> result = new LinkedList<double[]>();
        double finalStats[] = new double[3], retweetHistory[] = new double[retweets.size()], retweetDelta[] = new double[retweets.size()], 
        		likelihoodHistory[] = new double[retweets.size()], likelihoodDelta[] = new double[retweets.size()], viewsHistory[] = new double[retweets.size()], 
        		viewsDelta[] = new double[retweets.size()], prevLikelihood = 0, prevAvg = 0, avgHistory[] = new double[retweets.size()], 
        		avgDelta[] = new double[retweets.size()];
        int index = 0, prevRetweets = 0, prevViews = 0;
        Iterator<Integer> iRetweets = retweets.iterator();
        Iterator<Double> iLikelihood = likelihood.iterator();
        while (iRetweets.hasNext()) {
            retweetHistory[index] = iRetweets.next();
            retweetDelta[index] = retweetHistory[index] - prevRetweets;
            prevRetweets = (int)retweetHistory[index];
            likelihoodHistory[index] = iLikelihood.next();
            if (Double.isInfinite(likelihoodHistory[index]))
                likelihoodHistory[index] = 0;
            likelihoodDelta[index] = likelihoodHistory[index] - prevLikelihood;
            prevLikelihood = likelihoodHistory[index];
            if (likelihoodHistory[index] > 0) 
                viewsDelta[index] = (int)Math.floor(retweetHistory[index] / likelihoodHistory[index]);
            else
                viewsDelta[index] = 0;
            finalStats[0] += viewsDelta[index];
            viewsHistory[index] = prevViews + viewsDelta[index];
            prevViews = (int)viewsHistory[index];
            if (retweetHistory[index] == 0)
                avgHistory[index] = 0;
            else
                avgHistory[index] = viewsHistory[index] / retweetHistory[index];
            avgDelta[index] = avgHistory[index] - prevAvg;
            prevAvg = avgHistory[index];
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
        result.add(retweetHistory);
        result.add(retweetDelta);
        result.add(likelihoodHistory);
        result.add(likelihoodDelta);
        result.add(viewsHistory);
        result.add(viewsDelta);
        result.add(avgHistory);
        result.add(avgDelta);
        return result;
    }

    private static int readClusters(String path) {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            input.readLine();
            input.readLine();
            input.readLine();
            String dim = input.readLine();
            input.close();
            return Integer.parseInt(dim.substring(dim.indexOf('x') + 1));
        } catch (FileNotFoundException e) {
            return 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private static void makeFilesForClustering(LinkedList<MonitoredStatus> originalMonitor, String dir, int startNumber) throws InterruptedException, IOException {	
        Status updatedTweet;
        User updatedUser;
        int tweetNumber = startNumber;
        DirectedSparseGraph<Long, Pair<Long>> graph;
        MonitoredStatus tweet;
        @SuppressWarnings("unchecked") // We know originalMonitor is a LinkedList<MonitoredStatus>
		LinkedList<MonitoredStatus> monitor = (LinkedList<MonitoredStatus>)originalMonitor.clone();
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
    
    private static void startFilesForClustering(LinkedList<MonitoredStatus> monitor, String dir) throws InterruptedException, IOException {
        makeFilesForClustering(monitor, dir, 0);
    }
    
    @SuppressWarnings("unused")
    private static void continueFilesForClustering(String dir) throws InterruptedException, IOException, ClassNotFoundException {
        ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(CLUSTERING_PROGRESS)));
        @SuppressWarnings("unchecked") // There should be a linked list in the file.
		LinkedList<MonitoredStatus> monitor = (LinkedList<MonitoredStatus>)input.readObject();
        input.close();
        Scanner scanner = new Scanner(CLUSTERING_PROGRESS_NUMBER);
        int tweetNumber = scanner.nextInt();
        scanner.close();
        makeFilesForClustering(monitor, dir, tweetNumber);
    }

    private static void setDiffusionTreeDepths(LinkedList<MonitoredStatus> monitor, int startNumber) throws InterruptedException, IOException {
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
    }
    
    private static void startDiffusionTreeDepths(LinkedList<MonitoredStatus> monitor) throws InterruptedException, IOException {
        setDiffusionTreeDepths(monitor, 0);
    }
    
    @SuppressWarnings("unused")
    private static void continueDiffusionTreeDepths() throws InterruptedException, IOException, ClassNotFoundException {
        ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(DIFFUSION_PROGRESS)));
        @SuppressWarnings("unchecked") // There should be a linked list in the file.
		LinkedList<MonitoredStatus> monitor = (LinkedList<MonitoredStatus>)input.readObject();
        input.close();
        Scanner scanner = new Scanner(DIFFUSION_PROGRESS_NUMBER);
        int tweetNumber = scanner.nextInt();
        scanner.close();
        setDiffusionTreeDepths(monitor, tweetNumber);
    }

    private static boolean containsArray(String string, String[] strings) {
        for (int i = 0; i < strings.length; i++)
            if (string.contains(strings[i]))
                return true;
        return false;
    }

    private static void saveMonitorProgress(HashSet<MonitoredStatus> monitor, HashSet<MonitoredStatus> dead) throws IOException {
        System.out.println("Saving monitor progress.");
        saveObjectToFile(monitor, MONITOR_PROGRESS);
        saveObjectToFile(dead, DEAD_MONITOR_PROGRESS);
        printMonitorToFile(monitor, PRINTED_MONITOR_PROGRESS);
        printMonitorToFile(dead, PRINTED_DEAD_MONITOR_PROGRESS);
        System.out.println("Progress has been saved.");
    }

    private static void saveFinalMonitors(LinkedList<MonitoredStatus> monitor, LinkedList<MonitoredStatus> dead) throws IOException {
        System.out.println("Saving final monitors.");
        saveObjectToFile(monitor, MONITOR_FINAL);
        saveObjectToFile(dead, DEAD_MONITOR_FINAL);
        printMonitorToFile(monitor, PRINTED_MONITOR_FINAL);
        printMonitorToFile(dead, PRINTED_DEAD_MONITOR_FINAL);
        System.out.println("Final monitors have been saved.");
    }
    
    private static <T> LinkedList<T> turnSetIntoLinkedList(Set<T> set) {
        LinkedList<T> list = new LinkedList<T>();
        for (T element : set)
            list.add(element);
        return list;
    }
    
    private static void saveObjectToFile(Serializable obj, File file) throws IOException {
        ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        output.writeObject(obj);
        output.close();
    }
    
    private static void printMonitorToFile(Iterable<MonitoredStatus> monitor, File file) throws IOException {
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
        writer.println(new Date());
        writer.close();
        int i = 1;
        for (MonitoredStatus tweet : monitor) {
        	writer.println(i);
            tweet.printToFile(file);
            i++;
        }
    }

    private static LinkedList<Status> streamTweets(int count) throws InterruptedException {
        TwitterStream stream;
        Listener listener;
        secondsStreamed = 0;
        System.out.println("Started streaming.");
        listener = new Listener(count);
        stream = TwitterStreamFactory.getSingleton();
        stream.addListener(listener);
        stream.sample();
        while (!listener.limitHit()) {
        	if (listener.getError()) {
        		System.out.println("Error on stream. Restarting stream.");
        		listener = new Listener(count);
                stream = TwitterStreamFactory.getSingleton();
                stream.addListener(listener);
                stream.sample();
        	}
        	Thread.sleep(ONE_SECOND);
        	secondsStreamed++;
        }
        stream.cleanUp();
        stream.shutdown();
        Thread.sleep(TWO_SECONDS);
        System.out.println("Finished streaming.");
        return listener.getTweets();
    }

    private static void setUserAuth(boolean auth) {
        if (auth)
            twitter = userAuth;
        else
            twitter = appAuth;
    }

    private static DirectedSparseGraph<Long, Pair<Long>> makeDiffusionGraph(MonitoredStatus tweet) throws InterruptedException, TwitterException {
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
    
    private static double getRetweetLikelihood(Status status, List<User> retweeters) {
        if (!retweeters.isEmpty()) {
            int totalFollowers;
            if (status.getRetweetCount() == retweeters.size())
                totalFollowers = status.getUser().getFollowersCount();
            else
                totalFollowers = 0;
            for (User retweeter : retweeters) {
                totalFollowers += retweeter.getFollowersCount();
            }
            if (totalFollowers > 0)
            	return retweeters.size() / (double) totalFollowers;
            else
            	return 0;
        } else {
            return 0;
        }
    }

    private static DirectedSparseGraph<Long, Pair<Long>> getRetweeterFollowerGraph(User author, HashSet<User> retweeters) throws InterruptedException, TwitterException {
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
            System.out.println("Method: getRetweeterFollowerGraph. Retweeters size: " + retweeters.size());
            System.out.println("Method: getRetweeterFollowerGraph. Retweeter follow count: " + retweeter.getFollowersCount());
            graph.addVertex(retweeter.getId());
            followerList = findFollowers(retweeter.getId());
            for (long follower : followerList) {
                graph.addVertex(follower);
                graph.addEdge(new Pair<Long>(retweeter.getId(), follower), retweeter.getId(), follower);
            }
        }
        return graph;
    }

    private static void printGraphForMCL(DirectedSparseGraph<Long, Pair<Long>> graph, String filepath) throws FileNotFoundException {
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
    
    private static List<User> getSomeRetweeters(Status status, int retweeterCount) {
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
    
    private static void reconnect() throws InterruptedException, TwitterException {
        System.setProperty("twitter4j.loggerFactory", "twitter4j.NullLoggerFactory");
        findFollowersRate = 0;
        getRetweetsRate = 0;
        connectionSetup();
        System.out.println("Sleeping because of reconnection.");
        System.out.println(new Date());
        Thread.sleep(FIFTEEN_MINUTES);
    }

    private static LinkedList<Long> findFollowers(long userID) throws InterruptedException, TwitterException {
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

    @SuppressWarnings("unused")
    private static void drawGraphForGraphViz(DirectedSparseGraph<Long, Pair<Long>> g, File file) throws FileNotFoundException {
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

    @SuppressWarnings("unused")
    private static long[] getRetweeters(Status status) throws TwitterException {
		List<Status> statuses = twitter.getRetweets(status.getId());
		long[] ids = new long[statuses.size()];
		int i = 0;
		for (Status tweet: statuses) {
			ids[i] = tweet.getUser().getId();
			i++;
		}
		return ids;
	}

    //sUntil format: YYYY-MM-DD
    @SuppressWarnings("unused")
    private static List<Status> searchTweetsUntil(String sQuery, String sUntil) throws TwitterException {
        Query query = new Query(sQuery);
        query.setUntil(sUntil);
        QueryResult result;
        result = twitter.search(query);
        for (Status status : result.getTweets()) {
            System.out.println("@" + status.getUser().getScreenName() + ":" + status.getText() + " /// Date: " + status.getCreatedAt());
        }
        return result.getTweets();
    }

    //dSince format: YYYY-MM-DD
    @SuppressWarnings("unused")
    private static List<Status> getTimelineSince(String user, Date dSince) throws TwitterException {
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
    }

    @SuppressWarnings("unused")
	private static List<Status> getEntireTimeline(String user) throws TwitterException {
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
    }

    @SuppressWarnings("unused")
    private static List<Status> getFirstPageTimeline(String user) throws TwitterException {
        List<Status> statuses;
        Paging paging = new Paging(1, 100);
        statuses = twitter.getUserTimeline(user, paging);
        return statuses;
    }

    @SuppressWarnings("unused")
    private static long getIdFromUsername(String username) throws TwitterException {
        return twitter.showUser(username).getId();
    }

    @SuppressWarnings("unused")
    private static String getUsernameFromId(long id) throws TwitterException {
        return twitter.showUser(id).getScreenName();
    }
}
