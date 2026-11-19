// The first-person weapon and hand, drawn by ViewmodelRenderer. Unlike the world models, its key light is fixed in
// view space (it moves with the camera), on purpose; there is no fog on it.
// The uniform and attribute names are the contract with ViewmodelRenderer; ShaderFilesTest checks them.
attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec3 a_colour;
attribute float a_gloss;
attribute float a_accent;
uniform mat4 u_projTrans;
uniform mat4 u_model;
uniform mat3 u_normalMatrix;
uniform mat4 u_viewToWorld;
uniform vec4 u_accentColour;
uniform float u_light;
uniform float u_charge;
varying vec3 v_colour;
void main() {
    vec3 viewNormal = normalize(u_normalMatrix * a_normal);
    vec3 worldNormal = (u_viewToWorld * vec4(viewNormal, 0.0)).xyz;
    // The same shades as the world's faces, which MapGeometry bakes per vertex (SHADE_*): north-south 1.0,
    // east-west 0.72, floor 0.9, ceiling 0.62.
    vec3 weight = worldNormal * worldNormal;
    float shade = weight.z + weight.x * 0.72 + weight.y * (worldNormal.y > 0.0 ? 0.9 : 0.62);
    // A fixed light from above the left shoulder, so the form reads whichever way the player faces.
    float key = 0.5 + 0.75 * max(dot(viewNormal, normalize(vec3(-0.3, 0.9, 0.3))), 0.0);
    // The blast charge glows violet towards the muzzle, which is at negative z in gun space.
    float front = clamp((-a_position.z - 0.01) / 0.1, 0.0, 1.0);
    vec3 baseColour = mix(a_colour, u_accentColour.rgb, a_accent);
    float baseGloss = mix(a_gloss, u_accentColour.a, a_accent);
    // Glossy faces that sit halfway between the light and the eye pick up a sheen.
    float facing = max(dot(viewNormal, normalize(vec3(-0.3, 0.9, 1.3))), 0.0);
    float sheen = baseGloss * pow(facing, 10.0) * 0.3;
    // Faces seen nearly edge-on catch a faint rim of light, which lifts dark shapes off a dark background.
    float rim = pow(1.0 - abs(viewNormal.z), 2.0) * 0.09;
    v_colour = (baseColour * shade * key + vec3(sheen + rim)) * u_light
        + vec3(0.5, 0.22, 1.0) * u_charge * front * 0.55;
    gl_Position = u_projTrans * u_model * vec4(a_position, 1.0);
}
