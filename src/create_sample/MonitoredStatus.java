package create_sample;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;

import twitter4j.User;

/**
 * Represents a tweet we're monitoring or have monitored.
 * 
 * @author José Parada
 * @version 1.0
 * @serial Starts at <code>1L</code>, will go up one each time this class is modified.
 */
public class MonitoredStatus implements Serializable {
    private static final long serialVersionUID = 1L;
    private long id;
    private String text, sentiment, topic;
    private Date tweetCreated;
    private Date startedMonitoring;
    private LinkedList<Integer> retweetCount;
    private HashSet<User> retweeters;
    private LinkedList<Double> retweetLikelihood;
    private LinkedList<Double> combined; // retweet likelihood * retweetCount;
    private int treeDepth; // depth of the diffusion tree
    private int inactivePeriods, authorFollowerNumber;
    private boolean isDirect, hasMention, hasHashtag, hasURL, isExclamation, isQuestion, hasPositiveEmoticon, 
    hasNegativeEmoticon;

    /**
     * Constructor. Initializes a new tweet to be monitored with its observed values.
     * 
     * @param id this tweet's ID in the Twitter network.
     * @param text The text of this tweet. 140 characters or less!
     * @param tweetCreated The date and time this tweet was posted.
     * @param isDirect Whether this tweet is a direct mention or not. Direct mentions
     * are tweets that start with a mention (an username reference).
     * @param isMention Whether this tweet has a mention or not.
     * @param isHashtag Whether this tweet has a hashtag or not.
     * @param isURL Whether this tweet has an URL or not.
     * @param isExclamation Whether this tweet has an exclamation mark or not.
     * @param isQuestion Whether this tweet has a question mark or not.
     * @param hasPositiveEmoticon Whether this tweet has a positive emoticon or not. We
     * consider ":-)", ":)", ";)", ";-)", ":D", ":-D" as positive emoticons.
     * @param hasNegativeEmoticon Whether this tweet has a negative emoticon or not. We
     * consider ":(", ":-(", "D:", "D-:", ";_;" as negative emoticons.
     * @param followerNumber The number of followers the author has.
     * @param sentiment The sentiment of this tweet. Positive, negative or neutral.
     * @param topic The topic of this tweet, classified from a pool of 18 news topics.
     * @see SentimentClassifier
     * @see TopicClassifier
     */
    public MonitoredStatus(long id, String text, Date tweetCreated, boolean isDirect, boolean isMention,
            boolean isHashtag, boolean isURL, boolean isExclamation, boolean isQuestion,  boolean hasPositiveEmoticon, 
            boolean hasNegativeEmoticon, int followerNumber, String sentiment, String topic) {
        this.id = id;
        this.text = text;
        this.tweetCreated = tweetCreated;
        startedMonitoring = new Date();
        retweetCount = new LinkedList<Integer>();
        retweetLikelihood = new LinkedList<Double>();
        combined = new LinkedList<Double>();
        inactivePeriods = 0;
        this.isDirect = isDirect;
        this.hasMention = isMention;
        this.hasHashtag = isHashtag;
        this.hasURL = isURL;
        this.isExclamation = isExclamation;
        this.isQuestion = isQuestion;
        this.hasPositiveEmoticon = hasPositiveEmoticon;
        this.hasNegativeEmoticon = hasNegativeEmoticon;
        this.authorFollowerNumber = followerNumber;
        retweeters = new HashSet<User>();
        this.treeDepth = 0;
        this.sentiment = sentiment;
        this.topic = topic;
    }

    /**
     * Returns this tweet's topic, from a pool of 18 news topics. The possible
     * topics are:
     * <ul>
     * <li>Business_Finance
     * <li>Disaster_Accident
     * <li>Education
     * <li>Entertainment_Culture
     * <li>Environment
     * <li>Health_Medical_Pharma
     * <li>Hospitality_Recreation
     * <li>Human Interest
     * <li>Labor
     * <li>Law_Crime
     * <li>Other
     * <li>Politics
     * <li>Religion_Belief
     * <li>Social Issues
     * <li>Sports
     * <li>Technology_Internet
     * <li>War_Conflict
     * <li>Weather
     * </ul>
     * 
     * @return This tweet's topic.
     * @see TopicClassifier
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Sets this tweet's topic.
     * 
     * @param topic This tweet's topic.
     * @see TopicClassifier
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * Returns this tweet's sentiment. Positive, negative or neutral, expressed
     * "pos", "neg" or "neu".
     * 
     * @return This tweet's sentiment: "pos", "neg" or "neu".
     */
    public String getSentiment() {
        return sentiment;
    }

