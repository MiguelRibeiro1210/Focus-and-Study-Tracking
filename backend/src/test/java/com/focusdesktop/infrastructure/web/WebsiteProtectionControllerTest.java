package com.focusdesktop.infrastructure.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.infrastructure.config.ApiTokenConfiguration.ApiToken;
import com.focusdesktop.infrastructure.proxy.WebsiteProtectionRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WebsiteProtectionControllerTest {
    @Test void braveSetupRequiresAuthenticationLocalOriginAndExactExplicitConfirmation() throws Exception {
        var runtime = mock(WebsiteProtectionRuntime.class);
        var mvc = MockMvcBuilders.standaloneSetup(new WebsiteProtectionController(runtime))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new LocalApiSecurityFilter(new ApiToken("test-token"), new ObjectMapper().findAndRegisterModules())).build();
        String path = "/api/v1/website-protection/brave", body = "{\"confirmation\":\"OPEN_EXISTING_BRAVE_PROFILE\"}";
        mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", "Bearer test-token").header("Origin", "https://untrusted.example")
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        for (String invalid : java.util.List.of("{}", "{\"confirmation\":true}", "{\"confirmation\":\"wrong\"}",
                "{\"confirmation\":\"OPEN_EXISTING_BRAVE_PROFILE\",\"executable\":\"untrusted\"}"))
            mvc.perform(post(path).header("Authorization", "Bearer test-token").contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(runtime);
        mvc.perform(post(path).header("Authorization", "Bearer test-token").header("Origin", "http://127.0.0.1:5173")
                .contentType("application/json").content(body)).andExpect(status().isAccepted());
        verify(runtime).requestBraveSetup();
    }
}
