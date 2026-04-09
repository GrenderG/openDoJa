# Acrodea XF3 Reverse-Engineering Notes

## Scope

This note records the evidence gathered while reconstructing the missing `com.acrodea.xf3` library for openDoJa, with PACMANIA as the primary runtime target and Avatar Bowling as a secondary API-surface source.

Confidence labels:

- `confirmed`: directly observed in game bytecode, asset bytes, or NAND strings
- `high`: strong behavior/signature inference from direct usage
- `low`: narrow fallback added only to keep observed code paths running

## PACMANIA Findings

### Confirmed from bytecode

`PacMania.class` and `PacMania$GameLoop.class` directly reference these XF3 types and members:

- `com.acrodea.xf3.xfeTransformation`
- `com.acrodea.xf3.xfeMatrixTransformation`
  - `new xfeMatrixTransformation()`
  - `setTransformation(xfMatrix4, int)`
  - used via `xfeActor.setLocalTransformation(xfeTransformation)`
- `com.acrodea.xf3.xfeRoot`
  - constructor `(xfeGameGraphFactory)`
  - `clear()`
  - `getNodes()`
  - `getClock()`
  - `doIteration()`
  - `update()`
  - `render(xfeCamera, xfRectangleInt, boolean)`
- `com.acrodea.xf3.xfeClock`
  - `resetTick(long)`
  - `getTick()`
- `com.acrodea.xf3.xfeClockAdvanceState`
  - constructor `()`
  - `isReady()`
  - `didIteration()`
  - `set(boolean, boolean)`
- `com.acrodea.xf3.xfeActor`
  - `setLocalTransformation(xfeTransformation)`
- `com.acrodea.xf3.xfeCamera`
- `com.acrodea.xf3.xfeNode`
  - `getName()`
- `com.acrodea.xf3.xfeGameGraphNodeIterator`
  - `getNext()`
- `com.acrodea.xf3.xfePRSAnimationController`
  - `setTick(int)`
- `com.acrodea.xf3.def.xfeDefaultXF2Loader`
  - constructor `()`
  - `load(String, xfeRoot, xfeNodeList, xfeXF2Context, xfeXF2Reader)`
  - `getErrorMessage()`
- `com.acrodea.xf3.def.xfeDefaultHWAcceleratedGameGraphFactory`
  - constructor `()`
- `com.acrodea.xf3.xfeNodeList`
  - constructor `()`
- `com.acrodea.xf3.xfeOGLContext`
  - static `init(GraphicsOGL)`
  - static field `mGL`
- `com.acrodea.xf3.math.xfVector3`
  - constructors `()`, `(float,float,float)`
  - public fields `x`, `y`, `z`
- `com.acrodea.xf3.math.xfMatrix4`
  - constructor `()`
- `com.acrodea.xf3.math.xfMath`
  - `matrixLookAt(xfMatrix4, xfVector3, xfVector3, xfVector3)`
- `com.acrodea.xf3.math.xfRectangleInt`
  - constructor `()`
  - public fields `mX`, `mY`, `mWidth`, `mHeight`

### Confirmed matrix contract

From direct Bowling bytecode plus live runtime probes:

- game-facing `xfMatrix4` values exposed through `xfeMatrixTransformation.getMatrix()` store translation in `m[3][0..2]`
  - confirmed in `StageController.aaa()` and `GalaxyController.aaa()`, which read actor position from `matrix.m[3][0..2]`
  - confirmed in `CameraController.aaa()`, which reads the parent camera actor position from the same row
- the compatibility layer therefore has to distinguish:
  - public/game-facing XF3 matrices
  - internal renderer/OpenGL-style matrices
- `xfMath.matrixLookAt(...)` must build its corrected up vector as `cross(right, forward)`, not `cross(forward, right)`
  - direct evidence:
    - the in-repo `com.nttdocomo.ui.ogl.math.Matrix4f.lookAt(...)` implementation computes `actualUp` as `side x forward`
    - a live Bowling stage probe showed the broken path produced a camera world up basis of approximately `[0, -0.999, 0.037]`, which matches the upside-down stage frame
    - after switching to `cross(right, forward)`, the same live Bowling stage probe produced an upright camera world up basis of approximately `[0, +0.999, 0.037]`, and the stage preview rendered upright
- confirmed runtime Bowling stage values after the fix:
  - controller position: `[0,17000,-40000]`
  - controller look-at: `[0,10000,150000]`
  - resolved camera world basis is upright before projection:
    - row 1 / up basis approximately `[0.0000357, 0.99932194, 0.036817122]`

High-confidence inference:

- the remaining Bowling/PACMANIA title differences are no longer caused by the shared upside-down camera basis bug
- the corrected look-at up-vector rule is part of the real XF3 math contract, not a Bowling-specific workaround

