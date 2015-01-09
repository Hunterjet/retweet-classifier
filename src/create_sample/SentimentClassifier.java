package create_sample;

import java.io.File;
import java.io.IOException;

import com.aliasi.classify.ConditionalClassification;
import com.aliasi.classify.LMClassifier;
import com.aliasi.util.AbstractExternalizable;

/**
 * Uses a language model classifier to classify a tweet's sentiment as
 * positive, negative or neutral.
 * 
 * @author José Parada
 * @version 1.0
 */
public class SentimentClassifier {
    @SuppressWarnings("rawtypes") // We won't reference the type arguments of LMClassifier
    private LMClassifier classifier;

    /**
     * Constructor. Reads the language model classifier from a text file.
     * 
     * @param savedClassifier The text file where the classifier is saved.
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("rawtypes") // The file contains an LMClassifier
    public SentimentClassifier(File savedClassifier) throws IOException, ClassNotFoundException {
        classifier = (LMClassifier)AbstractExternalizable.readObject(savedClassifier);
    }

    /**
     * Classifies the sentiment of a given tweet using the classifier.
     * 
     * @param text The text of the tweet we want to classify.
     * @return The sentiment of the tweet: "pos", "neg" or "neu".
     */
    public String classify(String text) {
        ConditionalClassification classification = classifier.classify(text);
        return classification.bestCategory();
    }
}