package com.huayi.ai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.autoconfigure.vectorstore.elasticsearch.ElasticsearchVectorStoreProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Author: YuJian
 * Create: 2025-02-21 14:19
 * Description:
 */
@Configuration
public class RAGConfiguration {

    @Bean
    public ElasticsearchVectorStore elasticsearchVectorStore(OllamaEmbeddingModel ollamaEmbeddingModel, RestClient restClient, ElasticsearchVectorStoreProperties properties) {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(properties.getIndexName());
        options.setDimensions(properties.getDimensions());
        options.setSimilarity(properties.getSimilarity());
        return ElasticsearchVectorStore.builder(restClient, ollamaEmbeddingModel).options(options).build();
    }

    @Bean
    public SimpleVectorStore simpleVectorStore(OllamaEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchVectorStore elasticsearchVectorStore) {
        Optional<ElasticsearchClient> nativeClient = elasticsearchVectorStore.getNativeClient();
        return nativeClient.get();
    }

    @Bean
    public ChatClient ragClient(OllamaChatModel ollamaChatModel, ElasticsearchVectorStore elasticsearchVectorStore) {
        return ChatClient.builder(ollamaChatModel).defaultSystem(
                        """
                                你是一家名叫“xx信息科技”的知识库助手。
                                你会严格依据给定文档信息而不是已有的知识来回复用户问题。
                                如果答案不在文档信息中，你会准确的通知用户未找到匹配的内容。
                                """
                )
                .defaultAdvisors((new QuestionAnswerAdvisor(elasticsearchVectorStore)))
                .build();
    }
}