### Confirmed Bowling camera-chain divergence

From direct comparison of Bowling bytecode against the compatibility implementation:

- Bowling uses two different downstream renderers during gameplay:
  - `xfeRoot.render(...)` for the XF3 lane/background scene
  - the later Acrodea OGL path driven by `BaseCanvas.aao()`, which does:
    - `xfeCamera.getCameraView(apu)`
    - `xfGL.setMatrix(1, matrixWorld)`
    - `xfGL.setMatrix(2, cameraView.getViewMatrix())`
    - then draws pins and avatar content
- the compatibility layer had a real camera-chain mismatch:
  - `xfeRoot.worldMatrix(...)` includes both `xfeActor` and `xfeGroup` local transforms
  - `xfeCamera.worldMatrix(...)` used by `getCameraView(...)` only walked `xfeGroup`
- this is a confirmed bug, not an inference:
  - Bowling `CameraController` drives the camera actor via `xfeActor.setLocalTransformation(...)`
  - dropping `xfeActor` transforms from `xfeCamera.getCameraView(...)` means the OGL gameplay path sees a different camera than the XF3 scene path
  - that exactly matches the observed split where the lane/background could be positioned correctly while the player and pins were missing or off-camera

Implemented fix:

- `xfeCamera.worldMatrix(...)` now includes ancestor `xfeActor` transforms in the same way the XF3 scene renderer already did

Confidence:

- `confirmed` for the bug and the fix scope
- the expected user-visible outcome is that Bowling gameplay objects again share the same camera placement as the lane scene

### Confirmed Bowling OGL raster-state mismatches

From direct Bowling bytecode plus the current host `GraphicsOGL` implementation:

- Bowling ball-selection, avatar, and pin geometry all rely on the standard host OGL raster path:
  - `AvatarEngine.setViewport(...)`
  - `AvatarEngine.setPerspectiveView(...)`
  - `glFrontFace(...)`
  - `glCullFace(...)`
  - `glDepthFunc(...)`
  - `glDrawElements(...)`
- two concrete host mismatches were present:
  - cull orientation was reversed in `Graphics.isCulled(...)`
    - direct evidence:
      - the rasterizer's `edge(...)` helper is the negated 2D cross-product form
      - after mapping NDC into top-left screen coordinates, CCW window winding corresponds to a positive `edge(...)` result, not a negative one
      - the previous implementation used `area < 0` as `ccw`, which inverted both `GL_CCW` and `GL_CW`
    - Bowling bytecode relevance:
      - ball-selection and gameplay explicitly set `glFrontFace(GL_CW)` with culling enabled
      - selected balls are sometimes drawn twice with `glCullFace(GL_FRONT)` and then `glCullFace(GL_BACK)`, which is exactly the kind of path that can stay visible while single-pass objects disappear under reversed winding classification
  - Acrodea-matrix path selection leaked across unrelated draws
    - direct evidence:
      - host `OglState` tracked `standardModelViewConfigured` / `standardProjectionConfigured` but never reset them
      - `usesAcrodeaMatrices()` therefore became permanently false after any earlier standard OGL pass configured matrices once
      - Bowling uses both standard OGL preview/avatar passes and the later Acrodea matrix path for gameplay objects, so stale matrix-path state is incorrect by construction

Implemented fixes:

- `Graphics.isCulled(...)` now classifies `ccw` using `area > 0`
- `Graphics.OglState.beginDrawing()` now resets the per-pass matrix-path tracking:
  - `standardModelViewConfigured = false`
  - `standardProjectionConfigured = false`
  - `acrodeaWorldMatrix = identity`
  - `acrodeaCameraMatrix = null`

Confidence:

- `confirmed` for both bugs and both fixes

### Confirmed from PACMANIA `.xf2` assets

Direct strings recovered from `pm_op1.xf2`, `pm_cb1.xf2`, `pm_cb2.xf2`, `pm_cb3.xf2`, `pm_cb4.xf2`, and `pm_ed1.xf2`:

- node names:
  - `xfZoneNode1`
  - `xfActorNode1`
  - `xfActorNode2`
  - `xfActorNode3`
  - `xfActorNode4`
  - `xfActorNode5`
  - `xfActorNode7`
  - `xfActorNode8`
  - `camera1`
  - `directionalLight1`
  - `ambientLight1`
- animation/controller-like names:
  - `xfActorNode1_prscsg`
  - `xfActorNode2_prscsg`
  - `xfActorNode3_prscsg`
  - `xfActorNode4_prscsg`
  - `xfActorNode5_prscsg`
  - `xfActorNode7_prscsg`
  - `xfActorNode8_prscsg`
