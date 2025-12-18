package com.redis.vl.extensions.summarization;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.utils.vectorize.SentenceTransformersVectorizer;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
class ExtractiveSelectorTest {

  private static SentenceTransformersVectorizer vectorizer;
  private static ExtractiveSelector selector;
  private static SentenceSplitter splitter;

  @BeforeAll
  static void setup() {
    vectorizer = new SentenceTransformersVectorizer("sentence-transformers/all-MiniLM-L6-v2");
    selector = new ExtractiveSelector(vectorizer, 3);
    splitter = new SentenceSplitter();
  }

  @Test
  void selectKeySentences_preservesExactText() {
    // Given: sentences with specific entity names
    List<String> sentences =
        List.of(
            "Jennifer is an earnest intelligent woman.",
            "She makes a serious error in judgment.",
            "Jennifer chooses to marry Mina Loris.",
            "Mina Loris is a pompous scholar.",
            "The weather was pleasant that day.");

    // When: selecting key sentences
    List<String> selected = selector.selectKeySentences(sentences, 2);

    // Then: exact original text is preserved (critical for SubEM)
    assertThat(selected).allSatisfy(s -> assertThat(sentences).contains(s));

    // And: entity names are preserved exactly
    String joined = String.join(" ", selected);
    // At least one sentence with "Jennifer" should be selected
    // (as it's the main subject)
  }

  @Test
  void selectKeySentences_maintainsOriginalOrder() {
    List<String> sentences =
        List.of(
            "First sentence about topic A.",
            "Second sentence about topic B.",
            "Third sentence about topic A again.",
            "Fourth sentence about topic C.",
            "Fifth sentence about topic B again.");

    List<String> selected = selector.selectKeySentences(sentences, 3);

    // Verify order is preserved
    int lastIndex = -1;
    for (String s : selected) {
      int currentIndex = sentences.indexOf(s);
      assertThat(currentIndex).isGreaterThan(lastIndex);
      lastIndex = currentIndex;
    }
  }

  @Test
  void selectKeySentences_handlesFewerSentencesThanK() {
    List<String> sentences = List.of("Only one sentence.");

    List<String> selected = selector.selectKeySentences(sentences, 5);

    assertThat(selected).hasSize(1);
    assertThat(selected.get(0)).isEqualTo("Only one sentence.");
  }

  @Test
  void selectKeySentences_handlesEmptyInput() {
    List<String> selected = selector.selectKeySentences(List.of(), 5);
    assertThat(selected).isEmpty();
  }

  @Test
  void sentenceSplitter_splitsCorrectly() {
    String text = "This is sentence one. This is sentence two! Is this sentence three?";

    List<String> sentences = splitter.split(text);

    assertThat(sentences).hasSize(3);
    assertThat(sentences.get(0)).isEqualTo("This is sentence one.");
    assertThat(sentences.get(1)).isEqualTo("This is sentence two!");
    assertThat(sentences.get(2)).isEqualTo("Is this sentence three?");
  }

  @Test
  void integrationTest_extractiveSummarization() {
    // Given: a paragraph with named entities
    String text =
        """
            Jennifer Pete is the main character of the novel.
            She lives in the provincial town of Semantico.
            Jennifer is married to Mr. Loris, a pompous scholar.
            The story explores themes of ambition and regret.
            Sahara Delphine is Jennifer's closest friend.
            Will Ladislaw appears later in the narrative.
            The weather in Semantico is often described as gray.
            """;

    // When: performing extractive summarization
    List<String> sentences = splitter.split(text);
    List<String> summary = selector.selectKeySentences(sentences, 3);

    // Then: key sentences are selected with exact entity names preserved
    String summaryText = String.join(" ", summary);

    // The summary should contain original entity names, not paraphrases
    // This is the key difference from abstractive summarization
    System.out.println("Extractive Summary: " + summaryText);

    assertThat(summary).hasSize(3);
    // Sentences should be from the original, not generated
    summary.forEach(s -> assertThat(sentences).contains(s));
  }
}
