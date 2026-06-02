package com.tripagent.knowledge.retrieve;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量语义召回器
 * <p>
 * 基于 EmbeddingModel 余弦相似度，捕获语义层面的相关性。
 * 例如用户问"有什么好玩的"也能匹配到"景点推荐"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticRetriever implements Retriever {

    private final VectorStore vectorStore;

    @Override
    public List<Document> retrieve(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        log.debug("[{}] 召回 {} 个候选", name(), results.size());
        return results;
    }

    @Override
    public String name() {
        return "向量语义召回";
    }
}
