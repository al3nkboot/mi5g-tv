package tv.cleanyour.core.probe

import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.PackageName

// Output parsers (section 5.5): objects with parse(lines), a per-line cap of 8 KiB, and
// ProbeException as the only error. They read what a TV prints, which is untrusted: nothing here
// evaluates, every value is matched against a grammar, and anything that does not match is
// skipped or refused.
const val LINE_CAP = 8 * 1024

internal fun capped(stage: ProbeStage, lines: Sequence<String>): Sequence<String> = lines.mapIndexed { i, l ->
    if (l.length > LINE_CAP) throw ProbeException(stage, "line ${i + 1} is ${l.length} chars, over the $LINE_CAP cap")
    l.removeSuffix("\r")
}

// Lines over the cap are dropped instead (dumps where a long value is never one we read).
internal fun skipLong(lines: Sequence<String>): Sequence<String> = lines.filter { it.length <= LINE_CAP }.map { it.removeSuffix("\r") }

internal fun component(flat: String): Component? {
    val i = flat.indexOf('/')
    if (i <= 0) return null
    return runCatching { Component(PackageName(flat.substring(0, i)), flat.substring(i + 1)) }.getOrNull()
}

internal fun packageName(s: String): PackageName? = runCatching { PackageName(s) }.getOrNull()

// getprop: "[key]: [value]" lines.
object GetpropParser {
    private val LINE = Regex("^\\[([A-Za-z0-9_.:-]{1,256})\\]: \\[(.*)\\]$")
    fun parse(lines: Sequence<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (l in capped(ProbeStage.FACTS, lines)) LINE.matchEntire(l)?.let { out[it.groupValues[1]] = it.groupValues[2] }
        return out
    }
}

// pm list packages, with or without -f: "package:<path>=<name>" or "package:<name>". Names are
// kept as text in file order; the caller decides what a valid PackageName is (RFC-W1-2).
object PackagesParser {
    data class Listed(val name: String, val path: String?)
    private val NAME = Regex("^[A-Za-z][A-Za-z0-9_.]{0,254}$")
    fun parse(lines: Sequence<String>): List<Listed> {
        val out = ArrayList<Listed>()
        val seen = HashSet<String>()
        for (l in capped(ProbeStage.PACKAGES, lines)) {
            val t = l.trim()
            if (t.isEmpty()) continue
            if (!t.startsWith("package:")) throw ProbeException(ProbeStage.PACKAGES, "unexpected line: ${t.take(80)}")
            val body = t.removePrefix("package:")
            val eq = body.lastIndexOf('=')
            val (path, name) = if (eq >= 0 && body.startsWith("/")) body.substring(0, eq) to body.substring(eq + 1) else null to body
            if (!NAME.matches(name)) throw ProbeException(ProbeStage.PACKAGES, "not a package name: ${name.take(80)}")
            if (seen.add(name)) out += Listed(name, path)
        }
        return out
    }
}

// dumpsys package (whole, `packages` section only, or the probe's grep of it): per package the
// code path, versionCode, pkgFlags and user 0's installed and enabled state. Parsing stops at the
// "Hidden system packages:" section (the factory copies of updated system apps).
object DumpsysPackageParser {
    data class Info(val name: String, val codePath: String?, val versionCode: Long?, val flags: Set<String>, val installed: Boolean?, val enabled: Int?)
    private val HEAD = Regex("^ +Package \\[([A-Za-z][A-Za-z0-9_.]{0,254})\\] \\([0-9a-f]+\\):$")
    private val CODE = Regex("^ +codePath=(\\S+)$")
    private val VERSION = Regex("^ +versionCode=(-?\\d{1,19})\\b")
    private val FLAGS = Regex("^ +pkgFlags=\\[ ?([A-Z_ ]*?) ?\\]$")
    private val USER0 = Regex("^ +User 0: .*")
    private val INSTALLED = Regex("\\binstalled=(true|false)\\b")
    private val ENABLED = Regex("\\benabled=(\\d)\\b")

    fun parse(lines: Sequence<String>): Map<String, Info> {
        val out = LinkedHashMap<String, Info>()
        var cur: Info? = null
        fun flush() { cur?.let { if (it.name !in out) out[it.name] = it }; cur = null }
        for (l in skipLong(lines)) {
            if (l.startsWith("Hidden system packages:")) break
            val head = HEAD.matchEntire(l)
            if (head != null) { flush(); cur = Info(head.groupValues[1], null, null, emptySet(), null, null); continue }
            val c = cur ?: continue
            val code = CODE.matchEntire(l)
            val version = VERSION.find(l)
            val flags = FLAGS.matchEntire(l)
            cur = when {
                code != null -> c.copy(codePath = code.groupValues[1])
                version != null -> c.copy(versionCode = version.groupValues[1].toLongOrNull())
                flags != null -> c.copy(flags = flags.groupValues[1].split(' ').filter(String::isNotEmpty).toSet())
                USER0.matches(l) && c.enabled == null && c.installed == null ->
                    c.copy(installed = INSTALLED.find(l)?.groupValues?.get(1)?.toBooleanStrict(), enabled = ENABLED.find(l)?.groupValues?.get(1)?.toInt())
                else -> c
            }
        }
        flush()
        return out
    }
}

