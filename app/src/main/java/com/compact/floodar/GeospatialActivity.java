/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.compact.floodar;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.GuardedBy;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.RooftopAnchorState;
import com.google.ar.core.Anchor.TerrainAnchorState;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Earth;
import com.google.ar.core.Frame;
import com.google.ar.core.GeospatialPose;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.PlaybackStatus;
import com.google.ar.core.Pose;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.RecordingStatus;
import com.google.ar.core.ResolveAnchorOnRooftopFuture;
import com.google.ar.core.ResolveAnchorOnTerrainFuture;
import com.google.ar.core.Session;
import com.google.ar.core.StreetscapeGeometry;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.VpsAvailability;
import com.google.ar.core.VpsAvailabilityFuture;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.LocationPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.samplerender.Framebuffer;
import com.google.ar.core.examples.java.common.samplerender.IndexBuffer;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.Shader.BlendFactor;
import com.google.ar.core.examples.java.common.samplerender.Texture;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.examples.java.common.samplerender.arcore.PlaneRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.FineLocationPermissionNotGrantedException;
import com.google.ar.core.exceptions.GooglePlayServicesLocationLibraryNotLinkedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.core.exceptions.TextureNotSetException;
import com.google.ar.core.exceptions.UnsupportedConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;

/**
 * Main activity for the Geospatial API example.
 *
 * <p>This example shows how to use the Geospatial APIs. Once the device is localized, anchors can
 * be created at the device's geospatial location. Anchor locations are persisted across sessions
 * and will be recreated once localized.
 */
