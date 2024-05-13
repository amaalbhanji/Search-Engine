import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.FileInputStream;

public class BM25 {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Please input: java BM25 <index-directory> <queries-file> <output-file-name> <Stem/No>");
        }

        String indexDir = args[0];
        String queriesFile = args[1];
        String outputFile = args[2];
        String PorterStemmer = args[3];
        Boolean stem = false;

        if(PorterStemmer.equalsIgnoreCase("Stem")) {
            stem = true;
        }

        ArrayList<Integer> docLengths = loadDocLengths(indexDir + "/doc-lengths.ser");
        int totalDocs = docLengths.size();
        double averageDocLength = calculateAverageDocumentLength(docLengths);

        Map<String, Integer> lexicon = loadLexicon(indexDir + "/lexicon.ser");
        System.out.println(lexicon.size());
        Map<Integer, List<List<Integer>>> invertedIndex = loadInvertedIndex(indexDir + "/invertedIndex.ser");
        System.out.println(invertedIndex.get(0));
        Map<Integer, List<Integer>> queryMap = readAndConvertQueries(queriesFile, lexicon, stem);
        List<Map<String, String>> metaData = loadMetaData(indexDir + "/metadata.ser");
        processQueries(queryMap, invertedIndex, metaData, totalDocs, averageDocLength, outputFile, docLengths);

    }

    @SuppressWarnings("unchecked")
    public static Map<String, Integer> loadLexicon(String pathToFile) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pathToFile))) {
            return (Map<String, Integer>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<Integer, List<List<Integer>>> loadInvertedIndex(String pathToFile) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pathToFile))) {
            return (Map<Integer, List<List<Integer>>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<Integer> loadDocLengths(String pathToFile) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pathToFile))) {
            return (ArrayList<Integer>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static double calculateAverageDocumentLength(ArrayList<Integer> docLengths) {
        double totalLength = 0;
        for (int length : docLengths) {
            totalLength += length;
        }
        return totalLength / docLengths.size();
    }

    public static Map<Integer, List<Integer>> readAndConvertQueries(String fileName, Map<String, Integer> lexicon, boolean stem) {
        Map<Integer, List<Integer>> queryMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            int lineCount = 0;
            Integer currentTopicNumber = null;
            while ((line = br.readLine()) != null) {
                lineCount++;
                if (lineCount % 2 != 0) {
                    currentTopicNumber = Integer.parseInt(line.trim());
                } else {
                    List<String> tokens = tokenize(line, stem);
                    List<Integer> termIds = convertTokenToIDs(tokens, lexicon);
                    queryMap.put(currentTopicNumber, termIds);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return queryMap;
    }

    public static List<String> tokenize(String text, boolean stem) {

        List<String> tokens = new ArrayList<>();
        text = text.toLowerCase();
        int start = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (!Character.isLetterOrDigit(c)) {
                if (start != i) {
                    String token = text.substring(start, i);
                    if (stem) {
                        token = PorterStemmer.stem(token);
                    }
                    tokens.add(token);
                }

                start = i + 1;
            }
        }

        if (start != text.length()) {
            String token = text.substring(start, text.length());
            if (stem) {
                token = PorterStemmer.stem(token);
            }
            tokens.add(token);
        }

        return tokens;
    }

    public static List<Integer> convertTokenToIDs(List<String> tokens, Map<String, Integer> lexicon) {
        List<Integer> tokenIDs = new ArrayList<>();

        for (String token : tokens) {
            if (lexicon.containsKey(token)) {
                tokenIDs.add(lexicon.get(token));
            } else {
                int id = lexicon.size();
                lexicon.put(token, id);
                tokenIDs.add(id);
            }
        }

        return tokenIDs;
    }

    public static void processQueries(Map<Integer, List<Integer>> queryMap,
            Map<Integer, List<List<Integer>>> invertedIndex, List<Map<String, String>> metaData, int totalDocs,
            double averageDocLength, String outputFile, List<Integer> docLengths) {
        String outputText = "";
        ArrayList<Integer> keys = new ArrayList<Integer>(queryMap.keySet());
        Collections.sort(keys);
        for (Integer queryId : keys) {
            List<Integer> termIds = queryMap.get(queryId);
            Map<Integer, Double> docScores = new HashMap<>();

            for (Integer termId : termIds) {
                List<List<Integer>> postingsList = invertedIndex.get(termId);
                if (postingsList != null) {
                    for (List<Integer> posting : postingsList) {
                        int docId = posting.get(0);
                        int tf = posting.get(1);
                        int df = postingsList.size();
                        int docLength = docLengths.get(docId);
                        double score = calculateBM25Score(tf, df, docLength, totalDocs, averageDocLength);
                        docScores.put(docId, docScores.getOrDefault(docId, 0.0) + score);
                    }
                }
            }

            List<Map.Entry<Integer, Double>> sortedDocs = new ArrayList<>(docScores.entrySet());
            sortedDocs.sort(Map.Entry.<Integer, Double>comparingByValue().reversed());

            int rankCounter = 1;
            for (Map.Entry<Integer, Double> entry : sortedDocs) {
                if (rankCounter <= 1000) {
                    outputText += formatBM25Output(queryId, Collections.singletonList(entry ), metaData, rankCounter);
                    rankCounter++;
                } else {
                    break;
                }
            }
        }
        saveOutput(outputText, outputFile);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> loadMetaData(String pathToFile) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pathToFile))) {
            return (List<Map<String, String>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static double calculateBM25Score(int tf, int df, int docLength, int totalDocs, double averageDocLength) {
        double k1 = 1.2;
        double b = 0.75;
        double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5));
        double tfComponent = tf / (tf + k1 * (1 - b + b * docLength / averageDocLength));
        return idf * tfComponent;
    }

    public static String formatBM25Output(Integer queryId, List<Map.Entry<Integer, Double>> sortedDocs,
            List<Map<String, String>> metaData, int rank) {
        String output = "";
        int count = 0;
        for (Map.Entry<Integer, Double> entry : sortedDocs) {
            int docId = entry.getKey();
            double score = entry.getValue();
            String docno = getDocNoFromMetaData(docId, metaData);
            output += queryId + " Q0 " + docno + " " + rank + " " + score + " a7bhanjiBM25\n";
            count++;
            if(count >= 1000) {
                break;
            }
        }
        return output;
    }

    public static String getDocNoFromMetaData(int docid, List<Map<String, String>> metaData) {
        for (Map<String, String> docMetaData : metaData) {
            if (docMetaData.get("id").equals(String.valueOf(docid))) {
                return docMetaData.get("DOCNO");
            }
        }
        return null;
    }

    public static void saveOutput(String outputText, String outputFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(outputText);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