    /**
     * Sets this tweet's sentiment.
     * 
     * @param sentiment The tweet's sentiment.
     */
    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    /**
     * Returns a list containing this tweet's combined statistic, measured every
     * 15 minutes. We define the combined statistic as the number of retweets 
     * multiplied by the retweet likelihood.
     * 
     * @return This tweet's number of retweets multiplied by the retweet likelihood.
     */
    public LinkedList<Double> getCombined() {
        return combined;
    }

    /**
     * Sets the list of this tweet's combined statistic, measured every 15 minutes.
     * We define the combined statistic as the number of retweets multiplied by
     * the retweet likelihood.
     * 
     * @param combined A list containing this tweet's combined statistic every 15 
     * minutes.
     */
    public void setCombined(LinkedList<Double> combined) {
        this.combined = combined;
    }

    /**
     * Returns the depth of this tweet's diffusion tree. A tweet's diffusion
     * tree is a tree where each node is a retweeting user and each edge points
     * in the direction of the tweet flow. That means that if user A retweeted
     * the tweet, and user B follows user A and retweeted the tweet after him,
     * user B will be a child of user A in the tree.
     * 
     * @return The depth of this tweet's diffusion tree.
     */
    public int getTreeDepth() {
        return treeDepth;
    }

    /**
     * Set the depth of this tweet's diffusion tree. A tweet's diffusion
     * tree is a tree where each node is a retweeting user and each edge points
     * in the direction of the tweet flow. That means that if user A retweeted
     * the tweet, and user B follows user A and retweeted the tweet after him,
     * user B will be a child of user A in the tree.
     * 
     * @param treeDepth The depth of this tweet's diffusion tree.
     */
    public void setTreeDepth(int treeDepth) {
        this.treeDepth = treeDepth;
    }

    /**
     * Returns the set of the users who retweeted this tweet.
     * 
     * @return The set of users who retweeted this tweet.
     */
    public HashSet<User> getRetweeters() {
        return retweeters;
    }

    /**
     * Sets this tweet's retweeter set to the <code>HashSet</code> passed to this method.
     * 
     * @param retweeters The set of users who retweeted this tweet.
     */
    public void setRetweeters(HashSet<User> retweeters) {
        this.retweeters = retweeters;
    }

    /**
     * Returns the amount of followers this tweet's author has.
     * 
     * @return The number of followers this tweet's author has.
     */
    public int getFollowerNumber() {
        return authorFollowerNumber;
    }

    /**
     * Sets this tweet's author follower number.
     * 
     * @param followerNumber The number of followers this tweet's author has.
     */
    public void setFollowerNumber(int followerNumber) {
        this.authorFollowerNumber = followerNumber;
    }

    /**
     * Returns whether this tweet has a positive emoticon or not. We
     * consider ":-)", ":)", ";)", ";-)", ":D", ":-D" as positive emoticons.
     * 
     * @return True if this tweet has a positive emoticon, false otherwise.
     */
    public boolean hasPositiveEmoticon() {
        return hasPositiveEmoticon;
    }

    /**
     * Sets whether this tweet has a positive emoticon or not. We consider 
     * ":-)", ":)", ";)", ";-)", ":D", ":-D" as positive emoticons.
     * 
     * @param hasPositiveEmoticon True if this tweet has a positive emoticon,
     * false otherwise.
     */
    public void setPositiveEmoticon(boolean hasPositiveEmoticon) {
        this.hasPositiveEmoticon = hasPositiveEmoticon;
    }

    /**
     * Returns whether this tweet has a negative emoticon or not. We
     * consider ":(", ":-(", "D:", "D-:", ";_;" as negative emoticons.
     * 
     * @return True if this tweet has a negative emoticon, false otherwise.
     */
    public boolean hasNegativeEmoticon() {
        return hasNegativeEmoticon;
    }

    /**
     * Sets whether this tweet has a negative emoticon or not. We
     * consider ":(", ":-(", "D:", "D-:", ";_;" as negative emoticons.
     * 
     * @param hasNegativeEmoticon True if this tweet has a negative emoticon, 
     * false otherwise.
     */
    public void setNegativeEmoticon(boolean hasNegativeEmoticon) {
        this.hasNegativeEmoticon = hasNegativeEmoticon;
    }

    /**
     * Returns whether this tweet has an exclamation mark or not.
     * 
     * @return True if this tweet has an exclamation mark, false otherwise.
     */
    public boolean isExclamation() {
        return isExclamation;
    }

