package bank.pf.service;

import bank.pf.config.WireMockConfig;
import bank.pf.dto.response.AccountValidationResponse;
import bank.pf.dto.response.CpfValidationResponse;
import bank.pf.dto.response.InternalRestrictResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WireMockSetupServiceTest {

    @InjectMocks
    private WireMockSetupService wireMockSetupService;

    @Mock
    private WireMockConfig wireMockConfig;

    @Mock
    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        lenient().when(wireMockConfig.getWireMockServer()).thenReturn(wireMockServer);
        lenient().when(wireMockServer.port()).thenReturn(8090);
    }

    @Test
    void shouldSetupWireMockStubs() {
        // When
        wireMockSetupService.setup();

        // Then
        assertThat(wireMockSetupService.getBaseUrl()).isEqualTo("http://localhost:8090");
        assertThat(wireMockSetupService.getRestClient()).isNotNull();

        // Verify stubs were set up
        verify(wireMockServer, times(6)).stubFor(any());
    }

    @Test
    void shouldSetupCpfValidationStubs() {
        // Given
        ReflectionTestUtils.setField(wireMockSetupService, "baseUrl", "http://localhost:8090");
        ReflectionTestUtils.setField(wireMockSetupService, "restClient", RestClient.builder().baseUrl("http://localhost:8090").build());

        // When
        ReflectionTestUtils.invokeMethod(wireMockSetupService, "setupCpfValidationStubs", wireMockServer);

        // Then
        verify(wireMockServer, times(2)).stubFor(any());
    }

    @Test
    void shouldSetupAccountValidationStubs() {
        // Given
        ReflectionTestUtils.setField(wireMockSetupService, "baseUrl", "http://localhost:8090");
        ReflectionTestUtils.setField(wireMockSetupService, "restClient", RestClient.builder().baseUrl("http://localhost:8090").build());

        // When
        ReflectionTestUtils.invokeMethod(wireMockSetupService, "setupAccountValidationStubs", wireMockServer);

        // Then
        verify(wireMockServer, times(2)).stubFor(any());
    }

    @Test
    void shouldSetupInternalRestrictionsStubs() {
        // Given
        ReflectionTestUtils.setField(wireMockSetupService, "baseUrl", "http://localhost:8090");
        ReflectionTestUtils.setField(wireMockSetupService, "restClient", RestClient.builder().baseUrl("http://localhost:8090").build());

        // When
        ReflectionTestUtils.invokeMethod(wireMockSetupService, "setupInternalRestrictionsStubs", wireMockServer);

        // Then
        verify(wireMockServer, times(2)).stubFor(any());
    }

    @Test
    void shouldHandleJsonProcessingExceptionInCpfValidationStubs() throws JsonProcessingException {
        // Given
        ReflectionTestUtils.setField(wireMockSetupService, "baseUrl", "http://localhost:8090");
        ReflectionTestUtils.setField(wireMockSetupService, "restClient", RestClient.builder().baseUrl("http://localhost:8090").build());

        // Mock ObjectMapper to throw exception
        ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
        when(mockObjectMapper.writeValueAsString(any(CpfValidationResponse.class)))
                .thenThrow(new JsonProcessingException("Test exception") {
                });
        ReflectionTestUtils.setField(wireMockSetupService, "objectMapper", mockObjectMapper);

        // When
        ReflectionTestUtils.invokeMethod(wireMockSetupService, "setupCpfValidationStubs", wireMockServer);

        // Then
        verify(wireMockServer, never()).stubFor(any());
    }

    @Test
    void shouldHandleJsonProcessingExceptionInAccountValidationStubs() throws JsonProcessingException {
        // Given
        ReflectionTestUtils.setField(wireMockSetupService, "baseUrl", "http://localhost:8090");
        ReflectionTestUtils.setField(wireMockSetupService, "restClient", RestClient.builder().baseUrl("http://localhost:8090").build());

        // Mock ObjectMapper to throw exception
        ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
        when(mockObjectMapper.writeValueAsString(any(AccountValidationResponse.class)))
                .thenThrow(new JsonProcessingException("Test exception") {
                });
        ReflectionTestUtils.setField(wireMockSetupService, "objectMapper", mockObjectMapper);

        // When
        ReflectionTestUtils.invokeMethod(wireMockSetupService, "setupAccountValidationStubs", wireMockServer);

        // Then
        verify(wireMockServer, never()).stubFor(any());
    }

    @Test
    void shouldHandleJsonProcessingExceptionInInternalRestrictionsStubs() throws JsonProcessingException {
        // Given
        ReflectionTestUtils.setField(wireMockSetupService, "baseUrl", "http://localhost:8090");
        ReflectionTestUtils.setField(wireMockSetupService, "restClient", RestClient.builder().baseUrl("http://localhost:8090").build());

        // Mock ObjectMapper to throw exception
        ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
        when(mockObjectMapper.writeValueAsString(any(InternalRestrictResponse.class)))
                .thenThrow(new JsonProcessingException("Test exception") {
                });
        ReflectionTestUtils.setField(wireMockSetupService, "objectMapper", mockObjectMapper);

        // When
        ReflectionTestUtils.invokeMethod(wireMockSetupService, "setupInternalRestrictionsStubs", wireMockServer);

        // Then
        verify(wireMockServer, never()).stubFor(any());
    }
}
