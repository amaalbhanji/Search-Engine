import java.util.Scanner;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;

public class Evaluation {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter the path to the qrels file: ");
        String qrelsFilePath = scanner.nextLine();

        System.out.println("Enter the path to the results file: ");
        String resultsFilePath = scanner.nextLine();

        scanner.close();

        String studentNumber = new File(resultsFilePath).getName();
        studentNumber = studentNumber.substring(0, studentNumber.lastIndexOf('.'));
        String csvFileName = "BM25Scores.csv";

        Map<Integer, Set<String>> qrelsMap = readQrelsFile(qrelsFilePath);
        Map<Integer, List<Document>> resultsMap = readResultsFile(resultsFilePath);
        averagePrecisionScores(resultsMap, qrelsMap);
        precision10Scores(resultsMap, qrelsMap);
        ncdgAtKScores(resultsMap, qrelsMap, 10);
        ncdgAtKScores(resultsMap, qrelsMap, 1000);

        try {
            csvFile(csvFileName, resultsMap, qrelsMap);
            System.out.println("Successfully saved scores in CSV file: " + csvFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<Integer, Set<String>> readQrelsFile(String qrelsFilePath) {

        Map<Integer, Set<String>> qrelsMap = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(qrelsFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                int topicID = Integer.parseInt(parts[0]);
                String docno = parts[2];
                int judgment = Integer.parseInt(parts[3]);

                if (judgment > 0) {
                    Set<String> documentSet = qrelsMap.get(topicID);
                    if (documentSet == null) {
                        documentSet = new HashSet<>();
                        qrelsMap.put(topicID, documentSet);
                    }
                    documentSet.add(docno);

                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return qrelsMap;
    }

    static class Document {
        private String docno;
        private int rank;
        private double score;

        public Document(String docno, int rank, double score) {
            this.docno = docno;
            this.rank = rank;
            this.score = score;
        }

        public String getDocno() {
            return docno;
        }

        public int getRank() {
            return rank;
        }

        public double getScore() {
            return score;
        }

        public String toString() {
            return "{docno='" + docno + "', rank=" + rank + ", score=" + score + "}";
        }

    }

    public static Map<Integer, List<Document>> readResultsFile(String resultsFilePath) {
        Map<Integer, List<Document>> resultsMap = new HashMap<>();
        Boolean badFormat = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(resultsFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");

                if (parts.length != 6) {
                    badFormat = true;
                    break;
                }

                try {
                    int topicID = Integer.parseInt(parts[0]);
                    String docno = parts[2];
                    int rank = Integer.parseInt(parts[3]);
                    double score = Double.parseDouble(parts[4]);

                    Document document = new Document(docno, rank, score);

                    if (!resultsMap.containsKey(topicID)) {
                        resultsMap.put(topicID, new ArrayList<>());
                    }

                    resultsMap.get(topicID).add(document);
                } catch (NumberFormatException e) {
                    badFormat = true;
                    break;
                }
            }

            if (badFormat) {
                System.err.println("The results file has bad format.");
            }

            for (List<Document> documents : resultsMap.values()) {
                Collections.sort(documents, new Comparator<Document>() {
                    public int compare(Document d1, Document d2) {
                        if (d2.getScore() != d1.getScore()) {
                            return Double.compare(d2.getScore(), d1.getScore());
                        }
                        return d2.getDocno().compareTo(d1.getDocno());
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return resultsMap;
    }

    public static double calculateAveragePrecision(List<Document> retreivedDocuments, Set<String> relevantDocuments) {
        int relevant = 0;
        double sumPrecision = 0.0;
        int docProcessed = 0;
        for (int i = 0; i < retreivedDocuments.size() && i < 1000; i++) {
            Document document = retreivedDocuments.get(i);
            if (relevantDocuments.contains(document.getDocno())) {
                relevant++;
                sumPrecision += (double) relevant / (i + 1);
            }
            docProcessed++;
        }
        double averagePrecision;
        if (relevantDocuments.isEmpty()) {
            averagePrecision = 0;
        } else {
            averagePrecision = sumPrecision / relevantDocuments.size();
        }
        return averagePrecision;

    }

    public static void averagePrecisionScores(Map<Integer, List<Document>> resultsMap,
            Map<Integer, Set<String>> qrelsMap) {
        for (Integer topicID : qrelsMap.keySet()) {
            List<Document> retrievedDocs = resultsMap.getOrDefault(topicID, Collections.emptyList());
            Set<String> relevantDocs = qrelsMap.getOrDefault(topicID, Collections.emptySet());
            double averagePrecision = calculateAveragePrecision(retrievedDocs, relevantDocs);
            String formattedAveragePrecision = String.format("%.3f", averagePrecision);
            System.out.println("Topic " + topicID + " - Average Precision: " + formattedAveragePrecision);
        }
    }

    public static double calculatePrecision10(List<Document> retrievedDocuments, Set<String> relevantDocuments) {
        int relevant = 0;
        int documents = Math.min(retrievedDocuments.size(), 10);

        for (int i = 0; i < documents; i++) {
            Document document = retrievedDocuments.get(i);
            if (relevantDocuments.contains(document.getDocno())) {
                relevant++;
            }
        }

        return (double) relevant / 10;
    }

    public static void precision10Scores(Map<Integer, List<Document>> resultsMap, Map<Integer, Set<String>> qrelsMap) {
        for (Integer topicID : qrelsMap.keySet()) {
            List<Document> retrievedDocs = resultsMap.getOrDefault(topicID, Collections.emptyList());
            Set<String> relevantDocs = qrelsMap.getOrDefault(topicID, Collections.emptySet());

            double precisionAt10 = calculatePrecision10(retrievedDocs, relevantDocs);
            String formattedPrecisionAt10 = String.format("%.3f", precisionAt10);
            System.out.println("Topic " + topicID + " - Precision@10: " + formattedPrecisionAt10);
        }
    }

    public static double dcgAtK(List<Document> retrievedDocuments, Set<String> relevantDocuments, int k) {
        double dcg = 0.0;
        int documents = Math.min(retrievedDocuments.size(), k);

        for (int i = 0; i < documents; i++) {
            Document document = retrievedDocuments.get(i);

            if (relevantDocuments.contains(document.getDocno())) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));

            }
        }

        return dcg;
    }

    public static double idcgAtK(Set<String> relevantDocuments, int k) {
        double idcg = 0.0;
        int relevant = Math.min(relevantDocuments.size(), k);

        for (int i = 0; i < relevant; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        return idcg;

    }

    public static double ndcgAtKCalculation(List<Document> retrievedDocuments, Set<String> relevantDocuments, int k) {
        double dcg = dcgAtK(retrievedDocuments, relevantDocuments, k);
        double idcg = idcgAtK(relevantDocuments, k);

        if (idcg == 0) {
            return 0;
        } else {
            return dcg / idcg;
        }
    }

    public static void ncdgAtKScores(Map<Integer, List<Document>> resultsMap, Map<Integer, Set<String>> qrelsMap,
            int k) {
        for (Integer topicID : qrelsMap.keySet()) {
            List<Document> retrievedDocs = resultsMap.getOrDefault(topicID, Collections.emptyList());
            Set<String> relevantDocs = qrelsMap.getOrDefault(topicID, Collections.emptySet());

            double ndcgAtK = ndcgAtKCalculation(retrievedDocs, relevantDocs, k);
            String formattedNDCG = String.format("%.3f", ndcgAtK);
            System.out.println("Topic " + topicID + " - NDCG@" + k + ": " + formattedNDCG);
        }
    }

    public static void csvFile(String filename, Map<Integer, List<Document>> resultsMap,
            Map<Integer, Set<String>> qrelsMap) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {

            writer.println("Topic Number,Average Precision,NDCG@10,NDCG@1000,Precision@10");

            List<Integer> sortedTopicIds = new ArrayList<>(qrelsMap.keySet());
            Collections.sort(sortedTopicIds);

            for (Integer topicID : sortedTopicIds) {
                List<Document> retrievedDocs = resultsMap.getOrDefault(topicID, Collections.emptyList());
                Set<String> relevantDocs = qrelsMap.getOrDefault(topicID, Collections.emptySet());

                double averagePrecision = calculateAveragePrecision(retrievedDocs, relevantDocs);
                double ndcgAt10 = ndcgAtKCalculation(retrievedDocs, relevantDocs, 10);
                double ndcgAt1000 = ndcgAtKCalculation(retrievedDocs, relevantDocs, 1000);
                double precisionAt10 = calculatePrecision10(retrievedDocs, relevantDocs);

                String formattedAveragePrecision = String.format("%.3f", averagePrecision);
                String formattedNDCGAt10 = String.format("%.3f", ndcgAt10);
                String formattedNDCGAt1000 = String.format("%.3f", ndcgAt1000);
                String formattedPrecisionAt10 = String.format("%.3f", precisionAt10);

                writer.printf("%d,%s,%s,%s,%s\n", topicID, formattedAveragePrecision,
                        formattedNDCGAt10, formattedNDCGAt1000, formattedPrecisionAt10);
            }
        }

    }
}