    /**
     * Sets whether this tweet has an exclamation mark or not.
     * 
     * @param isExclamation True if this tweet has an exclamation mark, 
     * false otherwise.
     */
    public void setExclamation(boolean isExclamation) {
        this.isExclamation = isExclamation;
    }
    
    /**
     * Returns whether this tweet has a question mark or not.
     * 
     * @return True if this tweet has a question mark, false otherwise.
     */
    public boolean isQuestion() {
        return isQuestion;
    }

    /**
     * Sets whether this tweet has a question mark or not.
     * 
     * @param isQuestion True if this tweet has a question mark, 
     * false otherwise.
     */
    public void setQuestion(boolean isQuestion) {
        this.isQuestion = isQuestion;
    }

    /**
     * Returns a list containing the number of retweets this tweet has had 
     * every 15 minutes since we started monitoring it.
     * 
     * @return A list containing the number of retweets this tweet has had
     * every 15 minutes.
     */
    public LinkedList<Integer> getRetweetCount() {
        return retweetCount;
    }

    /**
     * Returns a list containing the retweet likelihood this tweet has had 
     * every 15 minutes since we started monitoring it.
     * 
     * @return A list containing the number of retweets this tweet has had
     * every 15 minutes.
     */
    public LinkedList<Double> getRetweetLikelihood() {
        return retweetLikelihood;
    }

    /**
     * Returns whether this tweet is a direct mention or not. Direct mentions
     * are tweets that start with a mention, which is a reference to an
     * username.
     * 
     * @return Whether this tweet is a direct mention or not.
     * @see #hasMention
     * @see #setMention
     */
    public boolean isDirect() {
        return isDirect;
    }
    
    /**
     * Sets whether this tweet is a direct mention or not. Direct mentions
     * are tweets that start with a mention, which is a reference to an
     * username.
     * 
     * @param isDirect Whether this tweet is a direct mention or not.
     * @see #hasMention
     * @see #setMention
     */
    public void setDirect(boolean isDirect) {
        this.isDirect = isDirect;
    }

    /**
     * Returns whether this tweet has a mention or not. A mention
     * is a reference to an username, written as {@literal @}username.
     * 
     * @return Whether this tweet has a mention or not.
     */
    public boolean hasMention() {
        return hasMention;
    }

    /**
     * Sets whether this tweet has a mention or not. A mention
     * is a reference to an username, written as {@literal @}username.
     * 
     * @param hasMention Whether this tweet has a mention or not.
     */
    public void setMention(boolean hasMention) {
        this.hasMention = hasMention;
    }

    /**
     * Returns whether this tweet has a hashtag or not. A hashtag is a way to
     * classify a tweet within the Twitter network. By writing a # symbol
     * followed by a string, the tweet, and the rest of the tweets with the
     * same hashtag, can be found using Twitter's search tool. This is a 
     * good way to discuss a certain topic in a thread-like structure.
     * 
     * @return Whether this tweet has a hashtag or not.
     */
    public boolean hasHashtag() {
        return hasHashtag;
    }

    /**
     * Sets whether this tweet has a hashtag or not. A hashtag is a way to
     * classify a tweet within the Twitter network. By writing a # symbol
     * followed by a string, the tweet, and the rest of the tweets with the
     * same hashtag, can be found using Twitter's search tool. This is a 
     * good way to discuss a certain topic in a thread-like structure.
     * 
     * @param hasHashtag Whether this tweet has a hashtag or not.
     */
    public void setHashtag(boolean hasHashtag) {
        this.hasHashtag = hasHashtag;
    }

    /**
     * Returns whether this tweet contains an URL or not.
     * 
     * @return Whether this tweet contains an URL or not.
     */
    public boolean hasURL() {
        return hasURL;
    }

    /**
     * Sets whether this tweet contains an URL or not.
     * 
     * @param hasURL Whether this tweet contains an URL or not.
     */
    public void setURL(boolean hasURL) {
        this.hasURL = hasURL;
    }

