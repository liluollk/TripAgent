package com.tripagent.knowledge.retrieve;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多路召回融合器
 * <p>
 * 接收多个 Retriever 实现，对每个 Retriever 的召回结果使用
 * RRF（Reciprocal Rank Fusion）算法进行融合排序，
 * 去重后返回综合排名最高的候选文档块。
 * <p>
 * RRF 融合公式：score(d) = Σ 1 / (k + rank_i(d))，其中 k=60 为平滑常数。
 * RRF 只看排名不看绝对分数，天然适合融合不同算法的结果。
 */
@Slf4j
@Component
public class MultiRetriever {

    /** RRF 平滑常数 */
    private static final int RRF_K = 60;

    /** 每路召回的候选数量 */
    private static final int PER_ROUTE_CANDIDATE_COUNT = 10;

    private final List<Retriever> retrievers;

    public MultiRetriever(List<Retriever> retrievers) {
        this.retrievers = retrievers;
        log.info("MultiRetriever 初始化，共 {} 路召回器: {}", retrievers.size(),
                retrievers.stream().map(Retriever::name).toList());
    }

    /**
     * 执行多路召回，返回 RRF 融合后的候选文档列表
     *
     * @param query 用户查询问题
     * @param topK  最终返回的候选文档数量
     * @return 经 RRF 融合排序后的候选文档列表
     */
    public List<Document> retrieve(String query, int topK) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, Document> keyToDocument = new LinkedHashMap<>();

        for (Retriever retriever : retrievers) {
            List<Document> results = retriever.retrieve(query, PER_ROUTE_CANDIDATE_COUNT);
            accumulateRrfScores(results, rrfScores, keyToDocument);
        }

        List<Document> mergedResults = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> keyToDocument.get(entry.getKey()))
                .toList();

        log.debug("多路召回融合后共 {} 个候选（去重后）", mergedResults.size());
        return mergedResults;
    }

    /**
     * 将一路召回结果的 RRF 分数累加到总分数表中
     */
    private void accumulateRrfScores(
            List<Document> rankedDocuments,
            Map<String, Double> rrfScores,
            Map<String, Document> keyToDocument) {

        for (int rank = 0; rank < rankedDocuments.size(); rank++) {
            Document document = rankedDocuments.get(rank);
            String uniqueKey = buildUniqueKey(document);
            double rrfContribution = 1.0 / (RRF_K + rank + 1);
            rrfScores.merge(uniqueKey, rrfContribution, Double::sum);
            keyToDocument.putIfAbsent(uniqueKey, document);
        }
    }

    /**
     * 构建文档的唯一标识
     */
    private String buildUniqueKey(Document document) {
        String source = document.getMetadata().containsKey("source")
                ? String.valueOf(document.getMetadata().get("source")) : "unknown";
        String chunkIndex = document.getMetadata().containsKey("chunkIndex")
                ? String.valueOf(document.getMetadata().get("chunkIndex")) : "0";
        return source + "::" + chunkIndex;
    }
}
