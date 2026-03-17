package com.redis.vl.utils.vectorize;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Pipeline;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * RedisVL vectorizer for face embeddings using Deep Java Library (DJL). Uses the face_feature model
 * from DJL model zoo for generating 512-dimensional face embeddings.
 *
 * <p>This vectorizer is compatible with Redis OM Spring's face embedding approach and can be used
 * for face verification, identification, and similarity search tasks.
 */
public class DJLFaceVectorizer extends BaseVectorizer implements AutoCloseable {

  private static final int EMBEDDING_DIMENSION = 512;
  private static final String MODEL_NAME = "face_feature";

  private final ZooModel<Image, float[]> model;
  private final ImageFactory imageFactory;

  /**
   * Constructs a DJL face vectorizer with default model configuration. Downloads the face_feature
   * model from DJL model zoo if not already cached.
   *
   * @throws IOException if model loading fails
   * @throws ModelNotFoundException if model cannot be found
   * @throws MalformedModelException if model format is invalid
   */
  public DJLFaceVectorizer() throws IOException, ModelNotFoundException, MalformedModelException {
    super(MODEL_NAME, EMBEDDING_DIMENSION, "FLOAT32");

    // Create criteria for face embedding model
    Criteria<Image, float[]> criteria =
        Criteria.builder()
            .optApplication(Application.CV.IMAGE_CLASSIFICATION)
            .setTypes(Image.class, float[].class)
            .optModelUrls("https://resources.djl.ai/test-models/pytorch/face_feature.zip")
            .optTranslator(new FaceFeatureTranslator())
            .build();

    this.model = criteria.loadModel();
    this.imageFactory = ImageFactory.getInstance();
  }

  /**
   * Generates face embedding from an image input stream.
   *
   * @param imageStream Input stream containing the face image
   * @return 512-dimensional float array representing the face embedding
   * @throws IOException if image loading fails
   * @throws TranslateException if model inference fails
   */
  public float[] embedImage(InputStream imageStream) throws IOException, TranslateException {
    Image image = imageFactory.fromInputStream(imageStream);
    try (Predictor<Image, float[]> predictor = model.newPredictor()) {
      return predictor.predict(image);
    }
  }

  /**
   * Generates face embeddings for a batch of images.
   *
   * @param imageStreams List of input streams containing face images
   * @return List of 512-dimensional float arrays representing face embeddings
   * @throws IOException if image loading fails
   * @throws TranslateException if model inference fails
   */
  public List<float[]> embedImageBatch(List<InputStream> imageStreams)
      throws IOException, TranslateException {
    List<Image> images = new ArrayList<>();
    for (InputStream is : imageStreams) {
      images.add(imageFactory.fromInputStream(is));
    }

    try (Predictor<Image, float[]> predictor = model.newPredictor()) {
      return predictor.batchPredict(images);
    }
  }

  @Override
  protected float[] generateEmbedding(String text) {
    throw new UnsupportedOperationException(
        "DJLFaceVectorizer is for face images, not text. Use embedImage(InputStream) instead.");
  }

  @Override
  protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
    throw new UnsupportedOperationException(
        "Batch text embedding not supported for DJLFaceVectorizer. Use embedImageBatch(List<InputStream>) instead.");
  }

  @Override
  public void close() {
    if (model != null) {
      model.close();
    }
  }

  /**
   * Translator for face feature extraction. Implements the same preprocessing pipeline as Redis OM
   * Spring's FaceFeatureTranslator.
   */
  private static class FaceFeatureTranslator implements Translator<Image, float[]> {

    @Override
    public NDList processInput(TranslatorContext ctx, Image input) {
      NDArray array = input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR);
      Pipeline pipeline = new Pipeline();
      pipeline
          .add(new ToTensor())
          .add(
              new Normalize(
                  new float[] {127.5f / 255.0f, 127.5f / 255.0f, 127.5f / 255.0f},
                  new float[] {128.0f / 255.0f, 128.0f / 255.0f, 128.0f / 255.0f}));

      return pipeline.transform(new NDList(array));
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
      try (NDList result = new NDList()) {
        long numOutputs = list.singletonOrThrow().getShape().get(0);
        for (int i = 0; i < numOutputs; i++) {
          result.add(list.singletonOrThrow().get(i));
        }
        float[][] embeddings = result.stream().map(NDArray::toFloatArray).toArray(float[][]::new);
        float[] feature = new float[embeddings.length];
        for (int i = 0; i < embeddings.length; i++) {
          feature[i] = embeddings[i][0];
        }
        return feature;
      }
    }
  }
}
