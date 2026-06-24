package com.pcamposu.ps3.hfwserver.gui

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.fasterxml.jackson.databind.ObjectMapper
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.concurrent.thread

private const val SERVER_APP_CLASS = "com.pcamposu.ps3.hfwserver.Ps3HfwUpdateServerApplication"
private const val DNS_SERVER_CLASS = "com.pcamposu.ps3.hfwserver.dns.Ps3DnsServer"
private const val FIRMWARE_RELEASES_API = "https://api.github.com/repos/dracinn/Firmware-Updates/releases?per_page=50"
private const val APP_NAME = "PS3 Firmware Update Server"
private const val APP_USER_AGENT = "ps3-firmware-update-server-gui"

fun main() {
    EventQueue.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        Ps3HfwUpdateServerGui().isVisible = true
    }
}

private class Ps3HfwUpdateServerGui : JFrame(APP_NAME) {
    private val firmwareFile = defaultFirmwareFile()
    private val systemProfileCombo = JComboBox(SystemProfile.entries.toTypedArray())
    private val hardwareProfileCombo = JComboBox(HardwareProfile.entries.toTypedArray())
    private val firmwareSourceModeCombo = JComboBox(FirmwareSourceMode.entries.toTypedArray())
    private val mainVariantCombo = JComboBox<MainFirmwareVariant>()
    private val childVariantCombo = JComboBox<FirmwareChildVariant>()
    private val ipCombo = JComboBox<String>()
    private val upstreamDnsField = JTextField("8.8.8.8")
    private val verboseCheck = JCheckBox("Verbose logs")
    private val localFirmwareTitle = JLabel("Local firmware")
    private val statusLabel = JLabel("Stopped")
    private val firmwareLabel = JLabel()
    private val remoteFirmwareLabel = JLabel("Checking remote releases...")
    private val ps3DnsLabel = JLabel()
    private val logArea = JTextArea()
    private val startButton = JButton("Start Server")
    private val stopButton = JButton("Stop")
    private val chooseLocalFirmwareButton = JButton("Choose PUP...")
    private val refreshFirmwareStatusButton = JButton("Refresh")
    private val downloadFirmwareButton = JButton("Download Remote Firmware")
    private val localFirmwareControls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
    private var serverHandle: ServerHandle? = null
    private var downloadInProgress = false
    private var firmwareAssets: List<FirmwareAsset> = emptyList()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(720, 520)
        preferredSize = Dimension(980, 720)

