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

public class TopicClassifier {
	private static File TRAINING_DIR = new File("topics");
	private static String[] CATEGORIES = {"Business_Finance", "Disaster_Accident", "Education", "Entertainment_Culture", "Environment",  
		"Health_Medical_Pharma", "Hospitality_Recreation", "Human Interest", "Labor", "Law_Crime", "Other", "Politics", "Religion_Belief", "Social Issues", 
		"Sports", "Technology_Internet", "War_Conflict", "Weather"};
	private static int NGRAM_SIZE = 6;
	private static JointClassifier<CharSequence> compiledClassifier;
	
	public TopicClassifier() {
		try {
			DynamicLMClassifier<NGramProcessLM> classifier = DynamicLMClassifier.createNGramProcess(CATEGORIES, NGRAM_SIZE);
			for(int i = 0; i < CATEGORIES.length; ++i) {
		        File classDir = new File(TRAINING_DIR, CATEGORIES[i]);
		        String[] trainingFiles = classDir.list();
		        for (int j = 0; j < trainingFiles.length; ++j) {
		            File file = new File(classDir, trainingFiles[j]);
		            String text = Files.readFromFile(file,"UTF-8");
		            Classification classification = new Classification(CATEGORIES[i]);
		            Classified<CharSequence> classified = new Classified<CharSequence>(text,classification);
		            classifier.handle(classified);
		        }
			}	
		    //compiling
		    compiledClassifier = (JointClassifier<CharSequence>) AbstractExternalizable.compile(classifier);
		} catch (Exception e) {
			System.out.println("Failed at making the classifier");
			e.printStackTrace();
		}
	}
	
	public String classify(String text) {
		JointClassification jc = compiledClassifier.classify(text);
		return jc.bestCategory();
	}
}
