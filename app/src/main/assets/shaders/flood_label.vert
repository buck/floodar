#version 300 es
// Textured billboard quad for gauge labels; picks one cell of the label atlas.
uniform mat4 u_ModelViewProjection;
uniform vec4 u_UvOffsetScale;  // xy = offset, zw = scale
layout(location = 0) in vec4 a_Position;
layout(location = 1) in vec2 a_Uv;
out vec2 v_Uv;

void main() {
  v_Uv = u_UvOffsetScale.xy + a_Uv * u_UvOffsetScale.zw;
  gl_Position = u_ModelViewProjection * vec4(a_Position.xyz, 1.0);
}
