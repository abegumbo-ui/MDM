# Builds FamilyMdmSetup.exe with the C# compiler that ships with Windows (no Visual Studio, no downloads).
#   powershell -ExecutionPolicy Bypass -File windows\FamilyMdmSetup\build.ps1
param([string]$Out = "")
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Out) { $Out = Join-Path $here 'FamilyMdmSetup.exe' }
$csc = Get-ChildItem 'C:\Windows\Microsoft.NET\Framework64\v4*\csc.exe' | Select-Object -First 1
if (-not $csc) { throw 'The .NET Framework C# compiler (csc.exe) was not found.' }
& $csc.FullName /nologo /target:winexe /optimize+ /warn:4 /platform:anycpu "/out:$Out" `
    /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll `
    (Join-Path $here 'FamilyMdmSetup.cs')
if ($LASTEXITCODE -ne 0) { throw "csc failed ($LASTEXITCODE)" }
"Built $Out ({0:N0} KB)" -f ((Get-Item $Out).Length / 1KB)
