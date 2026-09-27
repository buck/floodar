package com.compact.floodar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES30;
import android.opengl.GLUtils;
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
import com.google.ar.core.examples.java.common.samplerender.Texture;
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
 *   <li>Streetscape buildings stained below the water and lined at the waterline; terrain only
 *       hides water behind raised ground.
 *   <li>A muddy water surface around the camera that reflects the live camera image.
 *   <li>A depth gauge (1-ft stripes) or the reconstructed Clear Lake marker at a ground point.
 * </ul>
 */
final class FloodRenderer {
  private static final float FOOT = 0.3048f;
  private static final float SURFACE_HALF_SIZE = 80f;

  // Colors sampled from the resident's Harvey street video (2017-08-27).
  private static final float[] WATER_COLOR = {0.36f, 0.29f, 0.18f, 0.55f}; // stain on walls
  private static final float[] WATER_SURFACE_COLOR = {0.36f, 0.30f, 0.19f, 0.90f}; // mud
  private static final float[] SKY_COLOR = {0.75f, 0.77f, 0.79f}; // overcast reflection
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
  private final Shader labelShader;
  private final Mesh labelMesh;

  // Gauge labels "1 ft".."MAX_LABEL_FT ft", one per row of a texture atlas.
  private static final int MAX_LABEL_FT = 15;
  private static final int LABEL_CELL_W = 256;
  private static final int LABEL_CELL_H = 64;
  private static final float LABEL_HEIGHT_M = 0.08f;
  private static final float LABEL_WIDTH_M = LABEL_HEIGHT_M * LABEL_CELL_W / LABEL_CELL_H;

  private final float[] model = new float[16];
  private final float[] modelView = new float[16];
  private final float[] mvp = new float[16];
  private final float[] viewProjection = new float[16];

  /**
   * @param cameraTexture the ARCore camera image (external OES texture) the water reflects.
   */
  FloodRenderer(SampleRender render, Texture cameraTexture) throws IOException {
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
            .setTexture("u_CameraColorTexture", cameraTexture)
            .setFloat("u_FadeStart", 15f)
            .setFloat("u_FadeEnd", SURFACE_HALF_SIZE);
    solidShader =
        Shader.createFromAssets(render, "shaders/flood.vert", "shaders/flood_solid.frag", null);
    labelShader =
        Shader.createFromAssets(render, "shaders/flood_label.vert", "shaders/flood_label.frag", null)
            .setBlend(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
            .setCullFace(false)
            .setDepthWrite(false)
            .setTexture("u_Texture", createLabelAtlas(render));
    // Quad x in 0..1, y in -1..0 (top edge at the anchor height), with UVs.
    labelMesh = texturedQuad(render);

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
      meshShader
          .setFloat("u_WaterY", waterY)
          .setFloat(
              "u_IsTerrain", g.getType() == StreetscapeGeometry.Type.TERRAIN ? 1f : 0f)
          .setVec3("u_CameraPos", cameraPos);
      render.draw(e.getValue(), meshShader);
    }
  }

