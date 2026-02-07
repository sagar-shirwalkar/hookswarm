package dev.hookswarm.subscription.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hookswarm.TestFixtures;
import dev.hookswarm.common.PagedResponse;
import dev.hookswarm.common.exception.ResourceNotFoundException;
import dev.hookswarm.subscription.dto.SubscriptionResponse;
import dev.hookswarm.subscription.model.Subscription;
import dev.hookswarm.subscription.model.SubscriptionStatus;
import dev.hookswarm.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    SubscriptionService service;

    private static final String BASE_URL = "/api/v1/subscriptions";

    @Test
    void create_returnsCreatedWithLocation() throws Exception {
        Subscription sub = TestFixtures.subscription();
        when(service.create(any())).thenReturn(sub);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/webhook",
                                 "eventTypes": ["order.created"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/subscriptions/sub_01"))
                .andExpect(jsonPath("$.id").value("sub_01"))
                .andExpect(jsonPath("$.url").value("https://example.com/webhook"))
                .andExpect(jsonPath("$.secret").value(sub.secret())); // revealed on create
    }

    @Test
    void create_validationError_missingUrl() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.url").value("url is required"));
    }

    @Test
    void create_validationError_invalidUrl() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "not-a-url"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.url").exists());
    }

    @Test
    void create_validationError_maxRetriesTooHigh() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/hook", "maxRetries": 100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxRetries").exists());
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    void getById_returnsSubscriptionWithMaskedSecret() throws Exception {
        Subscription sub = TestFixtures.subscription();
        when(service.getById("sub_01")).thenReturn(sub);

        mockMvc.perform(get(BASE_URL + "/sub_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sub_01"))
                .andExpect(jsonPath("$.secret").value(containsString("****")));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(service.getById("missing"))
                .thenThrow(new ResourceNotFoundException("Subscription", "missing"));

        mockMvc.perform(get(BASE_URL + "/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("missing")));
    }

    @Test
    void list_returnsPagedResponse() throws Exception {
        SubscriptionResponse resp = SubscriptionResponse.from(TestFixtures.subscription());
        PagedResponse<SubscriptionResponse> paged = PagedResponse.of(
                List.of(resp), 0, 20, 1);
        when(service.list(0, 20)).thenReturn(paged);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_respectsPaginationParams() throws Exception {
        PagedResponse<SubscriptionResponse> paged = PagedResponse.of(
                List.of(), 2, 5, 0);
        when(service.list(2, 5)).thenReturn(paged);

        mockMvc.perform(get(BASE_URL + "?page=2&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void update_returnsUpdatedSubscription() throws Exception {
        Subscription updated = TestFixtures.subscription().withUpdate(
                "https://example.com/webhook",
                Set.of("order.created"),
                SubscriptionStatus.PAUSED, 5);
        when(service.update(eq("sub_01"), any())).thenReturn(updated);

        mockMvc.perform(patch(BASE_URL + "/sub_01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PAUSED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void update_validationError_invalidUrl() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/sub_01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "not-valid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.url").exists());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/sub_01"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Subscription", "missing"))
                .when(service).delete("missing");

        mockMvc.perform(delete(BASE_URL + "/missing"))
                .andExpect(status().isNotFound());
    }

}