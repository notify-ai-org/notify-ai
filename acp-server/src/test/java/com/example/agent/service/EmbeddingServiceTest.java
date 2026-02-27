package com.example.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.agent.enums.PageType;
import com.example.agent.interfaces.EmbeddingCache;
import com.example.agent.interfaces.EmbeddingProvider;
import com.example.agent.records.EmbeddingResult;
import com.example.agent.records.EntityRef;
import com.example.agent.records.MemoryPage;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for EmbeddingService.
 *
 * Note: {@code embedOne()} pushes requests onto an internal reactive Sink for
 * batching. Because {@code @PostConstruct init()} is not invoked in plain
 * Mockito tests, the batching loop never starts and {@code embedOne()} cannot
 * be tested in isolation here. Tests that exercise the provider and cache
 * interaction go through {@code embed(MemoryPage)}, which calls
 * {@code tryModel → embedOne} internally and therefore exercises the full
 * cache-miss → provider → cache-put path.
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

        // -----------------------------------------------------------------------
        // embed(MemoryPage) tests — exercises cache + provider via tryModel
        // -----------------------------------------------------------------------

        @Test
        void testEmbed_shouldReturnCachedVectorOnCacheHit() {
                // Arrange
                MemoryPage page = buildTestPage("tenant-1", "namespace-1", "page-1", "Test summary");

                // The text built by EmbeddingService.build(page) determines the hash
                String builtText = embeddingService.build(page);
                String textHash = EmbeddingService.sha256(builtText);

                float[] cachedVector = new float[] { 0.5f, 0.6f, 0.7f };

                // First model tried is "text-embedding-3-large"
                when(embeddingCache.get("text-embedding-3-large", null, textHash))
                                .thenReturn(Mono.just(cachedVector));

                // Act & Assert
                StepVerifier.create(embeddingService.embed(page))
                                .expectNextMatches(vector -> {
                                        assertArrayEquals(cachedVector, vector);
                                        return true;
                                })
                                .verifyComplete();

                // Provider should never be called on a cache hit
                verify(embeddingProvider, never()).embedBatch(anyString(), anyList(), anyList());
        }

        @Test
        void testEmbed_shouldCallProviderOnCacheMiss() {
                // Arrange
                MemoryPage page = buildTestPage("tenant-1", "namespace-1", "page-1", "Test summary");

                String builtText = embeddingService.build(page);
                String textHash = EmbeddingService.sha256(builtText);

                float[] expectedVector = new float[] { 0.1f, 0.2f, 0.3f };
                EmbeddingResult providerResult = new EmbeddingResult(
                                "text-embedding-3-large", textHash, expectedVector);

                // Cache miss for the first model
                when(embeddingCache.get("text-embedding-3-large", null, textHash))
                                .thenReturn(Mono.empty());

                // Provider returns a result
                when(embeddingProvider.embedBatch(eq("text-embedding-3-large"), anyList(), anyList()))
                                .thenReturn(Mono.just(List.of(providerResult)));

                // Cache put succeeds
                when(embeddingCache.put(eq("text-embedding-3-large"), isNull(),
                                eq(textHash), eq(expectedVector), any()))
                                .thenReturn(Mono.empty());

                // Act & Assert
                StepVerifier.create(embeddingService.embed(page))
                                .expectNextMatches(vector -> {
                                        assertArrayEquals(expectedVector, vector);
                                        return true;
                                })
                                .verifyComplete();

                verify(embeddingProvider).embedBatch(eq("text-embedding-3-large"), anyList(), anyList());
                verify(embeddingCache).put(eq("text-embedding-3-large"), isNull(),
                                eq(textHash), eq(expectedVector), any());
        }

        @Test
        void testEmbed_shouldFallbackToSecondModelWhenFirstFails() {
                // Arrange
                MemoryPage page = buildTestPage("tenant-1", "namespace-1", "page-1", "Test summary");

                String builtText = embeddingService.build(page);
                String textHash = EmbeddingService.sha256(builtText);

                float[] expectedVector = new float[] { 0.4f, 0.5f, 0.6f };
                EmbeddingResult providerResult = new EmbeddingResult(
                                "text-embedding-3-small", textHash, expectedVector);

                // Cache miss for both models
                when(embeddingCache.get(anyString(), any(), eq(textHash)))
                                .thenReturn(Mono.empty());

                // First model fails
                when(embeddingProvider.embedBatch(eq("text-embedding-3-large"), anyList(), anyList()))
                                .thenReturn(Mono.error(new RuntimeException("Model unavailable")));

                // Second model succeeds
                when(embeddingProvider.embedBatch(eq("text-embedding-3-small"), anyList(), anyList()))
                                .thenReturn(Mono.just(List.of(providerResult)));

                // Cache put succeeds
                when(embeddingCache.put(eq("text-embedding-3-small"), isNull(),
                                eq(textHash), eq(expectedVector), any()))
                                .thenReturn(Mono.empty());

                // Act & Assert
                StepVerifier.create(embeddingService.embed(page))
                                .expectNextMatches(vector -> {
                                        assertArrayEquals(expectedVector, vector);
                                        return true;
                                })
                                .verifyComplete();
        }

        @Test
        void testEmbed_shouldFailWhenAllModelsFail() {
                // Arrange
                MemoryPage page = buildTestPage("tenant-1", "namespace-1", "page-1", "Test summary");

                String builtText = embeddingService.build(page);
                String textHash = EmbeddingService.sha256(builtText);

                // Cache miss for both models
                when(embeddingCache.get(anyString(), any(), eq(textHash)))
                                .thenReturn(Mono.empty());

                // Both models fail
                when(embeddingProvider.embedBatch(anyString(), anyList(), anyList()))
                                .thenReturn(Mono.error(new RuntimeException("Provider error")));

                // Act & Assert
                StepVerifier.create(embeddingService.embed(page))
                                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                                                throwable.getMessage().contains("All embedding models failed"))
                                .verify();
        }

        // -----------------------------------------------------------------------
        // build() tests
        // -----------------------------------------------------------------------

        @Test
        void testBuild_shouldFormatPageContent() {
                // Arrange
                MemoryPage page = buildTestPage("tenant-1", "my-ns", "page-1", "Important summary text");

                // Act
                String result = embeddingService.build(page);

                // Assert
                assertNotNull(result);
                assertTrue(result.contains("Namespace: my-ns"));
                assertTrue(result.contains("Important summary text"));
        }

        @Test
        void testBuild_shouldHandleNullSummary() {
                // Arrange
                MemoryPage page = buildTestPage("tenant-1", "my-ns", "page-1", null);

                // Act
                String result = embeddingService.build(page);

                // Assert
                assertNotNull(result);
                assertTrue(result.contains("Namespace: my-ns"));
        }

        // -----------------------------------------------------------------------
        // sha256() tests
        // -----------------------------------------------------------------------

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
        void testSha256_differentInputsProduceDifferentHashes() {
                // Act
                String hash1 = EmbeddingService.sha256("hello");
                String hash2 = EmbeddingService.sha256("world");

                // Assert
                assertNotEquals(hash1, hash2);
        }

        // -----------------------------------------------------------------------
        // Helpers
        // -----------------------------------------------------------------------

        private static MemoryPage buildTestPage(String tenantId, String namespace,
                        String pageId, String summary) {
                return new MemoryPage(
                                pageId,
                                namespace,
                                "correlation-1",
                                PageType.SEMANTIC,
                                summary,
                                null, // severityMax
                                Instant.now(), // timestamp
                                0.8, // importance
                                0.9, // confidence
                                Instant.now(), // createdAt
                                Instant.now(), // updatedAt
                                Set.of(), // tags
                                List.of(new EntityRef("user", "user-1")),
                                null, // rawRef
                                null); // embedding
        }
}