public class GeospatialActivity extends AppCompatActivity
    implements SampleRender.Renderer,
        VpsAvailabilityNoticeDialogFragment.NoticeDialogListener,
        PrivacyNoticeDialogFragment.NoticeDialogListener {

  private static final String TAG = GeospatialActivity.class.getSimpleName();

  private static final String SHARED_PREFERENCES_SAVED_ANCHORS = "SHARED_PREFERENCES_SAVED_ANCHORS";
  private static final String ALLOW_GEOSPATIAL_ACCESS_KEY = "ALLOW_GEOSPATIAL_ACCESS";
  private static final String ANCHOR_MODE = "ANCHOR_MODE";
  private static final String FLOOD_SITE_ID = "FLOOD_SITE_ID";
  private static final String FLOOD_SCENARIO_ID = "FLOOD_SCENARIO_ID";

  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 1000f;

  // The thresholds that are required for horizontal and orientation accuracies before entering into
  // the LOCALIZED state. Once the accuracies are equal or less than these values, the app will
  // allow the user to place anchors.
  private static final double LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS = 10;
  private static final double LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES = 15;

  // Once in the LOCALIZED state, if either accuracies degrade beyond these amounts, the app will
  // revert back to the LOCALIZING state.
  private static final double LOCALIZED_HORIZONTAL_ACCURACY_HYSTERESIS_METERS = 10;
  private static final double LOCALIZED_ORIENTATION_YAW_ACCURACY_HYSTERESIS_DEGREES = 10;

  private static final int LOCALIZING_TIMEOUT_SECONDS = 180;
  private static final int MAXIMUM_ANCHORS = 20;
  private static final long DURATION_FOR_NO_TERRAIN_ANCHOR_RESULT_MS = 10000;

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;
  private Integer clearedAnchorsAmount = null;

  /** Timer to keep track of how much time has passed since localizing has started. */
  private long localizingStartTimestamp;
  /** Deadline for showing resolving terrain anchors no result yet message. */
  private long deadlineForMessageMillis;

  enum State {
    /** The Geospatial API has not yet been initialized. */
    UNINITIALIZED,
    /** The Geospatial API is not supported. */
    UNSUPPORTED,
    /** The Geospatial API has encountered an unrecoverable error. */
    EARTH_STATE_ERROR,
    /** The Session has started, but {@link Earth} isn't {@link TrackingState.TRACKING} yet. */
    PRETRACKING,
    /**
     * {@link Earth} is {@link TrackingState.TRACKING}, but the desired positioning confidence
     * hasn't been reached yet.
     */
    LOCALIZING,
    /** The desired positioning confidence wasn't reached in time. */
    LOCALIZING_FAILED,
    /**
     * {@link Earth} is {@link TrackingState.TRACKING} and the desired positioning confidence has
     * been reached.
     */
    LOCALIZED
  }

  private State state = State.UNINITIALIZED;

  enum AnchorType {
    // Set WGS84 anchor.
    GEOSPATIAL,
    // Set Terrain anchor.
    TERRAIN,
    // Set Rooftop anchor.
    ROOFTOP
  }

  private AnchorType anchorType = AnchorType.GEOSPATIAL;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private SampleRender render;
  private SharedPreferences sharedPreferences;

  // Flood sites from assets/sites.json and the current selection (null until chosen).
  private List<FloodSite> floodSites = Collections.emptyList();
  private volatile FloodSite selectedSite;
  private volatile FloodSite.Scenario selectedScenario;

  // Flood rendering state (GL thread). Ground is the world-Y the water depth is measured from:
  // a tapped anchor if set, otherwise a smoothed downward hit test from the camera.
  private FloodRenderer floodRenderer;
  private TextView floodInfoView;
  private Anchor groundAnchor;
  private Float groundY;
  private String groundSource = "searching";
  private String referenceSource = "";
  private int groundProbeFrame;
  private long lastFloodHudMillis;
  private static final float ASSUMED_PHONE_HEIGHT_M = 1.4f;
  private Earth.EarthState lastEarthErrorState;

  // ARCore session recording/playback (camera + sensors as MP4, replayable through the app).
  private File currentRecording;
  private String playbackName;
  private boolean playbackFinishedShown;

  private String lastStatusText;
  private TextView geospatialPoseTextView;
  private TextView statusTextView;
  private TextView tapScreenTextView;
  private Button setAnchorButton;
  private Button clearAnchorsButton;
  private Switch streetscapeGeometrySwitch;

  private PlaneRenderer planeRenderer;
  private BackgroundRenderer backgroundRenderer;
  private Framebuffer virtualSceneFramebuffer;
  private boolean hasSetTextureNames = false;
  // Set rendering Streetscape Geometry.
  private boolean isRenderStreetscapeGeometry = false;

  // Virtual object (ARCore geospatial)
  private Mesh virtualObjectMesh;
  private Shader geospatialAnchorVirtualObjectShader;
  // Virtual object (ARCore geospatial terrain)
  private Shader terrainAnchorVirtualObjectShader;

  private final Object anchorsLock = new Object();

  @GuardedBy("anchorsLock")
  private final List<Anchor> anchors = new ArrayList<>();

  private final Set<Anchor> terrainAnchors = new HashSet<>();
  private final Set<Anchor> rooftopAnchors = new HashSet<>();

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] modelMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16]; // view x model
  private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model

  private final float[] identityQuaternion = {0, 0, 0, 1};

  // Locks needed for synchronization
  private final Object singleTapLock = new Object();

  @GuardedBy("singleTapLock")
  private MotionEvent queuedSingleTap;
  // Tap handling and UI.
  private GestureDetector gestureDetector;

  // Point Cloud
  private VertexBuffer pointCloudVertexBuffer;
  private Mesh pointCloudMesh;
  private Shader pointCloudShader;
  // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
  // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
  private long lastPointCloudTimestamp = 0;

  // Provides device location.
  private FusedLocationProviderClient fusedLocationClient;

  // Streetscape geometry.
  private final ArrayList<float[]> wallsColor = new ArrayList<float[]>();

  private Shader streetscapeGeometryTerrainShader;
  private Shader streetscapeGeometryBuildingShader;
  // A set of planes representing building outlines and floors.
  private final Map<StreetscapeGeometry, Mesh> streetscapeGeometryToMeshes = new HashMap<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    sharedPreferences = getPreferences(Context.MODE_PRIVATE);
    loadFloodSites();

    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    geospatialPoseTextView = findViewById(R.id.geospatial_pose_view);
    statusTextView = findViewById(R.id.status_text_view);
    tapScreenTextView = findViewById(R.id.tap_screen_text_view);
    floodInfoView = findViewById(R.id.flood_info_view);
    setAnchorButton = findViewById(R.id.set_anchor_button);
    clearAnchorsButton = findViewById(R.id.clear_anchors_button);

    setAnchorButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            PopupMenu popup = new PopupMenu(GeospatialActivity.this, v);
            popup.setOnMenuItemClickListener(GeospatialActivity.this::settingsMenuClick);
            popup.inflate(R.menu.setting_menu);
            boolean recording = isRecording();
            popup
                .getMenu()
                .findItem(R.id.record_session)
                .setTitle(recording ? "Stop recording" : "Record session")
                .setVisible(playbackName == null);
            popup.getMenu().findItem(R.id.play_recording).setVisible(!recording);
            popup.getMenu().findItem(R.id.live_camera).setVisible(playbackName != null);
            popup.show();
            popup
                .getMenu()
                .findItem(sharedPreferences.getInt(ANCHOR_MODE, R.id.geospatial))
                .setChecked(true);
          }
        });

    clearAnchorsButton.setOnClickListener(view -> handleClearAnchorsButton());

    streetscapeGeometrySwitch = findViewById(R.id.streetscape_geometry_switch);
    // Initial terrain anchor mode is DISABLED.
    streetscapeGeometrySwitch.setChecked(false);
    streetscapeGeometrySwitch.setOnCheckedChangeListener(this::onRenderStreetscapeGeometryChanged);

    displayRotationHelper = new DisplayRotationHelper(/* activity= */ this);

    // Set up renderer.
    render = new SampleRender(surfaceView, this, getAssets());

    installRequested = false;
    clearedAnchorsAmount = null;

    // Set up touch listener.
    gestureDetector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                synchronized (singleTapLock) {
                  queuedSingleTap = e;
                }
                return true;
              }

              @Override
              public boolean onDown(MotionEvent e) {
                return true;
              }
            });
    surfaceView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(/* context= */ this);
  }

  @Override
  protected void onDestroy() {
    if (session != null) {
      // Explicitly close ARCore Session to release native resources.
      // Review the API reference for important considerations before calling close() in apps with
      // more complicated lifecycle requirements:
      // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
      session.close();
      session = null;
    }

    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (sharedPreferences.getBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, /* defValue= */ false)) {
      createSession();
    } else {
      showPrivacyNoticeDialog();
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  private void showPrivacyNoticeDialog() {
    DialogFragment dialog = PrivacyNoticeDialogFragment.createDialog();
    dialog.show(getSupportFragmentManager(), PrivacyNoticeDialogFragment.class.getName());
  }

  private void createSession() {
    Exception exception = null;
    String message = null;
    if (session == null) {

      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }
        if (!LocationPermissionHelper.hasFineLocationPermission(this)) {
          LocationPermissionHelper.requestFineLocationPermission(this);
          return;
        }

        // Create the session.
        // Plane finding mode is default on, which will help the dynamic alignment of terrain
        // anchors on ground.
        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }
    // Check VPS availability before configure and resume session.
    if (session != null) {
      getCurrentLocation();
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      configureSession();
      // To record a live camera session for later playback, call
      // `session.startRecording(recordingConfig)` at anytime. To playback a previously recorded AR
      // session instead of using the live camera feed, call
      // `session.setPlaybackDatasetUri(Uri)` before calling `session.resume()`. To
      // learn more about recording and playback, see:
      // https://developers.google.com/ar/develop/java/recording-and-playback
      session.resume();
    } catch (CameraNotAvailableException e) {
      message = "Camera not available. Try restarting the app.";
      exception = e;
    } catch (GooglePlayServicesLocationLibraryNotLinkedException e) {
      message = "Google Play Services location library not linked or obfuscated with Proguard.";
      exception = e;
    } catch (FineLocationPermissionNotGrantedException e) {
      message = "The Android permission ACCESS_FINE_LOCATION was not granted.";
      exception = e;
    } catch (UnsupportedConfigurationException e) {
      message = "This device does not support GeospatialMode.ENABLED.";
      exception = e;
    } catch (SecurityException e) {
      message = "Camera failure or the internet permission has not been granted.";
      exception = e;
    }

    if (message != null) {
      session = null;
      messageSnackbarHelper.showError(this, message);
      Log.e(TAG, "Exception configuring and resuming the session", exception);
      return;
    }
  }

  private void getCurrentLocation() {
    try {
      fusedLocationClient
          .getCurrentLocation(
              com.google.android.gms.location.LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
              /* cancellationToken= */ null)
          .addOnSuccessListener(
              new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                  if (location != null) {
                    checkVpsAvailability(location.getLatitude(), location.getLongitude());
                  } else {
                    Log.e(TAG, "Failed to get current location fix: location is null.");
                  }
                }
              });
    } catch (SecurityException e) {
      Log.e(TAG, "No location permissions granted by User!");
    }
  }

  private void checkVpsAvailability(double latitude, double longitude) {
    Log.i(
        TAG,
        String.format(
            Locale.ROOT, "Checking VPS availability for lat/lng: %.6f, %.6f", latitude, longitude));
    final VpsAvailabilityFuture future =
        session.checkVpsAvailabilityAsync(
            latitude,
            longitude,
            availability -> {
              if (availability != VpsAvailability.AVAILABLE) {
                Log.e(
                    TAG,
                    String.format(
                        Locale.ROOT,
                        "VPS is unavailable at lat/lng: %.6f, %.6f (status: %s)",
                        latitude,
                        longitude,
                        availability));
                showVpsNotAvailabilityNoticeDialog();
              }
            });
  }

  private void showVpsNotAvailabilityNoticeDialog() {
    DialogFragment dialog = VpsAvailabilityNoticeDialogFragment.createDialog();
    dialog.show(getSupportFragmentManager(), VpsAvailabilityNoticeDialogFragment.class.getName());
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
    // Check if this result pertains to the location permission.
    if (LocationPermissionHelper.hasFineLocationPermissionsResponseInResult(permissions)
        && !LocationPermissionHelper.hasFineLocationPermission(this)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(
              this,
              "Precise location permission is needed to run this application",
              Toast.LENGTH_LONG)
          .show();
      if (!LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        LocationPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(SampleRender render) {
    // A new GL context means a new camera texture; hand it to the session on the next frame.
    // Without this, resuming after a GL pause (app switch, playback) throws
    // TextureNotSetException from session.update().
    hasSetTextureNames = false;
    // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
    // an IOException.
    try {
      planeRenderer = new PlaneRenderer(render);
      floodRenderer = new FloodRenderer(render);
      backgroundRenderer = new BackgroundRenderer(render);
      virtualSceneFramebuffer = new Framebuffer(render, /* width= */ 1, /* height= */ 1);

      // Virtual object to render (ARCore geospatial)
      Texture virtualObjectTexture =
          Texture.createFromAsset(
              render,
              "models/spatial_marker_baked.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.SRGB);

      virtualObjectMesh = Mesh.createFromAsset(render, "models/geospatial_marker.obj");
      geospatialAnchorVirtualObjectShader =
          Shader.createFromAssets(
                  render,
                  "shaders/ar_unlit_object.vert",
                  "shaders/ar_unlit_object.frag",
                  /* defines= */ null)
              .setTexture("u_Texture", virtualObjectTexture);

      // Virtual object to render (Terrain anchor marker)
      Texture terrainAnchorVirtualObjectTexture =
          Texture.createFromAsset(
              render,
              "models/spatial_marker_yellow.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.SRGB);
      terrainAnchorVirtualObjectShader =
          Shader.createFromAssets(
                  render,
                  "shaders/ar_unlit_object.vert",
                  "shaders/ar_unlit_object.frag",
                  /* defines= */ null)
              .setTexture("u_Texture", terrainAnchorVirtualObjectTexture);

      backgroundRenderer.setUseDepthVisualization(render, false);
      backgroundRenderer.setUseOcclusion(render, false);

      // Point cloud
      pointCloudShader =
          Shader.createFromAssets(
                  render,
                  "shaders/point_cloud.vert",
                  "shaders/point_cloud.frag",
                  /* defines= */ null)
              .setVec4(
                  "u_Color", new float[] {31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
              .setFloat("u_PointSize", 5.0f);
      // four entries per vertex: X, Y, Z, confidence
      pointCloudVertexBuffer =
          new VertexBuffer(render, /* numberOfEntriesPerVertex= */ 4, /* entries= */ null);
      final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
      pointCloudMesh =
          new Mesh(
              render, Mesh.PrimitiveMode.POINTS, /* indexBuffer= */ null, pointCloudVertexBuffers);

      streetscapeGeometryBuildingShader =
          Shader.createFromAssets(
                  render,
                  "shaders/streetscape_geometry.vert",
                  "shaders/streetscape_geometry.frag",
                  /* defines= */ null)
              .setBlend(
                  BlendFactor.DST_ALPHA, // RGB (src)
                  BlendFactor.ONE); // ALPHA (dest)

      streetscapeGeometryTerrainShader =
          Shader.createFromAssets(
                  render,
                  "shaders/streetscape_geometry.vert",
                  "shaders/streetscape_geometry.frag",
                  /* defines= */ null)
              .setBlend(
                  BlendFactor.DST_ALPHA, // RGB (src)
                  BlendFactor.ONE); // ALPHA (dest)
      wallsColor.add(new float[] {0.5f, 0.0f, 0.5f, 0.3f});
      wallsColor.add(new float[] {0.5f, 0.5f, 0.0f, 0.3f});
      wallsColor.add(new float[] {0.0f, 0.5f, 0.5f, 0.3f});
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
    }
  }

  @Override
  public void onSurfaceChanged(SampleRender render, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    virtualSceneFramebuffer.resize(width, height);
  }

  @Override
  public void onDrawFrame(SampleRender render) {
    if (session == null) {
      return;
    }

    // Texture names should only be set once on a GL thread unless they change. This is done during
    // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(
          new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
      hasSetTextureNames = true;
    }

    // -- Update per-frame state

    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);
    updateStreetscapeGeometries(session.getAllTrackables(StreetscapeGeometry.class));

    // Obtain the current frame from ARSession. When the configuration is set to
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    // camera framerate.
    Frame frame;
    try {
      frame = session.update();
    } catch (CameraNotAvailableException e) {
      Log.e(TAG, "Camera not available during onDrawFrame", e);
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      return;
    } catch (TextureNotSetException e) {
      // Session lost its camera texture (e.g. after switching to/from playback); rebind next frame.
      Log.w(TAG, "Camera texture not set; rebinding", e);
      hasSetTextureNames = false;
      return;
    }
    Camera camera = frame.getCamera();

    if (playbackName != null
        && !playbackFinishedShown
        && session.getPlaybackStatus() == PlaybackStatus.FINISHED) {
      playbackFinishedShown = true;
      runOnUiThread(
          () ->
              Toast.makeText(
                      this,
                      "Playback finished — menu → Play back recording… to replay",
                      Toast.LENGTH_LONG)
                  .show());
    }

    // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
    // used to draw the background camera image.
    backgroundRenderer.updateDisplayGeometry(frame);

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

    Earth earth = session.getEarth();
    if (earth != null) {
      updateGeospatialState(earth);
    }

    // Show a message based on whether tracking has failed, if planes are detected, and if the user
    // has placed any objects.
    String message = null;
    switch (state) {
      case UNINITIALIZED:
        break;
      case UNSUPPORTED:
        message = getResources().getString(R.string.status_unsupported);
        break;
      case PRETRACKING:
        message = getResources().getString(R.string.status_pretracking);
        break;
      case EARTH_STATE_ERROR:
        message =
            getResources().getString(R.string.status_earth_state_error)
                + "\n("
                + lastEarthErrorState
                + ")";
        break;
      case LOCALIZING:
        message = getResources().getString(R.string.status_localize_hint);
        break;
      case LOCALIZING_FAILED:
        message = getResources().getString(R.string.status_localize_timeout);
        break;
      case LOCALIZED:
        if (lastStatusText.equals(getResources().getString(R.string.status_localize_hint))
            || lastStatusText.equals(getResources().getString(R.string.status_localize_timeout))) {
          message = getResources().getString(R.string.status_localize_complete);
        }
        break;
    }

    if (message != null && lastStatusText != message) {
      lastStatusText = message;
      runOnUiThread(
          () -> {
            statusTextView.setVisibility(View.VISIBLE);
            statusTextView.setText(lastStatusText);
          });
    }
    synchronized (anchorsLock) {
      if (anchors.size() >= MAXIMUM_ANCHORS) {
        runOnUiThread(
            () -> {
              tapScreenTextView.setVisibility(View.INVISIBLE);
            });
      }
    }

    // Handle user input.
    handleTap(frame, camera.getTrackingState());

    // -- Draw background

    if (frame.getTimestamp() != 0) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render);
    }

    // If not tracking, don't draw 3D objects. Planes and flood only need camera tracking;
    // geospatial anchors below also need VPS localization.
    if (camera.getTrackingState() != TrackingState.TRACKING) {
      return;
    }

    // -- Draw virtual objects

    // Get projection matrix.
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

    // Get camera matrix and draw.
    camera.getViewMatrix(viewMatrix, 0);

    // Visualize tracked points.
    // Use try-with-resources to automatically release the point cloud.
    try (PointCloud pointCloud = frame.acquirePointCloud()) {
      if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
        pointCloudVertexBuffer.set(pointCloud.getPoints());
        lastPointCloudTimestamp = pointCloud.getTimestamp();
      }
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
      pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
      render.draw(pointCloudMesh, pointCloudShader);
    }

    // Visualize planes.
    planeRenderer.drawPlanes(
        render,
        session.getAllTrackables(Plane.class),
        camera.getDisplayOrientedPose(),
        projectionMatrix);

    drawFlood(frame, camera);

    if (state != State.LOCALIZED) {
      return;
    }

    // Visualize anchors created by touch.
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);

    // -- Draw Streetscape Geometries.
    if (isRenderStreetscapeGeometry) {
      int index = 0;
      for (Map.Entry<StreetscapeGeometry, Mesh> set : streetscapeGeometryToMeshes.entrySet()) {
        StreetscapeGeometry streetscapeGeometry = set.getKey();
        if (streetscapeGeometry.getTrackingState() != TrackingState.TRACKING) {
          continue;
        }
        Mesh mesh = set.getValue();
        Pose pose = streetscapeGeometry.getMeshPose();
        pose.toMatrix(modelMatrix, 0);

        // Calculate model/view/projection matrices
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

        if (streetscapeGeometry.getType() == StreetscapeGeometry.Type.BUILDING) {
          float[] color = wallsColor.get(index % wallsColor.size());
          index += 1;
          streetscapeGeometryBuildingShader
              .setVec4(
                  "u_Color",
                  new float[] {/* r= */ color[0], /* g= */ color[1], /* b= */ color[2], color[3]})
              .setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
          render.draw(mesh, streetscapeGeometryBuildingShader);
        } else if (streetscapeGeometry.getType() == StreetscapeGeometry.Type.TERRAIN) {
          streetscapeGeometryTerrainShader
              .setVec4("u_Color", new float[] {/* r= */ 0f, /* g= */ .5f, /* b= */ 0f, 0.3f})
              .setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
          render.draw(mesh, streetscapeGeometryTerrainShader);
        }
      }
    }
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
    synchronized (anchorsLock) {
      for (Anchor anchor : anchors) {
        // Get the current pose of an Anchor in world space. The Anchor pose is updated
        // during calls to session.update() as ARCore refines its estimate of the world.
        // Only render resolved Terrain & Rooftop anchors and Geospatial anchors.
        if (anchor.getTrackingState() != TrackingState.TRACKING) {
          continue;
        }
        anchor.getPose().toMatrix(modelMatrix, 0);
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        float scale = getScale(anchor.getPose(), camera.getDisplayOrientedPose());
        scaleMatrix[0] = scale;
        scaleMatrix[5] = scale;
        scaleMatrix[10] = scale;
        Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
        // Rotate the virtual object 180 degrees around the Y axis to make the object face the GL
        // camera -Z axis, since camera Z axis faces toward users.
        float[] rotationMatrix = new float[16];
        Matrix.setRotateM(rotationMatrix, 0, 180, 0.0f, 1.0f, 0.0f);
        float[] rotationModelMatrix = new float[16];
        Matrix.multiplyMM(rotationModelMatrix, 0, modelMatrix, 0, rotationMatrix, 0);
        // Calculate model/view/projection matrices
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, rotationModelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

        // Update shader properties and draw
        if (terrainAnchors.contains(anchor) || rooftopAnchors.contains(anchor)) {
          terrainAnchorVirtualObjectShader.setMat4(
              "u_ModelViewProjection", modelViewProjectionMatrix);

          render.draw(virtualObjectMesh, terrainAnchorVirtualObjectShader, virtualSceneFramebuffer);
        } else {
          geospatialAnchorVirtualObjectShader.setMat4(
              "u_ModelViewProjection", modelViewProjectionMatrix);
          render.draw(
              virtualObjectMesh, geospatialAnchorVirtualObjectShader, virtualSceneFramebuffer);
        }
      }
      if (anchors.size() > 0) {
        String anchorMessage =
            getResources()
                .getQuantityString(
                    R.plurals.status_anchors_set, anchors.size(), anchors.size(), MAXIMUM_ANCHORS);
        runOnUiThread(
            () -> {
              statusTextView.setVisibility(View.VISIBLE);
              statusTextView.setText(anchorMessage);
            });
      }
    }

    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
  }

  /** Draws the selected flood scenario relative to the current ground estimate. */
  private void drawFlood(Frame frame, Camera camera) {
    FloodSite site = selectedSite;
    FloodSite.Scenario scenario = selectedScenario;
    if (site == null || scenario == null) {
      return;
    }
    Pose cameraPose = camera.getPose();
    float[] cameraPos = cameraPose.getTranslation();
    updateGround(frame, cameraPose);

    // Depth is measured from the scenario's reference surface: the tapped point if set (porch,
    // street...), otherwise the ground under the phone.
    boolean anchored =
        groundAnchor != null && groundAnchor.getTrackingState() == TrackingState.TRACKING;
    float referenceY = anchored ? groundAnchor.getPose().ty() : groundY;
    float depth = (float) scenario.depthMeters();
    float waterY = referenceY + depth;

    // Opaque poles first, then translucent water on buildings/terrain, then the surface.
    if (anchored) {
      Pose base = groundAnchor.getPose();
      if (site.marker != null) {
        floodRenderer.drawMarker(render, viewMatrix, projectionMatrix, base, site.marker);
      }
      floodRenderer.drawGauge(render, viewMatrix, projectionMatrix, base, depth, cameraPos);
    }
    floodRenderer.drawStreetscape(
        render, streetscapeGeometryToMeshes, viewMatrix, projectionMatrix, waterY, cameraPos);
    if (!scenario.isDry()) {
      floodRenderer.drawSurface(render, viewMatrix, projectionMatrix, waterY, cameraPos);
    }
    updateFloodHud(
        site, scenario, anchored, cameraPos[1] - groundY, waterY - groundY, referenceY - groundY);
  }

  /**
   * Keeps {@link #groundY} (the ground under the phone) current: every few frames cast a ray
   * straight down from the camera and take the first horizontal plane or Streetscape terrain
   * hit, smoothed. With no hit yet, assume the phone is held at chest height.
   */
  private void updateGround(Frame frame, Pose cameraPose) {
    if (groundY == null) {
      groundY = cameraPose.ty() - ASSUMED_PHONE_HEIGHT_M;
      groundSource = "assumed 1.4 m below phone";
    }
    if (groundProbeFrame++ % 10 != 0) {
      return;
    }
    float[] origin = cameraPose.getTranslation();
    float[] down = {0f, -1f, 0f};
    HitResult hit = pickGroundHit(frame.hitTest(origin, 0, down, 0));
    if (hit != null) {
      String source = isUpwardPlaneHit(hit) ? SOURCE_PLANE : SOURCE_TERRAIN;
      float y = hit.getHitPose().ty();
      // Restart smoothing when the source changes so plane and mesh heights never blend.
      groundY = source.equals(groundSource) ? 0.8f * groundY + 0.2f * y : y;
      groundSource = source;
    }
  }

  private void updateFloodHud(
      FloodSite site,
      FloodSite.Scenario scenario,
      boolean anchored,
      float phoneHeightM,
      float feetDepthM,
      float referenceAboveFeetM) {
    long now = System.currentTimeMillis();
    if (now - lastFloodHudMillis < 250) {
      return;
    }
    lastFloodHudMillis = now;
    int planes = 0;
    float area = 0f;
    for (Plane p : session.getAllTrackables(Plane.class)) {
      if (p.getTrackingState() == TrackingState.TRACKING
          && p.getSubsumedBy() == null
          && p.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING) {
        planes++;
        area += p.getExtentX() * p.getExtentZ();
      }
    }
    int buildings = 0;
    for (StreetscapeGeometry g : streetscapeGeometryToMeshes.keySet()) {
      if (g.getTrackingState() == TrackingState.TRACKING
          && g.getType() == StreetscapeGeometry.Type.BUILDING) {
        buildings++;
      }
    }
    String surface = scenario.reference; // "ground", "street", "porch"
    String depthText =
        scenario.isDry()
            ? String.format(
                Locale.US, "dry (water %.1f ft below %s)", -scenario.depthFt, surface)
            : String.format(Locale.US, "%s above %s", formatFeetInches(scenario.depthFt), surface);
    String reference =
        anchored
            ? "tapped " + surface + " (" + referenceSource + ")"
            : groundSource + " — tap the " + surface + " to lock";
    boolean feetDetected =
        groundSource.equals(SOURCE_PLANE) || groundSource.equals(SOURCE_TERRAIN);
    String feet =
        !feetDetected
            ? "— (no ground detected under you)"
            : feetDepthM <= 0
                ? String.format(Locale.US, "dry (%s below you)", formatFeetInches(-feetDepthM / 0.3048))
                : formatFeetInches(feetDepthM / 0.3048);
    // Height of the tapped point relative to the ground under the phone: a direct measurement
    // of e.g. porch-above-street.
    String tapped = "";
    if (anchored && feetDetected) {
      double inches = referenceAboveFeetM / 0.0254;
      tapped =
          String.format(
              Locale.US,
              "\nTapped point: %s (%.0f in) %s the ground under you",
              formatFeetInches(Math.abs(inches) / 12),
              Math.abs(inches),
              inches >= 0 ? "above" : "below");
    }
    String text =
        String.format(
            Locale.US,
            "%s%s — %s\n%s: %s\nReference: %s%s\nWater at your feet (%s): %s  (phone %.1f ft up)\n"
                + "Planes: %d (%.0f m²)  Buildings: %d  VPS: %s",
            isRecording() ? "● REC  " : (playbackName != null ? "▶ REPLAY  " : ""),
            site.area,
            site.name,
            scenario.label,
            depthText,
            reference,
            tapped,
            feetDetected ? groundSource : "none",
            feet,
            phoneHeightM / 0.3048f,
            planes,
            area,
            buildings,
            state);
    runOnUiThread(
        () -> {
          floodInfoView.setVisibility(View.VISIBLE);
          floodInfoView.setText(text);
        });
  }

  /** 0.35 -> "4 in", 1.75 -> "1 ft 9 in", 7.8 -> "7 ft 10 in". */
  private static String formatFeetInches(double feet) {
    long inches = Math.round(feet * 12);
    if (inches < 12) {
      return inches + " in";
    }
    return (inches / 12) + " ft " + (inches % 12) + " in";
  }

  /**
   * Updates all the StreetscapeGeometries. Existing StreetscapeGeometries will have pose updated,
   * and non-existing StreetscapeGeometries will be removed from the scene.
   */
  private void updateStreetscapeGeometries(Collection<StreetscapeGeometry> streetscapeGeometries) {
    for (StreetscapeGeometry streetscapeGeometry : streetscapeGeometries) {
      // If the Streetscape Geometry node is already added to the scene, then we'll simply update
      // the pose.
      if (streetscapeGeometryToMeshes.containsKey(streetscapeGeometry)) {
      } else {
        // Otherwise, we create a StreetscapeGeometry mesh and add it to the scene.
        Mesh mesh = getSampleRenderMesh(streetscapeGeometry);
        streetscapeGeometryToMeshes.put(streetscapeGeometry, mesh);
      }
    }
  }

  private Mesh getSampleRenderMesh(StreetscapeGeometry streetscapeGeometry) {
    FloatBuffer streetscapeGeometryBuffer = streetscapeGeometry.getMesh().getVertexList();
    streetscapeGeometryBuffer.rewind();
    VertexBuffer meshVertexBuffer =
        new VertexBuffer(
            render, /* numberOfEntriesPerVertex= */ 3, /* entries= */ streetscapeGeometryBuffer);
    IndexBuffer meshIndexBuffer =
        new IndexBuffer(render, streetscapeGeometry.getMesh().getIndexList());
    final VertexBuffer[] meshVertexBuffers = {meshVertexBuffer};
    return new Mesh(
        render,
        Mesh.PrimitiveMode.TRIANGLES,
        /* indexBuffer= */ meshIndexBuffer,
        meshVertexBuffers);
  }

  /** Configures the session with feature settings. */
  private void configureSession() {
    // Earth mode may not be supported on this device due to insufficient sensor quality.
    if (!session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
      state = State.UNSUPPORTED;
      return;
    }

    Config config = session.getConfig();
    config =
        config
            .setGeospatialMode(Config.GeospatialMode.ENABLED)
            .setStreetscapeGeometryMode(Config.StreetscapeGeometryMode.ENABLED);
    session.configure(config);
    state = State.PRETRACKING;
    localizingStartTimestamp = System.currentTimeMillis();
  }

  /** Change behavior depending on the current {@link State} of the application. */
  private void updateGeospatialState(Earth earth) {
    Earth.EarthState earthState = earth.getEarthState();
    if (earthState != Earth.EarthState.ENABLED) {
      if (state != State.EARTH_STATE_ERROR || earthState != lastEarthErrorState) {
        Log.e(TAG, "Earth state error: " + earthState);
        lastEarthErrorState = earthState;
      }
      state = State.EARTH_STATE_ERROR;
      return;
    }
    if (earth.getTrackingState() != TrackingState.TRACKING) {
      state = State.PRETRACKING;
      return;
    }
    if (state == State.PRETRACKING) {
      updatePretrackingState(earth);
    } else if (state == State.LOCALIZING || state == State.LOCALIZING_FAILED) {
      // Keep trying after the timeout: outdoors, VPS often succeeds once the camera finally
      // sees buildings. The timeout only changes the hint shown to the user.
      updateLocalizingState(earth);
    } else if (state == State.LOCALIZED) {
      updateLocalizedState(earth);
    }
  }

  /**
   * Handles the updating for {@link State.PRETRACKING}. In this state, wait for {@link Earth} to
   * have {@link TrackingState.TRACKING}. If it hasn't been enabled by now, then we've encountered
   * an unrecoverable {@link State.EARTH_STATE_ERROR}.
   */
  private void updatePretrackingState(Earth earth) {
    if (earth.getTrackingState() == TrackingState.TRACKING) {
      state = State.LOCALIZING;
      return;
    }

    runOnUiThread(() -> geospatialPoseTextView.setText(R.string.geospatial_pose_not_tracking));
  }

  /**
   * Handles the updating for {@link State.LOCALIZING}. In this state, wait for the horizontal and
   * orientation threshold to improve until it reaches your threshold.
   *
   * <p>If it takes too long for the threshold to be reached, this could mean that GPS data isn't
   * accurate enough, or that the user is in an area that can't be localized with StreetView.
   */
  private void updateLocalizingState(Earth earth) {
    GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
    if (geospatialPose.getHorizontalAccuracy() <= LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS
        && geospatialPose.getOrientationYawAccuracy()
            <= LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES) {
      state = State.LOCALIZED;
      synchronized (anchorsLock) {
        final int anchorNum = anchors.size();
        if (anchorNum == 0) {
          createAnchorFromSharedPreferences(earth);
        }
        if (anchorNum < MAXIMUM_ANCHORS) {
          runOnUiThread(
              () -> {
                setAnchorButton.setVisibility(View.VISIBLE);
                tapScreenTextView.setVisibility(View.VISIBLE);
                if (anchorNum > 0) {
                  clearAnchorsButton.setVisibility(View.VISIBLE);
                }
              });
        }
      }
      return;
    }

    if (state == State.LOCALIZING
        && TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - localizingStartTimestamp)
            > LOCALIZING_TIMEOUT_SECONDS) {
      state = State.LOCALIZING_FAILED;
      return;
    }

    updateGeospatialPoseText(geospatialPose);
  }

  /**
   * Handles the updating for {@link State.LOCALIZED}. In this state, check the accuracy for
   * degradation and return to {@link State.LOCALIZING} if the position accuracies have dropped too
   * low.
   */
  private void updateLocalizedState(Earth earth) {
    GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
    // Check if either accuracy has degraded to the point we should enter back into the LOCALIZING
    // state.
    if (geospatialPose.getHorizontalAccuracy()
            > LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS
                + LOCALIZED_HORIZONTAL_ACCURACY_HYSTERESIS_METERS
        || geospatialPose.getOrientationYawAccuracy()
            > LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES
                + LOCALIZED_ORIENTATION_YAW_ACCURACY_HYSTERESIS_DEGREES) {
      // Accuracies have degenerated, return to the localizing state.
      state = State.LOCALIZING;
      localizingStartTimestamp = System.currentTimeMillis();
      runOnUiThread(
          () -> {
            tapScreenTextView.setVisibility(View.INVISIBLE);
            clearAnchorsButton.setVisibility(View.INVISIBLE);
          });
      return;
    }

    updateGeospatialPoseText(geospatialPose);
  }

  private void updateGeospatialPoseText(GeospatialPose geospatialPose) {
    float[] quaternion = geospatialPose.getEastUpSouthQuaternion();
    String poseText =
        getResources()
            .getString(
                R.string.geospatial_pose,
                geospatialPose.getLatitude(),
                geospatialPose.getLongitude(),
                geospatialPose.getHorizontalAccuracy(),
                geospatialPose.getAltitude(),
                geospatialPose.getVerticalAccuracy(),
                quaternion[0],
                quaternion[1],
                quaternion[2],
                quaternion[3],
                geospatialPose.getOrientationYawAccuracy());
    runOnUiThread(
        () -> {
          geospatialPoseTextView.setText(poseText);
        });
  }

  // Return the scale in range [1, 2] after mapping a distance between camera and anchor to [2, 20].
  private float getScale(Pose anchorPose, Pose cameraPose) {
    double distance =
        Math.sqrt(
            Math.pow(anchorPose.tx() - cameraPose.tx(), 2.0)
                + Math.pow(anchorPose.ty() - cameraPose.ty(), 2.0)
                + Math.pow(anchorPose.tz() - cameraPose.tz(), 2.0));
    double mapDistance = Math.min(Math.max(2, distance), 20);
    return (float) (mapDistance - 2) / (20 - 2) + 1;
  }

  /**
   * Handles the button that creates an anchor.
   *
   * <p>Ensure Earth is in the proper state, then create the anchor. Persist the parameters used to
   * create the anchors so that the anchors will be loaded next time the app is launched.
   */
  private void handleSetAnchorButton() {}

  private File recordingsDir() {
    File dir = new File(getExternalFilesDir(null), "recordings");
    dir.mkdirs();
    return dir;
  }

  private boolean isRecording() {
    return session != null && session.getRecordingStatus() == RecordingStatus.OK;
  }

  /**
   * Records camera + sensor data to an MP4 under
   * /sdcard/Android/data/com.compact.floodar/files/recordings/ for later in-app playback.
   */
  private void startRecording() {
    if (session == null) {
      return;
    }
    String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    currentRecording = new File(recordingsDir(), "floodar-" + stamp + ".mp4");
    try {
      session.startRecording(
          new RecordingConfig(session)
              .setMp4DatasetUri(Uri.fromFile(currentRecording))
              .setAutoStopOnPause(true));
      Toast.makeText(this, "Recording session…", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Log.e(TAG, "Failed to start recording", e);
      Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
      currentRecording = null;
    }
  }

  private void stopRecording() {
    try {
      session.stopRecording();
      Toast.makeText(
              this, "Saved " + (currentRecording != null ? currentRecording.getName() : ""),
              Toast.LENGTH_LONG)
          .show();
    } catch (Exception e) {
      Log.e(TAG, "Failed to stop recording", e);
      Toast.makeText(this, "Stop failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void showRecordingPicker() {
    File[] files = recordingsDir().listFiles((dir, name) -> name.endsWith(".mp4"));
    if (files == null || files.length == 0) {
      Toast.makeText(this, "No recordings yet", Toast.LENGTH_SHORT).show();
      return;
    }
    Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName())); // newest first
    String[] names = new String[files.length];
    for (int i = 0; i < files.length; i++) {
      names[i] =
          String.format(
              Locale.US, "%s (%.0f MB)", files[i].getName(), files[i].length() / 1e6);
    }
    new AlertDialog.Builder(this)
        .setTitle("Play back recording")
        .setItems(names, (dialog, which) -> restartSessionWithPlayback(files[which]))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  /**
   * Switches between a recorded dataset and the live camera (file == null). The GL surface is
   * paused first so onDrawFrame never calls update() on a paused session.
   *
   * <p>Playback: set the dataset on the paused session. Live: ARCore 1.56 throws on
   * setPlaybackDatasetUri(null), so close the session and create a fresh one.
   */
  private void restartSessionWithPlayback(File file) {
    if (session == null) {
      return;
    }
    surfaceView.onPause();
    session.pause();

    // Anything tied to the old camera feed / session is stale.
    if (groundAnchor != null) {
      groundAnchor.detach();
      groundAnchor = null;
    }
    groundY = null;
    groundSource = "searching";
    hasSetTextureNames = false; // changing the dataset drops the camera texture binding
    playbackFinishedShown = false;
    state = State.PRETRACKING;
    localizingStartTimestamp = System.currentTimeMillis();

    if (file == null) {
      session.close();
      session = null;
      synchronized (anchorsLock) {
        anchors.clear();
        terrainAnchors.clear();
        rooftopAnchors.clear();
      }
      streetscapeGeometryToMeshes.clear();
      playbackName = null;
      createSession(); // creates, configures and resumes a live session
      surfaceView.onResume();
      return;
    }

    try {
      session.setPlaybackDatasetUri(Uri.fromFile(file));
      playbackName = file.getName();
    } catch (Exception e) {
      Log.e(TAG, "Failed to set playback dataset", e);
      Toast.makeText(this, "Playback failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
      playbackName = null;
    }
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      Log.e(TAG, "Camera not available on resume", e);
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      return;
    }
    surfaceView.onResume();
  }

  /** Loads flood sites and restores the last selection. */
  private void loadFloodSites() {
    try {
      floodSites = FloodSite.loadAll(this);
    } catch (IOException | JSONException e) {
      Log.e(TAG, "Failed to load flood sites", e);
      Toast.makeText(this, "Could not load flood sites: " + e, Toast.LENGTH_LONG).show();
      return;
    }
    selectedSite = FloodSite.find(floodSites, sharedPreferences.getString(FLOOD_SITE_ID, ""));
    if (selectedSite != null) {
      selectedScenario =
          selectedSite.findScenario(sharedPreferences.getString(FLOOD_SCENARIO_ID, ""));
    }
  }

  /** Two-step picker: site, then scenario at that site. */
  private void showFloodSitePicker() {
    if (floodSites.isEmpty()) {
      Toast.makeText(this, "No flood sites loaded", Toast.LENGTH_SHORT).show();
      return;
    }
    String[] names = new String[floodSites.size()];
    int checked = -1;
    for (int i = 0; i < floodSites.size(); i++) {
      FloodSite site = floodSites.get(i);
      names[i] = site.area + ": " + site.name;
      if (site == selectedSite) {
        checked = i;
      }
    }
    new AlertDialog.Builder(this)
        .setTitle("Flood site")
        .setSingleChoiceItems(
            names,
            checked,
            (dialog, which) -> {
              dialog.dismiss();
              showScenarioPicker(floodSites.get(which));
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showScenarioPicker(FloodSite site) {
    if (site.scenarios.isEmpty()) {
      Toast.makeText(this, "No flood data for " + site.name, Toast.LENGTH_SHORT).show();
      return;
    }
    String[] labels = new String[site.scenarios.size()];
    int checked = -1;
    for (int i = 0; i < site.scenarios.size(); i++) {
      FloodSite.Scenario sc = site.scenarios.get(i);
      labels[i] = sc.label + " — " + describeDepth(sc);
      if (site == selectedSite && sc == selectedScenario) {
        checked = i;
      }
    }
    new AlertDialog.Builder(this)
        .setTitle(site.name)
        .setSingleChoiceItems(
            labels,
            checked,
            (dialog, which) -> {
              dialog.dismiss();
              selectedSite = site;
              selectedScenario = site.scenarios.get(which);
              sharedPreferences
                  .edit()
                  .putString(FLOOD_SITE_ID, site.id)
                  .putString(FLOOD_SCENARIO_ID, selectedScenario.id)
                  .apply();
              Toast.makeText(
                      this,
                      site.name
                          + "\n"
                          + selectedScenario.label
                          + ": "
                          + describeDepth(selectedScenario)
                          + "\nTap the "
                          + selectedScenario.reference
                          + " to set the reference",
                      Toast.LENGTH_LONG)
                  .show();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private static String describeDepth(FloodSite.Scenario sc) {
    if (sc.isDry()) {
      return String.format(Locale.US, "dry (%.1f ft below %s)", -sc.depthFt, sc.reference);
    }
    return formatFeetInches(sc.depthFt) + " above " + sc.reference;
  }

  /** Menu button to choose anchor type. */
  protected boolean settingsMenuClick(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.anchor_reset) {
      return true;
    }
    if (itemId == R.id.flood_site) {
      showFloodSitePicker();
      return true;
    }
    if (itemId == R.id.record_session) {
      if (isRecording()) {
        stopRecording();
      } else {
        startRecording();
      }
      return true;
    }
    if (itemId == R.id.play_recording) {
      showRecordingPicker();
      return true;
    }
    if (itemId == R.id.live_camera) {
      restartSessionWithPlayback(null);
      return true;
    }
    item.setChecked(!item.isChecked());
    sharedPreferences.edit().putInt(ANCHOR_MODE, itemId).commit();
    if (itemId == R.id.geospatial) {
      anchorType = AnchorType.GEOSPATIAL;
      return true;
    } else if (itemId == R.id.terrain) {
      anchorType = AnchorType.TERRAIN;
      return true;
    } else if (itemId == R.id.rooftop) {
      anchorType = AnchorType.ROOFTOP;
      return true;
    }
    return false;
  }

  /** Creates anchor with the provided GeospatialPose, either from camera or HitResult. */
  private void createAnchorWithGeospatialPose(Earth earth, GeospatialPose geospatialPose) {
    double latitude = geospatialPose.getLatitude();
    double longitude = geospatialPose.getLongitude();
    double altitude = geospatialPose.getAltitude();
    float[] quaternion = geospatialPose.getEastUpSouthQuaternion();
    switch (anchorType) {
      case TERRAIN:
        createTerrainAnchor(earth, latitude, longitude, identityQuaternion);
        storeAnchorParameters(latitude, longitude, 0, identityQuaternion);
        break;
      case GEOSPATIAL:
        createAnchor(earth, latitude, longitude, altitude, quaternion);
        storeAnchorParameters(latitude, longitude, altitude, quaternion);
        break;
      case ROOFTOP:
        createRooftopAnchor(earth, latitude, longitude, identityQuaternion);
        storeAnchorParameters(latitude, longitude, 0, identityQuaternion);
        break;
    }
    runOnUiThread(
        () -> {
          clearAnchorsButton.setVisibility(View.VISIBLE);
        });
    if (clearedAnchorsAmount != null) {
      clearedAnchorsAmount = null;
    }
  }

  private void handleClearAnchorsButton() {
    synchronized (anchorsLock) {
      clearedAnchorsAmount = anchors.size();
      String message =
          getResources()
              .getQuantityString(
                  R.plurals.status_anchors_cleared, clearedAnchorsAmount, clearedAnchorsAmount);

      statusTextView.setVisibility(View.VISIBLE);
      statusTextView.setText(message);

      for (Anchor anchor : anchors) {
        anchor.detach();
      }
      anchors.clear();
    }
    clearAnchorsFromSharedPreferences();
    clearAnchorsButton.setVisibility(View.INVISIBLE);
    setAnchorButton.setVisibility(View.VISIBLE);
    tapScreenTextView.setVisibility(View.VISIBLE);
  }

  /** Create an anchor at a specific geodetic location using a EUS quaternion. */
  private void createAnchor(
      Earth earth, double latitude, double longitude, double altitude, float[] quaternion) {
    Anchor anchor =
        earth.createAnchor(
            latitude,
            longitude,
            altitude,
            quaternion[0],
            quaternion[1],
            quaternion[2],
            quaternion[3]);
    synchronized (anchorsLock) {
      anchors.add(anchor);
    }
  }

  /** Create a terrain anchor at a specific geodetic location using a EUS quaternion. */
  private void createTerrainAnchor(
      Earth earth, double latitude, double longitude, float[] quaternion) {
    final ResolveAnchorOnTerrainFuture future =
        earth.resolveAnchorOnTerrainAsync(
            latitude,
            longitude,
            /* altitudeAboveTerrain= */ 0.0f,
            quaternion[0],
            quaternion[1],
            quaternion[2],
            quaternion[3],
            (anchor, state) -> {
              if (state == TerrainAnchorState.SUCCESS) {
                synchronized (anchorsLock) {
                  anchors.add(anchor);
                  terrainAnchors.add(anchor);
                }
              } else {
                statusTextView.setVisibility(View.VISIBLE);
                statusTextView.setText(getString(R.string.status_terrain_anchor, state));
              }
            });
  }

  /** Create a rooftop anchor at a specific geodetic location using a EUS quaternion. */
  private void createRooftopAnchor(
      Earth earth, double latitude, double longitude, float[] quaternion) {
    final ResolveAnchorOnRooftopFuture future =
        earth.resolveAnchorOnRooftopAsync(
            latitude,
            longitude,
            /* altitudeAboveRooftop= */ 0.0f,
            quaternion[0],
            quaternion[1],
            quaternion[2],
            quaternion[3],
            (anchor, state) -> {
              if (state == RooftopAnchorState.SUCCESS) {
                synchronized (anchorsLock) {
                  anchors.add(anchor);
                  rooftopAnchors.add(anchor);
                }
              } else {
                statusTextView.setVisibility(View.VISIBLE);
                statusTextView.setText(getString(R.string.status_rooftop_anchor, state));
              }
            });
  }

  /**
   * Helper function to store the parameters used in anchor creation in {@link SharedPreferences}.
   */
  private void storeAnchorParameters(
      double latitude, double longitude, double altitude, float[] quaternion) {
    Set<String> anchorParameterSet =
        sharedPreferences.getStringSet(SHARED_PREFERENCES_SAVED_ANCHORS, new HashSet<>());
    HashSet<String> newAnchorParameterSet = new HashSet<>(anchorParameterSet);

    SharedPreferences.Editor editor = sharedPreferences.edit();
    String type = "";
    switch (anchorType) {
      case TERRAIN:
        type = "Terrain";
        break;
      case ROOFTOP:
        type = "Rooftop";
        break;
      default:
        type = "";
        break;
    }
    newAnchorParameterSet.add(
        String.format(
            type + "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
            latitude,
            longitude,
            altitude,
            quaternion[0],
            quaternion[1],
            quaternion[2],
            quaternion[3]));
    editor.putStringSet(SHARED_PREFERENCES_SAVED_ANCHORS, newAnchorParameterSet);
    editor.commit();
  }

  private void clearAnchorsFromSharedPreferences() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putStringSet(SHARED_PREFERENCES_SAVED_ANCHORS, null);
    editor.commit();
  }

  /** Creates all anchors that were stored in the {@link SharedPreferences}. */
  private void createAnchorFromSharedPreferences(Earth earth) {
    Set<String> anchorParameterSet =
        sharedPreferences.getStringSet(SHARED_PREFERENCES_SAVED_ANCHORS, null);
    if (anchorParameterSet == null) {
      return;
    }

    for (String anchorParameters : anchorParameterSet) {
      AnchorType type = AnchorType.GEOSPATIAL;
      if (anchorParameters.contains("Terrain")) {
        type = AnchorType.TERRAIN;
        anchorParameters = anchorParameters.replace("Terrain", "");
      } else if (anchorParameters.contains("Rooftop")) {
        type = AnchorType.ROOFTOP;
        anchorParameters = anchorParameters.replace("Rooftop", "");
      }
      String[] parameters = anchorParameters.split(",");
      if (parameters.length != 7) {
        Log.d(
            TAG, "Invalid number of anchor parameters. Expected four, found " + parameters.length);
        continue;
      }
      double latitude = Double.parseDouble(parameters[0]);
      double longitude = Double.parseDouble(parameters[1]);
      double altitude = Double.parseDouble(parameters[2]);
      float[] quaternion =
          new float[] {
            Float.parseFloat(parameters[3]),
            Float.parseFloat(parameters[4]),
            Float.parseFloat(parameters[5]),
            Float.parseFloat(parameters[6])
          };
      switch (type) {
        case TERRAIN:
          createTerrainAnchor(earth, latitude, longitude, quaternion);
          break;
        case ROOFTOP:
          createRooftopAnchor(earth, latitude, longitude, quaternion);
          break;
        default:
          createAnchor(earth, latitude, longitude, altitude, quaternion);
          break;
      }
    }

    runOnUiThread(() -> clearAnchorsButton.setVisibility(View.VISIBLE));
  }

  @Override
  public void onDialogPositiveClick(DialogFragment dialog) {
    if (!sharedPreferences.edit().putBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, true).commit()) {
      throw new AssertionError("Could not save the user preference to SharedPreferences!");
    }
    createSession();
  }

  @Override
  public void onDialogContinueClick(DialogFragment dialog) {
    dialog.dismiss();
  }

  private void onRenderStreetscapeGeometryChanged(CompoundButton button, boolean isChecked) {
    if (session == null) {
      return;
    }
    isRenderStreetscapeGeometry = isChecked;
  }

  /**
   * Handles the most recent user tap.
   *
   * <p>We only ever handle one tap at a time, since this app only allows for a single anchor.
   *
   * @param frame the current AR frame
   * @param cameraTrackingState the current camera tracking state
   */
  private void handleTap(Frame frame, TrackingState cameraTrackingState) {
    // Handle taps. Handling only one tap per frame, as taps are usually low frequency
    // compared to frame rate.
    synchronized (singleTapLock) {
      if (queuedSingleTap != null
          && selectedScenario != null
          && cameraTrackingState == TrackingState.TRACKING) {
        setGroundFromTap(frame, queuedSingleTap);
        queuedSingleTap = null;
        return;
      }
      synchronized (anchorsLock) {
        if (queuedSingleTap == null
            || anchors.size() >= MAXIMUM_ANCHORS
            || cameraTrackingState != TrackingState.TRACKING) {
          queuedSingleTap = null;
          return;
        }
      }
      Earth earth = session.getEarth();
      if (earth == null || earth.getTrackingState() != TrackingState.TRACKING) {
        queuedSingleTap = null;
        return;
      }

      for (HitResult hit : frame.hitTest(queuedSingleTap)) {
        if (shouldCreateAnchorWithHit(hit)) {
          Pose hitPose = hit.getHitPose();
          GeospatialPose geospatialPose = earth.getGeospatialPose(hitPose);
          createAnchorWithGeospatialPose(earth, geospatialPose);
          break; // Only handle the first valid hit.
        }
      }
      queuedSingleTap = null;
    }
  }

  private static final String SOURCE_PLANE = "detected plane";
  private static final String SOURCE_TERRAIN = "terrain mesh";

  private static boolean isUpwardPlaneHit(HitResult hit) {
    Trackable t = hit.getTrackable();
    return t instanceof Plane
        && ((Plane) t).getType() == Plane.Type.HORIZONTAL_UPWARD_FACING
        && ((Plane) t).isPoseInPolygon(hit.getHitPose());
  }

  private static boolean isTerrainHit(HitResult hit) {
    Trackable t = hit.getTrackable();
    return t instanceof StreetscapeGeometry
        && ((StreetscapeGeometry) t).getType() == StreetscapeGeometry.Type.TERRAIN;
  }

  /**
   * Picks the ground-like hit: the nearest detected upward plane if any, otherwise the nearest
   * Streetscape terrain hit. The terrain mesh is a smoothed model without curbs, steps or street
   * crown, and can sit inches above the real surface, so it must never win over a real plane.
   */
  private static HitResult pickGroundHit(List<HitResult> hits) {
    for (HitResult hit : hits) {
      if (isUpwardPlaneHit(hit)) {
        return hit;
      }
    }
    for (HitResult hit : hits) {
      if (isTerrainHit(hit)) {
        return hit;
      }
    }
    return null;
  }

  /** Moves the reference point (and gauge/marker) to the ground-like surface under a tap. */
  private void setGroundFromTap(Frame frame, MotionEvent tap) {
    HitResult hit = pickGroundHit(frame.hitTest(tap));
    if (hit != null) {
      if (groundAnchor != null) {
        groundAnchor.detach();
      }
      groundAnchor = hit.createAnchor();
      referenceSource = isUpwardPlaneHit(hit) ? SOURCE_PLANE : SOURCE_TERRAIN;
      return;
    }
    runOnUiThread(
        () ->
            Toast.makeText(
                    this, "No flat surface found there — tap a detected plane", Toast.LENGTH_SHORT)
                .show());
  }

  /** Returns {@code true} if and only if the hit can be used to create an Anchor reliably. */
  private boolean shouldCreateAnchorWithHit(HitResult hit) {
    Trackable trackable = hit.getTrackable();
    if (isRenderStreetscapeGeometry) {
      if (trackable instanceof StreetscapeGeometry) {
        return true;
      }
    }
    if (trackable instanceof Plane) {
      // Check if the hit was within the plane's polygon.
      return ((Plane) trackable).isPoseInPolygon(hit.getHitPose());
    } else if (trackable instanceof Point) {
      // Check if the hit was against an oriented point.
      return ((Point) trackable).getOrientationMode() == OrientationMode.ESTIMATED_SURFACE_NORMAL;
    }
    return false;
  }

}
