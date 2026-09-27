#version 300 es
// Full-screen quad in NDC for the underwater tint.
layout(location = 0) in vec4 a_Position;
out float v_ScreenY;

void main() {
  v_ScreenY = a_Position.y * 0.5 + 0.5;  // 0 = bottom, 1 = top of screen
  gl_Position = vec4(a_Position.xy, 0.0, 1.0);
}
