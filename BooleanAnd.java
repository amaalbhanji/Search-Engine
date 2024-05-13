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

public class BooleanAnd {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Please input: java BooleanAND <index-directory> <queries-file> <output-file-name");
        }

        String indexDir = args[0];
        String queriesFile = args[1];
        String outputFile = args[2];

        Map<String, Integer> lexicon = loadLexicon(indexDir + "/lexicon.ser");
        Map<Integer, List<List<Integer>>> invertedIndex = loadInvertedIndex(indexDir + "/invertedIndex.ser");
        Map<Integer, List<Integer>> queryMap = readAndConvertQueries(queriesFile, lexicon);
        List<Map<String, String>> metaData = loadMetaData(indexDir + "/metadata.ser");
        processQueries(queryMap, invertedIndex, metaData, outputFile);

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

    public static Map<Integer, List<Integer>> readAndConvertQueries(String fileName, Map<String, Integer> lexicon) {
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
                    List<String> tokens = tokenize(line);
                    List<Integer> termIds = convertTokenToIDs(tokens, lexicon);
                    queryMap.put(currentTopicNumber, termIds);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return queryMap;
    }

    public static List<String> tokenize(String text) {

        List<String> tokens = new ArrayList<>();
        text = text.toLowerCase();
        int start = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (!Character.isLetterOrDigit(c)) {
                if (start != i) {
                    String token = text.substring(start, i);
                    tokens.add(token);
                }

                start = i + 1;
            }
        }

        if (start != text.length()) {
            tokens.add(text.substring(start, text.length()));
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

    public static void processQueries(Map<Integer, List<Integer>> queryMap, Map<Integer, List<List<Integer>>> invertedIndex, List<Map<String, String>> metaData, String outputFile) {
        String outputText = "";
        ArrayList<Integer> keys = new ArrayList<Integer>( queryMap.keySet());
        Collections.sort(keys);
        for (Integer key : keys) {
            Integer queryId = key;
            List<Integer> termIds = queryMap.get(key);

            List<List<List<Integer>>> queryResults = new ArrayList<>();

            for (Integer termId : termIds) {
                List<List<Integer>> postingsList = invertedIndex.get(termId);

                if (postingsList != null) {
                    queryResults.add(postingsList);
                }
            }

            outputText += BooleanAND(queryId, queryResults, metaData);

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

    public static String BooleanAND(Integer queryId, List<List<List<Integer>>> queryResults,
            List<Map<String, String>> metaData) {
        HashMap<Integer, Integer> docCount = new HashMap<>();
        for (List<List<Integer>> postingsList : queryResults) {
            for (int i = 0; i < postingsList.size(); i++) {
                int docid = postingsList.get(i).get(0);
                if (docCount.containsKey(docid)) {
                    int count = docCount.get(docid);
                    docCount.put(docid, count + 1);
                } else {
                    docCount.put(docid, 1);
                }
            }
        }

        List<Integer> resultSet = new ArrayList<>();
        for (int docid : docCount.keySet()) {
            if (docCount.get(docid) == queryResults.size()) {
                resultSet.add(docid);
            }
        }
        return formatOutput(queryId, resultSet, metaData);
    }

    public static String formatOutput(Integer queryId, List<Integer> resultSet, List<Map<String, String>> metaData) {
        String output = "";
        for (int i = 0; i < resultSet.size(); i++) {
            int docid = resultSet.get(i);
            String docno = getDocNoFromMetaData(docid, metaData); 
            output += queryId + " Q0 " + docno + " " + (i + 1) + " " + (resultSet.size() - (i + 1)) + " a7bhanjiAND\n";
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