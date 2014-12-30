package create_sample;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;

public class Listener implements StatusListener {
	private int count, maxcount;
	private LinkedList<Status> tweets;
	
	public Listener() {
		count = 0;
		maxcount = Integer.MAX_VALUE - 1;
		tweets = new LinkedList<Status>();
	}
	
	public Listener(int max) {
		count = 0;
		maxcount = max;
		tweets = new LinkedList<Status>();
	}
	
	public boolean limitHit() {
		if (count >= maxcount)
			return true;
		else
			return false;
	}

	@Override
	public void onException(Exception arg0) {
		arg0.printStackTrace();
		
	}

	@Override
	public void onDeletionNotice(StatusDeletionNotice arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onScrubGeo(long arg0, long arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStallWarning(StallWarning arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStatus(Status arg0) {
		count++;
		if (count <= maxcount)
			tweets.add(arg0);
	}

	public LinkedList<Status> getTweets() {
		return tweets;
	}

	public void setTweets(LinkedList<Status> tweets) {
		this.tweets = tweets;
	}

	@Override
	public void onTrackLimitationNotice(int arg0) {
		// TODO Auto-generated method stub
		
	}
}
