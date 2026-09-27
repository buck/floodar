#version 300 es
// Colors Streetscape building/terrain meshes by height relative to the water surface: muddy
// stain below, pale debris line at the waterline. Above the water the fragment is fully
// transparent but still writes depth, so buildings hide the water surface behind them.
precision highp float;
uniform float u_WaterY;
uniform vec3 u_CameraPos;
uniform vec4 u_WaterColor;
uniform vec4 u_LineColor;
in vec3 v_WorldPos;
out vec4 o_FragColor;

void main() {
  float d = v_WorldPos.y - u_WaterY;
  // Keep the line a few pixels thick at any distance.
  float halfWidth = max(0.03, 0.003 * distance(v_WorldPos, u_CameraPos));
  if (abs(d) < halfWidth) {
    o_FragColor = u_LineColor;
  } else if (d < 0.0) {
    o_FragColor = u_WaterColor;
  } else {
    o_FragColor = vec4(0.0);
  }
}
