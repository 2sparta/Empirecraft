// Portable Windows GUI entry point. No shell, installer, admin rights or downloads.
#ifndef UNICODE
#define UNICODE
#endif
#define _UNICODE
#include <windows.h>
#include <wchar.h>
int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR args, int show) {
    WCHAR folder[32768], python[32768], command[32768];
    DWORD n = GetModuleFileNameW(NULL, folder, 32768);
    if (n == 0 || n >= 32000) return 1;
    WCHAR *slash = wcsrchr(folder, L'\\');
    if (!slash) return 1;
    *slash = 0;
    if (swprintf(python, 32768, L"%ls\\python\\pythonw.exe", folder) < 0) return 1;
    if (GetFileAttributesW(python) == INVALID_FILE_ATTRIBUTES) {
        MessageBoxW(NULL, L"Розпакуй усю папку лаунчера. Поруч з Empirecraft.exe потрібні папки python та application.", L"Empirecraft", MB_OK | MB_ICONERROR);
        return 1;
    }
    if (swprintf(command, 32768, L"\"%ls\" -I \"%ls\\application\\run.py\"", python, folder) < 0) return 1;
    SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_SYSTEM32);
    STARTUPINFOW startup = {0};
    PROCESS_INFORMATION process = {0};
    startup.cb = sizeof(startup);
    if (!CreateProcessW(python, command, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, folder, &startup, &process)) {
        MessageBoxW(NULL, L"Не вдалося запустити лаунчер. Перевір повноту архіву та Windows 10/11 x64.", L"Empirecraft", MB_OK | MB_ICONERROR);
        return 1;
    }
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return 0;
}
