#version 300 es
precision mediump float;
uniform sampler2D u_Texture;
in vec2 v_Uv;
out vec4 o_FragColor;

void main() {
  vec4 c = texture(u_Texture, v_Uv);
  if (c.a < 0.02) {
    discard;
  }
  o_FragColor = c;
}
