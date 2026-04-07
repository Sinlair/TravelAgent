package com.travalagent.infrastructure.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TravelKnowledgeHashEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public TravelKnowledgeHashEmbeddingModel(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request == null ? List.of() : request.getInstructions();
        for (int index = 0; index < instructions.size(); index++) {
            embeddings.add(new Embedding(embedText(instructions.get(index)), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document == null ? "" : document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private float[] embedText(String value) {
        float[] vector = new float[dimensions];
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String token : normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+")) {
            if (token.isBlank()) {
                continue;
            }
            int hash = Math.abs(token.hashCode());
            int index = hash % dimensions;
            vector[index] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    private void normalize(float[] vector) {
        double sumSquares = 0.0;
        for (float value : vector) {
            sumSquares += value * value;
        }
        if (sumSquares == 0.0) {
            return;
        }
        float norm = (float) Math.sqrt(sumSquares);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
    }
}