package tv.cleanyour.core.backup

import tv.cleanyour.core.model.TvId

// Who a laptop restore file is for. tvName and model are free text (the nickname typed on the
// phone, the model string the TV reports) and are cleaned before they reach any file.
data class ScriptHeader(val tvId: TvId, val tvName: String, val model: String, val madeOn: String, val appVersion: String)

// The three laptop files of the restore zip, built from RestoreCommands steps:
//
// - restore.sh: POSIX sh (dash, busybox ash, bash, zsh in sh mode) for a computer with adb. It
//   checks the TV before the first command: TvId = first 16 hex of sha256(ro.serialno +
//   ro.product.device), the same formula as the app (D37), so the file names the TV without
//   holding its serial number. Another TV, or a computer with no sha256 tool, stops the script
//   unless --allow-other-tv is passed. Every argument it passes on is quoted; each TV line is the
//   renderer's own line, given to `adb shell` as one argument.
// - restore.ps1: the same for Windows PowerShell 5.1 and PowerShell 7.
// - commands.txt: the bare lines, for upstream tools/tv.py --batch.
//
// Uninstall steps run only with --remove-apps (-RemoveApps), matching the app's own default of
// keeping its home screen installed as an ordinary app (UX 4.13).
object RestoreScripts {

    fun sh(h: ScriptHeader, steps: List<RestoreStep>): String = buildString {
        val name = TextSafety.ascii(h.tvName, 40)
        val model = TextSafety.ascii(h.model, 40)
        appendLine("#!/bin/sh")
        appendLine("# Clean Your TV: put back what we changed on $name ($model).")
        appendLine("# Made on ${TextSafety.ascii(h.madeOn, 20)} by Clean Your TV ${TextSafety.ascii(h.appVersion, 20)} from the restore point on the phone.")
        appendLine("#")
        appendLine("# Run it on a computer that has adb (Android platform tools), with debugging on the TV:")
        appendLine("#   sh restore.sh --host 192.168.1.50   connect to the TV over the network first")
        appendLine("#   sh restore.sh -s SERIAL             use a device that adb already lists (adb devices)")
        appendLine("#   sh restore.sh                       use the only device adb knows")
        appendLine("# Options:")
        appendLine("#   --dry-run          print the TV commands; do not talk to the TV")
        appendLine("#   --remove-apps      also remove the apps Clean Your TV installed (its home screen and cards)")
        appendLine("#   --allow-other-tv   run even when the TV is not the one this script was made for")
        appendLine("#")
        appendLine("# Before changing anything the script checks that it talks to the right TV. It compares a")
        appendLine("# fingerprint of the TV's serial number and device name with TV_ID below; the serial number")
        appendLine("# itself is not in this file. Each thing goes back to how it was before Clean Your TV first")
        appendLine("# changed it, in this order: apps switched on or off, settings, the home screen, the apps it")
        appendLine("# installed, its files. A step that fails is reported and the rest still run.")
        appendLine()
        appendLine("set -u")
        appendLine()
        appendLine("TV_ID=${TextSafety.shq(h.tvId.value)}")
        appendLine("TV_NAME=${TextSafety.shq(name)}")
        appendLine("TV_MODEL=${TextSafety.shq(model)}")
        appendLine()
        append(SH_BODY)
        appendLine()
        appendLine("say 'Putting back what Clean Your TV changed on %s.' \"\$TV_NAME\"")
        appendLine()
        for (s in steps) {
            when (s.kind) {
                RestoreStepKind.PUT_BACK -> s.lines.forEach { appendLine("step ${TextSafety.shWord(s.description)} ${TextSafety.shWord(it)}") }
                RestoreStepKind.REMOVE_APP -> s.lines.forEach { appendLine("app_step ${TextSafety.shWord(s.description)} ${TextSafety.shWord(it)}") }
                RestoreStepKind.NOT_FROM_LAPTOP -> appendLine("not_here ${TextSafety.shWord(s.description)} ${TextSafety.shWord(TextSafety.ascii(s.note ?: "", 200))}")
            }
        }
        appendLine()
        append(SH_FOOTER)
    }

