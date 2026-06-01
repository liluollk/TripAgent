package com.tripagent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 知识服务
 * 负责知识文档的加载、分块、存储和检索
 */
@Slf4j
@Service
public class KnowledgeService {

    private final VectorStore vectorStore;

    @Value("${trip.rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${trip.rag.knowledge-file:classpath:knowledge/nanjing-local.md}")
    private Resource knowledgeFile;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 应用启动时自动加载知识文档
     */
    @PostConstruct
    public void init() {
        if (!ragEnabled) {
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
        if (!knowledgeFile.exists()) {
            log.warn("知识文件不存在: {}", knowledgeFile.getFilename());
            return 0;
        }

        try (var reader = new BufferedReader(
                new InputStreamReader(knowledgeFile.getInputStream(), StandardCharsets.UTF_8))) {

            String content = reader.lines().collect(Collectors.joining("\n"));
            List<Document> chunks = splitIntoChunks(content);

            log.info("开始向量化 {} 个知识块...", chunks.size());
            vectorStore.add(chunks);
            log.info("知识文档加载完成，共 {} 个分块", chunks.size());
            return chunks.size();

        } catch (Exception e) {
            log.error("加载知识文档失败", e);
            throw new RuntimeException("知识文档加载失败", e);
        }
    }

    /**
     * 按 ## 标题分割 Markdown 为语义块
     */
    private List<Document> splitIntoChunks(String content) {
        List<Document> chunks = new ArrayList<>();
        // 使用多行模式分割
        String[] sections = content.split("(?m)(?=^## )");

        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 跳过一级标题（文档标题）
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                continue;
            }
            // 只处理 ## 开头的段落
            if (!trimmed.startsWith("## ")) {
                continue;
            }

            // 提取标题作为元数据
            String title = extractTitle(trimmed);
            String category = classifyCategory(title);

            chunks.add(new Document(
                trimmed,
                Map.of(
                    "title", title,
                    "category", category,
                    "source", "南京本地人攻略"
                )
            ));
        }

        return chunks;
    }

    /**
     * 提取 ## 标题
     */
    private String extractTitle(String section) {
        String[] lines = section.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                return trimmed.substring(3).trim();
            }
        }
        return "未知";
    }

    /**
     * 根据标题分类
     */
    private String classifyCategory(String title) {
        if (title.contains("玩") || title.contains("景点") || title.contains("陵") || title.contains("湖") || title.contains("府")) {
            return "景点";
        }
        if (title.contains("吃") || title.contains("美食") || title.contains("鸭") || title.contains("面")) {
            return "美食";
        }
        if (title.contains("出行") || title.contains("交通") || title.contains("机场") || title.contains("地铁")) {
            return "交通";
        }
        if (title.contains("避坑") || title.contains("指南")) {
            return "避坑";
        }
        if (title.contains("时间") || title.contains("季节") || title.contains("什么时候")) {
            return "时间";
        }
        return "其他";
    }

    /**
     * RAG 检索：根据查询搜索相关知识
     */
    public List<KnowledgeResult> search(String query, int topK) {
        if (!ragEnabled) {
            return List.of();
        }

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return results.stream()
                .map(doc -> {
                    String title = doc.getMetadata().containsKey("title")
                            ? String.valueOf(doc.getMetadata().get("title")) : "未知";
                    String category = doc.getMetadata().containsKey("category")
                            ? String.valueOf(doc.getMetadata().get("category")) : "其他";
                    return new KnowledgeResult(doc.getText(), title, category);
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

        StringBuilder context = new StringBuilder();
        context.append("【南京本地人攻略参考】\n\n");
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
