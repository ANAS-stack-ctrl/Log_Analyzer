package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.config.AiAssistantProperties;
import com.caciopee.loganalyzer.dto.AiContextResponseDto;
import com.caciopee.loganalyzer.dto.AssistantChatRequestDto;
import com.caciopee.loganalyzer.dto.AssistantChatResponseDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantChatServiceImplTest {

    private AssistantChatServiceImpl chatService;
    private AiContextBuilderService contextBuilder;

    @BeforeEach
    void setUp() {
        contextBuilder = mock(AiContextBuilderService.class);
        LogGroupAnalysisService groupAnalysis = mock(LogGroupAnalysisService.class);
        AiAssistantProperties props = new AiAssistantProperties();
        props.setEnabled(false);
        chatService = new AssistantChatServiceImpl(contextBuilder, groupAnalysis, props, new ObjectMapper());

        AiContextResponseDto ctx = new AiContextResponseDto();
        ctx.setContext("SYNTHÈSE MÉTIER\nSession test — 3 erreurs — filtre TRCEXP");
        when(contextBuilder.buildContext(any())).thenReturn(ctx);
        when(groupAnalysis.analyzeGroup(any())).thenReturn(new GroupAnalysisResponseDto());
    }

    @Test
    void chat_localMode_whenNoApiKey() {
        AssistantChatRequestDto req = new AssistantChatRequestDto();
        req.setImportId(1L);
        req.setGroupBy("sessionId");
        req.setGroupKey("1769540103782");
        req.setUserMessage("Pourquoi des erreurs ?");

        AssistantChatResponseDto res = chatService.chat(req);

        assertEquals("LOCAL", res.getMode());
        assertFalse(res.isAiConfigured());
        assertNotNull(res.getReply());
        assertTrue(res.getReply().contains("erreur") || res.getReply().contains("diagnostic"));
    }
}
