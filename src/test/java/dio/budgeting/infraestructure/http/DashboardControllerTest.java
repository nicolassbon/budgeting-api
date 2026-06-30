package dio.budgeting.infraestructure.http;

import dio.budgeting.application.DashboardService;
import dio.budgeting.application.output.CategoryTotalResponse;
import dio.budgeting.application.output.DashboardSummaryResponse;
import dio.budgeting.application.output.PeriodResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    @Test
    void shouldReturnCurrentMonthSummaryForAuthenticatedUser() throws Exception {
        DashboardService service = mock(DashboardService.class);
        when(service.currentMonthSummary()).thenReturn(new DashboardSummaryResponse(
                new PeriodResponse(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
                5750L,
                5750.0,
                3L,
                List.of(
                        new CategoryTotalResponse("COMIDA", 3500L, 3500.0, 2L),
                        new CategoryTotalResponse("TRANSPORTE", 2250L, 2250.0, 1L)
                )
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();

        mockMvc.perform(get("/dashboard/spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountCents").value(5750))
                .andExpect(jsonPath("$.totalAmount").value(5750.0))
                .andExpect(jsonPath("$.transactionCount").value(3))
                .andExpect(jsonPath("$.period.from").value("2026-03-01"))
                .andExpect(jsonPath("$.period.to").value("2026-04-01"))
                .andExpect(jsonPath("$.topCategories[0].category").value("COMIDA"))
                .andExpect(jsonPath("$.topCategories[0].totalAmountCents").value(3500))
                .andExpect(jsonPath("$.topCategories[0].totalAmount").value(3500.0))
                .andExpect(jsonPath("$.topCategories[0].transactionCount").value(2))
                .andExpect(jsonPath("$.topCategories[1].category").value("TRANSPORTE"));
    }

    @Test
    void shouldReturnEmptySummaryWithoutTopCategories() throws Exception {
        DashboardService service = mock(DashboardService.class);
        when(service.currentMonthSummary()).thenReturn(new DashboardSummaryResponse(
                new PeriodResponse(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
                0L,
                0.0,
                0L,
                List.of()
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();

        mockMvc.perform(get("/dashboard/spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountCents").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0.0))
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.topCategories").isArray())
                .andExpect(jsonPath("$.topCategories").isEmpty());
    }

    @Test
    void shouldNotExposeSavingsOrGoalFieldsInDashboardSummary() throws Exception {
        DashboardService service = mock(DashboardService.class);
        when(service.currentMonthSummary()).thenReturn(new DashboardSummaryResponse(
                new PeriodResponse(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
                5750L,
                5750.0,
                3L,
                List.of(
                        new CategoryTotalResponse("COMIDA", 3500L, 3500.0, 2L)
                )
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();

        mockMvc.perform(get("/dashboard/spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings").doesNotExist())
                .andExpect(jsonPath("$.savingsCents").doesNotExist())
                .andExpect(jsonPath("$.goals").doesNotExist())
                .andExpect(jsonPath("$.goalAmountCents").doesNotExist())
                .andExpect(jsonPath("$.AHORRO").doesNotExist());
    }
}