// cmd package query-activities / resolve-activity with the brief flag: a "priority=N ..." line
// followed by the component line. resolve gives one pair or "No activity found"; an ambiguous
// HOME resolves to the system chooser (android/...ResolverActivity).
object HomeParser {
    private val PRIORITY = Regex("^priority=(-?\\d{1,9}) ")

    fun parse(lines: Sequence<String>): List<HomeClaimant> {
        val out = ArrayList<HomeClaimant>()
        var priority: Int? = null
        for (raw in capped(ProbeStage.HOME, lines)) {
            val l = raw.trim()
            val pm = PRIORITY.find(l)
            if (pm != null) { priority = pm.groupValues[1].toInt(); continue }
            val p = priority ?: continue
            if ('/' in l && ' ' !in l) {
                component(l)?.let { c -> if (out.none { it.component == c }) out += HomeClaimant(c, p) }
                priority = null
            }
        }
        return out
    }

    // The last component line of a resolve-activity answer, or null ("No activity found").
    fun parseResolved(lines: Sequence<String>): Component? =
        capped(ProbeStage.HOME, lines).map { it.trim() }.filter { '/' in it && ' ' !in it && !it.startsWith("priority=") }.lastOrNull()?.let(::component)
}

// dumpsys hdmi_control: CEC devices (HdmiDeviceInfo lines) and the HDMI port list, in the
// Android 9 to 11 and the Android 12+ shapes. Ported from the spike's CecParser (S7).
object HdmiControlParser {
    data class Device(val name: String, val logicalAddress: Int, val deviceType: Int, val physicalAddress: Int, val port: Int) {
        val isTvItself: Boolean get() = physicalAddress == 0 && logicalAddress == 0
        val behindSwitch: Boolean get() = (physicalAddress and 0x0FFF) != 0
    }
    data class Port(val id: Int, val physicalAddress: Int, val cec: Boolean?, val arc: Boolean?)
    data class Report(val devices: List<Device>, val ports: List<Port>, val serviceMissing: Boolean) {
        // Port number to the OSD names seen on it, the TV itself excluded.
        fun namesByPort(): Map<Int, List<String>> = devices.filter { !it.isTvItself }.groupBy({ it.port }, { it.name })
    }

    private val LOGICAL = Regex("logical_address:\\s*0x([0-9A-Fa-f]{1,2})\\b")
    private val TYPE = Regex("device_type:\\s*0x([0-9A-Fa-f]{1,2})\\b")
    private val NAME = Regex("display_name:\\s*(.*?)\\s*(?:power_status:|physical_address:|$)")
    private val PHYSICAL = Regex("physical_address:\\s*0x([0-9A-Fa-f]{1,4})\\b")
    private val PORT_ID = Regex("port_id:\\s*(-?\\d{1,3})\\b")
    private val PORT_LINE = Regex("port_id:\\s*(\\d{1,3}),\\s*address:\\s*0x([0-9A-Fa-f]{1,4})(?:,\\s*cec:\\s*(true|false))?(?:,\\s*arc:\\s*(true|false))?")
    // Android 12+: HdmiPortInfo{id=1, type=INPUT, address=0x1000, cec=true, arc=false, eArc=false}.
    // The record body is a bounded run without braces and each field is found on its own; chained
    // lazy wildcards here backtracked for seconds on one hostile 8 KiB line.
    private val PORT_INFO_NEW = Regex("HdmiPortInfo\\{id=(\\d{1,3}),([^{}]{0,256})")
    private val PORT_ADDRESS = Regex("(?<![A-Za-z])address=0x([0-9A-Fa-f]{1,4})\\b")
    private val PORT_CEC = Regex("(?<![A-Za-z])cec=(true|false)\\b")
    private val PORT_ARC = Regex("(?<![A-Za-z])arc=(true|false)\\b")
    private val OSD_NAME = Regex("^[ -~]{0,14}$")   // CEC OSD names are at most 14 printable chars

