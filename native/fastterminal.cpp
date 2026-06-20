#include "fastsoftware3d_terminal.h"
#include <windows.h>
#include <stdio.h>

/**
 * @file fastterminal.cpp
 * @brief Native JNI C++ implementation for FastTerminal console hooks.
 * 
 * Provides high-performance integration with the Win32 Console APIs to query 
 * layout, toggle input modes, walk parent focus chains, and track pointer locations.
 */

// ============================================================================
// DLL Entry Point
// ============================================================================

/**
 * @brief Dynamic-Link Library entry point function.
 * 
 * Configures optimization flags on thread attachment.
 * 
 * @param hModule Handle to the DLL module.
 * @param ul_reason_for_call Reason flag calling the entry point.
 * @param lpReserved Reserved parameter.
 * @return BOOL TRUE on success, FALSE otherwise.
 */
BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {
    switch (ul_reason_for_call) {
        case DLL_PROCESS_ATTACH:
            DisableThreadLibraryCalls(hModule);
            // Force the Windows Console to natively interpret UTF-8 outputs
            SetConsoleOutputCP(CP_UTF8);
            SetConsoleCP(CP_UTF8);
            break;
        case DLL_PROCESS_DETACH:
            break;
    }
    return TRUE;
}

// ============================================================================
// JNI Implementations
// ============================================================================

/**
 * @brief Queries current console buffer width (columns) and visible window height.
 * 
 * Implements: JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_getTerminalSize
 * Retrieves the screen buffer info using Win32 `GetConsoleScreenBufferInfo`.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jintArray [cols, rows] console dimensions, or NULL on memory allocation failure.
 */
JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_getTerminalSize(JNIEnv* env, jclass clazz) {
    jintArray result = env->NewIntArray(2);
    if (result == NULL) return NULL;
    
    jint dims[2] = { 120, 30 }; // Premium default fallback geometry
    
    HANDLE hConsole = GetStdHandle(STD_OUTPUT_HANDLE);
    if (hConsole != INVALID_HANDLE_VALUE && hConsole != NULL) {
        CONSOLE_SCREEN_BUFFER_INFO csbi;
        if (GetConsoleScreenBufferInfo(hConsole, &csbi)) {
            dims[0] = csbi.srWindow.Right - csbi.srWindow.Left + 1; // Retrieve actual visible window column width!
            dims[1] = csbi.srWindow.Bottom - csbi.srWindow.Top + 1;
        }
    }
    
    env->SetIntArrayRegion(result, 0, 2, dims);
    return result;
}

/**
 * @brief Configures standard raw console modes (echo off, direct inputs, etc.) via SetConsoleMode.
 * 
 * Implements: JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setRawMode
 * Saves the original mode on first activation to allow safe cleanup later.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @param enableRaw True to set direct raw terminal input flags, false to restore original console state.
 */
JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setRawMode(JNIEnv* env, jclass clazz, jboolean enableRaw) {
    HANDLE hInput = GetStdHandle(STD_INPUT_HANDLE);
    if (hInput == INVALID_HANDLE_VALUE || hInput == NULL) return;
    
    static DWORD originalMode = 0;
    static bool hasOriginalMode = false;
    
    if (enableRaw) {
        if (!hasOriginalMode) {
            GetConsoleMode(hInput, &originalMode);
            hasOriginalMode = true;
        }
        DWORD rawMode = ENABLE_EXTENDED_FLAGS | ENABLE_WINDOW_INPUT | ENABLE_MOUSE_INPUT;
        SetConsoleMode(hInput, rawMode);
    } else {
        if (hasOriginalMode) {
            SetConsoleMode(hInput, originalMode);
        }
    }
}

/**
 * @brief Configures high-precision Virtual Terminal raw modes for standard input/output.
 */
JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setAnsiRawMode(JNIEnv* env, jclass clazz, jboolean enableRaw) {
    HANDLE hInput = GetStdHandle(STD_INPUT_HANDLE);
    if (hInput == INVALID_HANDLE_VALUE || hInput == NULL) return;
    
    static DWORD originalMode = 0;
    static bool hasOriginalMode = false;
    
    if (enableRaw) {
        if (!hasOriginalMode) {
            GetConsoleMode(hInput, &originalMode);
            hasOriginalMode = true;
        }
        
        // Disable line input (ENABLE_LINE_INPUT), echo input (ENABLE_ECHO_INPUT),
        // and processed input (ENABLE_PROCESSED_INPUT) to retrieve keys instantly.
        // Enable virtual terminal input (0x0200) to instruct conhost/wt to transmit
        // ANSI mouse sequences (SGR-1006) to the input stream.
        DWORD rawMode = originalMode;
        rawMode &= ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT | ENABLE_PROCESSED_INPUT);
        rawMode |= 0x0200; // ENABLE_VIRTUAL_TERMINAL_INPUT
        SetConsoleMode(hInput, rawMode);
    } else {
        if (hasOriginalMode) {
            SetConsoleMode(hInput, originalMode);
        }
    }
}

static BOOL CALLBACK FindTerminalChildEnum(HWND hwnd, LPARAM lParam) {
    char className[256];
    if (GetClassNameA(hwnd, className, sizeof(className))) {
        if (strstr(className, "TermControl") != NULL || 
            strstr(className, "Console") != NULL ||
            strstr(className, "VirtualConsole") != NULL) {
            if (IsWindowVisible(hwnd)) {
                *(HWND*)lParam = hwnd;
                return FALSE; // Found active visible terminal panel, stop!
            }
        }
    }
    return TRUE; // Continue scanning
}

/**
 * @brief Retrieves the console window's screen rect, client offset, and font character cell size.
 * 
 * Implements: JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_getConsoleWindowInfo
 * Useful for high-precision cursor positioning calculations, especially in custom UI elements.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jintArray Int array holding size and coordinate offsets, or NULL on error.
 */
JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_getConsoleWindowInfo(JNIEnv* env, jclass clazz) {
    HWND hwnd = GetConsoleWindow();
    
    // Check if we are hosted under Windows Terminal or another root container
    HWND hwndForeground = GetForegroundWindow();
    if (hwndForeground != NULL) {
        bool isOurWindow = (hwndForeground == hwnd);
        if (!isOurWindow) {
            HWND parent = hwnd;
            while (parent != NULL) {
                if (parent == hwndForeground) {
                    isOurWindow = true;
                    break;
                }
                parent = GetParent(parent);
            }
            if (!isOurWindow) {
                if (GetAncestor(hwnd, GA_ROOT) == hwndForeground || GetAncestor(hwnd, GA_ROOTOWNER) == hwndForeground) {
                    isOurWindow = true;
                }
            }
        }
        if (isOurWindow) {
            HWND hwndTerminalChild = NULL;
            EnumChildWindows(hwndForeground, FindTerminalChildEnum, (LPARAM)&hwndTerminalChild);
            if (hwndTerminalChild != NULL) {
                hwnd = hwndTerminalChild;
            } else {
                hwnd = hwndForeground;
            }
        }
    }

    RECT rect = {0, 0, 0, 0};
    GetWindowRect(hwnd, &rect);
    
    HANDLE hConsole = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_FONT_INFO fontInfo;
    int fontWidth = 8;
    int fontHeight = 16;
    if (GetCurrentConsoleFont(hConsole, FALSE, &fontInfo)) {
        COORD fontSize = GetConsoleFontSize(hConsole, fontInfo.nFont);
        fontWidth = fontSize.X;
        fontHeight = fontSize.Y;
    }
    
    POINT clientOffset = {0, 0};
    ClientToScreen(hwnd, &clientOffset);
    
    RECT clientRect = {0, 0, 0, 0};
    GetClientRect(hwnd, &clientRect);
    int clientWidth = clientRect.right;
    int clientHeight = clientRect.bottom;
    
    jintArray result = env->NewIntArray(8);
    if (result == NULL) return NULL;
    
    jint info[8] = {
        (jint)rect.left, (jint)rect.top,
        (jint)clientOffset.x, (jint)clientOffset.y,
        (jint)fontWidth, (jint)fontHeight,
        (jint)clientWidth, (jint)clientHeight
    };
    
    env->SetIntArrayRegion(result, 0, 8, info);
    return result;
}

