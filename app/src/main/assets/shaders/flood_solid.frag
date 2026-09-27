#version 300 es
// Flat color for poles.
precision mediump float;
uniform vec4 u_Color;
in vec3 v_WorldPos;
out vec4 o_FragColor;

void main() {
  o_FragColor = u_Color;
}