- texture/resource names:
  - `pm_op_logo01.gif`
  - `pm_chr_pm01.gif`
  - `pm_shadow02.gif`
  - `pm_cb3_bk01.gif`
  - `pm_ed_bk01.gif`

High-confidence inference:

- `.xf2` files carry scene graph node names as plain ASCII, enough to recover actor/camera/zone identifiers without a full format decoder.
- PACMANIA only needs one actor name explicitly from code: `xfActorNode4`.

### Confirmed `.xf2` record layout

From direct byte inspection of PACMANIA and Avatar Bowling `.xf2` files:

- file structure is a flat record stream with no global magic/header
- each record starts with a 12-byte little-endian header:
  - `u16 type`
  - `u16 id`
  - `u32 parent`
  - `u32 payloadSize`
- the next `payloadSize` bytes are the record payload

Confirmed record types needed so far:

- `1`
  - loader-node record
  - payload contains a counted node name and a trailing counted child-scene filename like `camera.xf2` or `lane01.xf2`
- `100`
  - zone node
  - payload starts with a counted zone name like `xfZoneNode1`
- `101`
  - actor node
  - payload starts with a 32-bit local parent/index field, then a counted actor name
- `103`
  - generic node record used for cameras, groups, lights, and other named nodes
  - payload starts with a 32-bit local parent/index field, then a counted node name
  - additional confirmed detail from Avatar Bowling:
    - this leading 32-bit field does not use raw record indices in all files
    - it resolves in the same reference space as shape/skinning links, keyed by each record header's `parent` value
    - evidence:
      - `lane01.xf2` works either way because those references happen to coincide with earlier record indices
      - `lane02.xf2` diverges and proves the distinction:
        - `polySurface431` shape record points at node ref `26`
        - the owning node is the type `103` record whose header `parent` value is `26`
        - child nodes like `polySurface439`, `polySurface440`, `polySurface452`, `polySurface431`, `polySurface461`, `polySurface460`, `polySurface434`, `polySurface438`, `polySurface457`, `polySurface456`, and `polySurface437` all chain correctly only when parent lookup uses header-parent refs
    - practical effect:
      - with ref-space parenting, Bowling's leaked lane geometry in `lane02.xf2` reattaches under `xfAtNd_lane01 -> xfGpNd2 -> polySurface351 -> ...`, which lets actor activation state gate it correctly during intro scenes
- `302`
  - PRS/animation reference record
  - payload starts with a counted controller name like `xfActorNode4_prscsg`
- `303`
  - parameterized-controller record
  - payload contains:
    - a counted target node name
    - a 32-bit key/value count
    - repeated counted-string pairs, introduced by byte `0x24`
  - confirmed Bowling examples:
    - `type = camera`, `direction = CamLookAt`, `position = CamPos`
    - `type = field`, `lane = 0`
- `204`
  - counted resource-name record
  - confirmed examples:
    - `pm_op_logo01.gif`
    - `lane_00.bmp`
- `107`
  - camera-shape record
  - confirmed examples:
    - `cameraShape1`
    - `camera1Shape`
- `108`
  - named shape record tied to a preceding node record
  - confirmed examples:
    - `p_lt1_PShape1`
    - `p_cb3_pacmanShape1`
    - `p_backstarShape1`
- `200`
  - shape subrecord carrying binary geometry-like payload
  - direct evidence:
    - always appears immediately after a shape record in PACMANIA logo/scenes
    - payload starts with two 32-bit values; the second value correlates strongly with vertex-like counts
    - examples:
      - `p_TM1`: second value `6`
      - `p_lt3_d1`: second value `66`
      - `p_lt1_P1`: second value `132`
- `201`
  - shape subrecord carrying a second binary payload associated with the same shape as type `200`
  - direct evidence:
    - follows the same shape chain as type `200`
    - often begins with the same second 32-bit count seen in the paired type `200`
- `203`
  - short shape/material-state subrecord associated with textured shapes
  - direct evidence:
    - present in PACMANIA shape chains immediately before an explicit type `204` resource-name record