        systemProfileCombo.maximumRowCount = SystemProfile.entries.size
        systemProfileCombo.preferredSize = Dimension(520, 34)
        systemProfileCombo.renderer = FirmwareVariantRenderer()
        hardwareProfileCombo.maximumRowCount = HardwareProfile.entries.size
        hardwareProfileCombo.preferredSize = Dimension(520, 34)
        hardwareProfileCombo.renderer = FirmwareVariantRenderer()
        firmwareSourceModeCombo.maximumRowCount = FirmwareSourceMode.entries.size
        firmwareSourceModeCombo.preferredSize = Dimension(520, 34)
        firmwareSourceModeCombo.renderer = FirmwareVariantRenderer()
        mainVariantCombo.maximumRowCount = MainFirmwareVariant.entries.size
        mainVariantCombo.preferredSize = Dimension(520, 34)
        mainVariantCombo.renderer = FirmwareVariantRenderer()
        childVariantCombo.preferredSize = Dimension(520, 34)
        childVariantCombo.renderer = FirmwareVariantRenderer()
        contentPane = JPanel(BorderLayout(14, 14)).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            add(headerPanel(), BorderLayout.NORTH)
            add(mainPanel(), BorderLayout.CENTER)
        }
        pack()
        setLocationRelativeTo(null)

        refreshNetworkAddresses()
        refreshFirmwareStatus()
        updateButtons(false)

        startButton.addActionListener { startServer() }
        stopButton.addActionListener { stopServer() }
        chooseLocalFirmwareButton.addActionListener { chooseFirmware() }
        refreshFirmwareStatusButton.addActionListener { refreshFirmwareStatus() }
        downloadFirmwareButton.addActionListener { downloadSelectedFirmware() }
        systemProfileCombo.addActionListener {
            refreshMainVariants()
            refreshChildVariants()
            populateFirmwareSourceCombo()
        }
        hardwareProfileCombo.addActionListener {
            refreshChildVariants()
            populateFirmwareSourceCombo()
        }
        firmwareSourceModeCombo.addActionListener {
            updateButtons(serverHandle != null)
            populateFirmwareSourceCombo()
            if (selectedFirmwareSourceMode() == FirmwareSourceMode.REMOTE && firmwareAssets.isEmpty()) {
                refreshFirmwareSources()
            }
        }
        mainVariantCombo.addActionListener {
            refreshChildVariants()
            populateFirmwareSourceCombo()
        }
        childVariantCombo.addActionListener { populateFirmwareSourceCombo() }
        refreshMainVariants()
        refreshChildVariants()
        refreshFirmwareSources()
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                stopServer()
            }
        })
    }

    private fun headerPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            val title = JLabel(APP_NAME).apply {
                font = font.deriveFont(Font.BOLD, 22f)
            }
            val subtitle = JLabel("DNS on port 53 and HTTP on port 80 for PS3 firmware updates")
            add(title, BorderLayout.NORTH)
            add(subtitle, BorderLayout.SOUTH)
        }
    }

    private fun mainPanel(): JSplitPane {
        return JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = JScrollPane(formPanel()).apply {
                border = BorderFactory.createEmptyBorder()
                minimumSize = Dimension(0, 280)
            }
            bottomComponent = logPanel()
            resizeWeight = 0.62
            isContinuousLayout = true
            border = BorderFactory.createEmptyBorder()
        }
    }

    private fun formPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            insets = Insets(6, 4, 6, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        fun addRow(row: Int, label: JLabel, component: java.awt.Component) {
            c.gridx = 0
            c.gridy = row
            c.weightx = 0.0
            panel.add(label, c)
            c.gridx = 1
            c.weightx = 1.0
            panel.add(component, c)
        }

        fun addRow(row: Int, label: String, component: java.awt.Component) {
            addRow(row, JLabel(label), component)
        }

        addRow(0, "System profile", systemProfileCombo)
        addRow(1, "Hardware status", hardwareProfileCombo)
        addRow(2, "Firmware source", firmwareSourceModeCombo)
        addRow(3, localFirmwareTitle, firmwareLabel)

        localFirmwareControls.apply {
            add(chooseLocalFirmwareButton)
            add(refreshFirmwareStatusButton)
        }
        addRow(4, "", localFirmwareControls)
        addRow(5, "Main variant", mainVariantCombo)
        addRow(6, "Child variant", childVariantCombo)
        addRow(7, "Remote firmware", remoteFirmwareLabel)
        val updateButtons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(downloadFirmwareButton)
        }
        addRow(8, "", updateButtons)

        addRow(9, "Local IP", ipCombo)
        val ipButtons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(JButton("Refresh IPs").apply { addActionListener { refreshNetworkAddresses() } })
            add(JButton("Copy PS3 DNS").apply { addActionListener { copyDnsAddress() } })
        }
        addRow(10, "", ipButtons)

        addRow(11, "Upstream DNS", upstreamDnsField)
        addRow(12, "Logging", verboseCheck)
        addRow(13, "PS3 DNS", ps3DnsLabel)
        addRow(14, "Status", statusLabel)

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(startButton)
            add(stopButton)
        }
        addRow(15, "", actions)

        c.gridx = 0
        c.gridy = 16
        c.gridwidth = 2
        c.weighty = 1.0
        panel.add(JPanel(), c)

        return panel
    }

    private fun logPanel(): JPanel {
        logArea.apply {
            isEditable = false
            rows = 12
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }

        return JPanel(BorderLayout(0, 8)).apply {
            minimumSize = Dimension(0, 160)
            add(JLabel("Activity"), BorderLayout.NORTH)
            add(JScrollPane(logArea).apply {
                preferredSize = Dimension(720, 180)
            }, BorderLayout.CENTER)
        }
    }

    private fun startServer() {
        if (serverHandle != null) return

        val firmware = firmwareFile
        if (!firmware.isFile) {
            showError("Firmware missing", "Put PS3UPDAT.PUP at ${firmware.absolutePath}, or choose a PUP file first.")
            return
        }

        val localIp = selectedIp()
        val upstreamDns = upstreamDnsField.text.trim()
        if (!isIpv4(upstreamDns)) {
            showError("Invalid DNS", "Enter a valid upstream DNS IPv4 address.")
            return
        }

        updateButtons(true)
        status("Starting...")
        appendLog("Starting server on DNS 53 and HTTP 80 with local IP $localIp")

        thread(name = "ps3-hfw-gui-starter", isDaemon = true) {
            try {
                val handle = ServerHandle.start(firmwareFile, localIp, upstreamDns, verboseCheck.isSelected, ::appendLog)
                SwingUtilities.invokeLater {
                    serverHandle = handle
                    status("Running")
                    ps3DnsLabel.text = localIp
                    appendLog("Server is running. Configure the PS3 primary and secondary DNS to $localIp.")
                }
            } catch (ex: Exception) {
                SwingUtilities.invokeLater {
                    serverHandle = null
                    updateButtons(false)
                    status("Stopped")
                    appendLog("Failed to start: ${rootMessage(ex)}")
                    showError(
                        "Server failed to start",
                        "Could not bind DNS port 53 or HTTP port 80.\n\nOn macOS/Linux, run the GUI with administrator privileges if these ports are free.\n\n${rootMessage(ex)}"
                    )
                }
            }
        }
    }

    private fun stopServer() {
        val handle = serverHandle ?: return
        serverHandle = null
        updateButtons(false)
        status("Stopping...")
        appendLog("Stopping server...")

        thread(name = "ps3-hfw-gui-stopper", isDaemon = true) {
            handle.stop()
            SwingUtilities.invokeLater {
                status("Stopped")
                appendLog("Server stopped.")
            }
        }
    }

    private fun refreshFirmwareSources() {
        if (downloadInProgress) return

        downloadInProgress = true
        updateButtons(serverHandle != null)
        appendLog("Checking dracinn/Firmware-Updates releases...")

        thread(name = "ps3-hfw-gui-release-fetch", isDaemon = true) {
            try {
                val assets = FirmwareReleaseClient.fetchFirmwareAssets()
                SwingUtilities.invokeLater {
                    firmwareAssets = assets
                    val shownAssets = populateFirmwareSourceCombo()
                    appendLog("Found ${assets.size} firmware PUP release asset(s); showing ${shownAssets.size} for ${selectedMainVariant().label} / ${selectedChildVariant().label}.")
                    if (assets.isEmpty()) {
                        showError("No firmware found", "No .PUP release assets were found in dracinn/Firmware-Updates.")
                    } else if (shownAssets.isEmpty()) {
                        showError("No matching firmware", "No firmware matched ${selectedMainVariant().label} / ${selectedChildVariant().label}. Choose another variant.")
                    }
                }
            } catch (ex: Exception) {
                SwingUtilities.invokeLater {
                    appendLog("Failed to fetch GitHub releases: ${rootMessage(ex)}")
                    showError("GitHub update check failed", rootMessage(ex))
                }
            } finally {
                SwingUtilities.invokeLater {
                    downloadInProgress = false
                    updateButtons(serverHandle != null)
                }
            }
        }
    }

    private fun downloadSelectedFirmware() {
        if (selectedFirmwareSourceMode() != FirmwareSourceMode.REMOTE) {
            showError("Remote source not selected", "Switch Firmware source to Remote GitHub before downloading.")
            return
        }

        val asset = selectedRemoteFirmwareAsset()
        if (asset == null) {
            showError("No remote firmware", "No remote firmware matched the selected main and child variants.")
            return
        }
        if (serverHandle != null) {
            showError("Server is running", "Stop the server before replacing PS3UPDAT.PUP.")
            return
        }

        downloadInProgress = true
        updateButtons(false)
        appendLog("Downloading ${asset.assetName} from ${asset.releaseName}...")

        thread(name = "ps3-hfw-gui-firmware-download", isDaemon = true) {
            try {
                FirmwareReleaseClient.downloadFirmware(asset, firmwareFile) { appendLog(it) }
                SwingUtilities.invokeLater {
                    refreshFirmwareStatus()
                    appendLog("Firmware ready at ${firmwareFile.absolutePath}")
                }
            } catch (ex: Exception) {
                SwingUtilities.invokeLater {
                    appendLog("Download failed: ${rootMessage(ex)}")
                    showError("Firmware download failed", rootMessage(ex))
                }
            } finally {
                SwingUtilities.invokeLater {
                    downloadInProgress = false
                    updateButtons(false)
                }
            }
        }
    }

    private fun chooseFirmware() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose PS3UPDAT.PUP"
            selectedFile = File("PS3UPDAT.PUP")
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val targetDir = firmwareFile.parentFile
            targetDir.mkdirs()
            chooser.selectedFile.copyTo(firmwareFile, overwrite = true)
            refreshFirmwareStatus()
            appendLog("Copied firmware to ${firmwareFile.absolutePath}")
        }
    }

    private fun refreshNetworkAddresses() {
        val addresses = localIpv4Addresses()
        ipCombo.removeAllItems()
        if (addresses.isEmpty()) {
            ipCombo.addItem("127.0.0.1")
        } else {
            addresses.forEach { ipCombo.addItem(it) }
        }
        ps3DnsLabel.text = selectedIp()
    }

    private fun refreshFirmwareStatus() {
        firmwareLabel.text = if (firmwareFile.isFile) {
            "Ready: ${firmwareFile.absolutePath} (${humanSize(firmwareFile.length())})"
        } else {
            "Missing: ${firmwareFile.absolutePath}"
        }
        firmwareLabel.foreground = if (firmwareFile.isFile) Color(0x1B, 0x6E, 0x2B) else Color(0x9B, 0x1C, 0x1C)
    }

    private fun copyDnsAddress() {
        val dns = selectedIp()
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(dns), null)
        appendLog("Copied PS3 DNS address: $dns")
    }

    private fun selectedIp(): String = ipCombo.selectedItem?.toString() ?: "127.0.0.1"

    private fun selectedFirmwareSourceMode(): FirmwareSourceMode {
        return firmwareSourceModeCombo.selectedItem as? FirmwareSourceMode ?: FirmwareSourceMode.REMOTE
    }

    private fun updateButtons(running: Boolean) {
        startButton.isEnabled = !running && !downloadInProgress
        stopButton.isEnabled = running
        ipCombo.isEnabled = !running && !downloadInProgress
        upstreamDnsField.isEnabled = !running && !downloadInProgress
        verboseCheck.isEnabled = !running && !downloadInProgress
        firmwareSourceModeCombo.isEnabled = !running && !downloadInProgress

        val remoteMode = selectedFirmwareSourceMode() == FirmwareSourceMode.REMOTE
        localFirmwareTitle.isVisible = !remoteMode
        firmwareLabel.isVisible = !remoteMode
        localFirmwareControls.isVisible = !remoteMode
        remoteFirmwareLabel.isVisible = remoteMode
        chooseLocalFirmwareButton.isEnabled = !running && !downloadInProgress && !remoteMode
        refreshFirmwareStatusButton.isEnabled = !running && !downloadInProgress
        downloadFirmwareButton.isEnabled = !running && !downloadInProgress && remoteMode
        systemProfileCombo.isEnabled = !running && !downloadInProgress && remoteMode
        hardwareProfileCombo.isEnabled = !running && !downloadInProgress && remoteMode
        mainVariantCombo.isEnabled = !running && !downloadInProgress && remoteMode
        childVariantCombo.isEnabled = !running && !downloadInProgress && remoteMode
        revalidate()
        repaint()
    }

    private fun status(text: String) {
        statusLabel.text = text
    }

    private fun appendLog(message: String) {
        val line = "[${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}] $message\n"
        if (SwingUtilities.isEventDispatchThread()) {
            logArea.append(line)
            logArea.caretPosition = logArea.document.length
        } else {
            SwingUtilities.invokeLater {
                logArea.append(line)
                logArea.caretPosition = logArea.document.length
            }
        }
    }

    private fun showError(title: String, message: String) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE)
    }

    private fun selectedMainVariant(): MainFirmwareVariant {
        return mainVariantCombo.selectedItem as? MainFirmwareVariant ?: selectedSystemProfile().allowedMainVariants.first()
    }

    private fun selectedChildVariant(): FirmwareChildVariant {
        return childVariantCombo.selectedItem as? FirmwareChildVariant ?: selectedMainVariant().children.first()
    }

    private fun selectedSystemProfile(): SystemProfile {
        return systemProfileCombo.selectedItem as? SystemProfile ?: SystemProfile.HFW_HEN_ONLY
    }

    private fun selectedHardwareProfile(): HardwareProfile {
        return hardwareProfileCombo.selectedItem as? HardwareProfile ?: HardwareProfile.NORMAL
    }

    private fun refreshMainVariants() {
        val currentSelection = mainVariantCombo.selectedItem as? MainFirmwareVariant
        val mainVariants = selectedSystemProfile().allowedMainVariants
        mainVariantCombo.removeAllItems()
        mainVariants.forEach { mainVariantCombo.addItem(it) }
        mainVariantCombo.selectedItem = currentSelection?.takeIf { it in mainVariants } ?: mainVariants.first()
        mainVariantCombo.maximumRowCount = mainVariants.size
    }

    private fun refreshChildVariants() {
        val currentSelection = childVariantCombo.selectedItem as? FirmwareChildVariant
        val children = selectedMainVariant().children
            .filter { selectedHardwareProfile().allows(it) }
            .ifEmpty { selectedMainVariant().children }
        childVariantCombo.removeAllItems()
        children.forEach { childVariantCombo.addItem(it) }
        childVariantCombo.selectedItem = currentSelection?.takeIf { it in children } ?: children.first()
        childVariantCombo.maximumRowCount = children.size
    }

    private fun populateFirmwareSourceCombo(): List<FirmwareAsset> {
        if (selectedFirmwareSourceMode() != FirmwareSourceMode.REMOTE) {
            remoteFirmwareLabel.text = "Using local PS3UPDAT.PUP"
            return emptyList()
        }

        val mainVariant = selectedMainVariant()
        val childVariant = selectedChildVariant()
        val shownAssets = firmwareAssets
            .filter { mainVariant.matches(it) && childVariant.matches(it) }
            .sortedWith(childVariant.sorter(mainVariant))

        remoteFirmwareLabel.text = shownAssets.firstOrNull()?.toString() ?: "No matching remote firmware"
        return shownAssets
    }

    private fun selectedRemoteFirmwareAsset(): FirmwareAsset? {
        if (selectedFirmwareSourceMode() != FirmwareSourceMode.REMOTE) {
            return null
        }

        val mainVariant = selectedMainVariant()
        val childVariant = selectedChildVariant()
        return firmwareAssets
            .filter { mainVariant.matches(it) && childVariant.matches(it) }
            .sortedWith(childVariant.sorter(mainVariant))
            .firstOrNull()
    }
}

