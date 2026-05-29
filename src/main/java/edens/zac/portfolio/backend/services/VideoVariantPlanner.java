package edens.zac.portfolio.backend.services;

/**
 * Pure decision logic for web-delivery video variants. Given the pixel dimensions of an uploaded
 * video, decides whether each variant (2000px "full", 1080px "web") needs a lossy re-encode or can
 * reuse a smaller path, never upscaling.
 *
 * <p>"Longest side" is max(width, height): a horizontal video is gated on width, a vertical one on
 * height. A variant is only re-encoded when the input's longest side STRICTLY exceeds the cap, so a
 * well-exported 2000px file is rewrapped losslessly rather than recompressed.
 */
public final class VideoVariantPlanner {

  public static final int FULL_MAX_LONGEST_SIDE = 2000;
  public static final int WEB_MAX_LONGEST_SIDE = 1080;

  private VideoVariantPlanner() {}

  /**
   * @param fullNeedsReencode true when longest side > {@link #FULL_MAX_LONGEST_SIDE}; otherwise the
   *     full variant is a lossless remux.
   * @param fullTargetLongestSide the box edge to fit the full variant into when re-encoding.
   * @param webIsSeparate true when longest side > {@link #WEB_MAX_LONGEST_SIDE}; otherwise the web
   *     variant reuses the full file (input already ≤ 1080, never upscale).
   * @param webTargetLongestSide the box edge to fit the web variant into when separate.
   */
  public record VideoVariantPlan(
      boolean fullNeedsReencode,
      int fullTargetLongestSide,
      boolean webIsSeparate,
      int webTargetLongestSide) {}

  /**
   * Compute the variant plan for a video with the given pixel dimensions.
   *
   * @param width pixel width of the source video
   * @param height pixel height of the source video
   * @return a {@link VideoVariantPlan} describing which variants need re-encoding
   */
  public static VideoVariantPlan compute(int width, int height) {
    int longest = Math.max(width, height);
    boolean fullReencode = longest > FULL_MAX_LONGEST_SIDE;
    boolean webSeparate = longest > WEB_MAX_LONGEST_SIDE;
    return new VideoVariantPlan(
        fullReencode, FULL_MAX_LONGEST_SIDE, webSeparate, WEB_MAX_LONGEST_SIDE);
  }
}
