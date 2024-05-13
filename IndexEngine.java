import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndexEngine {

    static ArrayList<Integer> docLengths = new ArrayList<>();
    static ArrayList<Map<String, String>> metaDataArray = new ArrayList<>();
    static boolean stem = false;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the file path for gzip");

        if (!scanner.hasNextLine()) {
            System.err.println("Error: No file path provided!");
            return;
        }

        String filePath = scanner.nextLine();

        System.out.println("Enter the folder path where the documents and meta data will be stored");

        if (!scanner.hasNextLine()) {
            System.err.println("Error: No folder path provided!");
            return;
        }
        String folderPath = scanner.nextLine();

        File directory = new File(folderPath);
        if (directory.exists()) {
            System.err.println("This directory already exists!");
            return;
        }

        System.out.println("Would you like to use Porter Stemming? (yes/no)");
        String stemResponse = scanner.nextLine();
        stem = "yes".equalsIgnoreCase(stemResponse.trim());

        getDocument(filePath, folderPath, stem);
        storeMetaData(folderPath);

    }

    public static void getDocument(String filePath, String folderPath, boolean stem) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(filePath))))) {
            String nextLine;
            String documentText = "";
            int id = 0;

            Map<String, Integer> lexicon = new HashMap<>();
            Map<Integer, String> IdToTerm = new HashMap<>();
            Map<Integer, List<List<Integer>>> invertedIndex = new HashMap<>();
            while ((nextLine = in.readLine()) != null) {
                documentText += nextLine + "\n";
                if (nextLine.contains("</DOC>")) {
                    String textFromDoc = extractTextFromTags(documentText);
                    List<String> tokens = tokenize(textFromDoc, stem);
                    docLengths.add(tokens.size());
                    List<Integer> tokenIDs = convertTokenToIDs(tokens, lexicon, IdToTerm);
                    Map<Integer, Integer> countWords = countWords(tokenIDs);
                    addToPostings(countWords, id, invertedIndex);

                    Map<String, String> mapMetaData = getMetaData(documentText, id);
                    saveDocument(documentText, folderPath, mapMetaData);
                    id++;
                    documentText = "";
                }

            }

            saveDocLength(folderPath);
            saveLexicon(lexicon, folderPath);
            saveIdToTerm(IdToTerm, folderPath);
            saveInvertedIndex(invertedIndex, folderPath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveDocument(String documentText, String folderPath, Map<String, String> mapMetaData) {

        String dateFile = mapMetaData.get("Date");
        String[] splitPartsofDate = dateFile.split("-");
        String year = splitPartsofDate[2];
        String month = splitPartsofDate[0];
        String day = splitPartsofDate[1];

        String directoryPath = folderPath + "/" + year + "/" + month + "/" + day;
        String fileName = mapMetaData.get("DOCNO");
        String pathToFile = directoryPath + "/" + fileName + ".txt";
        File file = new File(pathToFile);
        File parentDir = file.getParentFile();
        parentDir.mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToFile))) {
            writer.write("docno:" + mapMetaData.get("DOCNO"));
            writer.newLine();
            writer.write("internal id:" + mapMetaData.get("id"));
            writer.newLine();
            writer.write("date:" + mapMetaData.get("Date"));
            writer.newLine();
            writer.write("headline:" + mapMetaData.get("Headline"));
            writer.newLine();
            writer.write("raw document:");
            writer.newLine();
            writer.write(documentText);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static Map<String, String> getMetaData(String documentText, int id) {

        Map<String, String> mapMetaData = new HashMap<>();
        mapMetaData.put("id", String.valueOf(id));
        Pattern pattern = Pattern.compile("<DOCNO>(.+?)</DOCNO>");
        Matcher matcher = pattern.matcher(documentText);

        if (matcher.find()) {
            String docNumber = matcher.group(1).trim();
            mapMetaData.put("DOCNO", docNumber);

            String docNo = matcher.group(1);
            mapMetaData.put("Date",
                    docNo.substring(3, 5) + "-" + docNo.substring(5, 7) + "-19" + docNo.substring(7, 9));
        }

        Pattern pattern2 = Pattern.compile("<HEADLINE>\\s*<P>(.*?)</P>", Pattern.DOTALL);
        Matcher matcher2 = pattern2.matcher(documentText);

        if (matcher2.find()) {
            String headline = matcher2.group(1).trim();
            mapMetaData.put("Headline", headline);
        }

        metaDataArray.add(mapMetaData);
        return mapMetaData;
    }

    public static void storeMetaData(String folderPath) {
        String directoryPath = folderPath;
        String fileName = "metadata";
        String pathToFile = directoryPath + "/" + fileName + ".ser";
        File file = new File(pathToFile);
        File parentDir = file.getParentFile();
        parentDir.mkdirs();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(pathToFile))) {
            oos.writeObject(metaDataArray);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String extractTextFromTags(String documentText) {

        documentText = documentText.replace("\n", "");

        String docText = "";

        Pattern headlineTagPattern = Pattern.compile("<HEADLINE>(.*?)</HEADLINE>", Pattern.DOTALL);
        Matcher headlineTagMatcher = headlineTagPattern.matcher(documentText);

        if (headlineTagMatcher.find()) {
            docText += " " + headlineTagMatcher.group(1);
        }

        Pattern textTagPattern = Pattern.compile("<TEXT>(.*?)</TEXT>", Pattern.DOTALL);
        Matcher textTagMatcher = textTagPattern.matcher(documentText);

        if (textTagMatcher.find()) {
            docText += " " + textTagMatcher.group(1);
        }

        Pattern graphicTagPattern = Pattern.compile("<GRAPHIC>(.*?)</GRAPHIC>", Pattern.DOTALL);
        Matcher graphicTagMatcher = graphicTagPattern.matcher(documentText);

        if (graphicTagMatcher.find()) {
            docText += " " + graphicTagMatcher.group(1);
        }

        docText = docText.replaceAll("<[^>]+>", "");

        return docText;

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

    public static void saveDocLength(String folderPath) {
        serializeAndSave(docLengths, folderPath, "doc-lengths");

    }

    public static List<Integer> convertTokenToIDs(List<String> tokens, Map<String, Integer> lexicon,
            Map<Integer, String> IdToTerm) {

        List<Integer> tokenIDs = new ArrayList<>();

        for (String token : tokens) {
            if (lexicon.containsKey(token)) {
                tokenIDs.add(lexicon.get(token));
            } else {
                int id = lexicon.size();
                lexicon.put(token, id);
                IdToTerm.put(id, token);
                tokenIDs.add(id);
            }

        }
        return tokenIDs;
    }

    public static Map<Integer, Integer> countWords(List<Integer> tokenIds) {

        Map<Integer, Integer> wordCounts = new HashMap<>();

        for (int id : tokenIds) {
            if (wordCounts.containsKey(id)) {
                wordCounts.put(id, wordCounts.get(id) + 1);
            } else {
                wordCounts.put(id, 1);
            }
        }
        return wordCounts;
    }

    public static void addToPostings(Map<Integer, Integer> wordCounts, int docID,
            Map<Integer, List<List<Integer>>> invertedIndex) {
        for (Integer termID : wordCounts.keySet()) {
            int count = wordCounts.get(termID);

            List<List<Integer>> postings;
            if (invertedIndex.containsKey(termID)) {
                postings = invertedIndex.get(termID);
            } else {
                postings = new ArrayList<>();
                invertedIndex.put(termID, postings);
            }

            List<Integer> posting = new ArrayList<>();
            posting.add(docID);
            posting.add(count);

            postings.add(posting);

        }

    }

    public static void saveLexicon(Map<String, Integer> lexicon, String folderPath) {
        serializeAndSave(lexicon, folderPath, "lexicon");
    }

    public static void saveIdToTerm(Map<Integer, String> IdToTerm, String folderPath) {
        serializeAndSave(IdToTerm, folderPath, "IdToTerm");
    }

    public static void saveInvertedIndex(Map<Integer, List<List<Integer>>> invertedIndex, String folderPath) {
        serializeAndSave(invertedIndex, folderPath, "invertedIndex");
    }

    private static void serializeAndSave(Object object, String folderPath, String fileName) {
        String pathToFile = folderPath + "/" + fileName + ".ser";
        File file = new File(pathToFile);
        File parentDir = file.getParentFile();
        parentDir.mkdirs();

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(object);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

}
