// The map's walls, floor and ceiling: the texture times the baked shade and the room light, faded into the fog.
#ifdef GL_ES
precision mediump float;
#endif
uniform sampler2D u_texture;
uniform vec3 u_fogColour;
uniform float u_light;
varying vec2 v_texCoord;
varying float v_shade;
varying float v_fog;
void main() {
    vec3 colour = texture2D(u_texture, v_texCoord).rgb * v_shade * u_light;
    gl_FragColor = vec4(mix(colour, u_fogColour, v_fog), 1.0);
}