private class FirmwareVariantRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): java.awt.Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        preferredSize = Dimension(520, 30)
        return component
    }
}

private enum class FirmwareSourceMode(val label: String) {
    REMOTE("Remote GitHub firmware"),
    LOCAL("Local PS3UPDAT.PUP");

    override fun toString(): String = label
}

private enum class SystemProfile(
    val label: String,
    val allowedMainVariants: List<MainFirmwareVariant>,
) {
    HFW_HEN_ONLY("HFW/HEN or unknown CFW compatibility", listOf(MainFirmwareVariant.HFW)),
    RETAIL_CFW("Retail CEX, CFW-compatible", listOf(MainFirmwareVariant.CFW, MainFirmwareVariant.CFW_PEX)),
    DEBUG_DEX("Debug/DEX or DEX-converted", listOf(MainFirmwareVariant.DBG));

    override fun toString(): String = label
}

private enum class HardwareProfile(val label: String) {
    NORMAL("Standard hardware"),
    NO_BD("noBD - Blu-ray drive missing/broken"),
    NO_BT("noBT - Bluetooth/Wi-Fi board broken"),
    NO_BD_NO_BT("noBD+noBT");

    fun allows(childVariant: FirmwareChildVariant): Boolean {
        return when (this) {
            NORMAL -> childVariant == FirmwareChildVariant.STANDARD || childVariant == FirmwareChildVariant.HFW_STANDARD
            NO_BD -> childVariant == FirmwareChildVariant.NO_BD || childVariant == FirmwareChildVariant.HFW_STANDARD
            NO_BT -> childVariant == FirmwareChildVariant.NO_BT || childVariant == FirmwareChildVariant.HFW_STANDARD
            NO_BD_NO_BT -> childVariant == FirmwareChildVariant.NO_BD_NO_BT || childVariant == FirmwareChildVariant.HFW_STANDARD
        }
    }