    fun ps1(h: ScriptHeader, steps: List<RestoreStep>): String = buildString {
        val name = TextSafety.ascii(h.tvName, 40)
        val model = TextSafety.ascii(h.model, 40)
        appendLine("# Clean Your TV: put back what we changed on $name ($model).")
        appendLine("# Made on ${TextSafety.ascii(h.madeOn, 20)} by Clean Your TV ${TextSafety.ascii(h.appVersion, 20)} from the restore point on the phone.")
        appendLine("#")
        appendLine("# Run it on a computer that has adb (Android platform tools), with debugging on the TV:")
        appendLine("#   powershell -ExecutionPolicy Bypass -File restore.ps1 -TvHost 192.168.1.50")
        appendLine("#   powershell -ExecutionPolicy Bypass -File restore.ps1 -Serial SERIAL")
        appendLine("#   powershell -ExecutionPolicy Bypass -File restore.ps1")
        appendLine("# Options:")
        appendLine("#   -DryRun         print the TV commands; do not talk to the TV")
        appendLine("#   -RemoveApps     also remove the apps Clean Your TV installed (its home screen and cards)")
        appendLine("#   -AllowOtherTv   run even when the TV is not the one this script was made for")
        appendLine("#")
        appendLine("# Before changing anything the script checks that it talks to the right TV. It compares a")
        appendLine("# fingerprint of the TV's serial number and device name with TvId below; the serial number")
        appendLine("# itself is not in this file. Each thing goes back to how it was before Clean Your TV first")
        appendLine("# changed it, in this order: apps switched on or off, settings, the home screen, the apps it")
        appendLine("# installed, its files. A step that fails is reported and the rest still run.")
        appendLine("param(")
        appendLine("    [string]\$Serial = '',")
        appendLine("    [string]\$TvHost = '',")
        appendLine("    [switch]\$DryRun,")
        appendLine("    [switch]\$RemoveApps,")
        appendLine("    [switch]\$AllowOtherTv")
        appendLine(")")
        appendLine()
        appendLine("\$TvId = ${TextSafety.psq(h.tvId.value)}")
        appendLine("\$TvName = ${TextSafety.psq(name)}")
        appendLine("\$TvModel = ${TextSafety.psq(model)}")
        appendLine()
        append(PS_BODY)
        appendLine()
        appendLine("Say (\"Putting back what Clean Your TV changed on {0}.\" -f \$TvName)")
        appendLine()
        for (s in steps) {
            when (s.kind) {
                RestoreStepKind.PUT_BACK -> s.lines.forEach { appendLine("Step ${TextSafety.psq(s.description)} ${TextSafety.psq(it)}") }
                RestoreStepKind.REMOVE_APP -> s.lines.forEach { appendLine("AppStep ${TextSafety.psq(s.description)} ${TextSafety.psq(it)}") }
                RestoreStepKind.NOT_FROM_LAPTOP -> appendLine("NotHere ${TextSafety.psq(s.description)} ${TextSafety.psq(TextSafety.ascii(s.note ?: "", 200))}")
            }
        }
        appendLine()
        append(PS_FOOTER)
    }

    fun commandsTxt(h: ScriptHeader, steps: List<RestoreStep>): String = buildString {
        appendLine("# Clean Your TV: the TV commands that put back what we changed on ${TextSafety.ascii(h.tvName, 40)} (${TextSafety.ascii(h.model, 40)}).")
        appendLine("# Made on ${TextSafety.ascii(h.madeOn, 20)} by Clean Your TV ${TextSafety.ascii(h.appVersion, 20)}.")
        appendLine("# One command per line, for upstream tools/tv.py batch mode:")
        appendLine("#   TV_HOST=<tv ip> tools/venv/bin/python tools/tv.py --batch < commands.txt")
        appendLine("# tv.py does not check which TV it talks to; restore.sh and restore.ps1 do.")
        appendLine("# A line starting with \"# remove-app: \" uninstalls an app Clean Your TV installed. Delete that")
        appendLine("# prefix to run it too.")
        for (s in steps) {
            when (s.kind) {
                RestoreStepKind.PUT_BACK -> s.lines.forEach { appendLine(it) }
                RestoreStepKind.REMOVE_APP -> s.lines.forEach { appendLine("# remove-app: $it") }
                RestoreStepKind.NOT_FROM_LAPTOP -> appendLine("# not from a computer: ${s.description}: ${TextSafety.ascii(s.note ?: "", 200)}")
            }
        }
    }

