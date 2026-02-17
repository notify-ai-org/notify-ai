package com.example.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.agent.AgentOrchestrator;
import com.example.agent.MemoryPageRepository;
import com.example.agent.MemoryPageRepository.SearchResult;
import com.example.agent.config.AgentRegistry;
import com.example.agent.enums.PageType;
import com.example.agent.records.*;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Mock
    private AgentOrchestrator orchestrator;

    @Mock
    private AgentRegistry agentRegistry;

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
                embeddingService,
                orchestrator,
                agentRegistry);

        testFact = new Fact(
                "fact-1",
                "tenant-1",
                "correlation-1",
                "Test fact sentence",
                PageType.SEMANTIC,
                Instant.now(),
                0.9,
                0.8,
                List.of("tag1"),
                "correlation-1");

        testPage = new MemoryPage(
                "page-1",
                "tenant-1",
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
        when(pageRepo.findOpenPage(anyString(),any()))
                .thenReturn(Optional.of(any(MemoryPage.class)));
        when(embeddingService.embed(any(MemoryPage.class)))
                .thenReturn(Mono.just(new float[] { 0.1f, 0.2f, 0.3f }));

        // Act
        memoryAssembler.appendFact(testPage,testFact);

        // Assert
        verify(pageRepo).findOpenPage(eq("tenant-1"), any(Instant.class));
        verify(pageRepo, times(2)).upsert(any(MemoryPage.class),any(Duration.class));
        verify(embeddingService).embed(any(MemoryPage.class));
    }

    @Test
    void testAppendFact_shouldUpdateExistingPage() {
        // Arrange
        when(pageRepo.findOpenPages(anyString(), anyString(), any(), any()))
                .thenReturn(List.of(testPage));
        when(pageRepo.upsert(any(MemoryPage.class))).thenReturn(testPage);
        when(embeddingService.embed(any(MemoryPage.class)))
                .thenReturn(Mono.just(new float[] { 0.1f, 0.2f, 0.3f }));

        // Mock the summarization agent response
        when(agentRegistry.get(AgentRegistry.MEMORY_SUMMARIZER_AGENT_ID))
                .thenReturn("summarizer-id");
        when(orchestrator.executeAgent(anyString(), any(), any()))
                .thenReturn(Mono.just(Content.fromParts(
                        Part.fromText("{\"summary\": \"Updated summary\"}"))));

        // Act
        memoryAssembler.appendFact(testFact);

        // Assert
        verify(pageRepo).findOpenPages(eq("tenant-1"), eq("correlation-1"), any(), any());
        ArgumentCaptor<MemoryPage> pageCaptor = ArgumentCaptor.forClass(MemoryPage.class);
        verify(pageRepo, atLeastOnce()).upsert(pageCaptor.capture());
        verify(embeddingService).embed(any(MemoryPage.class));
    }

    @Test
    void testAppendFact_shouldGenerateEmbedding() {
        // Arrange
        float[] expectedEmbedding = new float[] { 0.5f, 0.6f, 0.7f };
        when(pageRepo.findOpenPages(anyString(), anyString(), any(), any()))
                .thenReturn(List.of(testPage));
        when(pageRepo.upsert(any(MemoryPage.class))).thenReturn(testPage);
        when(embeddingService.embed(any(MemoryPage.class)))
                .thenReturn(Mono.just(expectedEmbedding));

        when(agentRegistry.get(AgentRegistry.MEMORY_SUMMARIZER_AGENT_ID))
                .thenReturn("summarizer-id");
        when(orchestrator.executeAgent(anyString(), any(), any()))
                .thenReturn(Mono.just(Content.fromParts(
                        Part.fromText("{\"summary\": \"Updated summary\"}"))));

        // Act
        memoryAssembler.appendFact(testFact);

        // Assert
        ArgumentCaptor<MemoryPage> pageCaptor = ArgumentCaptor.forClass(MemoryPage.class);
        verify(pageRepo, atLeastOnce()).upsert(pageCaptor.capture());
        MemoryPage savedPage = pageCaptor.getValue();
        assertArrayEquals(expectedEmbedding, savedPage.embedding());
    }

    @Test
    void testSearch_shouldReturnTopKResults() {
        // Arrange
        int k = 5;
        String queryText = "test query";
        float[] queryVector = new float[] { 0.1f, 0.2f, 0.3f };

        EmbeddingResult embeddingResult = new EmbeddingResult(
                "req-1", queryVector, "model-1", "v1", "hash-1");

        List<SearchResult> searchResults = List.of(
                new SearchResult(testPage, 0.1f),
                new SearchResult(testPage, 0.2f));

        when(embeddingService.embedOne(any(EmbeddingRequest.class)))
                .thenReturn(Mono.just(embeddingResult));
        when(pageRepo.knnSearch(anyString(), any(float[].class), anyInt(), any(), any()))
                .thenReturn(searchResults);

        // Act
        List<VectorCandidate> results = memoryAssembler.search(
                "tenant-1",
                queryText,
                k,
                List.of(new EntityRef("user", "user-1")));

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        verify(embeddingService).embedOne(any(EmbeddingRequest.class));
        verify(pageRepo).knnSearch(eq("tenant-1"), eq(queryVector), eq(k), any(), any());
    }

    @Test
    void testSearch_shouldHandleEmptyResults() {
        // Arrange
        EmbeddingResult embeddingResult = new EmbeddingResult(
                "req-1", new float[] { 0.1f, 0.2f, 0.3f }, "model-1", "v1", "hash-1");

        when(embeddingService.embedOne(any(EmbeddingRequest.class)))
                .thenReturn(Mono.just(embeddingResult));
        when(pageRepo.knnSearch(anyString(), any(float[].class), anyInt(), any(), any()))
                .thenReturn(List.of());

        // Act
        List<VectorCandidate> results = memoryAssembler.search(
                "tenant-1",
                "test query",
                5,
                List.of());

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearch_shouldConvertDistanceToSimilarity() {
        // Arrange
        float distance = 0.3f;
        float expectedSimilarity = 1.0f - distance;

        EmbeddingResult embeddingResult = new EmbeddingResult(
                "req-1", new float[] { 0.1f, 0.2f, 0.3f }, "model-1", "v1", "hash-1");

        List<SearchResult> searchResults = List.of(
                new SearchResult(testPage, distance));

        when(embeddingService.embedOne(any(EmbeddingRequest.class)))
                .thenReturn(Mono.just(embeddingResult));
        when(pageRepo.knnSearch(anyString(), any(float[].class), anyInt(), any(), any()))
                .thenReturn(searchResults);

        // Act
        List<VectorCandidate> results = memoryAssembler.search(
                "tenant-1",
                "test query",
                5,
                List.of());

        // Assert
        assertEquals(1, results.size());
        assertEquals(expectedSimilarity, results.get(0).similarity(), 0.001);
    }

    @Test
    void testAppendFact_whenEmbeddingServiceFails_shouldHandleGracefully() {
        // Arrange
        when(pageRepo.findOpenPage(anyString(), anyString(), any(), any()))
                .thenReturn(List.of(testPage));
        when(embeddingService.embed(any(MemoryPage.class)))
                .thenReturn(Mono.error(new RuntimeException("Embedding service error")));

        when(agentRegistry.get(AgentRegistry.MEMORY_SUMMARIZER_AGENT_ID))
                .thenReturn("summarizer-id");
        when(orchestrator.executeAgent(anyString(), any(), any()))
                .thenReturn(Mono.just(Content.fromParts(
                        Part.fromText("{\"summary\": \"Updated summary\"}"))));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            memoryAssembler.appendFact(testFact);
        });
    }

    @Test
    void testSearch_whenEmbeddingServiceReturnsNull_shouldReturnEmptyList() {
        // Arrange
        when(embeddingService.embedOne(any(EmbeddingRequest.class)))
                .thenReturn(Mono.just(new EmbeddingResult("req-1", null, "model-1", "v1", "hash-1")));

        // Act
        List<VectorCandidate> results = memoryAssembler.search(
                "tenant-1",
                "test query",
                5,
                List.of());

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(pageRepo, never()).knnSearch(anyString(), any(), anyInt(), any(), any());
    }
}
