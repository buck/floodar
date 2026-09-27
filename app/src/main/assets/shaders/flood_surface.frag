#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
// Flood water surface, modeled on the resident's Harvey street video: opaque muddy water that
// mirrors the trees, houses and sky. The reflection samples the live camera image where the
// mirrored view ray lands ~25 m away (a planar-mirror approximation that is right for the far
// side of the street), perturbed by flow ripples. Off-screen lookups clamp to the edge.
precision highp float;
uniform samplerExternalOES u_CameraColorTexture;
uniform mat4 u_ViewProjection;
// Screen NDC -> camera texture UV: uv = origin + dx * (x+1)/2 + dy * (y+1)/2.
uniform vec2 u_UvOrigin;
uniform vec2 u_UvDx;
uniform vec2 u_UvDy;
uniform vec3 u_CameraPos;
uniform vec4 u_WaterColor;  // mud rgb, alpha looking straight down
uniform vec3 u_SkyColor;
uniform float u_Time;
uniform float u_FadeStart;
uniform float u_FadeEnd;
in vec3 v_WorldPos;
out vec4 o_FragColor;

const float REFLECT_DISTANCE = 25.0;

void main() {
  vec3 toFrag = v_WorldPos - u_CameraPos;
  vec3 dir = normalize(toFrag);
  vec2 p = v_WorldPos.xz;

  // Flow streaks plus chop; slopes perturb the reflection.
  float a1 = p.x * 1.7 + p.y * 0.4 + u_Time * 1.6;
  float a2 = p.x * 0.5 - p.y * 2.3 + u_Time * 1.1;
  float a3 = (p.x + p.y) * 5.1 - u_Time * 2.7;
  float ripple = 0.5 * sin(a1) + 0.3 * sin(a2) + 0.2 * sin(a3);
  vec2 slope = 0.5 * cos(a1) * vec2(1.7, 0.4)
             + 0.3 * cos(a2) * vec2(0.5, -2.3)
             + 0.2 * cos(a3) * vec2(5.1, 5.1);
  vec3 n = normalize(vec3(-0.012 * slope.x, 1.0, -0.012 * slope.y));

  vec3 mud = u_WaterColor.rgb * (0.88 + 0.16 * ripple);

  if (u_CameraPos.y < v_WorldPos.y) {
    // Seen from below (water over your head): a bright, rippling ceiling where daylight comes
    // through the murky surface, so the surface reads clearly as "up there".
    float glow = 0.55 + 0.25 * ripple;
    vec3 ceiling = mix(mud, u_SkyColor, glow);
    float a = 0.8 * (1.0 - smoothstep(u_FadeStart, u_FadeEnd, length(toFrag.xz)));
    o_FragColor = vec4(ceiling, a);
    return;
  }

  vec3 refl = u_SkyColor;
  vec3 r = reflect(dir, n);
  if (r.y > 0.0) {
    vec4 clip = u_ViewProjection * vec4(v_WorldPos + r * REFLECT_DISTANCE, 1.0);
    if (clip.w > 0.0) {
      // Clamp to the screen instead of fading to sky: off-screen reflections reuse the nearest
      // visible camera pixels (avoids pale bands at the screen edges). Only far above the top
      // edge does it blend to overcast sky.
      vec2 ndc = clip.xy / clip.w;
      float above = ndc.y - 1.0;
      ndc = clamp(ndc, vec2(-0.995), vec2(0.995));
      vec2 uv = u_UvOrigin + u_UvDx * (ndc.x * 0.5 + 0.5) + u_UvDy * (ndc.y * 0.5 + 0.5);
      vec3 cam = texture(u_CameraColorTexture, uv).rgb;
      refl = mix(cam, u_SkyColor, smoothstep(0.0, 0.6, above));
    }
  }
  // Muddy water dims and browns its reflections.
  refl = mix(refl, mud, 0.3) * 0.8;

  float cosTheta = abs(dir.y);  // 1 = looking straight down
  float fresnel = 0.03 + 0.97 * pow(1.0 - cosTheta, 4.0);
  float reflectivity = clamp(0.12 + 0.8 * fresnel, 0.0, 0.92);
  vec3 rgb = mix(mud, refl, reflectivity);
  float alpha = mix(u_WaterColor.a, 0.98, fresnel);

  alpha *= 1.0 - smoothstep(u_FadeStart, u_FadeEnd, length(toFrag.xz));
  o_FragColor = vec4(rgb, alpha);
}
