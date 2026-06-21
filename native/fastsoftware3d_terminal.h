#ifndef FASTTERMINAL_H
#define FASTTERMINAL_H

#include <jni.h>

/**
 * @file fastterminal.h
 * @brief JNI Exported Method Declarations for the FastTerminal Windows native hooks library.
 * 
 * Provides native Windows console querying capabilities (dimensions, raw input, active focus,
 * cell font dimensions, and mouse telemetry) to Java wrapper classes.
 */

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Queries the visible terminal window size in characters.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jintArray An array containing [columns, rows], or NULL if query fails.
 */
JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_getTerminalSize(JNIEnv* env, jclass clazz);

/**
 * @brief Configures standard console mode input/output flags (raw mode toggle).
 * 
 * Sets ENABLE_WINDOW_INPUT and ENABLE_MOUSE_INPUT, and disables line editing/echoing
 * for smooth real-time keypresses and mouse telemetry.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @param enableRaw jboolean flag to enable (true) or disable/restore (false) raw mode.
 */
JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setRawMode(JNIEnv* env, jclass clazz, jboolean enableRaw);

/**
 * @brief Configures high-precision Virtual Terminal raw modes for standard input/output.
 * 
 * Clears ENABLE_LINE_INPUT, ENABLE_ECHO_INPUT, and ENABLE_PROCESSED_INPUT,
 * and enables ENABLE_VIRTUAL_TERMINAL_INPUT (0x0200) to allow direct transmission
 * of SGR mouse escape sequences character-by-character.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @param enableRaw jboolean flag to enable (true) or restore (false) raw mode.
 */
JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setAnsiRawMode(JNIEnv* env, jclass clazz, jboolean enableRaw);

/**
 * @brief Retrieves hardware console window dimensions, font sizes, and layout.
 * 
 * Returns an array containing the screen rect bounding dimensions, client area offsets,
 * and current console font width/height cell properties.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jintArray An array containing [left, top, clientX, clientY, fontWidth, fontHeight], or NULL on error.
 */
JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_getConsoleWindowInfo(JNIEnv* env, jclass clazz);

/**
 * @brief Determines if the host terminal window currently holds active OS focus.
 * 
 * Walks parent and root owner window structures to correctly identify focus when
 * running embedded inside modern terminal hosts like wt.exe (Windows Terminal).
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jboolean JNI_TRUE if focused, JNI_FALSE otherwise.
 */
JNIEXPORT jboolean JNICALL Java_fastsoftware3d_terminal_FastTerminal_isTerminalFocused(JNIEnv* env, jclass clazz);

/**
 * @brief Checks if the mouse cursor is hovering over the active terminal window boundary.
 * 
 * Uses screen space coordinate translations to check mouse bounds dynamically.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jboolean JNI_TRUE if mouse is hovering over the terminal, JNI_FALSE otherwise.
 */
JNIEXPORT jboolean JNICALL Java_fastsoftware3d_terminal_FastTerminal_isMouseOverTerminal(JNIEnv* env, jclass clazz);

/**
 * @brief Toggles system mouse pointer cursor visibility globally.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @param visible jboolean true to show, false to hide.
 */
JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setSystemCursorVisible(JNIEnv* env, jclass clazz, jboolean visible);

/**
 * @brief Reads the visible console screen buffer and returns cell data as a flat int array.
 *
 * Each cell is encoded as three consecutive ints: [codepoint, fg_rgb, bg_rgb].
 * Colors are converted from the legacy 4-bit CHAR_INFO attribute byte to 24-bit RGB.
 * The first two elements of the array are [cols, rows] so the caller knows the dimensions.
 *
 * Total array length = 2 + (cols * rows * 3).
 *
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jintArray Flat cell data array, or NULL on failure.
 */
JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_readConsoleOutput(JNIEnv* env, jclass clazz);

/**
 * @brief Returns the current console cursor position as [col, row] (0-based).
 */
JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_getCursorPosition(JNIEnv* env, jclass clazz);
JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setTitle(JNIEnv* env, jclass clazz, jstring title);

/**
 * @brief Performs high-performance double-buffered cell diffing and ANSI escape sequence building in C++.
 */
JNIEXPORT jint JNICALL Java_fastsoftware3d_terminal_FastTerminalRenderer_renderAnsiNative(
    JNIEnv* env, jclass clazz,
    jintArray compositeCPArray, jintArray compositeFgArray, jintArray compositeBgArray,
    jintArray prevCPArray, jintArray prevFgArray, jintArray prevBgArray,
    jbyteArray outBufferArray, jint width, jint height,
    jboolean forceFullRedraw, jboolean diffRenderingEnabled, jboolean dirtyRectanglesEnabled
);

#ifdef __cplusplus
}
#endif

#endif // FASTTERMINAL_H
