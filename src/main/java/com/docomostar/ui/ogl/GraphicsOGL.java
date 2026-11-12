package com.docomostar.ui.ogl;

/** STAR entry points into the shared DoJa OpenGL ES renderer. */
public interface GraphicsOGL extends com.nttdocomo.ui.ogl.GraphicsOGL {
    /** Host integration, supplied by the STAR graphics view. */
    com.nttdocomo.ui.ogl.GraphicsOGL hostGraphicsOGL();
    @Override default void beginDrawing() {
        try { hostGraphicsOGL().beginDrawing(); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void endDrawing() {
        try { hostGraphicsOGL().endDrawing(); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glActiveTexture(int texture) {
        try { hostGraphicsOGL().glActiveTexture(texture); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glAlphaFunc(int func, float ref) {
        try { hostGraphicsOGL().glAlphaFunc(func, ref); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glBindTexture(int target, int texture) {
        try { hostGraphicsOGL().glBindTexture(target, texture); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glBlendFunc(int sfactor, int dfactor) {
        try { hostGraphicsOGL().glBlendFunc(sfactor, dfactor); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glClear(int mask) {
        try { hostGraphicsOGL().glClear(mask); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glClearColor(float red, float green, float blue, float alpha) {
        try { hostGraphicsOGL().glClearColor(red, green, blue, alpha); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glClearDepthf(float depth) {
        try { hostGraphicsOGL().glClearDepthf(depth); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glClearStencil(int s) {
        try { hostGraphicsOGL().glClearStencil(s); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glClientActiveTexture(int texture) {
        try { hostGraphicsOGL().glClientActiveTexture(texture); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glColor4f(float red, float green, float blue, float alpha) {
        try { hostGraphicsOGL().glColor4f(red, green, blue, alpha); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        try { hostGraphicsOGL().glColorMask(red, green, blue, alpha); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glColorPointer(int size, int type, int stride, com.nttdocomo.ui.ogl.DirectBuffer pointer) {
        try { hostGraphicsOGL().glColorPointer(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glColorPointer(int size, int type, int stride, DirectBuffer pointer) { glColorPointer(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
    @Override default void glColorPointer(int size, int type, int stride, int pointer) {
        try { hostGraphicsOGL().glColorPointer(size, type, stride, pointer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, com.nttdocomo.ui.ogl.DirectBuffer data) {
        try { hostGraphicsOGL().glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(data)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, DirectBuffer data) { glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(data)); }
    @Override default void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, com.nttdocomo.ui.ogl.DirectBuffer data) {
        try { hostGraphicsOGL().glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(data)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, DirectBuffer data) { glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(data)); }
    @Override default void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border) {
        try { hostGraphicsOGL().glCopyTexImage2D(target, level, internalformat, x, y, width, height, border); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        try { hostGraphicsOGL().glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glCullFace(int mode) {
        try { hostGraphicsOGL().glCullFace(mode); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDeleteTextures(int n, int[] textures) {
        try { hostGraphicsOGL().glDeleteTextures(n, textures); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDepthFunc(int func) {
        try { hostGraphicsOGL().glDepthFunc(func); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDepthMask(boolean flag) {
        try { hostGraphicsOGL().glDepthMask(flag); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDepthRangef(float zNear, float zFar) {
        try { hostGraphicsOGL().glDepthRangef(zNear, zFar); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDisable(int cap) {
        try { hostGraphicsOGL().glDisable(cap); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDisableClientState(int array) {
        try { hostGraphicsOGL().glDisableClientState(array); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDrawArrays(int mode, int first, int count) {
        try { hostGraphicsOGL().glDrawArrays(mode, first, count); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDrawElements(int mode, int count, int type, com.nttdocomo.ui.ogl.DirectBuffer indices) {
        try { hostGraphicsOGL().glDrawElements(mode, count, type, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(indices)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glDrawElements(int mode, int count, int type, DirectBuffer indices) { glDrawElements(mode, count, type, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(indices)); }
    @Override default void glDrawElements(int mode, int count, int type, int indices) {
        try { hostGraphicsOGL().glDrawElements(mode, count, type, indices); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glEnable(int cap) {
        try { hostGraphicsOGL().glEnable(cap); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glEnableClientState(int array) {
        try { hostGraphicsOGL().glEnableClientState(array); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glFlush() {
        try { hostGraphicsOGL().glFlush(); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glFogf(int pname, float param) {
        try { hostGraphicsOGL().glFogf(pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glFogfv(int pname, float[] params) {
        try { hostGraphicsOGL().glFogfv(pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glFrontFace(int mode) {
        try { hostGraphicsOGL().glFrontFace(mode); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glFrustumf(float left, float right, float bottom, float top, float zNear, float zFar) {
        try { hostGraphicsOGL().glFrustumf(left, right, bottom, top, zNear, zFar); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGenTextures(int n, int[] textures) {
        try { hostGraphicsOGL().glGenTextures(n, textures); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default int glGetError() {
        try { return hostGraphicsOGL().glGetError(); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetIntegerv(int pname, int[] params) {
        try { hostGraphicsOGL().glGetIntegerv(pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glHint(int target, int mode) {
        try { hostGraphicsOGL().glHint(target, mode); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLightModelf(int pname, float param) {
        try { hostGraphicsOGL().glLightModelf(pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLightModelfv(int pname, float[] params) {
        try { hostGraphicsOGL().glLightModelfv(pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLightf(int light, int pname, float param) {
        try { hostGraphicsOGL().glLightf(light, pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLightfv(int light, int pname, float[] params) {
        try { hostGraphicsOGL().glLightfv(light, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLineWidth(float width) {
        try { hostGraphicsOGL().glLineWidth(width); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLoadIdentity() {
        try { hostGraphicsOGL().glLoadIdentity(); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLoadMatrixf(float[] m) {
        try { hostGraphicsOGL().glLoadMatrixf(m); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLogicOp(int opcode) {
        try { hostGraphicsOGL().glLogicOp(opcode); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glMaterialf(int face, int pname, float param) {
        try { hostGraphicsOGL().glMaterialf(face, pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glMaterialfv(int face, int pname, float[] params) {
        try { hostGraphicsOGL().glMaterialfv(face, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glMatrixMode(int mode) {
        try { hostGraphicsOGL().glMatrixMode(mode); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glMultMatrixf(float[] m) {
        try { hostGraphicsOGL().glMultMatrixf(m); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glMultiTexCoord4f(int target, float s, float t, float r, float q) {
        try { hostGraphicsOGL().glMultiTexCoord4f(target, s, t, r, q); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glNormal3f(float nx, float ny, float nz) {
        try { hostGraphicsOGL().glNormal3f(nx, ny, nz); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glNormalPointer(int type, int stride, com.nttdocomo.ui.ogl.DirectBuffer pointer) {
        try { hostGraphicsOGL().glNormalPointer(type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glNormalPointer(int type, int stride, DirectBuffer pointer) { glNormalPointer(type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
    @Override default void glNormalPointer(int type, int stride, int pointer) {
        try { hostGraphicsOGL().glNormalPointer(type, stride, pointer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glOrthof(float left, float right, float bottom, float top, float zNear, float zFar) {
        try { hostGraphicsOGL().glOrthof(left, right, bottom, top, zNear, zFar); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glPixelStorei(int pname, int param) {
        try { hostGraphicsOGL().glPixelStorei(pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glPointSize(float size) {
        try { hostGraphicsOGL().glPointSize(size); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glPolygonOffset(float factor, float units) {
        try { hostGraphicsOGL().glPolygonOffset(factor, units); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glPopMatrix() {
        try { hostGraphicsOGL().glPopMatrix(); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glPushMatrix() {
        try { hostGraphicsOGL().glPushMatrix(); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glRotatef(float angle, float x, float y, float z) {
        try { hostGraphicsOGL().glRotatef(angle, x, y, z); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glSampleCoverage(float value, boolean invert) {
        try { hostGraphicsOGL().glSampleCoverage(value, invert); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glScalef(float x, float y, float z) {
        try { hostGraphicsOGL().glScalef(x, y, z); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glScissor(int x, int y, int width, int height) {
        try { hostGraphicsOGL().glScissor(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glShadeModel(int mode) {
        try { hostGraphicsOGL().glShadeModel(mode); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glStencilFunc(int func, int ref, int mask) {
        try { hostGraphicsOGL().glStencilFunc(func, ref, mask); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glStencilMask(int mask) {
        try { hostGraphicsOGL().glStencilMask(mask); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glStencilOp(int fail, int zfail, int zpass) {
        try { hostGraphicsOGL().glStencilOp(fail, zfail, zpass); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexCoordPointer(int size, int type, int stride, com.nttdocomo.ui.ogl.DirectBuffer pointer) {
        try { hostGraphicsOGL().glTexCoordPointer(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glTexCoordPointer(int size, int type, int stride, DirectBuffer pointer) { glTexCoordPointer(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
    @Override default void glTexCoordPointer(int size, int type, int stride, int pointer) {
        try { hostGraphicsOGL().glTexCoordPointer(size, type, stride, pointer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexEnvf(int target, int pname, float param) {
        try { hostGraphicsOGL().glTexEnvf(target, pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexEnvfv(int target, int pname, float[] params) {
        try { hostGraphicsOGL().glTexEnvfv(target, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, com.nttdocomo.ui.ogl.DirectBuffer pixels) {
        try { hostGraphicsOGL().glTexImage2D(target, level, internalformat, width, height, border, format, type, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pixels)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, DirectBuffer pixels) { glTexImage2D(target, level, internalformat, width, height, border, format, type, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pixels)); }
    @Override default void glTexParameterf(int target, int pname, float param) {
        try { hostGraphicsOGL().glTexParameterf(target, pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, com.nttdocomo.ui.ogl.DirectBuffer pixels) {
        try { hostGraphicsOGL().glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pixels)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, DirectBuffer pixels) { glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pixels)); }
    @Override default void glTranslatef(float x, float y, float z) {
        try { hostGraphicsOGL().glTranslatef(x, y, z); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glVertexPointer(int size, int type, int stride, com.nttdocomo.ui.ogl.DirectBuffer pointer) {
        try { hostGraphicsOGL().glVertexPointer(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glVertexPointer(int size, int type, int stride, DirectBuffer pointer) { glVertexPointer(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
    @Override default void glVertexPointer(int size, int type, int stride, int pointer) {
        try { hostGraphicsOGL().glVertexPointer(size, type, stride, pointer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glViewport(int x, int y, int width, int height) {
        try { hostGraphicsOGL().glViewport(x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, com.nttdocomo.ui.ogl.ByteBuffer data) {
        try { hostGraphicsOGL().glCompressedTexImage2D(target, level, internalformat, width, height, border, (com.nttdocomo.ui.ogl.ByteBuffer) DirectBufferFactory.unwrap(data)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, ByteBuffer data) { glCompressedTexImage2D(target, level, internalformat, width, height, border, (com.nttdocomo.ui.ogl.ByteBuffer) DirectBufferFactory.unwrap(data)); }
    @Override default void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, com.nttdocomo.ui.ogl.ByteBuffer data) {
        try { hostGraphicsOGL().glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, (com.nttdocomo.ui.ogl.ByteBuffer) DirectBufferFactory.unwrap(data)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, ByteBuffer data) { glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, (com.nttdocomo.ui.ogl.ByteBuffer) DirectBufferFactory.unwrap(data)); }
    @Override default void glDeleteTextures(int[] textures) {
        try { hostGraphicsOGL().glDeleteTextures(textures); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDrawElements(int mode, int count, com.nttdocomo.ui.ogl.DirectBuffer indices) {
        try { hostGraphicsOGL().glDrawElements(mode, count, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(indices)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glDrawElements(int mode, int count, DirectBuffer indices) { glDrawElements(mode, count, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(indices)); }
    @Override default void glGenTextures(int[] textures) {
        try { hostGraphicsOGL().glGenTextures(textures); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default String glGetString(int name) {
        try { return hostGraphicsOGL().glGetString(name); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glBindBuffer(int target, int buffer) {
        try { hostGraphicsOGL().glBindBuffer(target, buffer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glBufferData(int target, com.nttdocomo.ui.ogl.DirectBuffer data, int usage) {
        try { hostGraphicsOGL().glBufferData(target, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(data), usage); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glBufferData(int target, DirectBuffer data, int usage) { glBufferData(target, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(data), usage); }
    @Override default void glBufferSubData(int target, int offset, com.nttdocomo.ui.ogl.DirectBuffer data) {
        try { hostGraphicsOGL().glBufferSubData(target, offset, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(data)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glBufferSubData(int target, int offset, DirectBuffer data) { glBufferSubData(target, offset, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(data)); }
    @Override default void glClipPlanef(int plane, float[] equation) {
        try { hostGraphicsOGL().glClipPlanef(plane, equation); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glColor4ub(short red, short green, short blue, short alpha) {
        try { hostGraphicsOGL().glColor4ub(red, green, blue, alpha); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glCurrentPaletteMatrixOES(int matrixpaletteindex) {
        try { hostGraphicsOGL().glCurrentPaletteMatrixOES(matrixpaletteindex); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDeleteBuffers(int[] buffers) {
        try { hostGraphicsOGL().glDeleteBuffers(buffers); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDrawTexfOES(float x, float y, float z, float width, float height) {
        try { hostGraphicsOGL().glDrawTexfOES(x, y, z, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDrawTexiOES(int x, int y, int z, int width, int height) {
        try { hostGraphicsOGL().glDrawTexiOES(x, y, z, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glDrawTexsOES(short x, short y, short z, short width, short height) {
        try { hostGraphicsOGL().glDrawTexsOES(x, y, z, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGenBuffers(int[] buffers) {
        try { hostGraphicsOGL().glGenBuffers(buffers); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetBooleanv(int pname, boolean[] params) {
        try { hostGraphicsOGL().glGetBooleanv(pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetBufferParameteriv(int target, int pname, int[] params) {
        try { hostGraphicsOGL().glGetBufferParameteriv(target, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetClipPlanef(int pname, float[] eqn) {
        try { hostGraphicsOGL().glGetClipPlanef(pname, eqn); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetFloatv(int pname, float[] params) {
        try { hostGraphicsOGL().glGetFloatv(pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetLightfv(int light, int pname, float[] params) {
        try { hostGraphicsOGL().glGetLightfv(light, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetMaterialfv(int face, int pname, float[] params) {
        try { hostGraphicsOGL().glGetMaterialfv(face, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetTexEnvfv(int env, int pname, float[] params) {
        try { hostGraphicsOGL().glGetTexEnvfv(env, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetTexEnviv(int env, int pname, int[] params) {
        try { hostGraphicsOGL().glGetTexEnviv(env, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetTexParameterfv(int target, int pname, float[] params) {
        try { hostGraphicsOGL().glGetTexParameterfv(target, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glGetTexParameteriv(int target, int pname, int[] params) {
        try { hostGraphicsOGL().glGetTexParameteriv(target, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default boolean glIsBuffer(int buffer) {
        try { return hostGraphicsOGL().glIsBuffer(buffer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default boolean glIsEnabled(int cap) {
        try { return hostGraphicsOGL().glIsEnabled(cap); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default boolean glIsTexture(int texture) {
        try { return hostGraphicsOGL().glIsTexture(texture); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glLoadPaletteFromModelViewMatrixOES() {
        try { hostGraphicsOGL().glLoadPaletteFromModelViewMatrixOES(); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glMatrixIndexPointerOES(int size, int type, int stride, com.nttdocomo.ui.ogl.DirectBuffer pointer) {
        try { hostGraphicsOGL().glMatrixIndexPointerOES(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glMatrixIndexPointerOES(int size, int type, int stride, DirectBuffer pointer) { glMatrixIndexPointerOES(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
    @Override default void glMatrixIndexPointerOES(int size, int type, int stride, int pointer) {
        try { hostGraphicsOGL().glMatrixIndexPointerOES(size, type, stride, pointer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glPointParameterf(int pname, float param) {
        try { hostGraphicsOGL().glPointParameterf(pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glPointParameterfv(int pname, float[] params) {
        try { hostGraphicsOGL().glPointParameterfv(pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glPointSizePointerOES(int type, int stride, com.nttdocomo.ui.ogl.DirectBuffer pointer) {
        try { hostGraphicsOGL().glPointSizePointerOES(type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glPointSizePointerOES(int type, int stride, DirectBuffer pointer) { glPointSizePointerOES(type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
    @Override default void glPointSizePointerOES(int type, int stride, int pointer) {
        try { hostGraphicsOGL().glPointSizePointerOES(type, stride, pointer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexEnvi(int target, int pname, int param) {
        try { hostGraphicsOGL().glTexEnvi(target, pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexEnviv(int target, int pname, int[] params) {
        try { hostGraphicsOGL().glTexEnviv(target, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexParameterfv(int target, int pname, float[] params) {
        try { hostGraphicsOGL().glTexParameterfv(target, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexParameteri(int target, int pname, int param) {
        try { hostGraphicsOGL().glTexParameteri(target, pname, param); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glTexParameteriv(int target, int pname, int[] params) {
        try { hostGraphicsOGL().glTexParameteriv(target, pname, params); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    @Override default void glWeightPointerOES(int size, int type, int stride, com.nttdocomo.ui.ogl.DirectBuffer pointer) {
        try { hostGraphicsOGL().glWeightPointerOES(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
    default void glWeightPointerOES(int size, int type, int stride, DirectBuffer pointer) { glWeightPointerOES(size, type, stride, (com.nttdocomo.ui.ogl.DirectBuffer) DirectBufferFactory.unwrap(pointer)); }
    @Override default void glWeightPointerOES(int size, int type, int stride, int pointer) {
        try { hostGraphicsOGL().glWeightPointerOES(size, type, stride, pointer); }
        catch (com.nttdocomo.ui.UIException failure) { throw com.docomostar.ui.UIException.from(failure); }
    }
}