/**
 * @brief Checks if our console window is the active focused window in Windows.
 * 
 * Implements: JNIEXPORT jboolean JNICALL Java_fastsoftware3d_terminal_FastTerminal_isTerminalFocused
 * Correctly walks root ancestor boundaries for Modern Windows Terminal (wt.exe) host environments.
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jboolean True if focused, False otherwise.
 */
JNIEXPORT jboolean JNICALL Java_fastsoftware3d_terminal_FastTerminal_isTerminalFocused(JNIEnv* env, jclass clazz) {
    HWND hwndConsole = GetConsoleWindow();
    if (hwndConsole == NULL) return JNI_FALSE;
    
    HWND hwndForeground = GetForegroundWindow();
    if (hwndForeground == NULL) return JNI_FALSE;
    
    if (hwndForeground == hwndConsole) return JNI_TRUE;
    
    // Check root ancestors and owner window trees (crucial for wt.exe / Windows Terminal)
    if (GetAncestor(hwndConsole, GA_ROOT) == hwndForeground) return JNI_TRUE;
    if (GetAncestor(hwndConsole, GA_ROOTOWNER) == hwndForeground) return JNI_TRUE;
    
    // Walk parent hierarchy to handle embedded hosts
    HWND parent = hwndConsole;
    while ((parent = GetParent(parent)) != NULL) {
        if (parent == hwndForeground) return JNI_TRUE;
    }
    
    return JNI_FALSE;
}

/**
 * @brief Checks if the mouse cursor is hovering over our terminal window.
 * 
 * Implements: JNIEXPORT jboolean JNICALL Java_fastsoftware3d_terminal_FastTerminal_isMouseOverTerminal
 * 
 * @param env Pointer to the JNI environment.
 * @param clazz The calling Java class reference.
 * @return jboolean True if mouse is hovering over terminal, False otherwise.
 */