    override fun toString(): String = label
}

private enum class MainFirmwareVariant(
    val label: String,
    val children: List<FirmwareChildVariant>,
) {
    CFW("CFW-CEX", FirmwareChildVariant.CFW_CHILDREN),
    CFW_PEX("CFW-PEX", FirmwareChildVariant.MODIFIED_CFW_CHILDREN),
    DBG("CFW-DPEX", FirmwareChildVariant.MODIFIED_CFW_CHILDREN),
    HFW("HFW", listOf(FirmwareChildVariant.HFW_STANDARD));

    fun matches(asset: FirmwareAsset): Boolean {
        return when (this) {
            CFW -> asset.isCfw && !asset.isPex && !asset.isDpex && !asset.isHfw
            CFW_PEX -> asset.isPex
            DBG -> asset.isDpex || asset.isDbg
            HFW -> asset.isHfw
        }
    }

    override fun toString(): String = label
}

private enum class FirmwareChildVariant(val label: String) {
    STANDARD("Standard"),
    NO_BD("noBD"),
    NO_BT("noBT"),
    NO_BD_NO_BT("noBD+noBT"),
    HFW_STANDARD("HFW");

    fun matches(asset: FirmwareAsset): Boolean {
        return when (this) {
            STANDARD -> !asset.isNoBd && !asset.isNoBt
            NO_BD -> asset.isNoBd && !asset.isNoBt
            NO_BT -> !asset.isNoBd && asset.isNoBt
            NO_BD_NO_BT -> asset.isNoBd && asset.isNoBt
            HFW_STANDARD -> asset.isHfw
        }
    }

