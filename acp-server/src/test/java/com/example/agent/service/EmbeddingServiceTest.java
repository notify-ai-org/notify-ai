package com.example.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.agent.interfaces.EmbeddingCache;
import com.example.agent.interfaces.EmbeddingProvider;
import com.example.agent.records.EmbeddingRequest;
import com.example.agent.records.EmbeddingResult;
import com.example.agent.records.MemoryPage;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for EmbeddingService
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private EmbeddingCache embeddingCache;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(embeddingProvider, embeddingCache);
    }

    @Test
    void testEmbedOne_shouldReturnEmbedding() {
        // Arrange
        EmbeddingRequest request = new EmbeddingRequest(
                "tenant-1",
                "namespace-1",
                "page-1",
                "test text",
                "text-embedding-3-large",
                "v1",
                "hash-1");

        float[] expectedVector = new float[] { 0.1f, 0.2f, 0.3f };
        EmbeddingResult expectedResult = new EmbeddingResult(
                "req-1",
                "hash-1",
                expectedVector);

        when(embeddingCache.get(anyString(),anyString(),anyString(),anyString())).thenReturn(Mono.empty());
        when(embeddingProvider.embedBatch("model",anyList(),anyList())).thenReturn(Mono.just(List.of(expectedResult)));
        when(embeddingCache.put(anyString(),anyString(),anyString(),anyString(),expectedVector,any(Duration.class)))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(embeddingService.embedOne(request))
                .expectNextMatches(result -> {
                    assertNotNull(result);
                    assertArrayEquals(expectedVector, result.vector());
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void testEmbedOne_shouldUseCacheWhenAvailable() {
        // Arrange
        EmbeddingRequest request = new EmbeddingRequest(
                "tenant-1",
                "namespace-1",
                "page-1",
                "test text",
                "text-embedding-3-large",
                "v1",
                "hash-1");

        float[] cachedVector = new float[] { 0.5f, 0.6f, 0.7f };
        EmbeddingResult cachedResult = new EmbeddingResult(
                "req-1",
                "hash-1",
                cachedVector
        );

        when(embeddingCache.get(anyString(),anyString(),anyString(),anyString())).thenReturn(Mono.just(cachedResult));

        // Act & Assert
        StepVerifier.create(embeddingService.embedOne(request))
                .expectNextMatches(result -> {
                    assertArrayEquals(cachedVector, result.vector());
                    return true;
                })
                .verifyComplete();

        // Provider should not be called when cache hit
        verify(embeddingProvider, never()).embed(anyList());
    }

    @Test
    void testEmbedOne_shouldHandleProviderFailure() {
        // Arrange
        EmbeddingRequest request = new EmbeddingRequest(
                "tenant-1",
                "namespace-1",
                "page-1",
                "test text",
                "text-embedding-3-large",
                "v1",
                "hash-1");

        when(embeddingCache.get(anyString())).thenReturn(Mono.empty());
        when(embeddingProvider.embed(anyList()))
                .thenReturn(Mono.error(new RuntimeException("Provider error")));

        // Act & Assert
        StepVerifier.create(embeddingService.embedOne(request))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Provider error"))
                .verify();
    }

    @Test
    void testEmbed_shouldGenerateEmbeddingForMemoryPage() {
        // Arrange
        MemoryPage page = mock(MemoryPage.class);
        when(page.tenantId()).thenReturn("tenant-1");
        when(page.namespace()).thenReturn("namespace-1");
        when(page.pageId()).thenReturn("page-1");
        when(page.summary()).thenReturn("Test summary");

        float[] expectedVector = new float[] { 0.1f, 0.2f, 0.3f };
        EmbeddingResult expectedResult = new EmbeddingResult(
                "req-1",
                expectedVector,
                "text-embedding-3-large",
                "v1",
                EmbeddingService.sha256("Test summary"));

        when(embeddingCache.get(anyString())).thenReturn(Mono.empty());
        when(embeddingProvider.embed(anyList())).thenReturn(Mono.just(List.of(expectedResult)));
        when(embeddingCache.put(anyString(), any(EmbeddingResult.class), any(Duration.class)))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(embeddingService.embed(page))
                .expectNextMatches(vector -> {
                    assertNotNull(vector);
                    assertArrayEquals(expectedVector, vector);
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void testSha256_shouldGenerateConsistentHash() {
        // Arrange
        String text = "test text";

        // Act
        String hash1 = EmbeddingService.sha256(text);
        String hash2 = EmbeddingService.sha256(text);

        // Assert
        assertNotNull(hash1);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 produces 64 hex characters
    }

    @Test
    void testSha256_shouldHandleNullInput() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            String hash = EmbeddingService.sha256(null);
            assertNotNull(hash);
        });
    }

    @Test
    void testEmbedOne_shouldStorResultInCache() {
        // Arrange
        EmbeddingRequest request = new EmbeddingRequest(
                "tenant-1",
                "namespace-1",
                "page-1",
                "test text",
                "text-embedding-3-large",
                "v1",
                "hash-1");

        float[] vector = new float[] { 0.1f, 0.2f, 0.3f };
        EmbeddingResult result = new EmbeddingResult(
                "req-1",
                vector,
                "text-embedding-3-large",
                "v1",
                "hash-1");

        when(embeddingCache.get(anyString())).thenReturn(Mono.empty());
        when(embeddingProvider.embed(anyList())).thenReturn(Mono.just(List.of(result)));
        when(embeddingCache.put(anyString(), any(EmbeddingResult.class), any(Duration.class)))
                .thenReturn(Mono.empty());

        // Act
        embeddingService.embedOne(request).block();

        // Assert - cache put should be called
        verify(embeddingCache).put(anyString(), any(EmbeddingResult.class), any(Duration.class));
    }
}
