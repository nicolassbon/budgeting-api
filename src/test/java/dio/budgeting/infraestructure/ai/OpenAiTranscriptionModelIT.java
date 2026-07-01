package dio.budgeting.infraestructure.ai;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiTranscriptionModelIT {

    @Autowired
    OpenAiAudioTranscriptionModel openAiTranscriptionModel;

    @ParameterizedTest
    @CsvSource({
            "recording-1.mp3, 70000|setentamil",
            "recording-2.mp3, 5000|cincomil",
            "recording-3.mp3, 50000|cincuentamil",
            "recording-4.mp3, 100000|cienmil",
            "recording-5.mp3, 150000|cientocincuentamil",
            "recording-6.mp3, 30000|treintamil"
    })
    void should_containExpectedKeywords_when_audioFilesAreProcessed(String fileName, String expectedKeywords) {
        var recording = new ClassPathResource("audio/" + fileName);

        String response = openAiTranscriptionModel.call(recording);

        String normalizedResponse = response.toLowerCase().replaceAll("[^a-z0-9]", "");
        String[] keywords = expectedKeywords.split("\\|");

        assertThat(normalizedResponse).containsAnyOf(keywords);
    }
}