- `301`
  - transform/animation-like node record tied to a named node
  - direct evidence:
    - the first two 32-bit values are stable cross-record links:
      - value `0` matches the owning type `302` PRS-controller record's header parent id
      - value `1` matches the animated type `101`/`103` node record's header parent id
    - confirmed examples from `pm_op1.xf2`:
      - `camera1`: `(2, 4)` links `xfActorNode4_prscs` -> `camera1`
      - `p_lt1_P1`: `(8, 10)` links `xfActorNode2_prscs` -> `p_lt1_P1`
    - payload begins with a counted target-node name like `camera1`, `p_lt1_P1`, or `joint_cb4_pm_root`
    - PACMANIA title/logo nodes and Bowling camera/controller nodes both depend on these records
    - after the name, the first little-endian 32-bit value falls in the `24..31` range across observed PACMANIA scenes
    - the 4 bytes immediately after that subtype field are a shared big-endian duration in `pm_op1.xf2`
      - all observed PACMANIA title-scene `301` records decode to `14991`
    - several PACMANIA title records contain plain terminal scale envelopes stored verbatim near the tail, for example:
      - `p_lt1_P1`: `0.1 + 0.9`
      - repeated three times for the 3 scale axes
    - the payload also contains counted packed sample blocks, not just a single terminal transform
      - confirmed sample-count fields recovered from `pm_op1.xf2`:
        - `p_lt1_P1`: `19`, `21`, `16`, `16`, `16`
        - `p_lt_pacman1`: `11`, `21`, `1`
        - `p_TM1`: `8`
      - high-confidence inference:
        - the PACMANIA title letters use at least 5 animated scalar channels
        - those channels include non-scale motion, because a static terminal-scale-only fallback still leaves the title over-wide and clipped
    - the rest of the payload remains a packed curve/state block that is not yet fully decoded
- `304`
  - skin/material attachment record
  - confirmed examples:
    - `p_op_pacmanShape1_skin`
    - `p_cb3_redAShape1_skin`

High-confidence inference:

- the first 32-bit value in actor/node payloads is a local parent record index used to build scene hierarchy
  - confirmed examples:
    - in `camera.xf2`, `camera1` points at record `0`, which is `ActorCamera`
    - in `lane01.xf2`, `wall` points at record `4`, which is `xfGpNd2`
- Bowling’s top-level `field.xf2` depends on recursive loading of child `.xf2` scenes via type `1` records:
  - `galaxy.xf2`
  - `lane01.xf2`
  - `lane02.xf2`
  - `camera.xf2`

### Confirmed textured-shape format details

Direct byte-level decoding of PACMANIA title shapes and `pm_cb1.xf2` batches recovered these additional details:

- type `108` shape payload:
  - begins with `u32 nodeRef`
  - then a counted shape name
  - then `u32 batchCount`
  - each batch descriptor is 45 bytes
  - the descriptor layout is:
    - 9-byte fixed prefix:
      - `00 02 01 04 0e 00 00 00 01`
    - followed by 9 little-endian 32-bit values:
      - reserved/flags (`0` in observed PACMANIA title data)
      - type `203` tint/material ref or `-1`
      - type `204` texture ref or `-1`
      - aux ref A or `-1`
      - aux ref B or `-1`
      - type `200` geometry ref
      - type `201` slice ref
      - start vertex
      - vertex count
- type `201` slice payload:
  - `u32 count`
  - `u32 offset`
  - `u8 bitsPerValue`
  - then `count` MSB-packed integers with that bit width
  - confirmed PACMANIA title cases decode to sequential ranges, so for the observed scenes this is a compact sequential slice description rather than arbitrary index reuse
  - examples:
    - `p_lt1_P1`: `count=132`, `offset=0`, `bits=8`, values `0..131`
    - `p_backstar*`: `count=30`, `offset=0`, `bits=5`, values `0..29`
    - `pm_cb1_bkgnd`: batches `(54,0)`, `(36,54)`, `(60,90)`, `(1734,150)`
- type `200` format `15` geometry payload:
  - `u32 format = 15`
  - `u32 vertexCount`
  - then 8 quantized component streams
  - each stream stores:
    - `float base`
    - `float scale`
    - `u8 bitsPerValue`
    - `vertexCount` MSB-packed integers
  - the first 8 decoded component roles are confirmed by successful runtime rendering:
    - `0`: position X
    - `1`: position Y
    - `2`: position Z
    - `3`: normal X
    - `4`: normal Y
    - `5`: normal Z
    - `6`: texture U
    - `7`: texture V
  - observed UV ranges are usually not `0..1`; they are most often `0..65535`-like fixed-point coordinates promoted to floats
  - confirmed examples:
    - `pm_op1.xf2` title shapes: `U/V` maxima between about `15899` and `65535`
    - `pm_cb1.xf2` background/title meshes: `U/V` maxima up to `65535`
  - high-confidence inference:
    - XF2 stores UVs as 16-bit normalized coordinates
    - the compatibility layer now normalizes those decoded UV streams by `65535` when they exceed a small-unit range
  - format `15` ends with an additional 8-byte trailer that is still not fully identified
- type `200` format `13`:
  - observed in Bowling `lane01.xf2` and `galaxy.xf2`
  - the first 5 quantized streams decode cleanly as:
    - `0`: position X
    - `1`: position Y
    - `2`: position Z
    - `3`: texture U
    - `4`: texture V
  - observed payloads end with an 8-byte trailer `ff 00 ff 00 ff 00 ff 00`
