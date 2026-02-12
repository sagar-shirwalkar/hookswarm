package dev.hookswarm.delivery.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.TestFixtures;
import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.exception.ResourceNotFoundException;
import dev.hookswarm.delivery.model.*;
import dev.hookswarm.delivery.service.DeliveryService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeliveryController.class)
class DeliveryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean
    DeliveryService service;

    //
    // GET /api/v1/deliveries/{id}
    //

    @Nested
    class GetTaskEndpoint {

        @Test
        void returnsTask() throws Exception {
            DeliveryTask task = TestFixtures.pendingTask();
            when(service.getTask("task_01")).thenReturn(task);

            mockMvc.perform(get("/api/v1/deliveries/task_01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("task_01"))
                    .andExpect(jsonPath("$.eventId").value("evt_01"))
                    .andExpect(jsonPath("$.subscriptionId").value("sub_01"))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        void returns404WhenNotFound() throws Exception {
            when(service.getTask("missing"))
                    .thenThrow(new ResourceNotFoundException("DeliveryTask", "missing"));

            mockMvc.perform(get("/api/v1/deliveries/missing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("missing")));
        }
    }

    //
    // GET /api/v1/deliveries?eventId= or ?subscriptionId=
    //

    @Nested
    class ListTasksEndpoint {

        @Test
        void byEventId_returnsList() throws Exception {
            DeliveryTaskResponse resp = DeliveryTaskResponse.from(TestFixtures.pendingTask());
            when(service.getTasksByEventId("evt_01")).thenReturn(List.of(resp));

            mockMvc.perform(get("/api/v1/deliveries").param("eventId", "evt_01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].eventId").value("evt_01"));
        }

        @Test
        void bySubscriptionId_returnsPagedResponse() throws Exception {
            DeliveryTaskResponse resp = DeliveryTaskResponse.from(TestFixtures.pendingTask());
            PagedResponse<DeliveryTaskResponse> paged = PagedResponse.of(
                    List.of(resp), 0, 20, 1);
            when(service.getTasksBySubscriptionId("sub_01", 0, 20)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/deliveries").param("subscriptionId", "sub_01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void noQueryParam_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/deliveries"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            containsString("eventId or subscriptionId")));
        }

        @Test
        void customPagination() throws Exception {
            PagedResponse<DeliveryTaskResponse> paged = PagedResponse.of(
                    List.of(), 2, 5, 0);
            when(service.getTasksBySubscriptionId("sub_01", 2, 5)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/deliveries")
                            .param("subscriptionId", "sub_01")
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.size").value(5));
        }
    }

    //
    // GET /api/v1/deliveries/{id}/attempts
    //

    @Nested
    class GetAttemptsEndpoint {

        @Test
        void returnsAttemptList() throws Exception {
            DeliveryAttemptResponse att1 = new DeliveryAttemptResponse(
                    "att_01", "task_01", 1, 500, "error", 120, "HTTP 500",
                    Instant.parse("2025-01-15T10:00:30Z"));
            DeliveryAttemptResponse att2 = new DeliveryAttemptResponse(
                    "att_02", "task_01", 2, 200, "{\"ok\":true}", 45, null,
                    Instant.parse("2025-01-15T10:01:00Z"));

            when(service.getAttempts("task_01")).thenReturn(List.of(att1, att2));

            mockMvc.perform(get("/api/v1/deliveries/task_01/attempts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].attemptNumber").value(1))
                    .andExpect(jsonPath("$[0].httpStatusCode").value(500))
                    .andExpect(jsonPath("$[1].attemptNumber").value(2))
                    .andExpect(jsonPath("$[1].httpStatusCode").value(200))
                    .andExpect(jsonPath("$[1].errorMessage").doesNotExist());
        }

        @Test
        void returns404WhenTaskNotFound() throws Exception {
            when(service.getAttempts("missing"))
                    .thenThrow(new ResourceNotFoundException("DeliveryTask", "missing"));

            mockMvc.perform(get("/api/v1/deliveries/missing/attempts"))
                    .andExpect(status().isNotFound());
        }
    }

    //
    // POST /api/v1/deliveries/{id}/retry
    //

    @Nested
    class RetryEndpoint {

        @Test
        void retrySucceeds_returnsResetTask() throws Exception {
            DeliveryTaskResponse response = new DeliveryTaskResponse(
                    "task_01", "evt_01", "sub_01",
                    DeliveryStatus.PENDING, 3,
                    Instant.now(), Instant.now(), Instant.now());
            when(service.retryTask("task_01")).thenReturn(response);

            mockMvc.perform(post("/api/v1/deliveries/task_01/retry"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("task_01"))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        void retryNonRetryableStatus_returns400() throws Exception {
            when(service.retryTask("task_01"))
                    .thenThrow(new IllegalStateException(
                            "Cannot retry task in status DELIVERED. Must be FAILED or DEAD."));

            mockMvc.perform(post("/api/v1/deliveries/task_01/retry"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("DELIVERED")));
        }

        @Test
        void retryNonexistentTask_returns404() throws Exception {
            when(service.retryTask("missing"))
                    .thenThrow(new ResourceNotFoundException("DeliveryTask", "missing"));

            mockMvc.perform(post("/api/v1/deliveries/missing/retry"))
                    .andExpect(status().isNotFound());
        }
    }

    //
    // GET /api/v1/dlq
    //

    @Nested
    class ListDLQEndpoint {

        @Test
        void returnsPagedDLQ() throws Exception {
            DeadLetterResponse entry = new DeadLetterResponse(
                    "dlq_01", "task_01", "evt_01", "sub_01",
                    5, "HTTP 503", Instant.parse("2025-01-15T10:00:00Z"));
            PagedResponse<DeadLetterResponse> paged = PagedResponse.of(
                    List.of(entry), 0, 20, 1);
            when(service.listDeadLetters(0, 20)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/dlq"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value("dlq_01"))
                    .andExpect(jsonPath("$.content[0].totalAttempts").value(5))
                    .andExpect(jsonPath("$.content[0].lastError").value("HTTP 503"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void emptyDLQ() throws Exception {
            PagedResponse<DeadLetterResponse> paged = PagedResponse.of(
                    List.of(), 0, 20, 0);
            when(service.listDeadLetters(0, 20)).thenReturn(paged);

            mockMvc.perform(get("/api/v1/dlq"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    //
    // POST /api/v1/dlq/{id}/replay
    //

    @Nested
    class ReplayDLQEndpoint {

        @Test
        void replaySucceeds() throws Exception {
            DeliveryTaskResponse response = new DeliveryTaskResponse(
                    "task_01", "evt_01", "sub_01",
                    DeliveryStatus.PENDING, 0,
                    Instant.now(), Instant.now(), Instant.now());
            when(service.replayDeadLetter("dlq_01")).thenReturn(response);

            mockMvc.perform(post("/api/v1/dlq/dlq_01/replay"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.attemptCount").value(0));
        }

        @Test
        void replayNonexistentDLQEntry_returns404() throws Exception {
            when(service.replayDeadLetter("missing"))
                    .thenThrow(new ResourceNotFoundException("DeadLetterEntry", "missing"));

            mockMvc.perform(post("/api/v1/dlq/missing/replay"))
                    .andExpect(status().isNotFound());
        }
    }

}