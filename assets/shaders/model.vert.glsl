// The flat-shaded world models (targets, pickups, enemies, doors, props), lit per vertex. WorldRenderer draws the game
// with it and the model previewer in :tools draws with the same file, so a model previews as it looks in the game.
// The uniform and attribute names are the contract with both; ShaderFilesTest and ModelPreviewAppTest check them.
attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec3 a_colour;
attribute float a_gloss;
attribute float a_accent;
uniform mat4 u_projViewTrans;
uniform mat4 u_model;
uniform mat3 u_normalMatrix;
uniform vec4 u_accentColour;
uniform vec3 u_cameraPosition;
uniform vec2 u_fogRange;
uniform vec3 u_fogColour;
uniform float u_light;
uniform float u_hitFlash;
varying vec3 v_colour;
void main() {
    vec4 worldPos = u_model * vec4(a_position, 1.0);
    vec3 worldNormal = normalize(u_normalMatrix * a_normal);
    // The same shades as the map's faces, which MapGeometry bakes per vertex (SHADE_*): north-south 1.0,
    // east-west 0.72, floor 0.9, ceiling 0.62.
    vec3 weight = worldNormal * worldNormal;
    float shade = weight.z + weight.x * 0.72 + weight.y * (worldNormal.y > 0.0 ? 0.9 : 0.62);
    // Where the light comes from, in world space. Shaders.MODEL_LIGHT_* holds the same direction for the Warden's
    // skinned model (WardenModel.TO_LIGHT); ShadersTest fails if the two disagree.
    vec3 lightDir = normalize(vec3(-0.3, 0.9, 0.4));
    float key = 0.65 + 0.55 * max(dot(worldNormal, lightDir), 0.0);
    vec3 delta = u_cameraPosition - worldPos.xyz;
    float range = length(delta);
    vec3 toCamera = range > 1e-4 ? delta / range : vec3(0.0, 0.0, 1.0);
    vec3 halfVec = normalize(lightDir + toCamera);
    vec3 surfaceColour = mix(a_colour, u_accentColour.rgb, a_accent);
    float surfaceGloss = mix(a_gloss, u_accentColour.a, a_accent);
    float sheen = surfaceGloss * pow(max(dot(worldNormal, halfVec), 0.0), 12.0) * 0.35;
    float rim = pow(1.0 - max(dot(worldNormal, toCamera), 0.0), 2.5) * 0.12;
    vec3 lit = (surfaceColour * shade * key + vec3(sheen + rim)) * u_light;
    lit = mix(lit, vec3(1.0, 0.25, 0.25), u_hitFlash * 0.65);
    float fog = clamp((range - u_fogRange.x) / (u_fogRange.y - u_fogRange.x), 0.0, 1.0);
    v_colour = mix(lit, u_fogColour, fog);
    gl_Position = u_projViewTrans * worldPos;
}
