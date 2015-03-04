"""Train and test several classifiers on a sample of tweets, 

Reads text files containing a training sample and a test sample of 
tweets, and runs them through Random Forest, K Neighbors, Logistic 
Regression and SVC, using the tweet's first 15, 30, and 45 minutes
of history, to try to predict their final retweet amount by classifying
them into retweet buckets. Prints the score of the test classifications
along with the confusion matrix, precision and recall.

Created on 15/07/2014

@author: Jose Parada
"""

from __future__ import division
from sklearn.svm import SVC
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix
from fim import eclat
import glob

NUMBER_OF_RT_BUCKETS = 4
TRAINING_DIR = 'Training/'
TEST_DIR = 'Test/'
TRAINING_SAMPLE = TRAINING_DIR + 'Training.sample'
TEST_SAMPLE = TEST_DIR + 'Test.sample'

cols = ('#followers', 'isDirect', 'isMention', 'hasExclamation', 'hasHashtag', 
        'hasEmoticonNegative', 'hasEmoticonPositive', 'hasQuestion', 'hasURL', 
        'Sentiment')
colsOneP = ('#followers', 'isDirect', 'isMention', 'hasExclamation', 'hasHashtag', 
        'hasEmoticonNegative', 'hasEmoticonPositive', 'hasQuestion', 'hasURL', 
        'SentimentNegative', 'SentimentNeutral', 'SentimentPositive', 'Retweets 1',
        'RetweetsDif 1', 'Probability 1', 'ProbabilityDif 1', 'Views 1', 'ViewsDif 1',
        'FollowerAvg 1', 'FollowerAvgDif 1', 'TopicBucket 1', 'TopicBucket 2',
        'TopicBucket 3', 'TopicBucket 4');
colsTwoP = ('#followers', 'isDirect', 'isMention', 'hasExclamation', 'hasHashtag', 
        'hasEmoticonNegative', 'hasEmoticonPositive', 'hasQuestion', 'hasURL', 
        'SentimentNegative', 'SentimentNeutral', 'SentimentPositive', 'Retweets 1',
        'Retweets 2', 'RetweetsDif 1', 'RetweetsDif 2', 'Probability 1', 
        'Probability 2', 'ProbabilityDif 1', 'ProbabilityDif 2', 'Views 1', 
        'Views 2', 'ViewsDif 1', 'ViewsDif 2', 'FollowerAvg 1', 'FollowerAvg 2',
        'FollowerAvgDif 1', 'FollowerAvgDif 2', 'TopicBucket 1', 'TopicBucket 2',
        'TopicBucket 3', 'TopicBucket 4');
colsThreeP = ('#followers', 'isDirect', 'isMention', 'hasExclamation', 'hasHashtag', 
        'hasEmoticonNegative', 'hasEmoticonPositive', 'hasQuestion', 'hasURL', 
        'SentimentNegative', 'SentimentNeutral', 'SentimentPositive', 'Retweets 1',
        'Retweets 2', 'Retweets 3', 'RetweetsDif 1', 'RetweetsDif 2', 'RetweetsDif 3', 
        'Probability 1', 'Probability 2', 'Probability 3', 'ProbabilityDif 1', 
        'ProbabilityDif 2', 'ProbabilityDif 3', 'Views 1', 'Views 2', 'Views 3', 
        'ViewsDif 1', 'ViewsDif 2', 'ViewsDif 3', 'FollowerAvg 1', 'FollowerAvg 2', 
        'FollowerAvg 3', 'FollowerAvgDif 1', 'FollowerAvgDif 2', 'FollowerAvgDif 3', 
        'TopicBucket 1', 'TopicBucket 2', 'TopicBucket 3', 'TopicBucket 4');
tweetsPerBucket = [0, 0, 0, 0]
tweetsPerTopic = [{}, {}, {}, {}]

def replace_all(text, dic):
    """Replace all occurrences of the dictionary strings in the text."""
    for i, j in dic.iteritems():
        text = text.replace(i, j)
    return text