- type `200` format `29`:
  - observed in Bowling `lane02.xf2`
  - the first 5 streams decode in the same `X/Y/Z/U/V` pattern as format `13`
  - an additional trailing block remains undecoded after those shared streams
  - decoding the shared 5-stream prefix is already sufficient to recover Bowling's previously black lane/background geometry
- type `200` format `31`:
  - the first 8 streams follow the same position/normal/UV structure
  - after those shared streams, observed format-`31` payloads contain:
    - an 8-byte header that remains unidentified
    - then `vertexCount` influence lists of:
      - `u8 influenceCount`
      - repeated `u8 jointIndex`, `f32 weight`
  - PACMANIA `p_op_pacmanShape1` consumes exactly `2346` influence lists after skipping that 8-byte header
  - this is now sufficient to drive generic linear-blend skinning against type `304`
- type `203` payload:
  - the first 4 bytes behave like RGBA tint in observed files
  - confirmed examples:
    - `ff ff ff ff` for the untinted PACMANIA title material
    - `1d 4b 24 ff`, `a0 95 86 ff`, `bf 83 1a ff` in `pm_cb1.xf2`

- type `304` payload:
  - starts with:
    - `u32 rootRecordIndex`
    - `u32 shapeRef`
    - counted skin name
    - one observed padding byte
  - the remainder is a repeated 44-byte entry structure:
    - `u32 nodeRef`
    - 10 floats matching the node PRS layout from type `103`
  - PACMANIA `p_op_pacmanShape1_skin` resolves to 7 entries for:
    - `joint_pacman_root`
    - `joint_pacman_body`
    - `joint_pacman_headend`
    - `joint_pacman_upmouth`
    - `joint_pacman_upmouthend`
    - `joint_pacman_lomouth`
    - `joint_pacman_lomouthend`
  - Bowling `polySurfaceShape452_skin` resolves to 18 entries in the same structure

- type `103` / `101` node payload tail:
  - after the counted name, observed nodes consistently carry 10 floats
  - confirmed layout from raw examples:
    - translation `x`, `y`, `z`
    - quaternion `x`, `y`, `z`, `w`
    - scale `x`, `y`, `z`
  - strong evidence:
    - PACMANIA `camera1` decodes to `(0, 2, -15)` with identity rotation and unit scale
    - `joint_pacman_root` decodes to quaternion `(0, 0, 0.7071067, 0.7071068)`, which is a clean 90-degree bind rotation
  - the earlier compatibility heuristic that treated the 7th float as a fallback uniform scale was incorrect

High-confidence inference:

- PACMANIA title/logo batches are triangle lists over sequential vertex slices from type `200`/`201`
- the first shared `203`/`204` pair in `pm_op1.xf2` acts as the common title material/atlas for later title batches that reuse refs `14` and `15`

### PACMANIA title/logo composition

Direct record inspection of `pm_op1.xf2` shows that the missing PACMANIA logo is not a single fullscreen image:

- the scene contains many named title/logo nodes:
  - `p_lt1_P1`
  - `p_lt2_A1`
  - `p_lt3_d1`
  - `p_lt4_M1`
  - `p_lt5_A2`
  - `p_lt6_N1`
  - `p_lt7_I1`
  - `p_lt8_A3`
  - `p_backcircle1`
  - `p_backstar1` through `p_backstar13`
  - `p_TM1`
- those nodes have accompanying type `108` shape records and type `200`/`201` binary subrecords
- `pm_op_logo01.gif` appears as a type `204` texture/resource record inside those same shape chains

This is `confirmed`.

### Camera-shape payload evidence

Direct payload decoding of type `107` camera-shape records shows a stable trailing structure after the counted shape name:

- `u32 width`
- `u32 height`
- `float valueA`
- `float valueB`
- `float valueC`
- `float valueD`
- `float valueE`

Confirmed examples:

- PACMANIA `cameraShape1`:
  - `640`, `480`, `10.0`, `110.88`, `0.37508267`, `1.0`, `0.0`
- Bowling `cameraShape1`:
  - `640`, `480`, `6000.0`, `138600.0`, `0.6605947`, `1.0`, `0.0`

High-confidence inference:

- this record carries authored projection/camera parameters
- `valueA`/`valueB` are likely clip or focus distances
- `valueC` behaves more like `tan(fov/2)` than a raw radian angle:
  - PACMANIA: `0.37508267` -> `2 * atan(valueC)` = about `41.1` degrees
  - Bowling: `0.6605947` -> `2 * atan(valueC)` = about `66.9` degrees
