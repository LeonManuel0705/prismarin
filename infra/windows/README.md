# Windows Host Operations

The live network runs on a Windows machine, not on Docker. This documents how the servers are started, kept alive and updated today.

## Directory layout

Every server lives in its own directory under `C:\mcnetwork`:

```
C:\mcnetwork\
  velocity\        proxy (velocity.jar)
  lobby-1\         Paper backend (paper.jar)
  ...\             further Paper backends
```

Plugin jars are deployed to `C:\mcnetwork\<server>\plugins`. After a `gradlew build`, copy `core-velocity` into `velocity\plugins` and `core-paper` plus `lobby-paper` into each backend's `plugins` folder, then restart the affected server.

## WMI-detached start

Servers are not started as child processes of a console window. Instead each java process is created through WMI (`Win32_Process.Create`), which makes the WMI provider host the parent process. The effect: the server process is fully detached from whatever console launched it, so closing the terminal, logging out of the RDP session or the batch script exiting does not kill the servers.

The PowerShell call used per server looks like this:

```powershell
Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
  CommandLine      = 'java -Xms512M -Xmx2G -jar paper.jar nogui'
  CurrentDirectory = 'C:\mcnetwork\lobby-1'
}
```

`CurrentDirectory` matters: the server resolves `plugins\`, `config.yml` and its world folders relative to it. A successful call returns `ReturnValue = 0` and the `ProcessId` of the new java process.

## start-all.bat

`C:\mcnetwork\start-all.bat` boots the whole network. It iterates over the server directories and fires the WMI start for each one, proxy last so the backends are already registering when Velocity comes up:

```bat
@echo off
for %%S in (lobby-1) do (
  powershell -NoProfile -Command "Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = 'java -Xms512M -Xmx2G -jar paper.jar nogui'; CurrentDirectory = 'C:\mcnetwork\%%S' }"
)
timeout /t 15 /nobreak >nul
powershell -NoProfile -Command "Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = 'java -Xms512M -Xmx1G -jar velocity.jar'; CurrentDirectory = 'C:\mcnetwork\velocity' }"
```

Because the processes are detached, the batch window can be closed immediately after it finishes.

## Stopping and inspecting

Detached servers have no attached console, so stop them via in-game `/stop` (Paper) and `/velocity shutdown` or `end` through the proxy, or find the processes with:

```powershell
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
  Select-Object ProcessId, CommandLine
```

Kill a hung server only as a last resort with `Stop-Process -Id <pid>`, since that skips world saving.

## Relation to the Docker stack

The Windows host currently runs everything with `storage.mode: LOCAL` and Redis disabled. Once the network moves to shared storage, the Docker stack in `infra/` provides MongoDB and Redis; the servers on this host then only need the `config.yml` change described in `infra/README.md`.
