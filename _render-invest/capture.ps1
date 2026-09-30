param([string]$OutPath)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32 {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern IntPtr FindWindowW(string cls, string title);
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
"@

$proc = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*client-12111*' } | Select-Object -First 1
if (-not $proc) { Write-Output "NOPROC"; exit 1 }
$ps = Get-Process -Id $proc.ProcessId
$hwnd = $ps.MainWindowHandle
if ($hwnd -eq [IntPtr]::Zero) { Write-Output "NOWINDOW"; exit 1 }

[Win32+RECT]$r = New-Object Win32+RECT
[Win32+POINT]$p = New-Object Win32+POINT
[Win32]::GetWindowRect($hwnd, [ref]$r) | Out-Null
if ($r.Right - $r.Left -le 0) { Write-Output "NOWINDOWRECT"; exit 1 }

[Win32]::ShowWindow($hwnd, 9) | Out-Null   # SW_RESTORE
[Win32]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep -Milliseconds 700

[Win32]::GetClientRect($hwnd, [ref]$r) | Out-Null
$p.X = 0; $p.Y = 0
[Win32]::ClientToScreen($hwnd, [ref]$p) | Out-Null
$w = $r.Right; $h = $r.Bottom
if ($w -le 0 -or $h -le 0) { Write-Output "NOCLIENTRECT"; exit 1 }

$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($p.X, $p.Y, 0, 0, (New-Object System.Drawing.Size($w, $h)))
$g.Dispose()
$bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output ("SAVED " + $OutPath + " " + $w + "x" + $h)
