package com.redis.vl.extensions.summarization;

import com.redis.vl.utils.vectorize.SentenceTransformersVectorizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;

/**
 * BERT-based extractive summarization using sentence clustering.
 *
 * <p>This class selects the most representative sentences from a document by embedding sentences
 * with BERT, clustering them with k-means, and selecting the sentence closest to each cluster
 * centroid.
 *
 * <p><b>Key Feature:</b> Preserves original text exactly, which is critical for SubEM (Substring
 * Exact Match) evaluation where paraphrasing fails.
 *
 * <h2>Example Usage:</h2>
 *
 * <pre>{@code
 * SentenceTransformersVectorizer vectorizer = SentenceTransformersVectorizer.builder()
 *     .modelName("all-MiniLM-L6-v2")
 *     .build();
 *
 * ExtractiveSelector selector = new ExtractiveSelector(vectorizer);
 * SentenceSplitter splitter = new SentenceSplitter();
 *
 * String document = "Long document text...";
 * List<String> sentences = splitter.split(document);
 * List<String> keySentences = selector.selectKeySentences(sentences, 10);
 *
 * // keySentences contains the 10 most representative sentences
 * // in their original order, with exact original text preserved
 * }</pre>
 */
public class ExtractiveSelector {

  private final SentenceTransformersVectorizer embedder;
  private final int defaultNumSentences;
  private final int maxIterations;

  /**
   * Create an extractive selector with default settings.
   *
   * @param embedder The sentence transformer vectorizer for embeddings
   */
  public ExtractiveSelector(SentenceTransformersVectorizer embedder) {
    this(embedder, 10, 100);
  }

  /**
   * Create an extractive selector with custom number of sentences.
   *
   * @param embedder The sentence transformer vectorizer for embeddings
   * @param defaultNumSentences Default number of sentences to select
   */
  public ExtractiveSelector(SentenceTransformersVectorizer embedder, int defaultNumSentences) {
    this(embedder, defaultNumSentences, 100);
  }

  /**
   * Create an extractive selector with full configuration.
   *
   * @param embedder The sentence transformer vectorizer for embeddings
   * @param defaultNumSentences Default number of sentences to select
   * @param maxIterations Maximum k-means iterations
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Embedder is intentionally shared; it's a heavyweight resource")
  public ExtractiveSelector(
      SentenceTransformersVectorizer embedder, int defaultNumSentences, int maxIterations) {
    this.embedder = embedder;
    this.defaultNumSentences = defaultNumSentences;
    this.maxIterations = maxIterations;
  }

  /**
   * Select the most representative sentences using the default count.
   *
   * @param sentences List of sentences to select from
   * @return Selected sentences in original order
   */
  public List<String> selectKeySentences(List<String> sentences) {
    return selectKeySentences(sentences, defaultNumSentences);
  }

  /**
   * Select the k most representative sentences from the input.
   *
   * <p>Algorithm:
   *
   * <ol>
   *   <li>Embed all sentences using BERT
   *   <li>Cluster embeddings using k-means++
   *   <li>For each cluster, select the sentence closest to the centroid
   *   <li>Return sentences in their original order
   * </ol>
   *
   * @param sentences List of sentences to select from
   * @param k Number of sentences to select
   * @return Selected sentences in original order (preserves exact text)
   */
  public List<String> selectKeySentences(List<String> sentences, int k) {
    if (sentences == null || sentences.isEmpty()) {
      return List.of();
    }

    // If we have fewer sentences than k, return all
    if (sentences.size() <= k) {
      return new ArrayList<>(sentences);
    }

    // Filter out empty/whitespace sentences
    List<IndexedSentence> validSentences =
        IntStream.range(0, sentences.size())
            .filter(i -> sentences.get(i) != null && !sentences.get(i).isBlank())
            .mapToObj(i -> new IndexedSentence(i, sentences.get(i)))
            .toList();

    if (validSentences.size() <= k) {
      return validSentences.stream().map(IndexedSentence::text).toList();
    }

    // 1. Embed all sentences
    List<String> textsToEmbed = validSentences.stream().map(IndexedSentence::text).toList();
    List<float[]> embeddings = embedder.embedSentences(textsToEmbed);

    // 2. Create clusterable points
    List<EmbeddedSentence> points =
        IntStream.range(0, validSentences.size())
            .mapToObj(i -> new EmbeddedSentence(validSentences.get(i).index(), embeddings.get(i)))
            .toList();

    // 3. K-means++ clustering
    KMeansPlusPlusClusterer<EmbeddedSentence> clusterer =
        new KMeansPlusPlusClusterer<>(k, maxIterations);
    List<CentroidCluster<EmbeddedSentence>> clusters = clusterer.cluster(points);

    // 4. Select sentence closest to each cluster centroid
    List<Integer> selectedIndices =
        clusters.stream()
            .map(this::findClosestToCentroid)
            .map(EmbeddedSentence::index)
            .sorted() // Preserve original order
            .toList();

    // 5. Return original sentences
    return selectedIndices.stream().map(sentences::get).toList();
  }

  /** Find the sentence closest to the cluster centroid. */
  private EmbeddedSentence findClosestToCentroid(CentroidCluster<EmbeddedSentence> cluster) {
    double[] centroid = cluster.getCenter().getPoint();

    return cluster.getPoints().stream()
        .min(Comparator.comparingDouble(point -> euclideanDistance(point.getPoint(), centroid)))
        .orElseThrow(() -> new IllegalStateException("Empty cluster"));
  }

  /** Calculate Euclidean distance between two points. */
  private double euclideanDistance(double[] a, double[] b) {
    double sum = 0.0;
    for (int i = 0; i < a.length; i++) {
      double diff = a[i] - b[i];
      sum += diff * diff;
    }
    return Math.sqrt(sum);
  }

  /** Helper record to track original indices. */
  private record IndexedSentence(int index, String text) {}

  /** Builder for ExtractiveSelector. */
  public static Builder builder(SentenceTransformersVectorizer embedder) {
    return new Builder(embedder);
  }

  public static class Builder {
    private final SentenceTransformersVectorizer embedder;
    private int defaultNumSentences = 10;
    private int maxIterations = 100;

    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Embedder is intentionally shared; it's a heavyweight resource")
    public Builder(SentenceTransformersVectorizer embedder) {
      this.embedder = embedder;
    }

    public Builder defaultNumSentences(int n) {
      this.defaultNumSentences = n;
      return this;
    }

    public Builder maxIterations(int n) {
      this.maxIterations = n;
      return this;
    }

    public ExtractiveSelector build() {
      return new ExtractiveSelector(embedder, defaultNumSentences, maxIterations);
    }
  }
}
