package com.redis.vl.extensions.summarization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbeddedSentenceTest {

  @Test
  void indexIsPreserved() {
    EmbeddedSentence s = new EmbeddedSentence(3, new float[] {1.0f, 0.0f});
    assertThat(s.index()).isEqualTo(3);
  }

  @Test
  void getPointReturnsSameDimensionality() {
    float[] embedding = {1.0f, 2.0f, 3.0f};
    EmbeddedSentence s = new EmbeddedSentence(0, embedding);
    assertThat(s.getPoint()).hasSize(3);
  }

  @Test
  void cosineSimilarityOfIdenticalVectorsIsOne() {
    float[] v = {1.0f, 0.0f, 0.0f};
    EmbeddedSentence a = new EmbeddedSentence(0, v);
    EmbeddedSentence b = new EmbeddedSentence(1, v);
    assertThat(a.cosineSimilarity(b)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  void cosineSimilarityOfOrthogonalVectorsIsZero() {
    EmbeddedSentence a = new EmbeddedSentence(0, new float[] {1.0f, 0.0f});
    EmbeddedSentence b = new EmbeddedSentence(1, new float[] {0.0f, 1.0f});
    assertThat(a.cosineSimilarity(b)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  void cosineSimilarityOfOppositeVectorsIsMinusOne() {
    EmbeddedSentence a = new EmbeddedSentence(0, new float[] {1.0f, 0.0f});
    EmbeddedSentence b = new EmbeddedSentence(1, new float[] {-1.0f, 0.0f});
    assertThat(a.cosineSimilarity(b)).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  void cosineSimilarityOfZeroVectorIsZero() {
    EmbeddedSentence a = new EmbeddedSentence(0, new float[] {1.0f, 0.0f});
    EmbeddedSentence zero = new EmbeddedSentence(1, new float[] {0.0f, 0.0f});
    assertThat(a.cosineSimilarity(zero)).isEqualTo(0.0);
  }
}
