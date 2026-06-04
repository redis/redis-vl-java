package com.redis.vl.extensions.summarization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SentenceSplitterTest {

  private SentenceSplitter splitter;

  @BeforeEach
  void setUp() {
    splitter = new SentenceSplitter();
  }

  @Test
  void splitSimpleSentences() {
    List<String> result = splitter.split("Hello world. How are you? I am fine.");
    assertThat(result).hasSize(3);
  }

  @Test
  void splitReturnsEmptyListForNull() {
    assertThat(splitter.split(null)).isEmpty();
  }

  @Test
  void splitReturnsEmptyListForBlank() {
    assertThat(splitter.split("   ")).isEmpty();
  }

  @Test
  void splitSingleSentenceReturnsSingleElement() {
    List<String> result = splitter.split("Just one sentence here.");
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo("Just one sentence here.");
  }

  @Test
  void splitWithSpansReturnsEmptyForNull() {
    assertThat(splitter.splitWithSpans(null)).isEmpty();
  }

  @Test
  void splitWithSpansReturnsEmptyForBlank() {
    assertThat(splitter.splitWithSpans("")).isEmpty();
  }

  @Test
  void splitWithSpansReturnsCorrectCount() {
    var spans = splitter.splitWithSpans("First sentence. Second sentence.");
    assertThat(spans).hasSize(2);
  }
}