def get_sample(filePath, training, minimumPeriods=4):
    """Read a tweet sample from a sample file and return it as lists.
    
    The sample variables are transformed into floats if they're numbers.
    Some elements, like the retweet amounts, are transformed placed
    into buckets. The sample is returned in the form of two lists, X for 
    the independent variables and Y for the dependent variable, which is 
    the retweet amount.
    
    The elements of X are as follows:
    0:AuthorsFollowers, 1:isDirect, 2:isMention, 3:isExclamation, 
    4:hasHashtag, 5:hasNegativeEmoticon, 6:hasPositiveEmoticon, 
    7:isQuestion, 8:hasUrl, 9-11:Sentiment OHE, 12+: History data, 
    -1 - -4: Topic ratios.
    """
    sampleFile = open(filePath)
    X = []
    Y = []
    line = sampleFile.readline()
    while line != '':
        row = line.split()
            
        if int(row[-1]) >= minimumPeriods: 
            Y.append(float(row[0]))
            topic = row[13]
            
            if training:
                if Y[-1] == 0:
                    group = 0
                elif Y[-1] <= 10:
                    group = 1
                elif Y[-1] <= 50:
                    group = 2
                else:
                    group = 3
                tweetsPerBucket[group] += 1
                if topic in tweetsPerTopic[group]:
                    tweetsPerTopic[group][topic] += 1
                else:
                    tweetsPerTopic[group][topic] = 1
                    
            add = [float(x) for x in row[3:12]]
                
            ohe = OneHotEncoder()
            ohe.fit([[0], [1], [2]]);
            for e in ohe.transform([[row[12]]]).toarray()[0]:
                add.append(float(e))
                
            for _ in range(8):
                row = sampleFile.readline().split()
                for i in range(minimumPeriods - 1):
                    add.append(float(row[i]))
                    
            add.append(topic)
            
            X.append(add)
        else:
            for _ in range(8):
                sampleFile.readline()
        line = sampleFile.readline()
    
    for x in X:
        topic = x[-1]
        if topic in tweetsPerTopic[0]:
            x[-1] = tweetsPerTopic[0][topic] / tweetsPerBucket[0]
        else:
            x[-1] = 0
        for i in range(1, NUMBER_OF_RT_BUCKETS):
            if topic in tweetsPerTopic[i]:
                x.append(tweetsPerTopic[i][topic] / tweetsPerBucket[i])
            else:
                x.append(0)
    
    sampleFile.close()
    return X, Y

def get_sample_eclat(name):
    """Read a tweet sample from a sample file and return it in a format eclat
    can process.
    """
    sampleFile = open(name)
    X = []
    Y = []
    line = sampleFile.readline()
    while line != '':
        row = line.split()
        Y.append(int(row[0]))
        
        x = []
        if int(row[3]) < 50:
            x.append('#followers: 0-49')
        elif int(row[3]) < 100:
            x.append('#followers: 50-99')
        elif int(row[3]) < 500:
            x.append('#followers: 100-499')
        elif int(row[3]) < 1000:
            x.append('#followers: 500-999')
        elif int(row[3]) < 5000:
            x.append('#followers: 1000-4999')
        elif int(row[3]) < 10000:
            x.append('#followers: 5000-9999')
        else:
            x.append('#followers: 10000+')
            
        for i in range(4, 12):
            if int(row[i]):
                x.append(cols[i - 3])
            
        if int(row[12]) == 0:
            x.append('Sentiment: Negative')
        elif int(row[12]) == 1:
            x.append('Sentiment: Neutral')
        else:
            x.append('Sentiment: Positive')
        
        x.append('Topic: ' + row[13])
        X.append(x)
        
        for _ in range(8):
            sampleFile.readline()
        line = sampleFile.readline()
    
    return X, Y

def split_groups(Y):
    """Split the retweet amounts in Y into buckets.
    
    Bucket 0 - 0 retweets.
    Bucket 1 - 1 < retweets <= 10.
    Bucket 2 - 11 < retweets <= 50.
    Bucket 3 - More than 50 retweets.
    """
    Ysplit = []
    for y in Y:
        if y == 0:
            Ysplit.append(0)
        elif y <= 10:
            Ysplit.append(1)
        elif y <= 50:
            Ysplit.append(2)
        else:
            Ysplit.append(3)
    return Ysplit

