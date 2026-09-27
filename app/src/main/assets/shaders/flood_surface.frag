#version 300 es
// Water surface quad: translucent, faint ripples, fading out with horizontal distance.
precision highp float;
uniform vec3 u_CameraPos;
uniform vec4 u_WaterColor;
uniform float u_Time;
uniform float u_FadeStart;
uniform float u_FadeEnd;
in vec3 v_WorldPos;
out vec4 o_FragColor;

void main() {
  float dist = distance(v_WorldPos.xz, u_CameraPos.xz);
  float ripple = 0.5 + 0.5 * sin(v_WorldPos.x * 3.1 + u_Time * 1.3)
                           * sin(v_WorldPos.z * 2.7 - u_Time * 1.1);
  vec3 rgb = u_WaterColor.rgb * (0.85 + 0.3 * ripple);
  float alpha = u_WaterColor.a * (1.0 - smoothstep(u_FadeStart, u_FadeEnd, dist));
  o_FragColor = vec4(rgb, alpha);
}
