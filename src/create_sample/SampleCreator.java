package create_sample;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
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
import twitter4j.auth.OAuth2Token;
import twitter4j.conf.ConfigurationBuilder;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Pair;

public class SampleCreator {

    private static final int MAX_MONITORED = 1000; // 18000 max
    private static final int MAX_CYCLES = 40;
    private static final int MAX_LOOKUP_SIZE = 100; // Size of the Twitter API Status Lookup method response
    private static final int MIN_RETWEETS = 2; // Minimum amount of retweets necessary to monitor a tweet
    private static final long FIFTEEN_MINUTES = 900000;
    private static final long ONE_SECOND = 1000;
    private static final long TWO_SECONDS = 2000;
    // Amount of 15 minute blocks without retweets before tweet is delcared dead
    private static final int PERIODS_TO_DIE = 4; 
    private static final String[] POSITIVE_EMOTICONS = {":-)", ":)", ";)", ";-)", ":D", ":-D"};
    private static final String[] NEGATIVE_EMOTICONS = {":(", ":-(", "D:", "D-:", ";_;"};
    private static final File SAVED_SENTIMENT_CLASSIFIER = new File("SentimentClassifier.txt");
    private static final File SAVED_TOPIC_CLASSIFIER = new File("TopicClassifier.txt");
    
    private static int findFollowersRate = 0, getRetweetsRate = 0;
    private static Twitter twitter, userAuth, appAuth;
    private static PriorityQueue<Long> checkedRetweetedTweets = new PriorityQueue<Long>();

    public static void main(String[] args) throws TwitterException, ClassNotFoundException, IOException, InterruptedException {
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
            newSample = streamTweets(MAX_MONITORED);

            saveSets(monitor, dead);

            // End after a number of cycles
            if (cycles == MAX_CYCLES) {
                finished = true;
            }

            // Refresh rate limit window
            if (!finished) {
                try {
                    Thread.sleep(FIFTEEN_MINUTES);
                    getRetweetsRate = 0;
                } catch (InterruptedException e) {
                    System.out.println("Fatal: Rate limit refresh sleep interrupted");
                    e.printStackTrace();
                    throw e;
                }
            }
            
            cycles++;
        }

