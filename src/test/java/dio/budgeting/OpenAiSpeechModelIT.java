package dio.budgeting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiSpeechModelIT {

    @Autowired
    OpenAiAudioSpeechModel openAiSpeechModel;

    @Test
    void should_produceAudio_when_textIsProvided() throws IOException {
        byte[] response = openAiSpeechModel.call("El valor total del servicio que reservó es de 80.000 pesos argentinos. Puede confirmar el pago?");

        assertThat(response).hasSizeGreaterThan(1024);

        var tempFile = Files.createTempFile("AUDIO", ".mp3");
        Files.write(tempFile, response);
        System.out.println(tempFile.toAbsolutePath());
    }
}
