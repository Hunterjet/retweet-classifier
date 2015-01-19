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
from sklearn import svm
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix

cols = ('#followers', 'isDirect', 'isMention', 'hasExclamation', 'hasHashtag', 
        'hasEmoticonNegative', 'hasEmoticonPositive', 'hasQuestion', 'hasURL', 
        'Sentiment')
minPeriods = 4
totals = [0, 0, 0, 0]
topicCount = [{}, {}, {}, {}]
groups = 4

def replace_all(text, dic):
    """Replace all occurrences of the dictionary strings in the text."""
    for i, j in dic.iteritems():
        text = text.replace(i, j)
    return text

# X = 0:AuthorsFollowers, 1:isDirect, 2:isMention, 3:isExclamation, 
# 4:isHashtag, 5:isNegativeEmoticon, 6:isPositiveEmoticon, 7:isQuestion, 
# 8:isUrl, 9-11:Sentiment OHE, 12+: History data, -1 - -7: Topic ratios
def get_sample(name, training):
    """Read a tweet sample from a file and return it as lists.
    
    The sample variables are transformed into floats if they're numbers.
    Some elements, like the retweet amounts, are transformed placed
    into buckets. The sample is returned in the form of two lists, X for 
    the independent variables and Y for the dependent variable, which is 
    the retweet amount.
    
    The elements of X are as follows:
    0:AuthorsFollowers, 1:isDirect, 2:isMention, 3:isExclamation, 
    4:hasHashtag, 5:hasNegativeEmoticon, 6:hasPositiveEmoticon, 
    7:isQuestion, 8:hasUrl, 9-11:Sentiment OHE, 12+: History data, 
    -1 - -7: Topic ratios.
    """
    reps = {'true':'1', 'false':'0', 'Human Interest':'Human_Interest', 
            'Social Issues':'Social_Issues', 'pos':'2', 'neu':'1', 'neg':'0'}
    f = open(name + ".txt")
    f2 = open('temp.txt', 'w')
    for line in f:
        f2.write(replace_all(line, reps))
    f.close()
    f2.close()
    
    skipped = 0
    
    f = open('temp.txt')
    X = []
    Y = []
    line = f.readline()
    while line != '':
        row = line.split()
        if int(row[-1]) >= minPeriods: 
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
                totals[group] += 1
                if topic in topicCount[group]:
                    topicCount[group][topic] += 1
                else:
                    topicCount[group][topic] = 1
            add = [float(x) for x in row[3:12]]
            ohe = OneHotEncoder()
            ohe.fit([[0], [1], [2]]);
            for e in ohe.transform([[row[12]]]).toarray()[0]:
                add.append(float(e))
            for _ in range(8):
                row = f.readline().split()
                for i in range(minPeriods - 1):
                    add.append(float(row[i]))
            add.append(topic)
            X.append(add)
        else:
            for _ in range(8):
                f.readline()
            skipped += 1
        line = f.readline()
    
    for x in X:
        topic = x[-1]
        if topic in topicCount[0]:
            x[-1] = topicCount[0][topic] / totals[0]
        else:
            x[-1] = 0
        for i in range(1, groups):
            if topic in topicCount[i]:
                x.append(topicCount[i][topic] / totals[i])
            else:
                x.append(0)
    
    #print 'Skipped = ' + str(skipped)
    f.close()
    return X, Y

def split_groups(Y):
    """Split the retweet amounts in Y into buckets.
    
    Bucket 0 - 0 retweets.
    Bucket 1 - 1 < retweets <= 10.
    Bucket 2 - 11 < retweets <= 50.
    Bucket 3 - More than 50 retweets.
    """
    Yc = []
    for y in Y:
        if y == 0:
            Yc.append(0)
        elif y <= 10:
            Yc.append(1)
        elif y <= 50:
            Yc.append(2)
        else:
            Yc.append(3)
    return Yc

def classify(X, Y, X2, Y2, method):
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
        clf = svm.SVC()
        print 'SVC'
    elif method == 2:
        clf = LogisticRegression()
        print 'Logistic Regression'
    else:
        clf = KNeighborsClassifier(15)
        print 'K Neighbors'
    clf.fit(X, Y)
    print 'Score: ' + str(clf.score(X2, Y2))
    prediction = clf.predict(X2)
    bucketTotals = []
    for i in range(groups):
        bucketTotals.append(Y2.count(i))
    
    cm = confusion_matrix(Y2, prediction)
    
    precision = []
    recall = []
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
            
    print 'Confusion matrix: '
    print cm
    print 'Precision: ' + str(precision)
    print 'Recall: ' + str(recall)
    print

for periods in range(1, 4):
    print
    print 'Classifying with ' + str(periods) + ' periods'
    minPeriods = periods + 1
    X, Y = get_sample('CompleteSample6', 1)
    X2, Y2 = get_sample('TestSample6', 0)
    
    Ys = split_groups(Y)
    Y2s = split_groups(Y2)
    
    scalerY = StandardScaler().fit(Y)
    scalerX = StandardScaler().fit(X)
    
    bucketTotals = []
    for i in range(groups):
        bucketTotals.append(Y2s.count(i))
    print 'Bucket totals: ' + str(bucketTotals)
    print
    
    for i in range(4):
        classify(scalerX.transform(X), Ys, scalerX.transform(X2), Y2s, i)
        
    print