package dio.budgeting.application.output;

import java.time.LocalDate;

public record PeriodResponse(LocalDate from, LocalDate to) {
}