        // Experimenting
        /*
		int i = 0;
		LinkedList<MonitoredStatus> s = new LinkedList<MonitoredStatus>(), d = new LinkedList<MonitoredStatus>(), s2, d2;
		ObjectInputStream input;
	    try {
	    	input = new ObjectInputStream(new BufferedInputStream(new FileInputStream("Sample 2/Sabado/MonitorTest.ser")));
	    	monitor = (HashSet<MonitoredStatus>)input.readObject();
	    	dead = (HashSet<MonitoredStatus>)input.readObject();
	    	System.out.println(monitor.size());
	    	System.out.println(dead.size());
	    	input.close();
	    	for (MonitoredStatus m : monitor) {
	    		s.add(m);
	    	}
	    	for (MonitoredStatus m : dead) {
	    		d.add(m);
	    	}
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }

	    s2 = (LinkedList<MonitoredStatus>)s.clone();
    	d2 = (LinkedList<MonitoredStatus>)d.clone();

    	/*System.out.println(s.size());
    	System.out.println(d.size());
    	save2(s, d, "Sample 2/Viernes/MonitorTestC.ser");
    	System.out.println("CLUSTERING!");
	    clusters(s, d);
    	//continueClustering(20);
	    System.out.println("DIFFUSING!");
	    diffusion(s2, d2);
	    save2(s2, d2, "Sample 2/Viernes/MonitorTestDif.ser");

		LinkedList<String> dirs = new LinkedList<String>();
	    dirs.add("Test Sample Processed/Miercoles");
	    dirs.add("Test Sample Processed/Jueves");
	    dirs.add("Test Sample Processed/Sabado");
	    dirs.add("Test Sample Processed/Domingo");
	    makeSampleForPythonDC(dirs, "TestSample6");
	    //*/
        /*LinkedList<String> dirs2 = new LinkedList<String>();
	    dirs2.add("Complete Sample Processed/Lunes");
	    dirs2.add("Complete Sample Processed/Martes");
	    dirs2.add("Complete Sample Processed/Miercoles");
	    dirs2.add("Complete Sample Processed/Jueves");
	    dirs2.add("Complete Sample Processed/Viernes");
	    dirs2.add("Complete Sample Processed/Sabado");
	    dirs2.add("Complete Sample Processed/Domingo");
	    makeSampleForPythonDC(dirs2, "CompleteSample6");
	    /**/
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
                retweeters = getRetweeters(updated, updated.getRetweetCount() - tweet.getRetweetCount().peekLast());
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
                if (status.getLang().equals("en") && status.getRetweetCount() >= MIN_RETWEETS && monitor.size() < 150) {
                    addedTweet = new MonitoredStatus(status.getId(), status.getText(), status.getCreatedAt(),
                            (status.getUserMentionEntities().length > 0) && status.getText().startsWith("@"), 
                            status.getUserMentionEntities().length > 0, status.getHashtagEntities().length > 0, 
                            status.getText().contains("http://") || status.getText().contains("https://"), status.getText().contains("!"), 
                            status.getText().contains("?"), containsArray(status.getText(), POSITIVE_EMOTICONS), 
                            containsArray(status.getText(), NEGATIVE_EMOTICONS), status.getUser().getFollowersCount(), 
                            sentimentClassifier.classify(status.getText()), topicClassifier.classify(status.getText()));
                    if (status.getRetweetCount() > 0) {
                        retweeters = getRetweeters(status, status.getRetweetCount());
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
                        retweeters = getRetweeters(status, status.getRetweetCount());
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
        for (MonitoredStatus status: monitor) {
            System.out.println(tweetNumber);
            status.print();
            System.out.println();
            tweetNumber++;
        }
        System.out.println("===============DEAD===============");
        for (MonitoredStatus status: dead) {
            System.out.println(tweetNumber);
            status.print();
            System.out.println();
            tweetNumber++;
        }
    }
    
    public static void readMonitors() {

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

    //Returns an array with the maximum, average and total of the followers of the user set r.
    //0: Total
    //1: Average
    //2: Max
    public static double[] followerStats2(HashSet<User> r, LinkedList<Integer> retweets, LinkedList<Double> likelihood, int authorFollowers) {
        LinkedList<double[]> result = new LinkedList<double[]>();
        double finalStats[] = new double[3];
        finalStats[0] = authorFollowers;
        finalStats[2] = authorFollowers;
        int missing = retweets.peekLast() - r.size(), currentRt, currentUs, currentLi;
        for (User u : r) {
            finalStats[0] += u.getFollowersCount();
            if (finalStats[2] < u.getFollowersCount())
                finalStats[2] = u.getFollowersCount();
        }
        finalStats[1] = finalStats[0] / (r.size() + 1);
        result.add(finalStats);
        return finalStats;
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

    public static void continueClustering(int cont) {
        ObjectInputStream input;
        LinkedList<MonitoredStatus> s, d;
        try {
            input = new ObjectInputStream(new BufferedInputStream(new FileInputStream("ClusterProgress.ser")));
            s = (LinkedList<MonitoredStatus>)input.readObject();
            d = (LinkedList<MonitoredStatus>)input.readObject();
            input.close();
            Status st;
            User u;
            int i = cont;
            DirectedSparseGraph<Long, Pair<Long>> g;
            MonitoredStatus m;
            Iterator<MonitoredStatus> it = s.iterator();
            while (it.hasNext()) {
                g = null;
                m = it.next();
                System.out.println(i);
                System.out.println(m.getRetweetCount().peekLast());
                if (m.getRetweetCount().peekLast() != 0) {
                    st = twitter.showStatus(m.getId());
                    u = st.getUser();
                    g = getRtFollowerGraph(u, m.getRetweeters());
                }
                printGraphForMCL(g, "MCL\\" + i + ".abc");
                i++;
                it.remove();
                saveLists(s, d, "ClusterProgress.ser");
            }
            System.out.println("===============DEAD===============");
            it = d.iterator();
            while (it.hasNext()) {
                g = null;
                m = it.next();
                System.out.println(i);
                System.out.println(m.getRetweetCount().peekLast());
                if (m.getRetweetCount().peekLast() != 0) {
                    st = twitter.showStatus(m.getId());
                    u = st.getUser();
                    g = getRtFollowerGraph(u, m.getRetweeters());
                }
                printGraphForMCL(g, "MCLD\\" + i + ".abc");
                i++;
                it.remove();
                saveLists(s, d, "ClusterProgress.ser");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ZC");
        }
    }

    public static void continueDiffusion(File difFile) {

    }

    public static void makeSampleForPython(List<String> dirs, String sampleName) {
        ObjectInputStream input;
        int i = 0;
        try {
            HashSet<MonitoredStatus> s = new HashSet<MonitoredStatus>();
            PrintWriter output = new PrintWriter(new BufferedWriter(new FileWriter(sampleName + ".txt"))), 
                    output2 = new PrintWriter(new BufferedWriter(new FileWriter(sampleName + "D.txt")));
            for (String dir : dirs) {
                i++;
                System.out.println(i);
                input = new ObjectInputStream(new BufferedInputStream(new FileInputStream("TweetCollect/" + dir + "/MonitorTest.ser")));
                s = (HashSet<MonitoredStatus>)input.readObject();
                System.out.println(s.size());
                for (MonitoredStatus m : s)
                    output.println(m.getRetweetCount().getLast() + " " + m.isDirect() + " " + m.hasMention() + " " + 
                            m.isExclamation() + " " + m.hasHashtag() + " " + m.hasNegativeEmoticon() + " " + m.hasPositiveEmoticon() + 
                            " " + m.isQuestion() + " " + m.hasURL() + " " + m.getSentiment() + " " + m.getTopic());
                s = (HashSet<MonitoredStatus>)input.readObject();
                System.out.println(s.size());
                for (MonitoredStatus m : s)
                    output2.println(m.getRetweetCount().getLast() + " " + m.isDirect() + " " + m.hasMention() + " " + 
                            m.isExclamation() + " " + m.hasHashtag() + " " + m.hasNegativeEmoticon() + " " + m.hasPositiveEmoticon() + 
                            " " + m.isQuestion() + " " + m.hasURL() + " " + m.getSentiment() + " " + m.getTopic());
                input.close();
                s = null;
                System.gc();
            }
            output.close();
            output2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void clusters(LinkedList<MonitoredStatus> s, LinkedList<MonitoredStatus> d) {	
        Status st;
        User u;
        int i = 0;
        DirectedSparseGraph<Long, Pair<Long>> g;
        MonitoredStatus m;
        Iterator<MonitoredStatus> it = s.iterator();
        while (it.hasNext()) {
            try {
                g = null;
                m = it.next();
                System.out.println(i);
                System.out.println(m.getRetweetCount().peekLast());
                if (m.getRetweetCount().peekLast() != 0) {
                    st = twitter.showStatus(m.getId());
                    u = st.getUser();
                    g = getRtFollowerGraph(u, m.getRetweeters());
                }
                printGraphForMCL(g, "MCL\\" + i + ".abc");
            } catch (TwitterException e) {
                e.printStackTrace();
                System.out.println("Tweet deleted, skipping");
            } finally {
                i++;
                it.remove();
                saveLists(s, d, "ClusterProgress.ser");
            }

        }
        System.out.println("===============DEAD===============");
        it = d.iterator();
        while (it.hasNext()) {
            try {
                g = null;
                m = it.next();
                System.out.println(i);
                System.out.println(m.getRetweetCount().peekLast());
                if (m.getRetweetCount().peekLast() != 0) {
                    st = twitter.showStatus(m.getId());
                    u = st.getUser();
                    g = getRtFollowerGraph(u, m.getRetweeters());
                }
                printGraphForMCL(g, "MCLD\\" + i + ".abc");
            } catch (TwitterException e) {
                e.printStackTrace();
                System.out.println("Tweet deleted, skipping");
            } finally {
                i++;
                it.remove();
                saveLists(s, d, "ClusterProgress.ser");
            }
        }
    }

    public static void diffusion(LinkedList<MonitoredStatus> s, LinkedList<MonitoredStatus> d) {
        try { 
            int i = 0;
            DirectedSparseGraph<Long, Pair<Long>> g;
            Iterator<MonitoredStatus> it = s.iterator();
            MonitoredStatus m;
            while (it.hasNext()) {
                m = it.next();
                System.out.println(i);
                System.out.println(m.getRetweetCount().peekLast());
                if (m.getRetweetCount().peekLast() != 0) {
                    g = makeDiffusionGraph(m);
                } else {
                    m.setTreeDepth(1);
                }
                i++;
                saveLists(s, d, "DiffusionProgress.ser");
            }
            System.out.println("===============DEAD===============");
            it = d.iterator();
            while (it.hasNext()) {
                m = it.next();
                System.out.println(i);
                System.out.println(m.getRetweetCount().peekLast());
                if (m.getRetweetCount().peekLast() != 0) {
                    g = makeDiffusionGraph(m);
                } else {
                    m.setTreeDepth(1);
                }
                i++;
                saveLists(s, d, "DiffusionProgress.ser");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Z2");
        }
    }

    public static boolean containsArray(String s, String[] a) {
        for (int i = 0; i < a.length; i++)
            if (s.contains(a[i]))
                return true;
        return false;
    }

    public static void saveSets(HashSet<MonitoredStatus> s, HashSet<MonitoredStatus> d) {
        try {
            System.out.println("SAVING THE GAME... PLEASE DON'T TURN OFF THE POWER");
            int i = 0;
            OutputStream fileo, buffero;
            ObjectOutputStream output;
            fileo = new FileOutputStream("MonitorTest.ser");
            buffero =  new BufferedOutputStream(fileo);
            output = new ObjectOutputStream(buffero);
            output.writeObject(s);
            output.writeObject(d);
            output.close();
            PrintWriter writer = new PrintWriter("MonitorTest.txt");
            writer.println(new Date());
            writer.close();
            for (MonitoredStatus m : s)
                m.printToFile("MonitorTest.txt");
            PrintWriter writer2 = new PrintWriter(new BufferedWriter(new FileWriter("MonitorTest.txt", true)));
            writer2.println("===========DEAD===========");
            writer2.close();
            for (MonitoredStatus m : d) 
                m.printToFile("MonitorTest.txt");
            System.out.println("THE GAME HAS BEEN SAVED");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static void saveLists(LinkedList<MonitoredStatus> s, LinkedList<MonitoredStatus> d, String file) {
        try {
            System.out.println("SAVING THE GAME... PLEASE DON'T TURN OFF THE POWER");
            int i = 0;
            OutputStream fileo, buffero;
            ObjectOutputStream output;
            fileo = new FileOutputStream(file);
            buffero = new BufferedOutputStream(fileo);
            output = new ObjectOutputStream(buffero);
            output.writeObject(s);
            output.writeObject(d);
            output.close();
            System.out.println("THE GAME HAS BEEN SAVED");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static LinkedList<Status> streamTweets(int count) {
        TwitterStream stream;
        Listener listener;
        listener = new Listener(count);
        stream = TwitterStreamFactory.getSingleton();
        stream.addListener(listener);
        stream.sample();
        while (!listener.limitHit()) {
            try {
                Thread.sleep(ONE_SECOND);
            } catch (InterruptedException e) {
                System.out.println("E");
                e.printStackTrace();
            }
        }
        stream.cleanUp();
        stream.shutdown();
        try {
            Thread.sleep(TWO_SECONDS);
        } catch (InterruptedException e) {
            System.out.println("F");
            e.printStackTrace();
        }
        System.out.println("FINISHED STREAMING");
        return listener.getTweets();
    }

    public static void setUserAuth(boolean auth) {
        if (auth)
            twitter = userAuth;
        else
            twitter = appAuth;
    }

    public static boolean isRetweeter(Status status, String user) {
        List<Status> timeline = getTimelineSince(user, status.getCreatedAt());
        for (Status tweet: timeline) {

        }
        return true;
    }

    public static DirectedSparseGraph<Long, Pair<Long>> makeDiffusionGraph(MonitoredStatus status) {
        try {
            int i = 0, treeSize = 0;
            long[] rters = new long[status.getRetweeters().size()];
            for (User u : status.getRetweeters()) {
                rters[i] = u.getId();
                i++;
            }
            Arrays.sort(rters);
            DirectedSparseGraph<Long, Pair<Long>> g = new DirectedSparseGraph<Long, Pair<Long>>();
            Status st;
            st = twitter.showStatus(status.getId());
            long current = st.getUser().getId();
            g.addVertex(current);
            LinkedList<Long> q = new LinkedList<Long>();
            q.add(current);
            LinkedList<Long> followerList;
            ArrayList<Long> checked = new ArrayList<Long>();
            Long[] followers;
            treeSize = 1;
            int currentGen = 1, nextGen = 0;
            while (!q.isEmpty()) {
                System.out.println(q.size());
                current = q.pop();
                currentGen--;
                if (currentGen < 0) {
                    treeSize++;
                    currentGen = nextGen;
                    nextGen = 0;
                }
                checked.add(current);
                followerList = findFollowers(current);
                followers = followerList.toArray(new Long[followerList.size()]);
                Arrays.sort(followers);
                for (long rt : rters) {
                    if (!checked.contains(rt) && Arrays.binarySearch(followers, rt) >= 0) {
                        g.addVertex(rt);
                        g.addEdge(new Pair<Long>(current, rt), current, rt);
                        q.add(rt);
                        nextGen++;
                    }
                }
            }
            status.setTreeDepth(treeSize);
            System.out.println(treeSize);
            return g;
        } catch (TwitterException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    public static void drawGraph(DirectedSparseGraph<Long, Pair<Long>> g) {
        Collection<Pair<Long>> s = g.getEdges();
        try {
            PrintWriter p = new PrintWriter("graph2.txt");
            p.println("digraph g {");
            p.println("graph [splines = spline];");
            for (Pair<Long> e : s) {
                p.println("\"" + e.getFirst() + "\" -> \"" + e.getSecond() + "\";");
            }
            p.println("}");
            p.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public static List<User> getRetweeters(Status status, int rtCount) {
        Twitter auth;
        boolean limit;
        if (getRetweetsRate <= 15) {
            auth = userAuth;
            limit = false;
        } else if (getRetweetsRate <= 75) {
            auth = appAuth;
            limit = false;
        } else if (getRetweetsRate <= 90) {
            auth = userAuth;
            limit = true;
        } else {
            auth = appAuth;
            limit = true;
        }
        if (rtCount < 0)
            rtCount = 0;
        if (!limit) {
            try {
                List<Status> statuses = auth.getRetweets(status.getId());
                getRetweetsRate++;
                LinkedList<User> ids = new LinkedList<User>();
                Status tweet;
                if (statuses.size() < rtCount)
                    rtCount = statuses.size();
                for (int i = 0; i < rtCount; i++) {
                    tweet = statuses.remove(0);
                    //System.out.println(i);
                    ids.add(tweet.getUser());
                }
                return ids;
            } catch (TwitterException e) {
                System.out.println("WARNING: CHECK ERROR");
                return new LinkedList<User>();
            }
        } else {
            try {
                long[] statuses = auth.getRetweeterIds(status.getId(), -1l).getIDs();
                getRetweetsRate++;
                ResponseList<User> r = userAuth.lookupUsers(statuses);
                if (r.size() < rtCount)
                    rtCount = r.size();
                List<User> ids = r.subList(0, rtCount);
                return ids;
            } catch (TwitterException e) {
                e.printStackTrace();
                System.out.println("All rters protected/deleted");
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

    public static void reconnect() {
        System.setProperty("twitter4j.loggerFactory", "twitter4j.NullLoggerFactory");
        findFollowersRate = 0;
        getRetweetsRate = 0;
        userAuth = TwitterFactory.getSingleton();
        ConfigurationBuilder builder;
        builder = new ConfigurationBuilder();
        //builder.setUseSSL(true);
        builder.setApplicationOnlyAuthEnabled(true);
        appAuth = new TwitterFactory(builder.build()).getInstance();
        try {
            appAuth.getOAuth2Token();
        } catch (TwitterException e1) {
            System.out.println("A");
            e1.printStackTrace();
        }
        twitter = userAuth;
        System.out.println("Sleeping because of reconnection");
        System.out.println(new Date());
        try {
            Thread.sleep(900000);
        } catch (InterruptedException e) {
            System.out.println("Error on sleep in method reconnect");
            e.printStackTrace();
        }
    }

    public static LinkedList<Long> findFollowers(long userid) {
        long cursor = -1;
        IDs ids;
        LinkedList<Long> idr = new LinkedList<Long>();
        int rate = 0;
        ids = null;
        do {
            if (findFollowersRate == 15) {
                setUserAuth(false);
            }
            if (findFollowersRate == 30) {
                System.out.println("Sleeping because of findFollowers");
                System.out.println(new Date());
                try {
                    Thread.sleep(900000);
                } catch (InterruptedException e) {
                    System.out.println("Error on sleep in method findfollowers");
                    e.printStackTrace();
                }
                findFollowersRate = 0;
                setUserAuth(true);
            }
            try {
                findFollowersRate++;
                rate++;
                ids = twitter.getFollowersIDs(userid, cursor);
                for (Long one : ids.getIDs()) {
                    idr.add(one);
                }
                cursor = ids.getNextCursor();
            } catch (TwitterException e) {
                e.printStackTrace();
                if (e.getStatusCode() != -1) {
                    ids = null;
                    System.out.println("Protected user, skipping");
                    System.out.println("idr size = " + idr.size());
                    return idr; 
                } else {
                    System.out.println("Connection error");
                    reconnect();
                    rate--;
                }
            }
        } while ((cursor != 0) && (rate < 30));
        return idr;
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

    public static double getRetweetLikelihood(Status status, List<User> rters) {
        if (!rters.isEmpty()) {
            try {
                int totalFollowers;
                if (status.getRetweetCount() == rters.size())
                    totalFollowers = status.getUser().getFollowersCount();
                else
                    totalFollowers = 0;
                for (User rter : rters) {
                    totalFollowers += rter.getFollowersCount();
                }
                return rters.size() / (double) totalFollowers;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return -1;
        } else {
            return 0;
        }
    }

    /*public static double getMetric(Twitter twitter, Status status) {
		try {
			return twitter.showStatus(status.getId()).getRetweetCount() * getRetweetLikelihood(status, 0);
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}*/

    public static DirectedSparseGraph<Long, Pair<Long>> getRtFollowerGraph(User author, HashSet<User> rters) {
        DirectedSparseGraph<Long, Pair<Long>> g = new DirectedSparseGraph<Long, Pair<Long>>();
        LinkedList<Long> followerList = new LinkedList<Long>();
        User current = author;
        g.addVertex(current.getId());
        followerList = findFollowers(current.getId());
        for (long rt : followerList) {
            g.addVertex(rt);
            g.addEdge(new Pair<Long>(current.getId(), rt), current.getId(), rt);
        }
        for (User rter : rters) {
            System.out.println("Rters size: " + rters.size());
            System.out.println("Rter follow count: " + rter.getFollowersCount());
            g.addVertex(rter.getId());
            followerList = findFollowers(rter.getId());
            for (long rt : followerList) {
                g.addVertex(rt);
                g.addEdge(new Pair<Long>(rter.getId(), rt), rter.getId(), rt);
            }
        }
        return g;
    }

    public static void printGraphForMCL(DirectedSparseGraph<Long, Pair<Long>> g, String filepath) {
        try {
            PrintWriter p = new PrintWriter(filepath);
            if (g == null)
                p.println("empty");
            else {
                for (Pair<Long> ed : g.getEdges()) {
                    p.println(ed.getFirst() + " " + ed.getSecond());
                }
            }
            p.close();
        } catch (FileNotFoundException e) {
            System.out.println("ZB");
            e.printStackTrace();
        }
    }
}
