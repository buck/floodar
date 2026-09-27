#version 300 es
// Shared vertex shader for flood rendering: passes world-space position to the fragment shader.
uniform mat4 u_ModelViewProjection;
uniform mat4 u_Model;
layout(location = 0) in vec4 a_Position;
out vec3 v_WorldPos;

void main() {
  vec4 p = vec4(a_Position.xyz, 1.0);
  v_WorldPos = (u_Model * p).xyz;
  gl_Position = u_ModelViewProjection * p;
}