    fun parse(lines: Sequence<String>): Report {
        val all = capped(ProbeStage.INPUTS, lines).map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (all.isEmpty() || all.any { "Can't find service" in it || "does not exist" in it }) return Report(emptyList(), emptyList(), true)
        val devices = LinkedHashMap<String, Device>()
        val ports = LinkedHashMap<Int, Port>()
        for (line in all) {
            val physical = PHYSICAL.find(line)
            val logical = LOGICAL.find(line)
            if (physical != null && logical != null && "display_name:" in line) {
                val pa = physical.groupValues[1].toInt(16)
                val la = logical.groupValues[1].toInt(16)
                val type = TYPE.find(line)?.groupValues?.get(1)?.toInt(16) ?: -1
                val name = NAME.find(line)?.groupValues?.get(1)?.trim().orEmpty().takeIf { OSD_NAME.matches(it) } ?: ""
                val portField = PORT_ID.find(line)?.groupValues?.get(1)?.toInt() ?: 0
                val port = if (portField > 0) portField else (pa shr 12) and 0xF
                devices.putIfAbsent("$la/$pa", Device(name, la, type, pa, port))
                continue
            }
            if ("logical_address" in line) continue
            val port = PORT_LINE.find(line)?.let { m ->
                Port(m.groupValues[1].toInt(), m.groupValues[2].toInt(16), m.groupValues[3].takeIf { it.isNotEmpty() }?.toBooleanStrict(), m.groupValues[4].takeIf { it.isNotEmpty() }?.toBooleanStrict())
            } ?: PORT_INFO_NEW.find(line)?.let { m ->
                val body = m.groupValues[2]
                val address = PORT_ADDRESS.find(body) ?: return@let null
                val cec = PORT_CEC.find(body) ?: return@let null
                val arc = PORT_ARC.find(body) ?: return@let null
                Port(m.groupValues[1].toInt(), address.groupValues[1].toInt(16), cec.groupValues[1].toBooleanStrict(), arc.groupValues[1].toBooleanStrict())
            } ?: continue
            ports.putIfAbsent(port.id, port)
        }
        return Report(devices.values.toList(), ports.values.toList(), serviceMissing = false)
    }
}

// dumpsys tv_input: the TIF inputs (TvInputInfo{id=..., pkg=..., service=...}) and, from the
// hardware manager's connection list, which HDMI port each hardware input is. An input with no
// hardware line falls back to "HdmiN" in its service name.
object TvInputParser {
    data class Input(val id: String, val pkg: PackageName, val hdmiPort: Int?)
    private val INFO = Regex("TvInputInfo\\{id=([A-Za-z0-9_.$/:-]{1,300}), pkg=([A-Za-z][A-Za-z0-9_.]{0,254}), service=")
    private val HW_PORT = Regex("TvInputHardwareInfo \\{id=\\d+, type=9,.*?hdmi_port=(\\d{1,3})")
    private val NAME_PORT = Regex("[Hh][Dd][Mm][Ii](\\d)")

    fun parse(lines: Sequence<String>): List<Input> {
        val inputs = LinkedHashMap<String, PackageName>()
        val ports = HashMap<String, Int>()
        for (l in capped(ProbeStage.INPUTS, lines)) {
            if ("Can't find service" in l) return emptyList()
            for (m in INFO.findAll(l)) {
                val pkg = packageName(m.groupValues[2]) ?: continue
                inputs.putIfAbsent(m.groupValues[1], pkg)
            }
            val hw = HW_PORT.find(l)
            if (hw != null) INFO.find(l)?.let { ports[it.groupValues[1]] = hw.groupValues[1].toInt() }
        }
        return inputs.map { (id, pkg) ->
            Input(id, pkg, ports[id] ?: NAME_PORT.find(id.substringAfter('/').substringBefore('/'))?.groupValues?.get(1)?.toInt())
        }
    }
}

// settings list <ns>: "key=value" lines; the literal "null" is no value. A line with no key
// continues the previous value (values may hold newlines).
// A value stops growing at VALUE_CAP: rebuilding a string per continuation line was quadratic,
// and 4 MiB of one-character lines from a hostile TV kept the parser busy for minutes.
object SettingsListParser {
    const val VALUE_CAP = 64 * 1024
    private val LINE = Regex("^([A-Za-z0-9_.:-]{1,128})=(.*)$")
    fun parse(lines: Sequence<String>): Map<String, String?> {
        val out = LinkedHashMap<String, StringBuilder?>()
        var last: String? = null
        for (l in skipLong(lines)) {
            val m = LINE.matchEntire(l)
            if (m != null) {
                last = m.groupValues[1]
                out[last] = m.groupValues[2].takeIf { it != "null" }?.let(::StringBuilder)
            } else if (last != null && l.isNotEmpty()) {
                val key: String = last
                val value = out[key] ?: StringBuilder().also { out[key] = it }
                if (value.length + 1 + l.length <= VALUE_CAP) value.append('\n').append(l)
            }
        }
        return out.mapValuesTo(LinkedHashMap()) { it.value?.toString() }
    }
}

// dumpsys activity activities, filtered to ResumedActivity lines: the activity in front.
// Android 12+ prints topResumedActivity=, older releases mResumedActivity:.
object ResumedActivityParser {
    private val RECORD = Regex("(topResumedActivity|mResumedActivity|ResumedActivity)[=:] ?ActivityRecord\\{[0-9a-f]+ u\\d+ (\\S+)")
    fun parse(lines: Sequence<String>): Component? {
        val found = capped(ProbeStage.HOME, lines).mapNotNull { RECORD.find(it) }.map { it.groupValues[1] to it.groupValues[2] }.toList()
        val best = found.firstOrNull { it.first == "topResumedActivity" } ?: found.firstOrNull { it.first == "mResumedActivity" } ?: found.firstOrNull()
        return best?.let { component(it.second.removeSuffix("}")) }
    }
}