- `valueD` / `valueE` remain unknown
- the compatibility layer now uses width, height, near/far clip, and the `2 * atan(valueC)` projection mapping
- residual PACMANIA title error indicates this record alone does not explain the whole scene mismatch; type `301` still appears to contribute node-space title motion/placement

High-confidence inference:

- the PACMANIA logo is assembled from multiple textured shapes that sample `pm_op_logo01.gif` as an atlas
- title nodes also carry type `301` data that still appears to affect their final on-screen placement/size

Post-implementation status:

- the compatibility layer now renders this scene from decoded type `108`/`200`/`201` geometry and UV data instead of falling back to black
- type `103`/`101` nodes now use real PRS local transforms instead of the earlier scale heuristic
- type `107` camera-shape data now feeds preferred-view width/height, near/far clip, and a recovered `2 * atan(valueC)` projection mapping
- type `304` plus format-`31` now drive generic skinning for PACMANIA’s skinned PACMAN mesh
- Bowling's format `13` / `29` lane and galaxy geometry now decode through the same quantized-stream family instead of falling back to black
- a narrow type `301` fallback now recovers plain terminal scale envelopes that are stored verbatim in some title-node records, which materially improves the title composition without filename or node-name hacks
- title composition is materially improved, but still not 1:1 because type `301` node motion/placement remains only partially decoded

### Runtime progression verified

Verified with `CaptureJamFrame`, `CapturePresentedJamFrame`, `JamInputProbe`, and `JamSequenceProbe`:

- classloading succeeds past the original `NoClassDefFoundError`
- intro/logo rendering runs
- the PACMANIA title scene now shows decoded XF2 textured geometry instead of the earlier conservative black fallback
- a title/start screen appears after intro timing
- sending `ENTER` reaches a screen showing:
  - `HI-SCORE 50000`
  - `SOUND [ON/OFF]`
  - `ROUND 1`
  - `決定キーで開始`
- sending a second `ENTER` reaches an in-level maze/gameplay view
- adding `RIGHT` after that still produces a live in-game frame with `PLAYER1 READY`

This is `confirmed`.

## Avatar Bowling Findings

### Confirmed from bytecode

Avatar Bowling references a broader XF3 surface than PACMANIA. Confirmed direct signatures include:

- controller layer:
  - `xfeController`
  - `xfeNodeController`
  - `xfeControllerSet.addController(xfeController)`
  - `xfeControllerSet.getControllers(xfeNodeListIterator)`
  - `xfeXF2ParameterizedControllerFactory.createController(...)`
  - `xfeXF2ParameterizedControllerLoader(xfeXF2ParameterizedControllerFactory)`
- scene/iteration:
  - `xfeRoot.getClock()`
  - `xfeClock.advance()`
  - `xfeRoot.doIteration()`
  - `xfeRoot.render(xfeCamera, xfRectangleInt, boolean)`
  - `xfeRoot.getControllerSet()`
  - `xfeRoot.getResourceManager()`
- camera/view:
  - `xfeCamera.getPreferredView()`
  - `xfeCamera.getCameraView(xfRectangleInt)`
  - `xfeCamera.getParent()`
  - `xfeCameraView.getViewMatrix()`
  - `xfePreferredView.getFarClip()`
  - `xfePreferredView.getNearClip()`
  - `xfePreferredView.getFOV()`
  - `xfePreferredView.getHorizontalSafe()`
  - `xfePreferredView.getVerticalSafe()`
  - `xfePreferredView.getWidth()`
  - `xfePreferredView.getHeight()`
- actor/group/tree:
  - `xfeActor.getTransformation()`
  - `xfeMatrixTransformation.getMatrix()`
  - `xfeActor.getNodeHierarchy()`
  - `xfeNodeTreeIterator.getNext()`
  - `xfeActor.getZones(xfeNodeListIterator)`
  - `xfeActor.activate(xfeSubTree)`
  - `xfeActor.deactivate()`
  - `xfeGroup.getTransformation()`
- animation:
  - `xfePRSAnimationController.setPlaying(boolean)`
  - `xfePRSAnimationController.setLooping(boolean)`
  - `xfePRSAnimationController.setTick(int)`
  - same three methods on `xfePRSAnimationControllerSet`
- resources/parameters:
  - `xfeResourceManager.getResourceId(String)`
  - `xfeResourceManager.getResource(int)`
  - `xfeResourceManager.addResource(int, xfeResource)`
  - `xfeTextureResource.load(String)`
  - `xfeParameterDataSet.getValue(String)`
  - `xfeParameterDataValue.getType()`
  - `xfeParameterDataStringValue.getString(int)`
