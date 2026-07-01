package dio.budgeting.infraestructure.http.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantDemoControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user("hello").call().content()).thenReturn("chat-client-response");

        OpenAiChatModel openAiChatModel = mock(OpenAiChatModel.class);
        when(openAiChatModel.call("hello")).thenReturn("chat-model-response");

        TranscriptionModel transcriptionModel = mock(TranscriptionModel.class);
        when(transcriptionModel.transcribe(ArgumentMatchers.any()))
                .thenReturn("transcribed-text");

        TextToSpeechModel textToSpeechModel = mock(TextToSpeechModel.class);
        when(textToSpeechModel.call("hello world")).thenReturn("mp3-bytes".getBytes());

        mockMvc = MockMvcBuilders.standaloneSetup(
                new ChatClientController(chatClient),
                new ChatModelController(openAiChatModel),
                new TranscriptionController(transcriptionModel),
                new TextToSpeechController(textToSpeechModel)
        ).setControllerAdvice(new AssistantExceptionHandler()).build();
    }

    @Test
    void shouldKeepChatClientEndpointContract() throws Exception {
        mockMvc.perform(get("/api/chat-client").param("prompt", "hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("chat-client-response"));
    }

    @Test
    void shouldKeepChatModelEndpointContract() throws Exception {
        mockMvc.perform(get("/api/chat-model").param("prompt", "hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("chat-model-response"));
    }

    @Test
    void shouldKeepTranscriptionMultipartContract() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "audio.wav",
                "audio/wav",
                "audio".getBytes()
        );

        mockMvc.perform(multipart("/api/transcribe").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("transcribed-text"));
    }

    @Test
    void shouldKeepTextToSpeechCompatibilityContract() throws Exception {
        mockMvc.perform(post("/api/sinthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"text\": \"hello world\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mp3"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audio.mp3\""))
                .andExpect(content().bytes("mp3-bytes".getBytes()));
    }

    @Test
    void shouldRejectBlankChatPrompt() throws Exception {
        mockMvc.perform(get("/api/chat-client").param("prompt", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"error":"assistant_validation_error","message":"Prompt must not be blank"}
                        """));
    }

    @Test
    void shouldReturnBadGatewayWhenTextToSpeechFails() throws Exception {
        TextToSpeechModel failingModel = mock(TextToSpeechModel.class);
        when(failingModel.call("hello world")).thenThrow(new IllegalStateException("tts down"));

        mockMvc = MockMvcBuilders.standaloneSetup(
                new ChatClientController(mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS)),
                new ChatModelController(mock(OpenAiChatModel.class)),
                new TranscriptionController(mock(TranscriptionModel.class)),
                new TextToSpeechController(failingModel)
        ).setControllerAdvice(new AssistantExceptionHandler()).build();

        mockMvc.perform(post("/api/sinthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "hello world"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("""
                        {"error":"assistant_integration_error","message":"Assistant integration failed"}
                        """));
    }
}