    fun sorter(mainVariant: MainFirmwareVariant): Comparator<FirmwareAsset> {
        return compareByDescending<FirmwareAsset> { mainVariant.matches(it) }
            .thenByDescending { matches(it) }
            .thenByDescending { it.isEvilnat }
            .thenBy { it.releaseName }
    }

    override fun toString(): String = label

    companion object {
        val CFW_CHILDREN = listOf(STANDARD, NO_BD, NO_BT, NO_BD_NO_BT)
        val MODIFIED_CFW_CHILDREN = listOf(STANDARD, NO_BD, NO_BT, NO_BD_NO_BT)
    }
}

private data class FirmwareAsset(
    val releaseName: String,
    val tagName: String,
    val assetName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String?,
) {
    val searchableText: String
        get() = "$releaseName $tagName $assetName"

    val isCfw: Boolean
        get() = searchableText.contains("CFW", ignoreCase = true)

    val isHfw: Boolean
        get() = searchableText.contains("HFW", ignoreCase = true)

    val isPex: Boolean
        get() = searchableText.contains("PEX", ignoreCase = true) && !isDpex

    val isDpex: Boolean
        get() = normalizedSearchableText.contains("DPEX", ignoreCase = true) ||
                searchableText.contains("DBG", ignoreCase = true)

    val isDbg: Boolean
        get() = searchableText.contains("DBG", ignoreCase = true)

    val isNoBd: Boolean
        get() = normalizedSearchableText.contains("NOBD", ignoreCase = true)

    val isNoBt: Boolean
        get() = normalizedSearchableText.contains("NOBT", ignoreCase = true)

    val isEvilnat: Boolean
        get() = searchableText.contains("EVILNAT", ignoreCase = true)

    private val normalizedSearchableText: String
        get() = searchableText.replace("-", "").replace("_", "").replace(" ", "")

    override fun toString(): String {
        return "${trackName()} ${variantName()} - $releaseName (${humanSize(size)})"
    }

    private fun trackName(): String {
        return when {
            isDpex -> "CFW-DPEX"
            isPex -> "CFW-PEX"
            isCfw -> "CFW-CEX"
            isHfw -> "HFW"
            else -> tagName
        }
    }

    private fun variantName(): String {
        return when {
            isNoBd && isNoBt -> "noBD+noBT"
            isNoBd -> "noBD"
            isNoBt -> "noBT"
            isHfw -> "Standard"
            else -> "Standard"
        }
    }
}