  /**
   * Draws the water surface centered under/over the camera.
   *
   * @param uvTransform screen NDC to camera-texture UV as {originU, originV, dxU, dxV, dyU, dyV}
   */
  void drawSurface(
      SampleRender render,
      float[] view,
      float[] projection,
      float waterY,
      float[] cameraPos,
      float[] uvTransform) {
    Matrix.setIdentityM(model, 0);
    Matrix.translateM(model, 0, cameraPos[0], waterY, cameraPos[2]);
    Matrix.scaleM(model, 0, SURFACE_HALF_SIZE, 1f, SURFACE_HALF_SIZE);
    setMatrices(surfaceShader, view, projection);
    Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0);
    surfaceShader
        .setMat4("u_ViewProjection", viewProjection)
        .setVec2("u_UvOrigin", new float[] {uvTransform[0], uvTransform[1]})
        .setVec2("u_UvDx", new float[] {uvTransform[2], uvTransform[3]})
        .setVec2("u_UvDy", new float[] {uvTransform[4], uvTransform[5]})
        .setVec3("u_CameraPos", cameraPos)
        .setFloat("u_Time", (SystemClock.uptimeMillis() % 100000) / 1000f);
    render.draw(surfaceMesh, surfaceShader);
  }

  /**
   * Depth gauge at a ground point: alternating 1-ft stripes up to (and 1 ft past) the water,
   * with "1 ft", "2 ft"... labels just under each dividing line, turned to face the camera.
   */
  void drawGauge(
      SampleRender render,
      float[] view,
      float[] projection,
      Pose base,
      float depthMeters,
      float[] cameraPos) {
    int feet = Math.max(1, (int) Math.ceil(depthMeters / FOOT) + 1);
    for (int k = 0; k < feet; k++) {
      drawBox(
          render, view, projection, base, k * FOOT, FOOT, 0.08f, 0.08f,
          (k % 2 == 0) ? STRIPE_A : STRIPE_B);
    }
    float yawDeg =
        (float) Math.toDegrees(Math.atan2(cameraPos[0] - base.tx(), cameraPos[2] - base.tz()));
    for (int k = 1; k <= Math.min(feet, MAX_LABEL_FT); k++) {
      Matrix.setIdentityM(model, 0);
      Matrix.translateM(model, 0, base.tx(), base.ty() + k * FOOT - 0.01f, base.tz());
      Matrix.rotateM(model, 0, yawDeg, 0f, 1f, 0f);
      Matrix.translateM(model, 0, 0.06f, 0f, 0f); // just right of the pole
      Matrix.scaleM(model, 0, LABEL_WIDTH_M, LABEL_HEIGHT_M, 1f);
      Matrix.multiplyMM(modelView, 0, view, 0, model, 0);
      Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0);
      float cellV = 1f / MAX_LABEL_FT;
      labelShader
          .setMat4("u_ModelViewProjection", mvp)
          .setVec4("u_UvOffsetScale", new float[] {0f, (k - 1) * cellV, 1f, cellV});
      render.draw(labelMesh, labelShader);
    }
  }

  /** Renders "1 ft".."N ft" (white, dark outline) into one column of cells and uploads it. */
  private static Texture createLabelAtlas(SampleRender render) {
    Bitmap bmp =
        Bitmap.createBitmap(LABEL_CELL_W, LABEL_CELL_H * MAX_LABEL_FT, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);
    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setTypeface(Typeface.DEFAULT_BOLD);
    fill.setTextSize(LABEL_CELL_H * 0.72f);
    fill.setColor(Color.WHITE);
    Paint outline = new Paint(fill);
    outline.setStyle(Paint.Style.STROKE);
    outline.setStrokeWidth(7f);
    outline.setColor(Color.argb(230, 0, 0, 0));
    for (int k = 1; k <= MAX_LABEL_FT; k++) {
      String text = k + " ft";
      float baseline = (k - 1) * LABEL_CELL_H + LABEL_CELL_H * 0.78f;
      canvas.drawText(text, 8f, baseline, outline);
      canvas.drawText(text, 8f, baseline, fill);
    }
    Texture texture =
        new Texture(
            render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, /*useMipmaps=*/ false);
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.getTextureId());
    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    bmp.recycle();
    return texture;
  }

  private static Mesh texturedQuad(SampleRender render) {
    float[] pos = {0, 0, 0, 1, 0, 0, 1, -1, 0, 0, -1, 0};
    // Bitmap row 0 is the top of the image; GLUtils uploads it as t = 0.
    float[] uv = {0, 0, 1, 0, 1, 1, 0, 1};
    return new Mesh(
        render,
        Mesh.PrimitiveMode.TRIANGLES,
        new IndexBuffer(render, intBuffer(new int[] {0, 2, 1, 0, 3, 2})),
        new VertexBuffer[] {
          new VertexBuffer(render, 3, floatBuffer(pos)), new VertexBuffer(render, 2, floatBuffer(uv))
        });
  }

  private static FloatBuffer floatBuffer(float[] a) {
    FloatBuffer b =
        ByteBuffer.allocateDirect(a.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a);
    b.rewind();
    return b;
  }

  private static IntBuffer intBuffer(int[] a) {
    IntBuffer b =
        ByteBuffer.allocateDirect(a.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(a);
    b.rewind();
    return b;
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
