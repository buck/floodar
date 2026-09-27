package com.compact.floodar;

import android.opengl.Matrix;
import android.os.SystemClock;
import com.google.ar.core.Pose;
import com.google.ar.core.StreetscapeGeometry;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.samplerender.IndexBuffer;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.Shader.BlendFactor;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;

/**
 * Draws a flood scenario in AR world space, given the water surface height (world Y):
 *
 * <ul>
 *   <li>Streetscape building/terrain meshes stained below the water and lined at the waterline.
 *   <li>A muddy, sky-reflecting water surface around the camera, fading with distance.
 *   <li>A depth gauge (1-ft stripes) or the reconstructed Clear Lake marker at a ground point.
 * </ul>
 */
final class FloodRenderer {
  private static final float FOOT = 0.3048f;
  private static final float SURFACE_HALF_SIZE = 80f;

  // Colors sampled from the resident's Harvey street video (2017-08-27).
  private static final float[] WATER_COLOR = {0.40f, 0.33f, 0.20f, 0.55f}; // stain on walls
  private static final float[] WATER_SURFACE_COLOR = {0.47f, 0.39f, 0.25f, 0.88f}; // mud
  private static final float[] SKY_COLOR = {0.78f, 0.79f, 0.80f}; // overcast reflection
  private static final float[] LINE_COLOR = {0.88f, 0.84f, 0.70f, 0.95f}; // debris line
  private static final float[] STRIPE_A = {0.95f, 0.95f, 0.95f, 1f};
  private static final float[] STRIPE_B = {0.85f, 0.15f, 0.15f, 1f};
  private static final float[] MARKER_CAP = {0.75f, 0.75f, 0.75f, 1f};

  // Fractions of marker height, bottom to top, traced from the Blackburn photo (provisional).
  private static final float[] MARKER_BAND_FRACTIONS = {0.38f, 0.47f, 0.15f};

  private final Shader meshShader;
  private final Shader surfaceShader;
  private final Shader solidShader;
  private final Mesh surfaceMesh;
  private final Mesh boxMesh;

  private final float[] model = new float[16];
  private final float[] modelView = new float[16];
  private final float[] mvp = new float[16];

