package dio.budgeting.infraestructure.ai;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface TransactionAssistant {
    ResponseEntity<Resource> transcribe(MultipartFile file);

    InterpretationResult interpret(String prompt);
}
