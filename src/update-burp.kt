import org.json.JSONObject
import org.json.JSONTokener
import java.io.FileOutputStream
import java.lang.System.*
import java.net.URL
import java.net.URLEncoder
import java.util.prefs.Preferences
import java.util.regex.Pattern


fun main(args: Array<String>) {
    val license = getLicense()
    val osDetails = getOS()
    if(args.isNotEmpty()) {
        osDetails.base = args[0]
    }
    val burpVersion = getBurpVersion(osDetails)
    println("Current Burp version: $burpVersion")
    val update = getUpdate(ProductDetails("pro", burpVersion, osDetails.installationType), license)
    if(update == null) {
        println("Already up to date")
    }
    else {
        println("Downloading Burp version: ${update.version}")
        downloadUpdate(update, license)
        println("Installing update")
        osDetails.installUpdate(update)
        println("Installation completed")
    }
}

fun getOS(): OSDetails {
    val osName = getProperty("os.name")
    if(osName.startsWith("Windows")) {
        if(getProperty("sun.arch.data.model") == "64") {
            return Windows("win64")
        }
        else {
            return Windows("win32")
        }
    }
    else if(osName.equals("Mac OS X")) {
        return MacOS()
    }
    else if(osName.startsWith("Linux")) {
        return Linux()
    }
    else {
        throw Exception("Unknown OS $osName")
    }
}

interface OSDetails {
    val installationType: String
    var base: String
    val jar: String
    val java: String

    fun installUpdate(productDetails: ProductDetails)
}

class MacOS: OSDetails {
    override val installationType = "macos"
    override var base = "/Applications/Burp Suite Professional.app/Contents/"
    override val jar = "java/app/burpsuite_pro.jar"
    override val java = "PlugIns/jre.bundle/Contents/Home/jre/bin/java"

    override fun installUpdate(productDetails: ProductDetails) {
        var process = Runtime.getRuntime().exec(arrayOf("hdiutil", "attach", productDetails.fileName))
        process.waitFor()
        val hdiUtilOutput = process.inputStream.bufferedReader().use { it.readLine() }
        // TODO: parse hdiUtilOutput more robustly
        val mountDir = hdiUtilOutput.split(Pattern.compile("\\s{10,}"), 2)[1]
        runAndCheck(arrayOf("$mountDir/Burp Suite Professional Installer.app/Contents/MacOS/JavaApplicationStub", "-q"))
        process = Runtime.getRuntime().exec(arrayOf("hdiutil", "detach", mountDir))
        process.waitFor()
    }
}

class Windows(override val installationType: String): OSDetails {
    override var base = "/Applications/Burp Suite Professional.app/Contents/"
    override val jar = "java/app/burpsuite_pro.jar"
    override val java = "PlugIns/jre.bundle/Contents/Home/jre/bin/java"

    override fun installUpdate(productDetails: ProductDetails) {
        runAndCheck(arrayOf(productDetails.fileName!!, "-q"))
    }
}

class Linux: OSDetails {
    override val installationType = "linux"
    override var base = "/opt/BurpSuitePro/"
    override val jar = "burpsuite_pro.jar"
    override val java = "jre/bin/java"

    override fun installUpdate(productDetails: ProductDetails) {
        runAndCheck(arrayOf("/bin/sh", productDetails.fileName!!, "-q"))
    }
}

fun runAndCheck(command: Array<String>) {
    val process = Runtime.getRuntime().exec(command)
    val rc = process.waitFor()
    if(rc != 0) {
        throw Exception("Installer failed with return code: $rc")
    }

}

fun getBurpVersion(osDetails: OSDetails): String {
    val process = Runtime.getRuntime().exec(arrayOf(osDetails.base + osDetails.java, "-Djava.awt.headless=true", "-jar", osDetails.base + osDetails.jar, "--version"))
    val version = process.inputStream.bufferedReader().use { it.readLine() }
    return version.substringBefore("-")
}

const val portswigger = "portswigger.net"

fun getLicense(): String {
    val burpPrefs = Preferences.userRoot().node("burp")
    for(key in burpPrefs.keys()) {
        if(key.endsWith("==")) {
            return burpPrefs.get(key, "")
        }
    }
    throw Exception("License key not found")
}

data class ProductDetails(
    val product: String,
    val version: String,
    val platform: String,
    val fileName: String? = null
)

fun getUpdate(productDetails: ProductDetails, license: String): ProductDetails? {
    val url = URL("https://$portswigger/Burp/Releases/CheckForUpdates?product=${productDetails.product}&version=${productDetails.version}&license=${URLEncoder.encode(license, "utf-8")}")
    val updatesJson = url.openStream().bufferedReader().use { it.readText() }
    val root = JSONObject(JSONTokener(updatesJson))
    val updates = root.getJSONArray("updates")

    if(updates.length() == 0) {
        return null
    }

    val version = updates.getJSONObject(0).getString("version")

    val builds = updates.getJSONObject(0).getJSONArray("builds")
    var fileName: String? = null
    for(i in 1 until builds.length()) {
        val build = builds.getJSONObject(i)
        val installationType = build.getString("installationType")
        if(installationType == productDetails.platform) {
            fileName = build.getString("filename")
        }
    }
    return ProductDetails(productDetails.product, version, productDetails.platform, fileName)
}

fun downloadUpdate(productDetails: ProductDetails, license: String) {
    val url = URL("https://$portswigger/burp/releases/intooldownload?product=${productDetails.product}&version=${productDetails.version}&installationType=${productDetails.platform}&license=${URLEncoder.encode(license, "utf-8")}")

    val inputStream = url.openStream()
    val outputStream = FileOutputStream(productDetails.fileName)
    inputStream.use { input ->
        outputStream.use { fileOut ->
            input.copyTo(fileOut)
        }
    }
}
