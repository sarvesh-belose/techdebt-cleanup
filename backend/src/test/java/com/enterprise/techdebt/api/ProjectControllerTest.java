package com.enterprise.techdebt.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProjectControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void registerListUpdateArchiveLifecycle() throws Exception {
        String createBody = """
                {
                  "name": "payments-svc",
                  "description": "Core payments microservice",
                  "azureRepoUrl": "https://dev.azure.com/acme/banking/_git/payments-svc",
                  "defaultBranch": "main"
                }
                """;

        MvcResult created = mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("payments-svc"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.autoModeEnabled").value(false))
                .andReturn();

        JsonNode createdNode = mapper.readTree(created.getResponse().getContentAsString());
        long id = createdNode.get("id").asLong();

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("payments-svc"));

        String update = """
                { "description": "Updated desc", "autoModeEnabled": true }
                """;
        mvc.perform(put("/api/projects/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated desc"))
                .andExpect(jsonPath("$.autoModeEnabled").value(true));

        mvc.perform(post("/api/projects/" + id + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        mvc.perform(get("/api/projects"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/projects").param("includeArchived", "true"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(post("/api/projects/" + id + "/unarchive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void registerRejectsInvalidRepoUrl() throws Exception {
        String bad = """
                { "name": "bad", "azureRepoUrl": "not-a-url" }
                """;
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.azureRepoUrl").exists());
        assertThat(true).isTrue();
    }
}