- math helpers:
  - `xfMath.matrixIdentity(xfMatrix4)`
  - `xfMath.matrixTranslation(xfMatrix4, xfVector3)`
  - `xfMath.matrixInverseFast(xfMatrix4)`
  - `xfMath.abs(float)`
  - `xfVector3.set(float,float,float)`
  - `xfVector3(xfVector3)`
  - `xfMatrix4(xfMatrix4)`
  - `xfMatrix4.set(xfMatrix4)`
  - `xfMatrix4.mul(xfMatrix4)`
  - direct field read of `xfMatrix4.m` as `float[][]`
- misc:
  - `xfGL.getGL()`
  - `xfGL.setMatrix(int, xfMatrix4)`
  - field types `xfeMicrophone` and `xfeSound`

### Confidence

- signatures above: `confirmed`
- exact behavioral meaning of most controller/resource methods: `high` for signatures, `low` for full semantics

### Launch blocker resolved

Confirmed from bytecode plus `.xf2` records:

- Bowling does not look for a camera globally
- it creates a `CameraController` from a type `303` parameterized-controller record whose target node is `ActorCamera`
- after scene load, it scans `CameraController.getNode().getNodeHierarchy()` and expects an `xfeCamera` descendant there
- `camera.xf2` stores exactly that structure:
  - actor `ActorCamera`
  - controller params `type=camera`, `direction=CamLookAt`, `position=CamPos`
  - child camera node `camera1`

This is `confirmed`.

## NAND Dump Findings

### Direct XF3-name mining

Aggressive byte searches over `resources/n906iu_dump_nand/nand.bin`:

- no direct plain-string hits for:
  - `com/acrodea/xf3`
  - `com.acrodea.xf3`
  - `xfeTransformation`
  - `xfeRoot`
  - `xfeActor`

This is `confirmed`.

Interpretation: either the library is absent from plain uncompressed strings in the dump, or it only survives inside compressed archives/pages that did not expose those strings directly.

### Confirmed game/app evidence inside the NAND

Recovered strings and offsets:

- PACMANIA metadata around offset `6949769`
  - `PackageURL = http://www.nland.jp/iappli/906imyu/pacmania_N906i/pacmania_N906i.jar`
  - `AppClass = PacMania`
  - `TargetDevice = N906i, N906imyu`
  - `DrawArea = 240x325`
- PACMANIA-related names:
  - `PacMania.class` at offsets including `23011365`, `246278623`, `444482167`, `445134041`
- Avatar Bowling-related names:
  - `bowling.class` at offsets `532803193`, `533368955`
  - `BaseCanvas.class` at offsets `532802780`, `533548700`
  - `camera.xf2` at offsets `532803312`, `533375118`
  - `field.xf2` at offsets `532804127`, `533231306`

This is `confirmed`.

### Candidate binaries/resources found in the NAND

- raw ZIP/JAR signatures (`PK\003\004`, `PK\001\002`, `PK\005\006`) found throughout the dump
- carved examples:
  - a small ZIP at offset `849386` containing `_sub_data_jgi.dat`
  - a ZIP region starting near `5647424` with class entries like `of.class`, `og.class`, `oh.class`, `oi.class`, `oj.class`, plus a central directory later in the dump
- nearby printable resource names around the Avatar Bowling region show a full jar-like resource sequence:
  - `XFORGEAPP.SP`
  - `camera.xf2`
  - `CameraController.class`
  - `field.xf2`
  - `GalaxyController.class`
  - `lane01.xf2`
  - `lane02.xf2`
  - `galaxy.xf2`
  - many `.emdl`, `.obm`, `.bmp`, `.gif`, `.mld` assets
- one repeated Bowling archive region around offsets `532803193` / `533368955` clearly contains the game payload itself, not a standalone plain XF3 library
  - this was useful for confirming neighboring controller/resource names, but still did not expose plain `com.acrodea.xf3` class names

This is `confirmed`.

### Neighboring package/resource evidence

The dump clearly contains surrounding game/resource material for titles that depend on XF3-compatible scene data:

- PACMANIA scene/resources:
  - `pm_op1.xf2`
  - `pm_cb1.xf2`
  - `pm_cb2.xf2`
  - `pm_cb3.xf2`
  - `pm_cb4.xf2`
  - `pm_ed1.xf2`
- Avatar Bowling resources/classes:
  - `BaseCanvas.class`
  - `CameraController.class`
  - `GalaxyController.class`
  - `camera.xf2`
  - `field.xf2`

No standalone XF3 library jar filename was recovered from the dump with the searches run here.

## Implemented Compatibility Layer

### Confirmed or high-confidence API implemented

Implemented packages/classes:

- `com.acrodea.xf3.math`
  - `xfVector3`
  - `xfMatrix4`
  - `xfMath`
  - `xfRectangleInt`
