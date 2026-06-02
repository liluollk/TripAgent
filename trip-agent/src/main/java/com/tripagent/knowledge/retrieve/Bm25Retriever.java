package com.tripagent.knowledge.retrieve;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * BM25 关键词召回器
 * <p>
 * 基于 BM25 算法的关键词匹配，捕获精确关键词匹配。
 * 例如用户问"鸭血粉丝"能精确匹配到包含该关键词的文档。
 */
@Slf4j
@Component
public class Bm25Retriever implements Retriever {

    private final VectorStore vectorStore;

    /** 已索引的文档缓存 */
    private List<Document> indexedDocuments = new ArrayList<>();

    public Bm25Retriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 设置已索引的文档列表（由 KnowledgeService 在加载完成后调用）
     */
    public void setIndexedDocuments(List<Document> documents) {
        this.indexedDocuments = new ArrayList<>(documents);
        log.debug("[BM25] 已索引 {} 个文档", indexedDocuments.size());
    }

    @Override
    public List<Document> retrieve(String query, int topK) {
        if (indexedDocuments.isEmpty()) {
            return new ArrayList<>();
        }

        // BM25 超参数
        final double k1 = 1.5;
        final double b = 0.75;

        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return new ArrayList<>();
        }

        int totalDocumentCount = indexedDocuments.size();

        double averageDocumentLength = indexedDocuments.stream()
                .mapToInt(doc -> tokenize(doc.getText()).size())
                .average()
                .orElse(1.0);

        // 预计算每个词在多少篇文档中出现
        Map<String, Integer> termDocumentFrequency = new HashMap<>();
        for (Document document : indexedDocuments) {
            List<String> docTerms = tokenize(document.getText());
            for (String term : new HashSet<>(docTerms)) {
                termDocumentFrequency.merge(term, 1, Integer::sum);
            }
        }

        // 计算每篇文档的 BM25 分数
        List<ScoredDocument> scoredDocuments = new ArrayList<>();
        for (Document document : indexedDocuments) {
            List<String> docTerms = tokenize(document.getText());
            int documentLength = docTerms.size();

            Map<String, Long> termFrequencyInDoc = new HashMap<>();
            for (String term : docTerms) {
                termFrequencyInDoc.merge(term, 1L, Long::sum);
            }

            double bm25Score = 0.0;
            for (String queryTerm : queryTerms) {
                long termFrequency = termFrequencyInDoc.getOrDefault(queryTerm, 0L);
                if (termFrequency == 0) {
                    continue;
                }

                int documentFrequency = termDocumentFrequency.getOrDefault(queryTerm, 0);
                double idf = Math.log((totalDocumentCount - documentFrequency + 0.5)
                        / (documentFrequency + 0.5) + 1.0);
                double normalizedTf = (termFrequency * (k1 + 1))
                        / (termFrequency + k1 * (1 - b + b * documentLength / averageDocumentLength));

                bm25Score += idf * normalizedTf;
            }

            if (bm25Score > 0) {
                scoredDocuments.add(new ScoredDocument(document, bm25Score));
            }
        }

        List<Document> results = scoredDocuments.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(topK)
                .map(ScoredDocument::document)
                .toList();

        log.debug("[{}] 召回 {} 个候选", name(), results.size());
        return results;
    }

    /**
     * 简单分词：按空白字符切分英文，逐字符切分中文
     */
    private List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        String[] rawTokens = text.toLowerCase().split("[\\s\\p{Punct}]+");
        for (String token : rawTokens) {
            if (token.isEmpty()) {
                continue;
            }
            boolean containsChinese = token.chars()
                    .anyMatch(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN);
            if (containsChinese) {
                for (char ch : token.toCharArray()) {
                    String charStr = String.valueOf(ch);
                    if (!charStr.isBlank()) {
                        terms.add(charStr);
                    }
                }
            } else {
                if (token.length() > 1) {
                    terms.add(token);
                }
            }
        }
        return terms;
    }

    @Override
    public String name() {
        return "BM25 关键词召回";
    }

    private record ScoredDocument(Document document, double score) {}
}
