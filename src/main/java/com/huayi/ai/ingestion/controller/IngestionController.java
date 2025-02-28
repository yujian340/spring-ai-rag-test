package com.huayi.ai.ingestion.controller;

import com.huayi.ai.ingestion.service.IngestionService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * Author: YuJian
 * Create: 2025-02-21 08:42
 * Description:
 */
@RestController
public class IngestionController {
    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/documents")
    public ResponseEntity<String> uploadDocument(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            ingestionService.ingest(createFileResource(file));
            return ResponseEntity.ok("文档上传成功，名称：" + file.getOriginalFilename());
        } catch (IOException e) {
            return handleException("文件处理失败：", e, HttpStatus.BAD_REQUEST);
        } catch (RuntimeException e) {
            return handleException("文档接收失败：", e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/query")
    public Flux<String> query(String question,
                             HttpServletResponse response
    ) {
        response.setCharacterEncoding("UTF-8");
        if (!StringUtils.hasText(question)) {
            return Flux.just("请输入您想要询问的内容");
        }
        return ingestionService.retrieve(question);
    }

    private Resource createFileResource(MultipartFile file) throws IOException {
        return new InputStreamResource(file.getInputStream()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
    }

    private ResponseEntity<String> handleException(String message, Exception e, HttpStatus status) {
        logger.error("消息: {}, 状态: {}", message, status, e);
        return ResponseEntity.status(status).body(message + e.getMessage());
    }
}
