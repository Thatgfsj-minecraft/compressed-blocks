param([string]$Acts = "", [string]$Shot = "")

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W32B {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint cButtons, UIntPtr dwExtraInfo);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
"@

function Get-McHwnd {
  $proc = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*client-12111*' } | Select-Object -First 1
  if (-not $proc) { return [IntPtr]::Zero }
  return (Get-Process -Id $proc.ProcessId).MainWindowHandle
}

# Raise MC to topmost and wait until it really is the foreground window.
function Focus-McHard {
  $hwnd = Get-McHwnd
  if ($hwnd -eq [IntPtr]::Zero) { Write-Output "NOPROC"; exit 1 }
  [W32B]::ShowWindow($hwnd, 9) | Out-Null
  # HWND_TOPMOST = -1, SWP_NOMOVE|SWP_NOSIZE = 0x3, SWP_SHOWWINDOW = 0x40
  [W32B]::SetWindowPos($hwnd, [IntPtr](-1), 0, 0, 0, 0, 0x43) | Out-Null
  for ($i = 0; $i -lt 20; $i++) {
    [W32B]::SetForegroundWindow($hwnd) | Out-Null
    Start-Sleep -Milliseconds 150
    if ([W32B]::GetForegroundWindow() -eq $hwnd) { return $hwnd }
  }
  Write-Output "NOTFOREGROUND"
  exit 1
}

function Click-Client([IntPtr]$hwnd, [int]$cx, [int]$cy) {
  [W32B+RECT]$r = New-Object W32B+RECT
  [W32B]::GetClientRect($hwnd, [ref]$r) | Out-Null
  [W32B+POINT]$p = New-Object W32B+POINT
  $p.X = 0; $p.Y = 0
  [W32B]::ClientToScreen($hwnd, [ref]$p) | Out-Null
  $sx = $p.X + $cx; $sy = $p.Y + $cy
  [W32B]::SetCursorPos($sx, $sy) | Out-Null
  Start-Sleep -Milliseconds 120
  [W32B]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
  Start-Sleep -Milliseconds 60
  [W32B]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
  Start-Sleep -Milliseconds 250
}

function Shot-Client([IntPtr]$hwnd, [string]$path) {
  [W32B+RECT]$r = New-Object W32B+RECT
  [W32B]::GetClientRect($hwnd, [ref]$r) | Out-Null
  [W32B+POINT]$p = New-Object W32B+POINT
  $p.X = 0; $p.Y = 0
  [W32B]::ClientToScreen($hwnd, [ref]$p) | Out-Null
  $w = $r.Right; $h = $r.Bottom
  $bmp = New-Object System.Drawing.Bitmap($w, $h)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($p.X, $p.Y, 0, 0, (New-Object System.Drawing.Size($w, $h)))
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  Write-Output ("SHOT " + $path)
}

$hwnd = Focus-McHard
$sh = New-Object -ComObject WScript.Shell
foreach ($a in $Acts.Split(';')) {
  if ($a -eq "") { continue }
  if ($a.StartsWith("click:")) {
    # re-verify MC is foreground before every click
    if ([W32B]::GetForegroundWindow() -ne $hwnd) {
      $hwnd = Focus-McHard
    }
    $xy = $a.Substring(6).Split(',')
    Click-Client $hwnd ([int]$xy[0]) ([int]$xy[1])
    Write-Output ("CLICK " + $a)
  } elseif ($a.StartsWith("type:")) {
    $sh.SendKeys($a.Substring(5))
    Start-Sleep -Milliseconds 350
    Write-Output ("TYPE " + $a)
  } elseif ($a.StartsWith("wait:")) {
    Start-Sleep -Milliseconds ([int]$a.Substring(5))
  } elseif ($a -eq "esc") {
    $sh.SendKeys("{ESC}")
    Start-Sleep -Milliseconds 400
    Write-Output "ESC"
  } elseif ($a -eq "enter") {
    $sh.SendKeys("{ENTER}")
    Start-Sleep -Milliseconds 400
    Write-Output "ENTER"
  } elseif ($a -eq "untop") {
    [W32B]::SetWindowPos($hwnd, [IntPtr](-2), 0, 0, 0, 0, 0x43) | Out-Null
    Write-Output "UNTOP"
  }
}
if ($Shot -ne "") {
  if ([W32B]::GetForegroundWindow() -ne $hwnd) { $hwnd = Focus-McHard }
  Shot-Client $hwnd $Shot
}
# leave window non-topmost so the desktop user keeps control
$hwnd2 = Get-McHwnd
if ($hwnd2 -ne [IntPtr]::Zero) { [W32B]::SetWindowPos($hwnd2, [IntPtr](-2), 0, 0, 0, 0, 0x43) | Out-Null }