private object FirmwareReleaseClient {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val mapper = ObjectMapper()

    fun fetchFirmwareAssets(): List<FirmwareAsset> {
        val request = HttpRequest.newBuilder(URI.create(FIRMWARE_RELEASES_API))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", APP_USER_AGENT)
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("GitHub returned HTTP ${response.statusCode()}")
        }

        val releases = mapper.readTree(response.body())
        val assets = mutableListOf<FirmwareAsset>()
        for (release in releases.elements().asSequence()) {
            val releaseName = release.path("name").asText(release.path("tag_name").asText("Release"))
            val tagName = release.path("tag_name").asText("")
            for (asset in release.path("assets").elements().asSequence()) {
                val assetName = asset.path("name").asText("")
                if (!assetName.endsWith(".PUP", ignoreCase = true)) continue
                assets += FirmwareAsset(
                    releaseName = releaseName,
                    tagName = tagName,
                    assetName = assetName,
                    downloadUrl = asset.path("browser_download_url").asText(),
                    size = asset.path("size").asLong(),
                    sha256 = asset.path("digest").asText(null)?.removePrefix("sha256:")
                )
            }
        }

        return assets.sortedWith(
            compareByDescending<FirmwareAsset> { it.isCfw }
                .thenByDescending { it.isEvilnat }
                .thenBy { it.isHfw }
                .thenBy { it.releaseName }
        )
    }

    fun downloadFirmware(asset: FirmwareAsset, target: File, log: (String) -> Unit) {
        target.parentFile.mkdirs()
        val tempFile = File(target.parentFile, "${target.name}.download")
        tempFile.delete()
        val request = HttpRequest.newBuilder(URI.create(asset.downloadUrl))
            .header("User-Agent", APP_USER_AGENT)
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()))
        if (response.statusCode() !in 200..299) {
            tempFile.delete()
            throw IllegalStateException("Download returned HTTP ${response.statusCode()}")
        }

        if (asset.sha256 != null) {
            log("Verifying SHA-256...")
            val actual = sha256(tempFile)
            if (!actual.equals(asset.sha256, ignoreCase = true)) {
                tempFile.delete()
                throw IllegalStateException("SHA-256 mismatch. Expected ${asset.sha256}, got $actual")
            }
        }

        Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        log("Downloaded ${asset.assetName} (${humanSize(target.length())}).")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private class ServerHandle(
    private val context: ConfigurableApplicationContext,
    private val dnsServer: Any,
) {
    fun stop() {
        runCatching {
            dnsServer.javaClass.getMethod("isRunning").invoke(dnsServer) as? Boolean
        }.getOrNull()?.let { running ->
            if (running) {
                dnsServer.javaClass.getMethod("stop").invoke(dnsServer)
            }
        }
        context.close()
    }

    companion object {
        fun start(firmwareFile: File, localIp: String, upstreamDns: String, verbose: Boolean, log: (String) -> Unit): ServerHandle {
            val firmwarePath = firmwareFile.absolutePath
            System.setProperty("server.port", "80")
            System.setProperty("firmware.path", firmwarePath)
            System.setProperty("upstream.dns", upstreamDns)
            System.setProperty("local.ip", localIp)
            System.setProperty("ps3.verbose", verbose.toString())
            System.setProperty("verbose", verbose.toString())
            System.setProperty("ps3.dns.port", "53")
            System.setProperty("ps3.dns.upstream", upstreamDns)
            System.setProperty("ps3.dns.localIp", localIp)
            System.setProperty("ps3.firmware.path", firmwarePath)

            val appClass = Class.forName(SERVER_APP_CLASS)
            val app = SpringApplication(appClass).apply {
                setBannerMode(Banner.Mode.OFF)
                setLogStartupInfo(false)
                setRegisterShutdownHook(false)
            }

            val context = app.run()
            return try {
                val dnsClass = Class.forName(DNS_SERVER_CLASS)
                val dnsServer = context.getBean(dnsClass)
                dnsServer.javaClass.getMethod("start").invoke(dnsServer)
                log("HTTP server started on port 80.")
                log("DNS server started on port 53.")
                ServerHandle(context, dnsServer)
            } catch (ex: Exception) {
                context.close()
                throw ex
            }
        }
    }
}

private fun localIpv4Addresses(): List<String> {
    return NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filterNot { it.isLoopbackAddress }
        .map { it.hostAddress }
        .distinct()
        .sortedWith(compareByDescending<String> { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }.thenBy { it })
}

private fun defaultFirmwareFile(): File {
    val home = System.getProperty("user.home")
    val appDataDir = if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
        File(home, "Library/Application Support/$APP_NAME")
    } else {
        File(home, ".$APP_NAME")
    }
    return File(appDataDir, "firmware/PS3UPDAT.PUP")
}

private fun isIpv4(value: String): Boolean {
    val parts = value.split(".")
    return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
}

private fun humanSize(bytes: Long): String {
    val mib = bytes / (1024.0 * 1024.0)
    return "%.1f MiB".format(mib)
}

private fun rootMessage(ex: Throwable): String {
    var current: Throwable = ex
    while (current.cause != null) {
        current = current.cause!!
    }
    return current.message ?: ex.message ?: ex.javaClass.simpleName
}