- `com.acrodea.xf3`
  - `xfeTransformation`
  - `xfeMatrixTransformation`
  - `xfeNode`
  - `xfeGroup`
  - `xfeSubTree`
  - `xfeZone`
  - `xfeNodeList`
  - `xfeNodeListIterator`
  - `xfeNodeTreeIterator`
  - `xfeGameGraphNodeIterator`
  - `xfeController`
  - `xfeNodeController`
  - `xfeControllerSet`
  - `xfePRSAnimationController`
  - `xfePRSAnimationControllerSet`
  - `xfeActor`
  - `xfeCamera`
  - `xfePreferredView`
  - `xfeCameraView`
  - `xfeClock`
  - `xfeClockAdvanceState`
  - `xfeGameGraphFactory`
  - `xfeRoot`
  - `xfeOGLContext`
  - `xfGL`
  - `xfeResource`
  - `xfeResourceManager`
  - `xfeTextureResource`
  - `xfeParameterDataValue`
  - `xfeParameterDataStringValue`
  - `xfeParameterDataSet`
  - `xfeSound`
  - `xfeMicrophone`
- `com.acrodea.xf3.def`
  - `xfeDefaultHWAcceleratedGameGraphFactory`
  - `xfeDefaultXF2Loader`
- `com.acrodea.xf3.loader`
  - `xfeXF2Context`
  - `xfeXF2Reader`
  - `xfeXF2ChunkLoader`
  - `xfeXF2ParameterizedControllerFactory`
  - `xfeXF2ParameterizedControllerLoader`

### Behavior choices

- `xfeDefaultXF2Loader`
  - `high`
  - parses `.xf2` as a little-endian record stream instead of doing plain string extraction
  - loads nested child scenes from type `1` loader-node records
  - reconstructs actor/camera/group hierarchy from node record payloads
  - creates parameterized controllers from type `303` records through the registered controller factory
  - recovers image/resource names from explicit type `204` records only; no game-specific companion-filename guesses remain in runtime code
- `xfeRoot.render(...)`
  - `low`
  - does not implement real XF3 scene rendering yet
  - instead, if the host graphics object is also the current `GraphicsOGL`, it draws a recovered scene image only when that image fully covers the current viewport
  - it treats the XF3 rectangle as viewport sizing input, not as a 2D image origin
  - when no high-confidence full-scene image resolves, it falls back to a plain black scene instead of drawing likely texture-atlas assets
- clock/iteration/controller/resource behavior
  - `low`
  - narrow compatibility behavior only
  - enough for PACMANIA’s observed intro/title/level flow and for Avatar Bowling’s signatures to resolve

## Remaining Unknowns

- the actual XF2 chunk format and scene graph semantics
- real PRS animation timing and controller behavior
- real camera/view/projection math beyond the narrow helper contracts used here
- XF3 sound/microphone behavior
- whether the NAND still contains a recoverable standalone XF3 library in compressed or fragmented form not reached by the current carving/search pass

## Current Status

What now works for PACMANIA:

- no more startup `NoClassDefFoundError` on `xfeTransformation`
- intro/logo sequence runs
- title/start screen is reachable
- gameplay/in-level screen is reachable with synthetic input
- the earlier corrupted PACMAN logo fallback draw is gone; unsupported XF2 image references now fail closed to black instead of drawing atlas garbage

What now works for Avatar Bowling:

- launch progresses past the earlier `No camera found` / `NullPointerException` on `xfeCamera.getCameraView(...)`
- verified at runtime with `CaptureJamFrame`:
  - 8-second capture reaches the X-FORGE intro/logo frame
  - 18-second capture still shows a live intro sequence instead of crashing at scene-load completion
  - current intro captures no longer show stale-frame accumulation after implementing host-side `GraphicsOGL.glClearColor(...)` and `glClear(...)` behavior in the desktop graphics fallback

What is still incomplete:

- the software renderer still only implements the recovered XF2 subset, not a full XF3 feature set
- PACMANIA title output is still not 1:1; the remaining gap is concentrated in partially decoded type `301` PRS/controller behavior
- a further reverse-engineering pass confirmed that `301` is not just a tail transform override:
  - it carries controller-linked packed PRS sample data with a shared duration field
  - experimental runtime playback of those packed channels changed the PACMANIA logo/title composition materially, which confirms the remaining error is in channel decoding/scheduling rather than in mesh absence
  - that experimental playback was not kept in runtime code yet because the recovered channel packing is still incomplete enough to regress the title/start path
- the recovered UV normalization is structurally supported by the decoded data, but in the latest headless captures it did not yet change the visible PACMANIA or Bowling frames, so additional scene-state/controller work is still required
