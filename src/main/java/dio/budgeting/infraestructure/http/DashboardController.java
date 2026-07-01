package dio.budgeting.infraestructure.http;

import dio.budgeting.application.DashboardService;
import dio.budgeting.application.output.DashboardSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/spending")
    @ResponseStatus(HttpStatus.OK)
    public DashboardSummaryResponse spending(
            @RequestHeader(value = "Time-Zone", required = false) String timeZoneHeader) {
        return dashboardService.currentMonthSummary(timeZoneHeader);
    }
}
