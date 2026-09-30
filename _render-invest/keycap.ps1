param([string]$OutPath, [string]$Keys = "ESC", [int]$DelayMs = 900)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W32 {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint cButtons, UIntPtr dwExtraInfo);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
"@

$proc = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*client-12111*' } | Select-Object -First 1
if (-not $proc) { Write-Output "NOPROC"; exit 1 }
$ps = Get-Process -Id $proc.ProcessId
$hwnd = $ps.MainWindowHandle

[W32+RECT]$r = New-Object W32+RECT
[W32]::ShowWindow($hwnd, 9) | Out-Null
[W32]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep -Milliseconds 500

$sh = New-Object -ComObject WScript.Shell
foreach ($k in $Keys.Split(',')) {
  switch ($k) {
    "ESC"   { $sh.SendKeys("{ESC}") }
    "F3B"   { $sh.SendKeys("%{ESC}"); }
    default { $sh.SendKeys($k) }
  }
  Start-Sleep -Milliseconds 350
}
Start-Sleep -Milliseconds $DelayMs

[W32]::GetClientRect($hwnd, [ref]$r) | Out-Null
[W32+POINT]$p = New-Object W32+POINT
$p.X = 0; $p.Y = 0
[W32]::ClientToScreen($hwnd, [ref]$p) | Out-Null
$w = $r.Right; $h = $r.Bottom

$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($p.X, $p.Y, 0, 0, (New-Object System.Drawing.Size($w, $h)))
$g.Dispose()
$bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output ("SAVED " + $OutPath + " " + $w + "x" + $h)