  FloodRenderer(SampleRender render) throws IOException {
    meshShader =
        Shader.createFromAssets(render, "shaders/flood.vert", "shaders/flood_mesh.frag", null)
            .setBlend(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
            .setVec4("u_WaterColor", WATER_COLOR)
            .setVec4("u_LineColor", LINE_COLOR);
    surfaceShader =
        Shader.createFromAssets(render, "shaders/flood.vert", "shaders/flood_surface.frag", null)
            .setBlend(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
            .setDepthWrite(false)
            .setCullFace(false)
            .setVec4("u_WaterColor", WATER_SURFACE_COLOR)
            .setVec3("u_SkyColor", SKY_COLOR)
            .setFloat("u_FadeStart", 15f)
            .setFloat("u_FadeEnd", SURFACE_HALF_SIZE);
    solidShader =
        Shader.createFromAssets(render, "shaders/flood.vert", "shaders/flood_solid.frag", null);

    // Unit quad in XZ (-1..1), scaled/translated per frame.
    surfaceMesh =
        mesh(
            render,
            new float[] {-1, 0, -1, 1, 0, -1, 1, 0, 1, -1, 0, 1},
            new int[] {0, 1, 2, 0, 2, 3, 0, 2, 1, 0, 3, 2});
    // Unit box: x,z in -0.5..0.5, y in 0..1.
    float[] v = new float[24];
    int i = 0;
    for (int y = 0; y <= 1; y++) {
      for (int z = 0; z <= 1; z++) {
        for (int x = 0; x <= 1; x++) {
          v[i++] = x - 0.5f;
          v[i++] = y;
          v[i++] = z - 0.5f;
        }
      }
    }
    boxMesh =
        mesh(
            render,
            v,
            new int[] {
              0, 2, 3, 0, 3, 1, // bottom
              4, 5, 7, 4, 7, 6, // top
              0, 1, 5, 0, 5, 4, // z = -0.5
              2, 6, 7, 2, 7, 3, // z = +0.5
              0, 4, 6, 0, 6, 2, // x = -0.5
              1, 3, 7, 1, 7, 5 // x = +0.5
            });
  }

  /** Tints Streetscape meshes below the water and draws the waterline on them. */
  void drawStreetscape(
      SampleRender render,
      Map<StreetscapeGeometry, Mesh> meshes,
      float[] view,
      float[] projection,
      float waterY,
      float[] cameraPos) {
    for (Map.Entry<StreetscapeGeometry, Mesh> e : meshes.entrySet()) {
      StreetscapeGeometry g = e.getKey();
      if (g.getTrackingState() != TrackingState.TRACKING) {
        continue;
      }
      g.getMeshPose().toMatrix(model, 0);
      setMatrices(meshShader, view, projection);
      meshShader.setFloat("u_WaterY", waterY).setVec3("u_CameraPos", cameraPos);
      render.draw(e.getValue(), meshShader);
    }
  }

  /** Draws the translucent water surface centered under/over the camera. */
  void drawSurface(
      SampleRender render, float[] view, float[] projection, float waterY, float[] cameraPos) {
    Matrix.setIdentityM(model, 0);
    Matrix.translateM(model, 0, cameraPos[0], waterY, cameraPos[2]);
    Matrix.scaleM(model, 0, SURFACE_HALF_SIZE, 1f, SURFACE_HALF_SIZE);
    setMatrices(surfaceShader, view, projection);
    surfaceShader
        .setVec3("u_CameraPos", cameraPos)
        .setFloat("u_Time", (SystemClock.uptimeMillis() % 100000) / 1000f);
    render.draw(surfaceMesh, surfaceShader);
  }

  /** Depth gauge at a ground point: alternating 1-ft stripes up to (and 1 ft past) the water. */
  void drawGauge(
      SampleRender render, float[] view, float[] projection, Pose base, float depthMeters) {
    int feet = Math.max(1, (int) Math.ceil(depthMeters / FOOT) + 1);
    for (int k = 0; k < feet; k++) {
      drawBox(
          render, view, projection, base, k * FOOT, FOOT, 0.08f, 0.08f,
          (k % 2 == 0) ? STRIPE_A : STRIPE_B);
    }
  }

  /** The 2010 Clear Lake marker: blue Category 4 band, green Category 5 band, grey cap. */
  void drawMarker(
      SampleRender render, float[] view, float[] projection, Pose base, FloodSite.Marker marker) {
    float height = (float) marker.heightMeters();
    float y = 0f;
    for (int k = 0; k < MARKER_BAND_FRACTIONS.length; k++) {
      float[] color = MARKER_CAP;
      if (k < marker.bandColors.size()) {
        int c = marker.bandColors.get(k);
        color =
            new float[] {
              ((c >> 16) & 0xff) / 255f, ((c >> 8) & 0xff) / 255f, (c & 0xff) / 255f, 1f
            };
      }
      float h = height * MARKER_BAND_FRACTIONS[k];
      drawBox(render, view, projection, base, y, h, 0.6f, 0.25f, color);
      y += h;
    }
  }

  private void drawBox(
      SampleRender render,
      float[] view,
      float[] projection,
      Pose base,
      float y0,
      float height,
      float width,
      float depth,
      float[] color) {
    Matrix.setIdentityM(model, 0);
    Matrix.translateM(model, 0, base.tx(), base.ty() + y0, base.tz());
    Matrix.scaleM(model, 0, width, height, depth);
    setMatrices(solidShader, view, projection);
    solidShader.setVec4("u_Color", color);
    render.draw(boxMesh, solidShader);
  }

  private void setMatrices(Shader shader, float[] view, float[] projection) {
    Matrix.multiplyMM(modelView, 0, view, 0, model, 0);
    Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0);
    shader.setMat4("u_Model", model).setMat4("u_ModelViewProjection", mvp);
  }

  private static Mesh mesh(SampleRender render, float[] vertices, int[] indices) {
    FloatBuffer vb =
        ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices);
    vb.rewind();
    IntBuffer ib =
        ByteBuffer.allocateDirect(indices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(indices);
    ib.rewind();
    return new Mesh(
        render,
        Mesh.PrimitiveMode.TRIANGLES,
        new IndexBuffer(render, ib),
        new VertexBuffer[] {new VertexBuffer(render, 3, vb)});
  }
}
