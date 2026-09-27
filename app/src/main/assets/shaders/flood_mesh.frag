#version 300 es
// Colors Streetscape meshes by height relative to the water surface.
// Buildings: muddy stain below, pale debris line at the waterline (fading out with distance).
// Terrain: never drawn in color (the water surface covers it); above the water it still writes
// depth so raised ground hides the water behind it.
// Above the water, fragments are transparent but write depth, so buildings hide the water.
precision highp float;
uniform float u_WaterY;
uniform float u_IsTerrain;
uniform vec3 u_CameraPos;
uniform vec4 u_WaterColor;
uniform vec4 u_LineColor;
in vec3 v_WorldPos;
out vec4 o_FragColor;

void main() {
  float d = v_WorldPos.y - u_WaterY;
  if (u_IsTerrain > 0.5) {
    if (d < 0.0) {
      discard;
    }
    o_FragColor = vec4(0.0);
    return;
  }
  float dist = distance(v_WorldPos, u_CameraPos);
  float halfWidth = max(0.025, 0.0015 * dist);
  if (abs(d) < halfWidth) {
    o_FragColor = vec4(u_LineColor.rgb, u_LineColor.a * (1.0 - smoothstep(40.0, 90.0, dist)));
  } else if (d < 0.0) {
    o_FragColor = u_WaterColor;
  } else {
    o_FragColor = vec4(0.0);
  }
}
