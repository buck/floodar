#version 300 es
// Flood water surface, modeled on Harvey street video: opaque muddy khaki looking down,
// sky-grey mirror toward the horizon (Fresnel), streaky flow ripples, fading with distance.
precision highp float;
uniform vec3 u_CameraPos;
uniform vec4 u_WaterColor;  // mud rgb, alpha looking straight down
uniform vec3 u_SkyColor;    // overcast sky reflected at grazing angles
uniform float u_Time;
uniform float u_FadeStart;
uniform float u_FadeEnd;
in vec3 v_WorldPos;
out vec4 o_FragColor;

void main() {
  vec3 toFrag = v_WorldPos - u_CameraPos;
  vec3 dir = normalize(toFrag);
  vec2 p = v_WorldPos.xz;

  // Long flow streaks plus finer chop.
  float ripple = 0.5 * sin(p.x * 1.7 + p.y * 0.4 + u_Time * 1.6)
               + 0.3 * sin(p.x * 0.5 - p.y * 2.3 + u_Time * 1.1)
               + 0.2 * sin((p.x + p.y) * 5.1 - u_Time * 2.7);

  float cosTheta = abs(dir.y);  // 1 = looking straight down
  float fresnel = 0.02 + 0.98 * pow(1.0 - cosTheta, 5.0);
  fresnel = clamp(fresnel + 0.10 * ripple * (1.0 - cosTheta), 0.0, 1.0);

  vec3 mud = u_WaterColor.rgb * (0.92 + 0.08 * ripple);
  vec3 rgb = mix(mud, u_SkyColor, 0.85 * fresnel);
  float alpha = mix(u_WaterColor.a, 0.97, fresnel);

  if (u_CameraPos.y < v_WorldPos.y) {
    // Seen from below (water over your head): murky ceiling.
    rgb = mud * 0.7;
    alpha = 0.55;
  }

  alpha *= 1.0 - smoothstep(u_FadeStart, u_FadeEnd, length(toFrag.xz));
  o_FragColor = vec4(rgb, alpha);
}
