package create_sample;

import java.io.File;
import java.io.IOException;

import com.aliasi.classify.ConditionalClassification;
import com.aliasi.classify.LMClassifier;
import com.aliasi.util.AbstractExternalizable;

public class SentimentClassifier {

	String[] categories;
	LMClassifier cla;

	public SentimentClassifier() throws IOException, ClassNotFoundException {
		cla = (LMClassifier)AbstractExternalizable.readObject(new File("SentimentClassifier.txt"));
		categories = cla.categories();
	}

	public String classify(String text) {
		ConditionalClassification classification = cla.classify(text);
		return classification.bestCategory();
	}
}