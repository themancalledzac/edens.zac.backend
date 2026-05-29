package edens.zac.portfolio.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edens.zac.portfolio.backend.services.VideoVariantPlanner.VideoVariantPlan;
import org.junit.jupiter.api.Test;

class VideoVariantPlannerTest {

  @Test
  void horizontal8k_reencodesBothVariants() {
    VideoVariantPlan plan = VideoVariantPlanner.compute(7680, 4320);
    assertTrue(plan.fullNeedsReencode(), "8K longest > 2000 → full re-encode");
    assertTrue(plan.webIsSeparate(), "8K longest > 1080 → separate web encode");
    assertEquals(2000, plan.fullTargetLongestSide());
    assertEquals(1080, plan.webTargetLongestSide());
  }

  @Test
  void typical2000pxExport_remuxesFull_separateWeb() {
    VideoVariantPlan plan = VideoVariantPlanner.compute(2000, 1125);
    assertFalse(plan.fullNeedsReencode());
    assertTrue(plan.webIsSeparate());
  }

  @Test
  void fullHd1920_remuxesFull_separateWeb() {
    VideoVariantPlan plan = VideoVariantPlanner.compute(1920, 1080);
    assertFalse(plan.fullNeedsReencode());
    assertTrue(plan.webIsSeparate(), "longest 1920 > 1080 → separate web");
  }

  @Test
  void exactly1080_remuxesFull_webReusesFull() {
    VideoVariantPlan plan = VideoVariantPlanner.compute(1080, 1080);
    assertFalse(plan.fullNeedsReencode());
    assertFalse(plan.webIsSeparate());
  }

  @Test
  void tinyInput_neverUpscales_bothReuseOriginal() {
    VideoVariantPlan plan = VideoVariantPlanner.compute(800, 600);
    assertFalse(plan.fullNeedsReencode());
    assertFalse(plan.webIsSeparate());
  }

  @Test
  void verticalUsesLongestSide() {
    VideoVariantPlan tall = VideoVariantPlanner.compute(1125, 2000);
    assertFalse(tall.fullNeedsReencode());
    assertTrue(tall.webIsSeparate());

    VideoVariantPlan veryTall = VideoVariantPlanner.compute(2250, 4000);
    assertTrue(veryTall.fullNeedsReencode());
    assertTrue(veryTall.webIsSeparate());
  }
}