    // Everything between the TV constants and the steps. Plain text, no Kotlin templates.
    private val SH_BODY = """
ADB="${'$'}{ADB:-adb}"
SERIAL=''
HOST=''
DRY_RUN=0
REMOVE_APPS=0
ALLOW_OTHER_TV=0
DONE=0
FAILED=0
KEPT=0
SKIPPED=0

usage() {
  printf '%s\n' 'Usage: sh restore.sh [--host IP[:PORT] | -s SERIAL] [--dry-run] [--remove-apps] [--allow-other-tv]'
}

while [ "${'$'}#" -gt 0 ]; do
  case "${'$'}1" in
    -s|--serial)
      [ "${'$'}#" -ge 2 ] || { printf 'restore.sh: %s needs a value\n' "${'$'}1" >&2; exit 2; }
      SERIAL="${'$'}2"; shift 2 ;;
    --host)
      [ "${'$'}#" -ge 2 ] || { printf 'restore.sh: %s needs a value\n' "${'$'}1" >&2; exit 2; }
      HOST="${'$'}2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --remove-apps) REMOVE_APPS=1; shift ;;
    --allow-other-tv) ALLOW_OTHER_TV=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'restore.sh: unknown option: %s\n' "${'$'}1" >&2; usage >&2; exit 2 ;;
  esac
done

# say FORMAT [ARG...]: progress for the person at the computer (stderr in a dry run, so that the
# dry run output is only TV commands and comments)
say() {
  fmt="${'$'}1"; shift
  if [ "${'$'}DRY_RUN" = 1 ]; then printf "# ${'$'}fmt\n" "${'$'}@" >&2; else printf "${'$'}fmt\n" "${'$'}@"; fi
}

tv_adb() {
  if [ -n "${'$'}SERIAL" ]; then "${'$'}ADB" -s "${'$'}SERIAL" "${'$'}@"; else "${'$'}ADB" "${'$'}@"; fi
}

tv_prop() {
  tv_adb shell "getprop '${'$'}1'" 2>/dev/null | tr -d '\r'
}

sha256_hex() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256
  elif command -v openssl >/dev/null 2>&1; then openssl dgst -sha256 -r
  else cat >/dev/null; return 1
  fi
}

check_tv() {
  if ! command -v "${'$'}ADB" >/dev/null 2>&1; then
    printf '%s\n' 'adb was not found. Install the Android platform tools, or set ADB to the path of adb.' >&2
    exit 2
  fi
  if [ -n "${'$'}HOST" ]; then
    case "${'$'}HOST" in *:*) ;; *) HOST="${'$'}HOST:5555" ;; esac
    out=${'$'}("${'$'}ADB" connect "${'$'}HOST" 2>&1)
    case "${'$'}out" in
      *"connected to"*) ;;
      *) printf 'Could not connect to %s: %s\n' "${'$'}HOST" "${'$'}out" >&2; exit 2 ;;
    esac
    SERIAL="${'$'}HOST"
  fi
  state=${'$'}(tv_adb get-state 2>&1)
  if [ "${'$'}state" != device ]; then
    printf 'No TV is ready over adb (%s). Is debugging on, and did you allow this computer on the TV?\n' "${'$'}state" >&2
    exit 2
  fi
  found_serial=${'$'}(tv_prop ro.serialno)
  found_device=${'$'}(tv_prop ro.product.device)
  found_model=${'$'}(tv_prop ro.product.model)
  found_id=${'$'}(printf '%s%s' "${'$'}found_serial" "${'$'}found_device" | sha256_hex | cut -c1-16)
  if [ "${'$'}found_id" = "${'$'}TV_ID" ]; then
    return 0
  fi
  if [ -z "${'$'}found_id" ]; then
    why='this computer has no sha256sum, shasum or openssl to check it with'
  else
    why="the TV that answered is a ${'$'}found_model with another serial number"
  fi
  if [ "${'$'}ALLOW_OTHER_TV" = 1 ]; then
    printf 'Careful: this may not be %s (%s). Carrying on because of --allow-other-tv.\n' "${'$'}TV_NAME" "${'$'}why" >&2
    return 0
  fi
  printf 'Stopped: this script was made for %s (%s), and %s.\n' "${'$'}TV_NAME" "${'$'}TV_MODEL" "${'$'}why" >&2
  printf '%s\n' 'Nothing was changed. If you are sure it is the right TV, run it again with --allow-other-tv.' >&2
  exit 3
}

# step DESCRIPTION LINE: run one TV command
step() {
  if [ "${'$'}DRY_RUN" = 1 ]; then printf '%s\n' "${'$'}2"; return 0; fi
  if out=${'$'}(tv_adb shell "${'$'}2" 2>&1); then
    case "${'$'}out" in
      *Error:*|*Failure*|*Exception*) ok=0 ;;
      *) ok=1 ;;
    esac
  else
    ok=0
  fi
  if [ "${'$'}ok" = 1 ]; then
    DONE=${'$'}((DONE + 1))
    printf 'ok       %s\n' "${'$'}1"
  else
    FAILED=${'$'}((FAILED + 1))
    printf 'FAILED   %s\n         %s\n' "${'$'}1" "${'$'}2"
    [ -z "${'$'}out" ] || printf '         %s\n' "${'$'}out"
  fi
}

# app_step DESCRIPTION LINE: uninstall an app we installed, only with --remove-apps
app_step() {
  if [ "${'$'}REMOVE_APPS" = 1 ]; then step "${'$'}1" "${'$'}2"; return 0; fi
  KEPT=${'$'}((KEPT + 1))
  if [ "${'$'}DRY_RUN" = 1 ]; then printf '# kept (needs --remove-apps): %s\n' "${'$'}2"; return 0; fi
  printf 'kept     %s (add --remove-apps to remove it too)\n' "${'$'}1"
}

# not_here DESCRIPTION WHY: a change that cannot be put back from a computer
not_here() {
  SKIPPED=${'$'}((SKIPPED + 1))
  if [ "${'$'}DRY_RUN" = 1 ]; then printf '# not from a computer: %s: %s\n' "${'$'}1" "${'$'}2"; return 0; fi
  printf 'skipped  %s: %s\n' "${'$'}1" "${'$'}2"
}

[ "${'$'}DRY_RUN" = 1 ] || check_tv
""".trimStart('\n')

