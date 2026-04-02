package code.with.vanilson.tenantservice.controller;

import code.with.vanilson.tenantservice.application.CreateTenantRequest;
import code.with.vanilson.tenantservice.application.TenantResponse;
import code.with.vanilson.tenantservice.application.TenantService;
import code.with.vanilson.tenantservice.application.UpdateTenantRequest;
import code.with.vanilson.tenantservice.exception.TenantAlreadyExistsException;
import code.with.vanilson.tenantservice.exception.TenantGlobalExceptionHandler;
import code.with.vanilson.tenantservice.exception.TenantNotFoundException;
import code.with.vanilson.tenantservice.exception.TenantNotOperationalException;
import code.with.vanilson.tenantservice.presentation.TenantController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TenantControllerTest — Controller Tests (MockMvc + Mockito)
 * <p>
 * Tests HTTP layer only: request parsing, response codes, JSON structure.
 * Business logic is mocked via @Mock TenantService.
 *
 * @author vamuhong
 * @version 4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantController — Controller Tests")
class TenantControllerTest {

    @Mock private TenantService   tenantService;
    @Mock private MessageSource   messageSource;

    @InjectMocks
    private TenantController tenantController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private TenantResponse sampleResponse;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc = MockMvcBuilders
                .standaloneSetup(tenantController)
                .setControllerAdvice(new TenantGlobalExceptionHandler(messageSource))
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleResponse = new TenantResponse(
                "tenant-001", "Acme Corp", "acme-corp",
                "admin@acme.com", "FREE", "ACTIVE",
                100, 1_073_741_824L,
                LocalDateTime.of(2024, 1, 1, 0, 0), null);
    }

    // -------------------------------------------------------
    @Nested @DisplayName("GET /api/v1/tenants")
    class FindAll {

        @Test @DisplayName("should return 200 with list of tenants")
        void shouldReturn200WithList() throws Exception {
            when(tenantService.findAll()).thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/v1/tenants"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].tenantId").value("tenant-001"))
                    .andExpect(jsonPath("$[0].slug").value("acme-corp"))
                    .andExpect(jsonPath("$[0].plan").value("FREE"));
        }

        @Test @DisplayName("should return 200 with empty list when no tenants exist")
        void shouldReturn200WithEmptyList() throws Exception {
            when(tenantService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/tenants"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("GET /api/v1/tenants/{tenantId}")
    class FindById {

        @Test @DisplayName("should return 200 when tenant found")
        void shouldReturn200WhenFound() throws Exception {
            when(tenantService.findByTenantId("tenant-001")).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/v1/tenants/tenant-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId").value("tenant-001"))
                    .andExpect(jsonPath("$.name").value("Acme Corp"));
        }

        @Test @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(tenantService.findByTenantId("ghost"))
                    .thenThrow(new TenantNotFoundException("tenant.not.found", "tenant.not.found"));

            mockMvc.perform(get("/api/v1/tenants/ghost"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("tenant.not.found"));
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("POST /api/v1/tenants")
    class Create {

        @Test @DisplayName("should return 201 on successful tenant creation")
        void shouldReturn201OnCreate() throws Exception {
            CreateTenantRequest req = new CreateTenantRequest(
                    "Acme Corp", "acme-corp", "admin@acme.com", "FREE");
            when(tenantService.createTenant(any())).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tenantId").value("tenant-001"))
                    .andExpect(jsonPath("$.slug").value("acme-corp"));
        }

        @Test @DisplayName("should return 409 when slug already exists")
        void shouldReturn409WhenSlugTaken() throws Exception {
            CreateTenantRequest req = new CreateTenantRequest(
                    "Acme Corp", "acme-corp", "admin@acme.com", null);
            when(tenantService.createTenant(any()))
                    .thenThrow(new TenantAlreadyExistsException("tenant.slug.already.exists",
                            "tenant.slug.already.exists"));

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("tenant.slug.already.exists"));
        }

        @Test @DisplayName("should return 400 on invalid request — missing name")
        void shouldReturn400OnMissingName() throws Exception {
            String badPayload = "{\"slug\":\"test\",\"contactEmail\":\"a@b.com\"}";

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.name").exists());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("PUT /api/v1/tenants/{tenantId}")
    class Update {

        @Test @DisplayName("should return 200 on successful update")
        void shouldReturn200OnUpdate() throws Exception {
            UpdateTenantRequest req = new UpdateTenantRequest("Updated Name", "new@acme.com");
            when(tenantService.updateTenant(eq("tenant-001"), any())).thenReturn(sampleResponse);

            mockMvc.perform(put("/api/v1/tenants/tenant-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId").value("tenant-001"));
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("PATCH /api/v1/tenants/{tenantId}/suspend")
    class Suspend {

        @Test @DisplayName("should return 204 on successful suspension")
        void shouldReturn204OnSuspend() throws Exception {
            doNothing().when(tenantService).suspendTenant("tenant-001");

            mockMvc.perform(patch("/api/v1/tenants/tenant-001/suspend"))
                    .andExpect(status().isNoContent());
        }

        @Test @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenNotFound() throws Exception {
            doThrow(new TenantNotFoundException("tenant.not.found", "tenant.not.found"))
                    .when(tenantService).suspendTenant("ghost");

            mockMvc.perform(patch("/api/v1/tenants/ghost/suspend"))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("DELETE /api/v1/tenants/{tenantId}")
    class Delete {

        @Test @DisplayName("should return 204 on successful deletion")
        void shouldReturn204OnDelete() throws Exception {
            doNothing().when(tenantService).deleteTenant("tenant-001");

            mockMvc.perform(delete("/api/v1/tenants/tenant-001"))
                    .andExpect(status().isNoContent());
        }

        @Test @DisplayName("should return 404 when tenant not found")
        void shouldReturn404WhenNotFound() throws Exception {
            doThrow(new TenantNotFoundException("tenant.not.found", "tenant.not.found"))
                    .when(tenantService).deleteTenant("ghost");

            mockMvc.perform(delete("/api/v1/tenants/ghost"))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("GET /api/v1/tenants/{tenantId}/validate")
    class Validate {

        @Test @DisplayName("should return 200 for ACTIVE tenant")
        void shouldReturn200ForActive() throws Exception {
            when(tenantService.validateTenant("tenant-001")).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/v1/tenants/tenant-001/validate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.rateLimit").value(100));
        }

        @Test @DisplayName("should return 403 for SUSPENDED tenant")
        void shouldReturn403ForSuspended() throws Exception {
            when(tenantService.validateTenant("tenant-001"))
                    .thenThrow(new TenantNotOperationalException("tenant.suspended", "tenant.suspended"));

            mockMvc.perform(get("/api/v1/tenants/tenant-001/validate"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("tenant.suspended"));
        }
    }
}
