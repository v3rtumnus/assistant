package at.altenburger.assistant.controller;

import at.altenburger.assistant.service.ChatGeneratorService;
import at.altenburger.assistant.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final ChatGeneratorService chatGeneratorService;;

    @PostMapping(value = "/chat", produces = "text/plain")
    public String promptSync(@RequestBody String clientPrompt) {
        return chatGeneratorService.generate(clientPrompt);
    }
}