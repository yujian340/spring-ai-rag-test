package com.huayi.ai.ingestion.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.huayi.ai.ingestion.util.TextRankUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.vectorstore.elasticsearch.ElasticsearchVectorStoreProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import java.io.IOException;
import java.util.Map;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.ObjectProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import reactor.core.publisher.Flux;

/**
 * Author: YuJian
 * Create: 2025-02-21 08:40
 * Description:
 */
@Service
public class IngestionService {
    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    @Value("classpath:/prompts/qa.st")
    private Resource systemResource;

    private final ElasticsearchVectorStore vectorStore;

    private final ChatClient ragClient;

    private final ElasticsearchClient elasticsearchClient;

    private final ElasticsearchVectorStoreProperties options;

    private static final String textField = "content";

    private static final String vectorField = "embedding";
    private static final String FILE_NAME = "file_name";
    private static final String FILE_TYPE = "file_type";
    private static final String DOC_KEYWORD = "doc_keyword";


    public IngestionService(
            ElasticsearchVectorStore elasticsearchVectorStore,
            ElasticsearchClient elasticsearchClient,
            ElasticsearchVectorStoreProperties options,
            ChatClient ragClient) {
        this.vectorStore = elasticsearchVectorStore;
        this.elasticsearchClient = elasticsearchClient;
        this.options = options;
        this.ragClient = ragClient;
    }

    /**
     * 通过读取、转换和存储文档到向量存储库中
     */
    public void ingest(Resource file) {
        logger.info("开始接收文档，文档：{}", file.getFilename());
        List<Document> documents = transformDocument(file);
        documents.forEach(document -> {
            document.getMetadata().put(FILE_NAME, file.getFilename());
            document.getMetadata().put(FILE_TYPE, "网络安全");
            document.getMetadata().put(DOC_KEYWORD, TextRankUtil.textRank(document.getText(), 30).toString());
        });
        logger.info("开始导入数据到 ES =================================");
        logger.info("create embedding and save to vector store");
        createIndexIfNotExists();
        vectorStore.add(documents);
        logger.info("导入数据到 ES 完成=================================");
    }

    /**
     * 创建Elasticsearch索引（如果不存在）
     * <p>
     * 方法逻辑：
     * 1. 检查索引是否已存在，存在则直接返回
     * 2. 配置索引基础设置（分片数/副本数）
     * 3. 构建索引字段映射：
     * - vectorField: 稠密向量类型，配置维度数和相似度算法
     * - textField: 文本类型字段
     * - metadata: 包含ref_doc_id关键字类型的元数据字段
     * 4. 调用Elasticsearch客户端创建索引
     * 5. 处理创建结果，失败时抛出运行时异常
     * <p>
     * 异常处理：
     * - 捕获IO异常并转换为运行时异常向上抛出
     * - 记录详细的错误日志
     */
    private void createIndexIfNotExists() {
        try {
            // 从配置对象获取索引参数
            String indexName = options.getIndexName();
            Integer dimsLength = options.getDimensions();
            String similarityAlgo = options.getSimilarity().name();

            // 检查索引是否已存在
            if (vectorStore.indexExists()) {
                logger.debug("Index {} already exists. Skipping creation.", vectorStore.getName());
                return;
            }

            // 配置索引基础设置（1分片1副本）
            IndexSettings indexSettings = IndexSettings
                    .of(settings -> settings.numberOfShards(String.valueOf(1)).numberOfReplicas(String.valueOf(1)));

            // 构建字段映射配置
            Map<String, Property> properties = new HashMap<>();
            // 向量字段配置：开启索引、设置维度、指定相似度算法
            properties.put(vectorField, Property.of(property -> property.denseVector(
                    DenseVectorProperty.of(dense -> dense.index(true).dims(dimsLength).similarity(similarityAlgo)))));
            // 文本字段基础配置
            properties.put(textField, Property.of(property -> property.text(TextProperty.of(t -> t))));

            // 构建元数据字段映射
            Map<String, Property> metadata = new HashMap<>();
            metadata.put("ref_doc_id", Property.of(property -> property.keyword(KeywordProperty.of(k -> k))));
            properties.put("metadata",
                    Property.of(property -> property.object(ObjectProperty.of(op -> op.properties(metadata)))));

            // 执行索引创建请求
            CreateIndexResponse indexResponse = elasticsearchClient.indices()
                    .create(createIndexBuilder -> createIndexBuilder.index(indexName)
                            .settings(indexSettings)
                            .mappings(TypeMapping.of(mappings -> mappings.properties(properties))));

            // 处理创建结果
            if (!indexResponse.acknowledged()) {
                throw new RuntimeException("failed to create index");
            }
            logger.info("create elasticsearch index {} successfully", indexName);
        } catch (IOException e) {
            logger.error("failed to create index", e);
            throw new RuntimeException(e);
        }
    }


    /**
     * 读取并转换文档内容
     */
    private List<Document> transformDocument(Resource file) {
        List<Document> documentText = new TikaDocumentReader(file).get();
        return TokenTextSplitter.builder()
                .withChunkSize(200)
                .withKeepSeparator(true)
                .build().apply(documentText);
    }

    public Flux<String> retrieve(String prompt) {
        String promptTemplate = getPromptTemplate(systemResource);
        List<String> keyword = TextRankUtil.textRank(prompt, 5);
        Filter.Expression expression = null;
        switch (keyword.size()) {
            case 1:
                expression = new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(0)).build();
                break;
            case 2:
                expression = new FilterExpressionBuilder().or(
                        new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(0)),
                        new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(1))
                ).build();
                break;
            case 3:
                expression = new FilterExpressionBuilder().or(
                        new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(0)),
                        new FilterExpressionBuilder().or(
                                new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(1)),
                                new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(2))
                        )
                ).build();
                break;
            case 4:
                expression = new FilterExpressionBuilder().or(
                        new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(0)),
                        new FilterExpressionBuilder().or(
                                new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(1)),
                                new FilterExpressionBuilder().or(
                                        new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(2)),
                                        new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(3))
                                )
                        )
                ).build();
                break;
            case 5:
                expression = new FilterExpressionBuilder().or(
                        new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(0)),
                        new FilterExpressionBuilder().or(
                                new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(1)),
                                new FilterExpressionBuilder().or(
                                        new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(2)),
                                        new FilterExpressionBuilder().or(
                                                new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(3)),
                                                new FilterExpressionBuilder().in(DOC_KEYWORD, keyword.get(4))
                                        )
                                )
                        )
                ).build();
                break;
            default:
                new FilterExpressionBuilder().nin(DOC_KEYWORD, "");
                break;
        }
//        logger.info(expression.toString());
        SearchRequest searchRequest = SearchRequest.builder().
                topK(4)
                .similarityThresholdAll()
                .filterExpression(expression)
                .query(prompt)
                .build();
//        List<Document> vectorStoreResult = vectorStore.similaritySearch(searchRequest);
//        String documents = vectorStoreResult.stream().map(Document::getText)
//                .collect(Collectors.joining(System.lineSeparator()));
//        logger.info(documents);
        return ragClient.prompt()
                .advisors(new QuestionAnswerAdvisor(vectorStore, searchRequest, promptTemplate))
                .user(prompt)
                .stream()
                .content();
    }

    private String getPromptTemplate(Resource systemResource) {
        try {
            logger.info("Loading system resource: {}", systemResource.getURI());
            return systemResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
