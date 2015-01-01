package create_sample;

import com.aliasi.classify.Classification;
import com.aliasi.classify.Classified;
import com.aliasi.classify.DynamicLMClassifier;
import com.aliasi.classify.JointClassification;
import com.aliasi.classify.JointClassifier;
import com.aliasi.lm.NGramProcessLM;
import com.aliasi.util.AbstractExternalizable;

import java.io.File;
import java.io.IOException;

import com.aliasi.util.Files;

/**
 * Uses a language model classifier to classify a tweet's topic from a list of 
 * 18 news topics. The possible topics are:
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
 * @author José Parada
 * @version 1.0
 */
public class TopicClassifier {
    private static final File TRAINING_DIR = new File("topics");
    private static final String[] CATEGORIES = {"Business_Finance", "Disaster_Accident", "Education", "Entertainment_Culture", "Environment",  
        "Health_Medical_Pharma", "Hospitality_Recreation", "Human Interest", "Labor", "Law_Crime", "Other", "Politics", "Religion_Belief", "Social Issues", 
        "Sports", "Technology_Internet", "War_Conflict", "Weather"};
    private static final int NGRAM_SIZE = 6; // Amount of words the language model classifier will take together when calculating probabilities.
    private static JointClassifier<CharSequence> compiledClassifier;

    /**
     * Constructor. Reads the classifier from a text file, which is a precompiled
     * classifier using the training sample referenced in {@link #trainClassifier() trainClassifier}.
     * 
     * @throws IOException
     * @throws ClassNotFoundException
     * @see #trainClassifier() trainClassifier
     */
    @SuppressWarnings("unchecked") // The file contains a JointClassifier
    public TopicClassifier() throws IOException, ClassNotFoundException {
        compiledClassifier = (JointClassifier<CharSequence>)AbstractExternalizable.readObject(new File("TopicClassifier.txt"));
    }

    /**
     * Classifies the topic of a given tweet using the classifier.
     * 
     * @param text The text of the tweet we want to classify.
     * @return A topic from the list in {@link TopicClassifier the class description}.
     * @see TopicClassifier
     */
    public String classify(String text) {
        JointClassification jc = compiledClassifier.classify(text);
        return jc.bestCategory();
    }

    /**
     * Trains a classifier from scratch using a training directory with a 
     * training sample. This training sample was first used in
     * {@link <a href=http://fabianabel.de/papers/2011-wis-twitter-um-umap.pdf>Analyzing User Modeling on Twitter for Personalized News Recommendations</a>}.
     * 
     * @throws IOException
     * @throws ClassNotFoundException
     * @see <a href=http://fabianabel.de/papers/2011-wis-twitter-um-umap.pdf>Analyzing User Modeling on Twitter for Personalized News Recommendations</a>
     */
    @SuppressWarnings("unchecked") // The file contains a JointClassifier
    public void trainClassifier() throws IOException, ClassNotFoundException {
        DynamicLMClassifier<NGramProcessLM> classifier = DynamicLMClassifier.createNGramProcess(CATEGORIES, NGRAM_SIZE);
        for(int i = 0; i < CATEGORIES.length; ++i) {
            File classDir = new File(TRAINING_DIR, CATEGORIES[i]);
            String[] trainingFiles = classDir.list();
            for (int j = 0; j < trainingFiles.length; ++j) {
                File file = new File(classDir, trainingFiles[j]);
                String text = Files.readFromFile(file, "UTF-8");
                Classification classification = new Classification(CATEGORIES[i]);
                Classified<CharSequence> classified = new Classified<CharSequence>(text, classification);
                classifier.handle(classified);
            }
        }	
        // compiling
        AbstractExternalizable.compileTo(classifier, new File("TopicClassifier.txt"));
        compiledClassifier = (JointClassifier<CharSequence>) AbstractExternalizable.compile(classifier);
    }
}
