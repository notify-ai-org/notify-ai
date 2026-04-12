package com.notify.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notify.agent.MemoryPageRepository;
import com.notify.agent.MemoryPageRepository.SearchResult;
import com.notify.agent.enums.PageType;
import com.notify.agent.records.*;

import reactor.core.publisher.Mono;

/**
 * Unit tests for DefaultMemoryAssembler
 */
@ExtendWith(MockitoExtension.class)
class DefaultMemoryAssemblerTest {

        @Mock
        private MemoryPageRepository pageRepo;

        @Mock
        private EmbeddingService embeddingService;

        private DefaultMemoryAssembler memoryAssembler;
        private Fact testFact;
        private MemoryPage testPage;

        @BeforeEach
        void setUp() {
                memoryAssembler = new DefaultMemoryAssembler(
                                Duration.ofHours(1),
                                Duration.ofMinutes(30),
                                50,
                                pageRepo,
                                embeddingService);

                testFact = new Fact(
                                "fact-1",
                                "correlation-1",
                                "Test fact sentence",
                                Instant.now(),
                                0.9,
                                0.8,
                                30,
                                List.of("source-event-1"),
                                "correlation-1");

                testPage = new MemoryPage(
                                "page-1",
                                "namespace-1",
                                "correlation-1",
                                PageType.SEMANTIC,
                                "Existing summary",
                                null,
                                Instant.now(),
                                0.8,
                                0.9,
                                Instant.now(),
                                Instant.now(),
                                Set.of("tag1"),
                                List.of(new EntityRef("user", "user-1")),
                                "raw-ref",
                                new float[] { 0.1f, 0.2f, 0.3f });
        }

        @Test
        void testAppendFact_shouldCreateNewPageWhenNoneExists() {
                // Arrange
                when(embeddingService.embed(any(MemoryPage.class)))
                                .thenReturn(Mono.just(new float[] { 0.1f, 0.2f, 0.3f }));

                // Act
                memoryAssembler.appendFact(testPage, testFact);

                // Assert
                verify(pageRepo).upsert(any(MemoryPage.class), any(Duration.class));
                verify(embeddingService).embed(any(MemoryPage.class));
        }

        @Test
        void testAppendFact_shouldUpdateExistingPage() {
                // Arrange: appendFact(MemoryPage, Fact) directly updates the page
                when(embeddingService.embed(any(MemoryPage.class)))
                                .thenReturn(Mono.just(new float[] { 0.1f, 0.2f, 0.3f }));

                // Act
                memoryAssembler.appendFact(testPage, testFact);

                // Assert
                ArgumentCaptor<MemoryPage> pageCaptor = ArgumentCaptor.forClass(MemoryPage.class);
                verify(pageRepo).upsert(pageCaptor.capture(), any(Duration.class));
                verify(embeddingService).embed(any(MemoryPage.class));

                MemoryPage savedPage = pageCaptor.getValue();
                assertNotNull(savedPage.summary());
                assertTrue(savedPage.summary().contains("Test fact sentence"));
        }

        @Test
        void testAppendFact_shouldGenerateEmbedding() {
                // Arrange
                float[] expectedEmbedding = new float[] { 0.5f, 0.6f, 0.7f };
                when(embeddingService.embed(any(MemoryPage.class)))
                                .thenReturn(Mono.just(expectedEmbedding));

                // Act
                MemoryPage result = memoryAssembler.appendFact(testPage, testFact);

                // Assert
                assertArrayEquals(expectedEmbedding, result.embedding());
                verify(pageRepo).upsert(any(MemoryPage.class), any(Duration.class));
                verify(embeddingService).embed(any(MemoryPage.class));
        }

        @Test
        void testSearch_shouldReturnTopKResults() {
                // Arrange
                int k = 5;
                String queryText = "test query";
                float[] queryVector = new float[] { 0.1f, 0.2f, 0.3f };

                EmbeddingResult embeddingResult = new EmbeddingResult(
                                "model-1", "hash-1", queryVector);

                List<SearchResult> searchResults = List.of(
                                new SearchResult(testPage, 0.1),
                                new SearchResult(testPage, 0.2));

                when(embeddingService.embedOne(any(EmbeddingRequest.class)))
                                .thenReturn(Mono.just(embeddingResult));
                when(pageRepo.knnSearch(any(float[].class), anyInt(), any(), any()))
                                .thenReturn(searchResults);

                // Act
                List<VectorCandidate> results = memoryAssembler.search(
                                queryText,
                                Set.of(PageType.SEMANTIC),
                                null,
                                k);

                // Assert
                assertNotNull(results);
                assertEquals(2, results.size());
                verify(embeddingService).embedOne(any(EmbeddingRequest.class));
                verify(pageRepo).knnSearch(eq(queryVector), eq(k), any(), any());
        }

        @Test
        void testSearch_shouldHandleEmptyResults() {
                // Arrange
                EmbeddingResult embeddingResult = new EmbeddingResult(
                                "model-1", "hash-1", new float[] { 0.1f, 0.2f, 0.3f });

                when(embeddingService.embedOne(any(EmbeddingRequest.class)))
                                .thenReturn(Mono.just(embeddingResult));
                when(pageRepo.knnSearch(any(float[].class), anyInt(), any(), any()))
                                .thenReturn(List.of());

                // Act
                List<VectorCandidate> results = memoryAssembler.search(
                                "test query",
                                Set.of(PageType.SEMANTIC),
                                null,
                                5);

                // Assert
                assertNotNull(results);
                assertTrue(results.isEmpty());
        }

        @Test
        void testSearch_shouldConvertDistanceToSimilarity() {
                // Arrange
                double distance = 0.3;
                double expectedSimilarity = 1.0 - distance;

                EmbeddingResult embeddingResult = new EmbeddingResult(
                                "model-1", "hash-1", new float[] { 0.1f, 0.2f, 0.3f });

                List<SearchResult> searchResults = List.of(
                                new SearchResult(testPage, distance));

                when(embeddingService.embedOne(any(EmbeddingRequest.class)))
                                .thenReturn(Mono.just(embeddingResult));
                when(pageRepo.knnSearch(any(float[].class), anyInt(), any(), any()))
                                .thenReturn(searchResults);

                // Act
                List<VectorCandidate> results = memoryAssembler.search(
                                "test query",
                                Set.of(PageType.SEMANTIC),
                                null,
                                5);

                // Assert
                assertEquals(1, results.size());
                assertEquals(expectedSimilarity, results.get(0).similarity(), 0.001);
        }

        @Test
        void testAppendFact_whenEmbeddingServiceFails_shouldHandleGracefully() {
                // Arrange
                when(embeddingService.embed(any(MemoryPage.class)))
                                .thenReturn(Mono.error(new RuntimeException("Embedding service error")));

                // Act & Assert
                assertThrows(RuntimeException.class, () -> {
                        memoryAssembler.appendFact(testPage, testFact);
                });
        }

        @Test
        void testSearch_whenEmbeddingServiceReturnsNull_shouldReturnEmptyList() {
                // Arrange
                when(embeddingService.embedOne(any(EmbeddingRequest.class)))
                                .thenReturn(Mono.just(new EmbeddingResult("model-1", "hash-1", null)));

                // Act
                List<VectorCandidate> results = memoryAssembler.search(
                                "test query",
                                Set.of(PageType.SEMANTIC),
                                null,
                                5);

                // Assert
                assertNotNull(results);
                assertTrue(results.isEmpty());
                verify(pageRepo, never()).knnSearch(any(), anyInt(), any(), any());
        }
}
