package dio.budgeting.assistant;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class AssistantHttpResponses {

    private static final MediaType AUDIO_MP3 = MediaType.parseMediaType("audio/mp3");
    private static final String AUDIO_FILE_NAME = "audio.mp3";

    private AssistantHttpResponses() {
    }

    static ResponseEntity<Resource> mp3Attachment(byte[] audio) {
        return ResponseEntity.ok()
                .contentType(AUDIO_MP3)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(AUDIO_FILE_NAME)
                                .build()
                                .toString())
                .body(new ByteArrayResource(audio));
    }
}
