package twitter4j;

import java.io.File;
import com.aliasi.classify.ConditionalClassification;
import com.aliasi.classify.LMClassifier;
import com.aliasi.util.AbstractExternalizable;

public class SentimentAnalyzer {

	String[] categories;
	LMClassifier cla;

	public SentimentAnalyzer() {
		try {
			cla = (LMClassifier)AbstractExternalizable.readObject(new File("classifier.txt"));
			categories = cla.categories();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String classify(String text) {
		ConditionalClassification classification = cla.classify(text);
		return classification.bestCategory();
	}
}