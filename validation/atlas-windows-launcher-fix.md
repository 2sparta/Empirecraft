# Atlas Windows launcher fix — 2026-10-24

The 1.0.0 launcher invoked `powershell.exe` by bare name, so installation stopped before the PowerShell script when Windows could not resolve that name through PATH.

The 1.0.1 launcher checks full Windows PowerShell paths (Sysnative and System32), standard PowerShell 7 locations and then PATH. It does not change PATH or rerun a failing installer through a second engine. It also preserves the PowerShell script's exit code, rejects an empty instance path and explains when the archive has not been extracted completely or no engine is installed.

The full ZIP has been rebuilt as `EmpireCraft-Medieval-Atlas-1.0.1.zip`. Its internal icon resource pack stays at 1.0.0: the artwork and PowerShell installation logic are unchanged.

Validation: Python sources compile; the six existing package/installer integration tests pass using PowerShell 7 on Linux. Two native Windows regression tests have been added for an empty PATH and propagation of a failing script's exit code. They are skipped on Linux and still require a Windows run; cmd.exe itself was not executed here. The packaged CMD is ASCII with CRLF line endings and matches the standalone replacement file byte-for-byte. The ZIP checksum is provided alongside the release.
