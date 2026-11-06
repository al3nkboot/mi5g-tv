package tv.cleanyour.core.ops
import kotlin.test.*
import org.junit.Test
import tv.cleanyour.core.model.PackageName
class PackageValidation21Test {
 @Test fun rejectsInjectionAtEveryPackageEntryPoint() {
  for (bad in listOf("com.a;reboot", "com.a\nreboot", "com.a$(id)", "com.a`id`", "-com.a", "com.1bad", "com._bad", "com..a", "com.a/Bad", " com.a")) {
   assertFailsWith<IllegalArgumentException> { PackageName(bad) }
   assertFailsWith<IllegalArgumentException> { ShellRenderer.launchPackageLine(bad) }
   assertFailsWith<IllegalArgumentException> { ShellRenderer.packagePathLine(bad) }
  }
 }
 @Test fun tclArtIsProtectedFromDebloat(){assertNotNull(Denylist.staticReason("com.tcl.exhibit"));assertFailsWith<IllegalArgumentException>{ShellRenderer.disableUserPackageLine(PackageName("com.tcl.exhibit"))}}
}