    private val SH_FOOTER = """
if [ "${'$'}DRY_RUN" = 1 ]; then exit 0; fi
printf '\nDone: %s put back, %s failed, %s kept, %s not possible from here.\n' "${'$'}DONE" "${'$'}FAILED" "${'$'}KEPT" "${'$'}SKIPPED"
if [ "${'$'}FAILED" -gt 0 ]; then
  printf '%s\n' 'Some steps failed. The lines above show the command and what the TV said.'
  exit 1
fi
printf '%s\n' 'If the home screen has not changed yet, press Home on the remote or restart the TV.'
exit 0
""".trimStart('\n')

    private val PS_BODY = """
${'$'}ErrorActionPreference = 'Continue'
${'$'}Adb = if (${'$'}env:ADB) { ${'$'}env:ADB } else { 'adb' }
${'$'}script:Done = 0
${'$'}script:Failed = 0
${'$'}script:Kept = 0
${'$'}script:Skipped = 0

function Say([string]${'$'}Text) {
    if (${'$'}DryRun) { [Console]::Error.WriteLine('# ' + ${'$'}Text) } else { Write-Output ${'$'}Text }
}

function Invoke-TvAdb([string[]]${'$'}AdbArgs) {
    if (${'$'}Serial) { ${'$'}AdbArgs = @('-s', ${'$'}Serial) + ${'$'}AdbArgs }
    # each stderr line as its plain text: Windows PowerShell 5.1 would otherwise wrap it in an
    # error record whose text says NativeCommandError and RemoteException
    ${'$'}out = (& ${'$'}Adb @AdbArgs 2>&1 | ForEach-Object { "${'$'}_" } | Out-String)
    return @{ Code = ${'$'}LASTEXITCODE; Out = ${'$'}out.Trim() }
}

function Get-TvProp([string]${'$'}Name) {
    return (Invoke-TvAdb @('shell', "getprop '${'$'}Name'")).Out.Trim()
}

function Test-Tv {
    if (-not (Get-Command ${'$'}Adb -ErrorAction SilentlyContinue)) {
        [Console]::Error.WriteLine('adb was not found. Install the Android platform tools, or set ADB to the path of adb.')
        exit 2
    }
    if (${'$'}TvHost) {
        if (${'$'}TvHost -notmatch ':') { ${'$'}script:TvHost = ${'$'}TvHost + ':5555' }
        ${'$'}c = (& ${'$'}Adb connect ${'$'}script:TvHost 2>&1 | Out-String)
        if (${'$'}c -notmatch 'connected to') {
            [Console]::Error.WriteLine(('Could not connect to {0}: {1}' -f ${'$'}script:TvHost, ${'$'}c.Trim()))
            exit 2
        }
        ${'$'}script:Serial = ${'$'}script:TvHost
    }
    ${'$'}state = (Invoke-TvAdb @('get-state')).Out
    if (${'$'}state -ne 'device') {
        [Console]::Error.WriteLine(('No TV is ready over adb ({0}). Is debugging on, and did you allow this computer on the TV?' -f ${'$'}state))
        exit 2
    }
    ${'$'}foundSerial = Get-TvProp 'ro.serialno'
    ${'$'}foundDevice = Get-TvProp 'ro.product.device'
    ${'$'}foundModel = Get-TvProp 'ro.product.model'
    ${'$'}sha = [System.Security.Cryptography.SHA256]::Create()
    ${'$'}digest = ${'$'}sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes(${'$'}foundSerial + ${'$'}foundDevice))
    ${'$'}foundId = (-join (${'$'}digest | ForEach-Object { ${'$'}_.ToString('x2') })).Substring(0, 16)
    if (${'$'}foundId -eq ${'$'}TvId) { return }
    ${'$'}why = ('the TV that answered is a {0} with another serial number' -f ${'$'}foundModel)
    if (${'$'}AllowOtherTv) {
        [Console]::Error.WriteLine(('Careful: this may not be {0} ({1}). Carrying on because of -AllowOtherTv.' -f ${'$'}TvName, ${'$'}why))
        return
    }
    [Console]::Error.WriteLine(('Stopped: this script was made for {0} ({1}), and {2}.' -f ${'$'}TvName, ${'$'}TvModel, ${'$'}why))
    [Console]::Error.WriteLine('Nothing was changed. If you are sure it is the right TV, run it again with -AllowOtherTv.')
    exit 3
}

# Step DESCRIPTION LINE: run one TV command
function Step([string]${'$'}What, [string]${'$'}Line) {
    if (${'$'}DryRun) { Write-Output ${'$'}Line; return }
    ${'$'}r = Invoke-TvAdb @('shell', ${'$'}Line)
    if (${'$'}r.Code -eq 0 -and ${'$'}r.Out -cnotmatch 'Error:|Failure|Exception') {
        ${'$'}script:Done++
        Write-Output ('ok       ' + ${'$'}What)
    } else {
        ${'$'}script:Failed++
        Write-Output ('FAILED   ' + ${'$'}What)
        Write-Output ('         ' + ${'$'}Line)
        if (${'$'}r.Out) { Write-Output ('         ' + ${'$'}r.Out) }
    }
}

# AppStep DESCRIPTION LINE: uninstall an app we installed, only with -RemoveApps
function AppStep([string]${'$'}What, [string]${'$'}Line) {
    if (${'$'}RemoveApps) { Step ${'$'}What ${'$'}Line; return }
    ${'$'}script:Kept++
    if (${'$'}DryRun) { Write-Output ('# kept (needs -RemoveApps): ' + ${'$'}Line); return }
    Write-Output ('kept     ' + ${'$'}What + ' (add -RemoveApps to remove it too)')
}

# NotHere DESCRIPTION WHY: a change that cannot be put back from a computer
function NotHere([string]${'$'}What, [string]${'$'}Why) {
    ${'$'}script:Skipped++
    if (${'$'}DryRun) { Write-Output ('# not from a computer: ' + ${'$'}What + ': ' + ${'$'}Why); return }
    Write-Output ('skipped  ' + ${'$'}What + ': ' + ${'$'}Why)
}

if (-not ${'$'}DryRun) { Test-Tv }
""".trimStart('\n')

    private val PS_FOOTER = """
if (${'$'}DryRun) { exit 0 }
Write-Output ''
Write-Output ('Done: {0} put back, {1} failed, {2} kept, {3} not possible from here.' -f ${'$'}script:Done, ${'$'}script:Failed, ${'$'}script:Kept, ${'$'}script:Skipped)
if (${'$'}script:Failed -gt 0) {
    Write-Output 'Some steps failed. The lines above show the command and what the TV said.'
    exit 1
}
Write-Output 'If the home screen has not changed yet, press Home on the remote or restart the TV.'
exit 0
""".trimStart('\n')
}
