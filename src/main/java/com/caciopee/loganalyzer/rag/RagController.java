package com.caciopee.loganalyzer.rag;

import com.caciopee.loganalyzer.rag.dto.RagAnswerDto;
import com.caciopee.loganalyzer.rag.dto.RagAskRequest;
import com.caciopee.loganalyzer.rag.dto.RagIndexStatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API du module RAG.
 *
 * <ul>
 *   <li>POST /rag/index/{importId}       → lance l'indexation IA d'un import</li>
 *   <li>GET  /rag/index/{importId}/status → progression de l'indexation</li>
 *   <li>DELETE /rag/index/{importId}      → supprime l'index d'un import</li>
 *   <li>POST /rag/ask                     → pose une question sur un import (réponse ancrée)</li>
 * </ul>
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    private final LogEmbeddingIndexService indexService;
    private final RagChatService chatService;

    public RagController(LogEmbeddingIndexService indexService, RagChatService chatService) {
        this.indexService = indexService;
        this.chatService = chatService;
    }

    @PostMapping("/index/{importId}")
    public RagIndexStatusDto index(@PathVariable Long importId,
                                   @RequestParam(defaultValue = "false") boolean reindex) {
        return indexService.startIndexing(importId, reindex);
    }

    @GetMapping("/index/{importId}/status")
    public RagIndexStatusDto status(@PathVariable Long importId) {
        return indexService.getStatus(importId);
    }

    @DeleteMapping("/index/{importId}")
    public ResponseEntity<Void> delete(@PathVariable Long importId) {
        indexService.deleteIndex(importId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ask")
    public RagAnswerDto ask(@RequestBody RagAskRequest request) {
        return chatService.ask(request);
    }
}
