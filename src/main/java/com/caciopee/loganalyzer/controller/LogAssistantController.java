package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.AssistantAnswerResponse;
import com.caciopee.loganalyzer.dto.AssistantQuestionRequest;
import com.caciopee.loganalyzer.service.LogAssistantService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assistant")
public class LogAssistantController {

    private final LogAssistantService logAssistantService;

    public LogAssistantController(LogAssistantService logAssistantService) {
        this.logAssistantService = logAssistantService;
    }

    @PostMapping("/ask")
    public AssistantAnswerResponse ask(@RequestBody AssistantQuestionRequest request) {
        return logAssistantService.ask(request.getQuestion());
    }
}