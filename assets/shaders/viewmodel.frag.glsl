// The first-person weapon and hand: the colour is worked out per vertex in viewmodel.vert.glsl.
#ifdef GL_ES
precision mediump float;
#endif
varying vec3 v_colour;
void main() {
    gl_FragColor = vec4(v_colour, 1.0);
}
