package create_sample;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;

import twitter4j.User;

public class MonitoredStatus implements Serializable {
	private long id, lastRtFrom;
	private String text, sentiment, topic;
	private Date tweetCreated;
	private Date startedMonitoring;
	private LinkedList<Integer> retweetCount;
	//private LinkedList<Integer> retweetGenCount;
	private HashSet<User> retweeters;
	private LinkedList<Double> retweetLikelihood;
	private LinkedList<Double> combined; //retweet likelihood * retweetGenCount;
	private int treeLength;
	//private LinkedList<Integer> clusterCount;
	private int cycles, followerNumber;
	private boolean monitorLikelihood, isDirect, isMention, isHashtag, isURL, isExclamation, isQuestion, isPositiveE, 
		isNegativeE;

	public MonitoredStatus(long id, String text, Date tweetCreated, boolean monitorLikelihood, boolean isDirect, boolean isMention,
			boolean isHashtag, boolean isURL, boolean isExclamation, boolean isQuestion,  boolean isPositiveE, boolean isNegativeE, int followerNumber, 
			String sentiment, String topic) {
		this.id = id;
		this.text = text;
		this.tweetCreated = tweetCreated;
		startedMonitoring = new Date();
		retweetCount = new LinkedList<Integer>();
		retweetLikelihood = new LinkedList<Double>();
		combined = new LinkedList<Double>();
		cycles = 0;
		this.monitorLikelihood = monitorLikelihood;
		lastRtFrom = 0;
		this.isDirect = isDirect;
		this.isMention = isMention;
		this.isHashtag = isHashtag;
		this.isURL = isURL;
		this.isExclamation = isExclamation;
		this.isQuestion = isQuestion;
		this.isPositiveE = isPositiveE;
		this.isNegativeE = isNegativeE;
		this.followerNumber = followerNumber;
		retweeters = new HashSet<User>();
		this.treeLength = 0;
		this.sentiment = sentiment;
		this.topic = topic;
	}
	
	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public String getSentiment() {
		return sentiment;
	}

	public void setSentiment(String sentiment) {
		this.sentiment = sentiment;
	}

	public LinkedList<Double> getCombined() {
		return combined;
	}

	public void setCombined(LinkedList<Double> combined) {
		this.combined = combined;
	}
	
	public int getTreeLength() {
		return treeLength;
	}

	public void setTreeLength(int treeLength) {
		this.treeLength = treeLength;
	}
	
	public HashSet<User> getRetweeters() {
		return retweeters;
	}
	
	public void setRetweeters(HashSet<User> retweeters) {
		this.retweeters = retweeters;
	}

	public int getFollowerNumber() {
		return followerNumber;
	}

	public void setFollowerNumber(int followerNumber) {
		this.followerNumber = followerNumber;
	}

	public boolean isPositiveE() {
		return isPositiveE;
	}

	public void setPositiveE(boolean isPositiveE) {
		this.isPositiveE = isPositiveE;
	}

	public boolean isNegativeE() {
		return isNegativeE;
	}

	public void setNegativeE(boolean isNegativeE) {
		this.isNegativeE = isNegativeE;
	}

	public boolean isExclamation() {
		return isExclamation;
	}

	public void setExclamation(boolean isExclamation) {
		this.isExclamation = isExclamation;
	}

	public boolean isQuestion() {
		return isQuestion;
	}

	public void setQuestion(boolean isQuestion) {
		this.isQuestion = isQuestion;
	}

	public long getLastRtFrom() {
		return lastRtFrom;
	}

	public void setLastRtFrom(long lastRtFrom) {
		this.lastRtFrom = lastRtFrom;
	}
	
	public boolean isMonitorLikelihood() {
		return monitorLikelihood;
	}

	public void setMonitorLikelihood(boolean monitorLikelihood) {
		this.monitorLikelihood = monitorLikelihood;
	}

	public LinkedList<Integer> getRetweetCount() {
		return retweetCount;
	}

	public LinkedList<Double> getRetweetLikelihood() {
		return retweetLikelihood;
	}
	
	public boolean isDirect() {
		return isDirect;
	}

	public void setDirect(boolean isDirect) {
		this.isDirect = isDirect;
	}

	public boolean isMention() {
		return isMention;
	}

	public void setMention(boolean isMention) {
		this.isMention = isMention;
	}

	public boolean isHashtag() {
		return isHashtag;
	}

	public void setHashtag(boolean isHashtag) {
		this.isHashtag = isHashtag;
	}

	public boolean isURL() {
		return isURL;
	}

	public void setURL(boolean isURL) {
		this.isURL = isURL;
	}

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
		if (monitorLikelihood) {
			System.out.print("=====RetweetLikelihood: ");
			for (double rtl : retweetLikelihood) {
				System.out.print(rtl + ", ");
			}
			System.out.println();
			System.out.print("=====Combined: ");
			for (double rtl : combined) {
				System.out.print(rtl + ", ");
			}
			System.out.println();
		}
		System.out.println("Is Direct: " + isDirect);
		System.out.println("Is Mention: " + isMention);
		System.out.println("Is Hashtag: " + isHashtag);
		System.out.println("Is URL: " + isURL);
		System.out.println("Is Exclamation: " + isExclamation);
		System.out.println("Is Question: " + isQuestion);
		System.out.println("Sentiment: " + sentiment);
		System.out.println("Topic: " + topic);
		System.out.println("Is Emoticon Positive: " + isPositiveE);
		System.out.println("Is Emoticon Negative: " + isNegativeE);
		System.out.println("Author's followers: " + followerNumber);
		System.out.print("Retweeters: ");
		for (User u : retweeters)
			System.out.print(u.getScreenName() + ", ");
		System.out.println();
		System.out.println();
	}
	
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
			if (monitorLikelihood) {
				writer.print("=====RetweetLikelihood: ");
				for (double rtl : retweetLikelihood) {
					writer.print(rtl + ", ");
				}
				writer.println();
				writer.print("=====Combined: ");
				for (double rtl : combined) {
					writer.print(rtl + ", ");
				}
				writer.println();
			}
			writer.println("Is Direct: " + isDirect);
			writer.println("Is Mention: " + isMention);
			writer.println("Is Hashtag: " + isHashtag);
			writer.println("Is URL: " + isURL);
			writer.println("Is Exclamation: " + isExclamation);
			writer.println("Is Question: " + isQuestion);
			writer.println("Sentiment: " + sentiment);
			writer.println("Topic: " + topic);
			writer.println("Is Emoticon Positive: " + isPositiveE);
			writer.println("Is Emoticon Negative: " + isNegativeE);
			writer.println("Author's followers: " + followerNumber);
			writer.println();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public long getId() {
		return id;
	}

	public String getText() {
		return text;
	}

	public Date getTweetCreated() {
		return tweetCreated;
	}

	public Date getStartedMonitoring() {
		return startedMonitoring;
	}
	
	public void addObservation(int oRetweetCount, double oRetweetLikelihood) {
		if(!retweetCount.isEmpty() && (retweetCount.getLast() == oRetweetCount)) {
			cycles++;
		}
		else {
			cycles = 0;
		}
		retweetCount.add(oRetweetCount);
		retweetLikelihood.add(oRetweetLikelihood);
		combined.add(oRetweetCount * oRetweetLikelihood);
	}

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

	public int getCycles() {
		return cycles;
	}
}