    /**
     * Prints to console most of this tweet's characteristics.
     */
    public void print() {
        System.out.println("ID = " + id);
        System.out.println("Text = " + text);
        System.out.println("Created at = " + tweetCreated);
        System.out.println("Started monitoring at = " + startedMonitoring);
        System.out.print("RetweetCount: ");
        for (int rtc : retweetCount) {
            System.out.print(rtc + ", ");
        }
        System.out.println();
        
        System.out.print("RetweetLikelihood: ");
        for (double rtl : retweetLikelihood) {
            System.out.print(rtl + ", ");
        }
        System.out.println();
        System.out.print("Combined: ");
        for (double rtl : combined) {
            System.out.print(rtl + ", ");
        }
        System.out.println();
        
        System.out.println("Is Direct: " + isDirect);
        System.out.println("Is Mention: " + hasMention);
        System.out.println("Is Hashtag: " + hasHashtag);
        System.out.println("Is URL: " + hasURL);
        System.out.println("Is Exclamation: " + isExclamation);
        System.out.println("Is Question: " + isQuestion);
        System.out.println("Sentiment: " + sentiment);
        System.out.println("Topic: " + topic);
        System.out.println("Is Emoticon Positive: " + hasPositiveEmoticon);
        System.out.println("Is Emoticon Negative: " + hasNegativeEmoticon);
        System.out.println("Author's followers: " + authorFollowerNumber);
        System.out.print("Retweeters: ");
        for (User u : retweeters)
            System.out.print(u.getScreenName() + ", ");
        System.out.println();
        System.out.println();
    }

    /**
     * Prints to a file most of this tweet's characteristics.
     * 
     * @param file The file to print to.
     */
    public void printToFile(String file) {
        try { 
            PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
            writer.println("ID = " + id);
            writer.println("Text = " + text);
            writer.println("Created at = " + tweetCreated);
            writer.println("Started monitoring at = " + startedMonitoring);
            writer.print("RetweetCount: ");
            for (int rtc : retweetCount) {
                writer.print(rtc + ", ");
            }
            writer.println();
            
            writer.print("RetweetLikelihood: ");
            for (double rtl : retweetLikelihood) {
                writer.print(rtl + ", ");
            }
            writer.println();
            writer.print("Combined: ");
            for (double rtl : combined) {
                writer.print(rtl + ", ");
            }
            writer.println();
            
            writer.println("Is Direct: " + isDirect);
            writer.println("Is Mention: " + hasMention);
            writer.println("Is Hashtag: " + hasHashtag);
            writer.println("Is URL: " + hasURL);
            writer.println("Is Exclamation: " + isExclamation);
            writer.println("Is Question: " + isQuestion);
            writer.println("Sentiment: " + sentiment);
            writer.println("Topic: " + topic);
            writer.println("Is Emoticon Positive: " + hasPositiveEmoticon);
            writer.println("Is Emoticon Negative: " + hasNegativeEmoticon);
            writer.println("Author's followers: " + authorFollowerNumber);
            writer.println();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns this tweet's ID in the Twitter network.
     * 
     * @return This tweet's ID.
     */
    public long getId() {
        return id;
    }

    /**
     * Returns this tweet's text. 140 character or less!
     * 
     * @return This tweet's text.
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the date and time this tweet was posted.
     * 
     * @return The date and time this tweet was posted.
     */
    public Date getTweetCreated() {
        return tweetCreated;
    }

    /**
     * Returns the date and time we started monitoring this tweet.
     * 
     * @return The date and time we started monitoring this tweet.
     */
    public Date getStartedMonitoring() {
        return startedMonitoring;
    }

    /**
     * Adds an observation of the number of retweets, the retweet likelihood
     * and the combined statistic of this tweet. This method is called every 
     * 15 minutes to keep an
     * accurate record of these measurements. If the retweet amount is the same
     * as the previously observed amount, the count of inactive periods goes 
     * up. A tweet is considered dead when it reaches 4 inactive periods, which
     * is an hour of inactivity.
     * 
     * @param oRetweetCount The number of retweets observed in this period.
     * @param oRetweetLikelihood The retweet likelihood observed in this period.
     */
    public void addObservation(int oRetweetCount, double oRetweetLikelihood) {
        if(!retweetCount.isEmpty() && (retweetCount.getLast() == oRetweetCount)) {
            inactivePeriods++;
        }
        else {
            inactivePeriods = 0;
        }
        retweetCount.add(oRetweetCount);
        retweetLikelihood.add(oRetweetLikelihood);
        combined.add(oRetweetCount * oRetweetLikelihood);
    }

    /**
     * Returns whether this tweet is the same as the tweet passed in the
     * argument. We consider two tweets to be the same if they share the
     * same ID.
     * 
     * @param obj The tweet to compare this tweet against.
     * @return True if the tweets have the same ID, false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MonitoredStatus other = (MonitoredStatus) obj;
        if (id != other.id)
            return false;
        return true;
    }

    /**
     * Returns the amount of periods since this tweet's last retweet. Periods
     * are 15 minutes long, and a tweet is considered dead when it reaches an
     * hour of inactivity.
     * 
     * @return The amount of periods since this tweet's last retweet.
     */
    public int getInactivePeriods() {
        return inactivePeriods;
    }
}