def classify(X, Y, Xstring, Y2, method, periods):
    """Trains and tests a sample against a classifier.
    
    Two samples of tweets, one for training and one for testing, are run
    through a classifier of the caller's choosing through the method
    argument. 
    
    The possible values of the method argument are:
    0 - Random Forest
    1 - SVC
    2 - Logistic Regression
    3 - K Neighbors
    
    The score, confusion matrix, precision and recall are printed.
    """
    if method == 0:
        clf = RandomForestClassifier(100)
        print 'Random Forest'
    elif method == 1:
        clf = SVC()
        print 'SVC'
    elif method == 2:
        clf = LogisticRegression()
        print 'Logistic Regression'
    else:
        clf = KNeighborsClassifier(15)
        print 'K Neighbors'
    clf.fit(X, Y)
    print 'Score: ' + str(clf.score(Xstring, Y2))
    if method == 0:
        print 'Importances: '
        importances = clf.feature_importances_
        for i in range(len(importances)):
            if (periods == 1):
                print "    " + colsOneP[i] + ": " + str(importances[i])
            elif (periods == 2):
                print "    " + colsTwoP[i] + ": " + str(importances[i])
            else:
                print "    " + colsThreeP[i] + ": " + str(importances[i])
    prediction = clf.predict(Xstring)
    bucketTotals = []
    for i in range(NUMBER_OF_RT_BUCKETS):
        bucketTotals.append(Y2.count(i))
    
    cm = confusion_matrix(Y2, prediction)
    
    precision = []
    recall = []
    fscore = []
    bucketDif = 0
    for i in range(len(bucketTotals)):
        if bucketTotals[i] == 0:
            precision.append(1)
            recall.append(1)
            bucketDif += 1
        else:
            truePositive = cm[i - bucketDif, i - bucketDif]
            testPositive = sum(cm[:, i - bucketDif])
            conditionPositive = sum(cm[i - bucketDif, :])
            if testPositive != 0:
                precision.append(truePositive / testPositive)
            else:
                precision.append(1)
            recall.append(truePositive / conditionPositive)
        if (precision[-1] + recall[-1] == 0):
            fscore.append(0)
        else:
            fscore.append(2 * ((precision[-1] * recall[-1]) / (precision[-1] + recall[-1])));
    print 'Confusion matrix: '
    print cm
    print 'Precision: ' + str(precision)
    print 'Recall: ' + str(recall)
    print 'F-score: ' + str(fscore);
    print
    
def combine_files():
    """Combines all sample files into two large training and test samples.
    
    Replaces everything necessary for the classifiers and eclat to read the 
    samples properly.
    """
    replacements = {'true':'1', 'false':'0', 'Human Interest':'Human_Interest',
                'Social Issues':'Social_Issues', 'pos':'2', 'neu':'1', 'neg':'0'}
    trainingDir = glob.glob(TRAINING_DIR + '*.txt')
    testDir = glob.glob(TEST_DIR + '*.txt')
    
    trainingFile = open(TRAINING_SAMPLE, 'w')
    for fileName in trainingDir:
        inFile = open(fileName)
        for line in inFile:
            trainingFile.write(replace_all(line, replacements))
        inFile.close()
    trainingFile.close()
    
    testFile = open(TEST_SAMPLE, 'w')
    for fileName in testDir:
        inFile = open(fileName)
        for line in inFile:
            testFile.write(replace_all(line, replacements))
        inFile.close()
    testFile.close()

combine_files()
for periods in range(1, 4):
    print 'Classifying with ' + str(periods) + ' periods'
    minimumPeriods = periods + 1
    X, Y = get_sample(TRAINING_SAMPLE, 1, minimumPeriods)
    X2, Y2 = get_sample(TEST_SAMPLE, 0, minimumPeriods)
    
    Ys = split_groups(Y)
    Y2s = split_groups(Y2)
    
    scalerY = StandardScaler().fit(Y)
    scalerX = StandardScaler().fit(X)
    
    bucketTotals = []
    for i in range(NUMBER_OF_RT_BUCKETS):
        bucketTotals.append(Y2s.count(i))
    print 'Tweets per retweet bucket: ' + str(bucketTotals)
    print
    
    for i in range(4):
        classify(scalerX.transform(X), Ys, scalerX.transform(X2), Y2s, i, periods)
        
    print
    print

X, Y = get_sample_eclat(TRAINING_SAMPLE)
X2, Y2 = get_sample_eclat(TEST_SAMPLE)

Ys = split_groups(Y)
Y2s = split_groups(Y2)

# Split and combine the samples according to retweet bucket.
x0 = []
x1 = []
x2 = []
x3 = []
for i in range(len(Ys)):
    if Ys[i] == 0:
        x0.append(X[i])
    elif Ys[i] == 1:
        x1.append(X[i])
    elif Ys[i] == 2:
        x2.append(X[i])
    else: 
        x3.append(X[i])
        
for i in range(len(Y2s)):
    if Y2s[i] == 0:
        x0.append(X2[i])
    elif Y2s[i] == 1:
        x1.append(X2[i])
    elif Y2s[i] == 2:
        x2.append(X2[i])
    else:
        x3.append(X2[i])
        
for x in X2:
    X.append(x)
        
s = 20

print 'eclat'
print 'Length = ' + str(len(X))
for r in eclat(X, target='m', report='sa', supp=s): print r
print
print 'RT = 0'
print 'Length = ' + str(len(x0))
for r in eclat(x0, target='m', report='sa', supp=s): print r
print
print 'RT <= 10'
print 'Length = ' + str(len(x1))
for r in eclat(x1, target='m', report='sa', supp=s): print r
print
print 'RT <= 50'
print 'Length = ' + str(len(x2))
for r in eclat(x2, target='m', report='sa', supp=s): print r
print
print 'RT > 50'
print 'Length = ' + str(len(x3))
for r in eclat(x3, target='m', report='sa', supp=s): print r