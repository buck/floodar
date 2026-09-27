#version 300 es
// Underwater tint when the camera is below the water surface: murky brown, denser toward the
// bottom of the view (deeper water), lighter toward the top (nearer the surface).
precision mediump float;
uniform vec3 u_DeepColor;
uniform vec3 u_ShallowColor;
uniform float u_Strength;  // overall opacity, 0..1
in float v_ScreenY;
out vec4 o_FragColor;

void main() {
  float t = smoothstep(0.0, 1.0, v_ScreenY);
  vec3 rgb = mix(u_DeepColor, u_ShallowColor, t);
  float alpha = u_Strength * mix(1.0, 0.75, t);
  o_FragColor = vec4(rgb, alpha);
}
