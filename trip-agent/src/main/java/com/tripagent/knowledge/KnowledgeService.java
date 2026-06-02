package com.tripagent.knowledge;

import com.tripagent.config.RagProperties;
import com.tripagent.knowledge.chunk.TextSplitter;
import com.tripagent.knowledge.retrieve.Bm25Retriever;
import com.tripagent.knowledge.retrieve.MultiRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识服务
 * 负责知识文档的加载、分块、存储和检索
 */
@Slf4j
@Service
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final ResourceLoader resourceLoader;
    private final TextSplitter textSplitter;
    private final MultiRetriever multiRetriever;
    private final Bm25Retriever bm25Retriever;

    public KnowledgeService(VectorStore vectorStore, RagProperties ragProperties, ResourceLoader resourceLoader,
                            TextSplitter textSplitter, MultiRetriever multiRetriever, Bm25Retriever bm25Retriever) {
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
        this.resourceLoader = resourceLoader;
        this.textSplitter = textSplitter;
        this.multiRetriever = multiRetriever;
        this.bm25Retriever = bm25Retriever;
    }

    /**
     * 应用启动时自动加载知识文档
     */
    @PostConstruct
    public void init() {
        if (!ragProperties.enabled()) {
            log.info("RAG 功能已禁用");
            return;
        }
        try {
            loadKnowledgeFile();
        } catch (Exception e) {
            log.warn("知识文档加载失败，RAG 功能不可用: {}", e.getMessage());
        }
    }

    /**
     * 加载知识文档并分块存储
     */
    public int loadKnowledgeFile() {
        Resource knowledgeFile = resourceLoader.getResource(ragProperties.knowledgeFile());
        if (!knowledgeFile.exists()) {
            log.warn("知识文件不存在: {}", knowledgeFile.getFilename());
            return 0;
        }

        try (var reader = new BufferedReader(
                new InputStreamReader(knowledgeFile.getInputStream(), StandardCharsets.UTF_8))) {

            String content = reader.lines().collect(Collectors.joining("\n"));
            String source = knowledgeFile.getFilename();

            // 使用递归语义分块器替代简单的 ## 标题分块
            List<Document> chunks = textSplitter.split(content, source);

            log.info("开始向量化 {} 个知识块...", chunks.size());
            vectorStore.add(chunks);

            // 为 BM25 召回器建立索引
            bm25Retriever.setIndexedDocuments(chunks);

            log.info("知识文档加载完成，共 {} 个分块", chunks.size());
            return chunks.size();

        } catch (Exception e) {
            log.error("加载知识文档失败", e);
            throw new RuntimeException("知识文档加载失败", e);
        }
    }

    /**
     * RAG 检索：多路召回 + RRF 融合
     */
    public List<KnowledgeResult> search(String query, int topK) {
        if (!ragProperties.enabled()) {
            return List.of();
        }

        // 使用多路召回（语义 + BM25）+ RRF 融合
        List<Document> results = multiRetriever.retrieve(query, topK);

        return results.stream()
                .map(doc -> {
                    String source = doc.getMetadata().containsKey("source")
                            ? String.valueOf(doc.getMetadata().get("source")) : "未知";
                    return new KnowledgeResult(doc.getText(), source, "攻略");
                })
                .toList();
    }

    /**
     * RAG 增强：获取查询相关的上下文信息
     */
    public String getRagContext(String query) {
        List<KnowledgeResult> results = search(query, 3);
        if (results.isEmpty()) {
            return "";
        }

        // 从元数据中获取来源城市，动态生成标题
        String sourceCity = results.stream()
                .filter(r -> r.category() != null)
                .map(r -> {
                    // 从内容中尝试提取城市名（标题通常在第一行）
                    String content = r.content();
                    if (content.startsWith("## ")) {
                        return content.substring(3).split("[\\s\\n]")[0];
                    }
                    return "本地";
                })
                .findFirst()
                .orElse("本地");

        StringBuilder context = new StringBuilder();
        context.append("【").append(sourceCity).append("攻略参考】\n\n");
        for (KnowledgeResult result : results) {
            context.append(result.content()).append("\n\n");
        }
        return context.toString();
    }

    /**
     * 知识检索结果
     */
    public record KnowledgeResult(
            String content,
            String title,
            String category
    ) {}
}