JNIEXPORT jboolean JNICALL Java_fastsoftware3d_terminal_FastTerminal_isMouseOverTerminal(JNIEnv* env, jclass clazz) {
    HWND hwndConsole = GetConsoleWindow();
    if (hwndConsole == NULL) return JNI_FALSE;

    POINT pt;
    if (GetCursorPos(&pt)) {
        HWND hwndUnderMouse = WindowFromPoint(pt);
        if (hwndUnderMouse == hwndConsole || IsChild(hwndConsole, hwndUnderMouse)) {
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

static bool cursorHidden = false;

/**
 * @brief Toggles system mouse pointer cursor visibility globally.
 * 
 * Implements: JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setSystemCursorVisible
 */
JNIEXPORT void JNICALL Java_fastsoftware3d_terminal_FastTerminal_setSystemCursorVisible(JNIEnv* env, jclass clazz, jboolean visible) {
    if (!visible) {
        if (!cursorHidden) {
            int w = GetSystemMetrics(SM_CXCURSOR);
            int h = GetSystemMetrics(SM_CYCURSOR);
            int maskSize = (w * h) / 8;
            BYTE* andMask = (BYTE*)malloc(maskSize);
            BYTE* xorMask = (BYTE*)malloc(maskSize);
            if (andMask && xorMask) {
                memset(andMask, 0xFF, maskSize);
                memset(xorMask, 0x00, maskSize);
                HCURSOR hEmptyNormal = CreateCursor(GetModuleHandle(NULL), 0, 0, w, h, andMask, xorMask);
                HCURSOR hEmptyIBeam = CreateCursor(GetModuleHandle(NULL), 0, 0, w, h, andMask, xorMask);
                HCURSOR hEmptyHand = CreateCursor(GetModuleHandle(NULL), 0, 0, w, h, andMask, xorMask);
                if (hEmptyNormal != NULL) {
                    SetSystemCursor(hEmptyNormal, 32512); // OCR_NORMAL
                    if (hEmptyIBeam != NULL) SetSystemCursor(hEmptyIBeam, 32513); // OCR_IBEAM
                    if (hEmptyHand != NULL) SetSystemCursor(hEmptyHand, 32649); // OCR_HAND
                    cursorHidden = true;
                    // Force immediate cursor redraw by setting cursor pos to itself
                    POINT p;
                    if (GetCursorPos(&p)) {
                        SetCursorPos(p.x, p.y);
                    }
                }
            }
            if (andMask) free(andMask);
            if (xorMask) free(xorMask);
        }
    } else {
        if (cursorHidden) {
            SystemParametersInfo(SPI_SETCURSORS, 0, NULL, 0);
            cursorHidden = false;
            // Force immediate cursor redraw by setting cursor pos to itself
            POINT p;
            if (GetCursorPos(&p)) {
                SetCursorPos(p.x, p.y);
            }
        }
    }
}

// ============================================================================
// Console Screen Buffer Snapshot
// ============================================================================

/**
 * @brief Legacy 4-bit console color index → 24-bit RGB lookup table.
 *
 * Matches the default Windows Terminal / conhost color palette so the snapshot
 * looks as close as possible to what the user actually sees.
 *
 * Index layout (CHAR_INFO attribute nibbles):
 *   bits 0-3 = foreground color index
 *   bits 4-7 = background color index
 */
static const COLORREF CONSOLE_PALETTE[16] = {
    0x0C0C0C, // 0  Black
    0x0037DA, // 1  Dark Blue
    0x13A10E, // 2  Dark Green
    0x3A96DD, // 3  Dark Cyan
    0xC50F1F, // 4  Dark Red
    0x881798, // 5  Dark Magenta
    0xC19C00, // 6  Dark Yellow
    0xCCCCCC, // 7  Gray
    0x767676, // 8  Dark Gray
    0x3B78FF, // 9  Blue
    0x16C60C, // 10 Green
    0x61D6D6, // 11 Cyan
    0xE74856, // 12 Red
    0xB4009E, // 13 Magenta
    0xF9F1A5, // 14 Yellow
    0xF2F2F2  // 15 White
};

/**
 * @brief Reads the visible console screen buffer and returns cell data as a flat int array.
 *
 * Layout: [cols, rows, cp0, fg0, bg0, cp1, fg1, bg1, ...]
 * Total length = 2 + cols * rows * 3.
 *
 * Characters are returned as Unicode codepoints (ReadConsoleOutputW gives WCHAR).
 * Colors are mapped from the 4-bit CHAR_INFO attribute byte using the default palette.
 * Falls back gracefully: returns a 2-element array [0, 0] on any failure so Java
 * can detect the unsupported case (e.g. Windows Terminal pseudo-console).
 */
JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_readConsoleOutput(JNIEnv* env, jclass clazz) {
    HANDLE hOut = GetStdHandle(STD_OUTPUT_HANDLE);
    if (hOut == INVALID_HANDLE_VALUE || hOut == NULL) {
        jintArray fail = env->NewIntArray(2);
        jint z[2] = {0, 0};
        env->SetIntArrayRegion(fail, 0, 2, z);
        return fail;
    }

    CONSOLE_SCREEN_BUFFER_INFO csbi;
    if (!GetConsoleScreenBufferInfo(hOut, &csbi)) {
        jintArray fail = env->NewIntArray(2);
        jint z[2] = {0, 0};
        env->SetIntArrayRegion(fail, 0, 2, z);
        return fail;
    }

    // Visible window region only — same as what getTerminalSize() returns
    int cols = csbi.srWindow.Right  - csbi.srWindow.Left + 1;
    int rows = csbi.srWindow.Bottom - csbi.srWindow.Top  + 1;
    int total = cols * rows;

    CHAR_INFO* buf = (CHAR_INFO*)malloc(total * sizeof(CHAR_INFO));
    if (!buf) {
        jintArray fail = env->NewIntArray(2);
        jint z[2] = {0, 0};
        env->SetIntArrayRegion(fail, 0, 2, z);
        return fail;
    }

    COORD bufSize   = { (SHORT)cols, (SHORT)rows };
    COORD bufOrigin = { 0, 0 };
    SMALL_RECT readRegion = csbi.srWindow; // read exactly the visible window

    BOOL ok = ReadConsoleOutputW(hOut, buf, bufSize, bufOrigin, &readRegion);
    if (!ok) {
        free(buf);
        jintArray fail = env->NewIntArray(2);
        jint z[2] = {0, 0};
        env->SetIntArrayRegion(fail, 0, 2, z);
        return fail;
    }

    // Pack result: [cols, rows, cp, fg, bg, cp, fg, bg, ...]
    int resultLen = 2 + total * 3;
    jintArray result = env->NewIntArray(resultLen);
    if (!result) { free(buf); return NULL; }

    jint* data = (jint*)malloc(resultLen * sizeof(jint));
    if (!data) { free(buf); return NULL; }

    data[0] = (jint)cols;
    data[1] = (jint)rows;

    for (int i = 0; i < total; i++) {
        WCHAR wch   = buf[i].Char.UnicodeChar;
        WORD  attr  = buf[i].Attributes;

        int fgIdx = (attr)      & 0x0F;
        int bgIdx = (attr >> 4) & 0x0F;

        // Map to 24-bit RGB via palette
        COLORREF fgRgb = CONSOLE_PALETTE[fgIdx];
        COLORREF bgRgb = CONSOLE_PALETTE[bgIdx];

        // COLORREF is 0x00BBGGRR — convert to 0xRRGGBB
        int fg24 = (((fgRgb)       & 0xFF) << 16)  // R
                 | (((fgRgb >> 8)  & 0xFF) << 8)   // G
                 |  ((fgRgb >> 16) & 0xFF);         // B

        int bg24 = (((bgRgb)       & 0xFF) << 16)
                 | (((bgRgb >> 8)  & 0xFF) << 8)
                 |  ((bgRgb >> 16) & 0xFF);

        // Use space for null/control characters
        int cp = (wch >= 0x20) ? (int)wch : (int)' ';

        data[2 + i * 3 + 0] = cp;
        data[2 + i * 3 + 1] = fg24;
        data[2 + i * 3 + 2] = bg24;
    }

    env->SetIntArrayRegion(result, 0, resultLen, data);
    free(data);
    free(buf);
    return result;
}

/**
 * @brief Returns the current console cursor position as [col, row] (0-based).
 */
JNIEXPORT jintArray JNICALL Java_fastsoftware3d_terminal_FastTerminal_getCursorPosition(JNIEnv* env, jclass clazz) {
    jintArray result = env->NewIntArray(2);
    jint pos[2] = {0, 0};
    HANDLE hOut = GetStdHandle(STD_OUTPUT_HANDLE);
    if (hOut != INVALID_HANDLE_VALUE && hOut != NULL) {
        CONSOLE_SCREEN_BUFFER_INFO csbi;
        if (GetConsoleScreenBufferInfo(hOut, &csbi)) {
            // Return position relative to the visible window top-left
            pos[0] = csbi.dwCursorPosition.X - csbi.srWindow.Left;
            pos[1] = csbi.dwCursorPosition.Y - csbi.srWindow.Top;
        }
    }
    env->SetIntArrayRegion(result, 0, 2, pos);
    return result;
}
