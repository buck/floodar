#version 300 es
// Flat color for poles, with a slight darkening toward the bottom of each foot so stripes read
// as solid. Using v_WorldPos also keeps u_Model alive: if the linker strips it, setting it
// throws "Shader uniform does not exist".
precision mediump float;
uniform vec4 u_Color;
in vec3 v_WorldPos;
out vec4 o_FragColor;

void main() {
  float shade = 0.85 + 0.15 * fract(v_WorldPos.y * 3.28084);
  o_FragColor = vec4(u_Color.rgb * shade, u_Color.a);
}
