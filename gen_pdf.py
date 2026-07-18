from fpdf import FPDF
import os

OUTPUT_PATH = r"C:\Users\Owner\Desktop\New folder (5)\INF_Driver_Injection_Walkthrough.pdf"
os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)

class PDF(FPDF):
    def header(self):
        self.set_fill_color(30, 30, 60)
        self.rect(0, 0, 210, 18, 'F')
        self.set_font("Helvetica", "B", 11)
        self.set_text_color(255, 255, 255)
        self.set_xy(0, 4)
        self.cell(0, 10, "INF File Injection & Hardware ID Lookup - Technical Walkthrough", align="C")
        self.set_text_color(0, 0, 0)
        self.ln(12)

    def footer(self):
        self.set_y(-14)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120, 120, 120)
        self.cell(0, 10, f"Page {self.page_no()} | INF Driver Injection Walkthrough", align="C")

    def chapter_title(self, num, title):
        self.set_fill_color(30, 30, 60)
        self.set_text_color(255, 255, 255)
        self.set_font("Helvetica", "B", 13)
        self.cell(0, 10, f"  {num}  {title}", ln=True, fill=True)
        self.set_text_color(0, 0, 0)
        self.ln(3)

    def section_title(self, title):
        self.set_font("Helvetica", "B", 11)
        self.set_text_color(30, 30, 120)
        self.cell(0, 8, title, ln=True)
        self.set_text_color(0, 0, 0)
        self.ln(1)

    def body(self, txt):
        self.set_font("Helvetica", "", 10)
        self.multi_cell(0, 6, txt)
        self.ln(2)

    def note_box(self, label, txt, r=255, g=243, b=205):
        self.set_fill_color(r, g, b)
        self.set_draw_color(200, 160, 0)
        if label.startswith("WARNING"):
            self.set_fill_color(255, 220, 220)
            self.set_draw_color(200, 0, 0)
        elif label.startswith("TIP"):
            self.set_fill_color(220, 240, 255)
            self.set_draw_color(0, 100, 200)
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(80, 40, 0)
        x = self.get_x()
        y = self.get_y()
        self.rect(x, y, 180, 6 + self.get_string_width(txt) // 170 * 6 + 10, 'FD')
        self.set_xy(x + 3, y + 2)
        self.cell(0, 5, f"  {label}", ln=True)
        self.set_font("Helvetica", "", 9.5)
        self.set_text_color(0, 0, 0)
        self.set_x(x + 3)
        self.multi_cell(174, 5.5, txt)
        self.ln(4)

    def cmd_block(self, code_lines):
        self.set_fill_color(30, 30, 30)
        self.set_text_color(0, 230, 100)
        self.set_font("Courier", "", 9)
        x = self.get_x()
        y = self.get_y()
        total_h = len(code_lines) * 6 + 6
        self.rect(x, y, 180, total_h, 'F')
        self.set_xy(x + 4, y + 3)
        for line in code_lines:
            self.cell(0, 6, line, ln=True)
            self.set_x(x + 4)
        self.set_text_color(0, 0, 0)
        self.ln(4)

    def bullet(self, items, indent=5):
        self.set_font("Helvetica", "", 10)
        for item in items:
            self.set_x(self.l_margin + indent)
            self.cell(5, 6, chr(149))
            self.multi_cell(0, 6, item)
        self.ln(2)

    def numbered(self, items, indent=5):
        self.set_font("Helvetica", "", 10)
        for i, item in enumerate(items, 1):
            self.set_x(self.l_margin + indent)
            self.cell(8, 6, f"{i}.")
            self.multi_cell(0, 6, item)
        self.ln(2)

pdf = PDF()
pdf.set_auto_page_break(auto=True, margin=18)
pdf.set_margins(14, 22, 14)
pdf.add_page()

# COVER
pdf.set_font("Helvetica", "B", 22)
pdf.set_text_color(30, 30, 60)
pdf.ln(10)
pdf.cell(0, 14, "INF File Injection &", ln=True, align="C")
pdf.cell(0, 14, "Hardware ID Lookup", ln=True, align="C")
pdf.set_font("Helvetica", "", 13)
pdf.set_text_color(80, 80, 80)
pdf.cell(0, 10, "A Complete Technical Walkthrough", ln=True, align="C")
pdf.ln(4)
pdf.set_draw_color(30, 30, 60)
pdf.set_line_width(0.8)
pdf.line(14, pdf.get_y(), 196, pdf.get_y())
pdf.ln(6)
pdf.set_font("Helvetica", "", 10)
pdf.set_text_color(50, 50, 50)
cover_intro = (
    "This guide explains how to manually inject .INF driver files into Windows, how to locate "
    "the correct Hardware ID for any device, and how to resolve driver corruption and handshake "
    "errors. Each section walks through the process step-by-step with exact commands and "
    "screenshot callouts so that even readers without deep Windows internals experience can "
    "follow along safely."
)
pdf.multi_cell(0, 6, cover_intro)
pdf.ln(4)

pdf.set_font("Helvetica", "B", 10)
pdf.set_text_color(30, 30, 60)
pdf.cell(0, 7, "Contents", ln=True)
toc = [
    ("1",  "What Is an INF File?"),
    ("2",  "Tools & Prerequisites"),
    ("3",  "Finding Hardware IDs - Five Methods"),
    ("4",  "Locating or Obtaining the Correct INF File"),
    ("5",  "Manual INF Injection via PnPUtil (Recommended)"),
    ("6",  "Manual INF Injection via Device Manager"),
    ("7",  "Manual INF Injection via DISM (Offline / WinPE)"),
    ("8",  "Manual INF Injection via SetupAPI / rundll32"),
    ("9",  "Fixing Corrupted Drivers"),
    ("10", "Fixing Handshake / Code 10 / Code 43 Errors"),
    ("11", "Verifying the Injection Worked"),
    ("12", "Rollback & Uninstall"),
    ("13", "Troubleshooting Quick-Reference"),
]
pdf.set_font("Helvetica", "", 10)
pdf.set_text_color(0, 0, 0)
for num, title in toc:
    pdf.set_x(18)
    pdf.cell(14, 6, num + ".")
    pdf.cell(0, 6, title, ln=True)
pdf.ln(4)

# SECTION 1
pdf.add_page()
pdf.chapter_title("1", "What Is an INF File?")
pdf.body(
    "An INF (Setup Information) file is a plain-text configuration file that Windows uses to "
    "install hardware drivers. It tells Windows what files to copy, which registry keys to "
    "create, and how to associate the driver with a specific piece of hardware."
)
pdf.section_title("Key INF File Sections")
pdf.bullet([
    "[Version]  - Declares the INF version, driver provider, and date.",
    "[Manufacturer] / [Models] - Lists the hardware IDs the driver supports.",
    "[Install] - Specifies which files to copy and registry edits to make.",
    "[Strings] - Human-readable names substituted throughout the file.",
    "[SourceDisksFiles] - Maps driver files to disk/cab locations.",
])
pdf.body(
    "Windows stores installed INF files in C:\\Windows\\INF\\ and a copy in "
    "C:\\Windows\\System32\\DriverStore\\FileRepository\\. Each driver package gets its own "
    "subfolder named after the INF (e.g., usbstor.inf_amd64_<hash>\\)."
)
pdf.note_box("NOTE",
    "You should never manually edit files inside DriverStore. Always use PnPUtil or DISM "
    "to add or remove driver packages safely.")

# SECTION 2
pdf.chapter_title("2", "Tools & Prerequisites")
pdf.section_title("Built-in Windows Tools (no install needed)")
pdf.bullet([
    "PnPUtil.exe - The primary command-line tool for driver package management. Present on all modern Windows versions.",
    "Device Manager (devmgmt.msc) - GUI tool; right-click > Update Driver > Browse my computer.",
    "DISM.exe - Deployment Image Servicing and Management; used for offline driver injection.",
    "SetupAPI / rundll32 - Low-level setup API; rarely needed but documented below.",
    "PowerShell Get-PnpDevice / Add-WindowsDriver - Alternative scripting interface.",
])
pdf.section_title("Optional / Diagnostic Tools")
pdf.bullet([
    "Driver Store Explorer (RAPR.exe) - GUI front-end to DriverStore; great for listing/removing old driver packages.",
    "DevCon.exe - Command-line Device Manager from the Windows Driver Kit (WDK). Free download from Microsoft.",
    "USBDeview / DevManView - NirSoft utilities; useful for listing hidden/disconnected devices.",
    "DriverVerifier (verifier.exe) - Built-in tool to stress-test drivers and catch corruption.",
    "Event Viewer (eventvwr.msc) - Check System and Application logs for driver errors (Event IDs 219, 7026, etc.).",
])
pdf.section_title("Prerequisites")
pdf.bullet([
    "An Administrator account (or the ability to elevate via UAC).",
    "The correct .INF file (and any associated .SYS, .DLL, .CAT files in the same folder).",
    "Secure Boot does NOT block third-party drivers by default on Windows 10/11, but drivers must be "
    "WHQL-signed or you must enable Test Mode (bcdedit /set testsigning on) for unsigned drivers.",
])
pdf.note_box("WARNING - Test Signing",
    "Enabling Test Mode (testsigning) disables Driver Signature Enforcement and may lower system "
    "security. Use it only in a controlled or development environment and re-disable it when done "
    "with: bcdedit /set testsigning off  followed by a reboot.")

# SECTION 3
pdf.add_page()
pdf.chapter_title("3", "Finding Hardware IDs - Five Methods")
pdf.body(
    "Before injecting a driver, you must know the exact Hardware ID of the device so you can "
    "confirm the INF file actually supports it. Windows identifies devices by a string like:\n"
    "   USB\\VID_045E&PID_0745\n"
    "   PCI\\VEN_8086&DEV_1C22&SUBSYS_1C221028&REV_05\n\n"
    "There are five reliable ways to find this string."
)

pdf.section_title("Method 1 - Device Manager (GUI)")
pdf.numbered([
    "Press Win + X, then click Device Manager.",
    "Locate your device. If it has no driver, it will appear under 'Other Devices' with a yellow "
    "exclamation mark.",
    "Right-click the device > Properties.",
    "Click the Details tab.",
    "In the Property drop-down, select Hardware Ids.",
    "The list of IDs is shown in order from most-specific (top) to least-specific (bottom). "
    "Copy the top entry for the most precise match.",
])

pdf.section_title("Method 2 - PnPUtil (Command Line)")
pdf.cmd_block([
    "# List all connected devices and their hardware IDs",
    "pnputil /enum-devices /connected /ids",
    "",
    "# Filter for a specific class (e.g., USB)",
    "pnputil /enum-devices /class USB /ids",
])

pdf.section_title("Method 3 - PowerShell Get-PnpDevice")
pdf.cmd_block([
    "# Show all devices with their IDs",
    "Get-PnpDevice | Select-Object FriendlyName, InstanceId, Status | Format-List",
    "",
    "# Show hardware IDs of a specific device (by friendly name search)",
    "Get-PnpDeviceProperty -InstanceId (Get-PnpDevice | Where-Object {",
    "    $_.FriendlyName -like '*Ethernet*'",
    "} | Select-Object -First 1 -ExpandProperty InstanceId) -KeyName DEVPKEY_Device_HardwareIds",
])

pdf.section_title("Method 4 - WMIC (Legacy, still works on Windows 10)")
pdf.cmd_block([
    "wmic path Win32_PnpEntity get Name,DeviceID /format:list | findstr /i \"DeviceID\"",
])

pdf.section_title("Method 5 - Registry")
pdf.body(
    "Every enumerated device has a key under:\n"
    "  HKLM\\SYSTEM\\CurrentControlSet\\Enum\\<Bus>\\<DeviceID>\\<InstanceID>\n\n"
    "Open Regedit, navigate to the path above, and read the HardwareID multi-string value. "
    "This is useful when a device is not shown in Device Manager because its driver crashed."
)
pdf.note_box("TIP",
    "Copy the FULL hardware ID string, including the vendor/device codes and any subsystem or "
    "revision suffixes. Windows checks IDs from most to least specific, so an exact match always "
    "wins over a generic one.")

# SECTION 4
pdf.add_page()
pdf.chapter_title("4", "Locating or Obtaining the Correct INF File")
pdf.section_title("Option A - Windows Update / Microsoft Catalog")
pdf.body(
    "Go to https://www.catalog.update.microsoft.com and search by your Hardware ID "
    "(e.g., VID_045E&PID_0745). Download the .cab file, extract it with:"
)
pdf.cmd_block([
    "expand -F:* DriverPackage.cab C:\\Drivers\\Extracted\\",
])
pdf.body("The extracted folder will contain the .INF, .SYS, and usually a .CAT (catalog/signature) file.")

pdf.section_title("Option B - Manufacturer Website")
pdf.body(
    "Download the driver package from the device manufacturer. Most packages are self-extracting "
    "EXEs. Run the EXE, let it extract, then immediately cancel the installation wizard. The "
    "extracted files are usually placed in C:\\Users\\<User>\\AppData\\Local\\Temp\\ or "
    "C:\\Drivers\\. Copy that folder to a permanent location before it gets cleaned up."
)

pdf.section_title("Option C - Extract from an Existing Windows Installation")
pdf.body(
    "If another machine has the driver working, copy the entire DriverStore package folder:\n"
    "  C:\\Windows\\System32\\DriverStore\\FileRepository\\<driver_folder>\\\n\n"
    "This folder contains the INF, SYS, and all supporting files and is self-contained."
)

pdf.section_title("Option D - Extract from a WIM/ISO")
pdf.cmd_block([
    "# Mount a Windows ISO or WIM to extract its built-in drivers",
    "dism /Mount-Wim /WimFile:C:\\ISO\\sources\\install.wim /Index:1 /MountDir:C:\\WimMount /ReadOnly",
    "xcopy C:\\WimMount\\Windows\\System32\\DriverStore\\FileRepository\\<driver_folder>\\ C:\\Drivers\\ /E /I",
    "dism /Unmount-Wim /MountDir:C:\\WimMount /Discard",
])

pdf.section_title("Verifying the INF Matches Your Hardware ID")
pdf.body(
    "Open the .INF in Notepad. Search for the [Manufacturer] section, then find the related "
    "[Models] section for your architecture (e.g., [Models.NTamd64]). Each line has the format:\n\n"
    "   Description = InstallSection, HardwareID1 [, HardwareID2, ...]\n\n"
    "Confirm your device's Hardware ID appears in at least one of those lines. If it does not, "
    "the INF will not install for your device."
)

# SECTION 5
pdf.add_page()
pdf.chapter_title("5", "Manual INF Injection via PnPUtil (Recommended)")
pdf.body(
    "PnPUtil is the safest, Microsoft-supported method. It adds the INF to the DriverStore "
    "and optionally installs it for matching devices in one command."
)

pdf.section_title("Step 1 - Open an Elevated Command Prompt")
pdf.numbered([
    "Press Win, type cmd.",
    "Right-click Command Prompt > Run as administrator.",
    "Click Yes on the UAC prompt.",
])

pdf.section_title("Step 2 - Stage the Driver Package into DriverStore")
pdf.cmd_block([
    "pnputil /add-driver C:\\Drivers\\mydriver.inf /subdirs",
    "",
    "# /subdirs also scans sub-folders for INF files",
    "# Output example:",
    "#  Microsoft PnP Utility",
    "#  Processing inf: mydriver.inf",
    "#  Driver package added successfully.",
    "#  Published name: oem42.inf",
])
pdf.body(
    "Windows assigns the INF a published name (oem42.inf, oem43.inf, etc.) and stores it in "
    "C:\\Windows\\INF\\. The original folder is copied into DriverStore."
)

pdf.section_title("Step 3 - Install / Bind to a Device")
pdf.cmd_block([
    "# Install the driver for any matching connected device",
    "pnputil /add-driver C:\\Drivers\\mydriver.inf /install",
    "",
    "# Or: scan for hardware changes to trigger automatic binding",
    "pnputil /scan-devices",
])

pdf.section_title("Step 4 - Confirm the Driver Is Listed")
pdf.cmd_block([
    "pnputil /enum-drivers",
    "# Look for your Published Name (e.g., oem42.inf) and confirm",
    "# Original Name matches your INF filename.",
])

pdf.note_box("TIP",
    "If the device is not yet connected, stage the driver first (/add-driver without /install). "
    "When you plug the device in, Windows will automatically find and apply the staged driver.")

# SECTION 6
pdf.add_page()
pdf.chapter_title("6", "Manual INF Injection via Device Manager (GUI)")
pdf.body(
    "Use this method when you prefer a graphical interface or when PnPUtil is unavailable."
)
pdf.numbered([
    "Open Device Manager: press Win + X > Device Manager, or run devmgmt.msc.",
    "Find the device. If it has no driver, it will be under 'Other Devices' with a yellow bang.",
    "Right-click the device > Update driver.",
    "Select 'Browse my computer for drivers'.",
    "Click Browse and navigate to the folder that contains your .INF file. Make sure "
    "'Include subfolders' is checked if the SYS file is in a subfolder.",
    "Click Next. Windows will scan the folder, match the Hardware ID against the INF, and "
    "install the driver. If it finds a match, you will see 'Windows has successfully updated "
    "your driver software'.",
    "If Windows says it cannot find a driver, try the fallback: click 'Let me pick from a "
    "list of available drivers', click 'Have Disk...', then Browse to the .INF file directly.",
    "Reboot if prompted.",
])
pdf.note_box("NOTE",
    "Device Manager only matches hardware IDs; it will refuse to install an INF that does not "
    "contain your device's Hardware ID in its [Models] section. If that happens, verify you "
    "have the correct INF (see Section 4).")

# SECTION 7
pdf.add_page()
pdf.chapter_title("7", "Manual INF Injection via DISM (Offline / WinPE)")
pdf.body(
    "DISM is the right tool when:\n"
    "  a) You are servicing an offline Windows image (VHD, WIM, or mounted drive from WinPE).\n"
    "  b) Windows cannot boot and you need to inject a driver before the next boot.\n"
    "  c) You are deploying a custom image and want drivers pre-staged."
)

pdf.section_title("Inject a Driver into a Mounted WIM Image")
pdf.cmd_block([
    "# 1. Mount the WIM",
    "dism /Mount-Image /ImageFile:C:\\Images\\install.wim /Index:1 /MountDir:C:\\MountPoint",
    "",
    "# 2. Add a single driver",
    "dism /Image:C:\\MountPoint /Add-Driver /Driver:C:\\Drivers\\mydriver.inf",
    "",
    "# 3. Add ALL drivers from a folder recursively",
    "dism /Image:C:\\MountPoint /Add-Driver /Driver:C:\\Drivers\\ /Recurse",
    "",
    "# 4. Verify the driver was added",
    "dism /Image:C:\\MountPoint /Get-Drivers",
    "",
    "# 5. Commit and unmount",
    "dism /Unmount-Image /MountDir:C:\\MountPoint /Commit",
])

pdf.section_title("Inject a Driver into an Offline Windows Installation (e.g., from WinPE)")
pdf.body(
    "If Windows is installed on D: and you are booted into WinPE or another OS:"
)
pdf.cmd_block([
    "dism /Image:D:\\ /Add-Driver /Driver:C:\\Drivers\\mydriver.inf /ForceUnsigned",
    "",
    "# /ForceUnsigned allows unsigned drivers; remove this flag if the driver is signed.",
])
pdf.note_box("WARNING",
    "/ForceUnsigned bypasses driver signature checks. Only use this for trusted drivers in "
    "emergency recovery scenarios. Reboot into the repaired OS after injection.")

# SECTION 8
pdf.add_page()
pdf.chapter_title("8", "Manual INF Injection via SetupAPI / rundll32 (Advanced)")
pdf.body(
    "This method calls the Windows Setup API directly. It is rarely needed today but is "
    "useful for legacy compatibility or scripting scenarios."
)
pdf.cmd_block([
    "# Install an INF using rundll32 + setupapi",
    "rundll32 setupapi.dll,InstallHinfSection DefaultInstall 132 C:\\Drivers\\mydriver.inf",
    "",
    "# The '132' is a flag combination:",
    "#   128 = Do not prompt the user (quiet mode)",
    "#   4   = Reboot if needed",
    "#   = 132 combined",
    "",
    "# Alternative: use the newer SetupCopyOEMInf API via a helper",
    "# (PowerShell wrapper - requires the Win32 API call)",
])
pdf.body(
    "The DefaultInstall section in the INF must exist and must reference [CopyFiles] and "
    "[AddReg] sections for this to work. Not all INF files have a DefaultInstall section; "
    "most modern PnP driver INFs do not. In that case, use PnPUtil (Section 5) instead."
)
pdf.note_box("TIP",
    "If you see 'Error 0x800B0101: A required certificate in the certificate chain is not signed "
    "by a trusted root certificate authority', the driver is not signed. Enable Test Mode or "
    "obtain a signed version of the driver.")

# SECTION 9
pdf.add_page()
pdf.chapter_title("9", "Fixing Corrupted Drivers")
pdf.body(
    "Driver corruption can occur due to incomplete installs, failed Windows Updates, ransomware, "
    "or hardware failure. Symptoms include: device not recognized, yellow bang in Device "
    "Manager, BSODs (Blue Screens), or Windows reporting 'This device cannot start (Code 10)'."
)

pdf.section_title("Step 1 - Identify the Corrupt Driver")
pdf.cmd_block([
    "# Check System Event Log for driver errors",
    "Get-WinEvent -LogName System | Where-Object {$_.Id -in 219,7026,7034,7023} | Format-List",
    "",
    "# Check SetupAPI log for installation errors",
    "notepad C:\\Windows\\INF\\setupapi.dev.log",
])

pdf.section_title("Step 2 - Remove the Corrupt Driver Package")
pdf.cmd_block([
    "# List all third-party (OEM) drivers",
    "pnputil /enum-drivers",
    "",
    "# Delete the specific corrupt driver package by published name",
    "pnputil /delete-driver oem42.inf /uninstall /force",
    "",
    "# /uninstall  = also removes it from any devices currently using it",
    "# /force      = remove even if currently in use (reboot required)",
])

pdf.section_title("Step 3 - Repair Built-in Windows Drivers with SFC and DISM")
pdf.cmd_block([
    "# Run System File Checker to restore corrupted system files (including in-box drivers)",
    "sfc /scannow",
    "",
    "# If SFC reports it could not repair, run DISM to repair the Windows image first",
    "dism /Online /Cleanup-Image /RestoreHealth",
    "",
    "# Then re-run SFC",
    "sfc /scannow",
])

pdf.section_title("Step 4 - Reinstall the Driver")
pdf.body(
    "After removing the corrupt driver, follow the injection steps in Section 5 or 6 to "
    "reinstall the correct, clean version of the driver."
)

pdf.section_title("Step 5 - Verify DriverStore Integrity")
pdf.cmd_block([
    "# List all packages in DriverStore",
    "pnputil /enum-drivers /class All",
    "",
    "# If a package folder is orphaned (exists in FileRepository but not in pnputil list),",
    "# use Driver Store Explorer (RAPR.exe) to clean it up safely.",
])

pdf.note_box("WARNING",
    "Do NOT manually delete folders from C:\\Windows\\System32\\DriverStore\\FileRepository\\. "
    "This will corrupt the DriverStore and may require a Windows repair install to fix. "
    "Always use PnPUtil or DISM to remove packages.")

# SECTION 10
pdf.add_page()
pdf.chapter_title("10", "Fixing Handshake / Code 10 / Code 43 Errors")
pdf.body(
    "Handshake errors occur when the device and Windows cannot agree on a communication "
    "protocol, power state, or initialization sequence. Common error codes:\n\n"
    "  Code 10  - 'This device cannot start.'  Driver failed to initialize.\n"
    "  Code 43  - 'Windows has stopped this device because it has reported problems.' "
    "The driver reported a fatal error to Windows.\n"
    "  Code 28  - 'The drivers for this device are not installed.'\n"
    "  Code 52  - 'Windows cannot verify the digital signature for the drivers required.' "
    "Unsigned driver blocked."
)

pdf.section_title("Diagnostic Step 1 - Read the Exact Error")
pdf.cmd_block([
    "# Get the device's problem code and status",
    "Get-PnpDevice | Where-Object {$_.Status -ne 'OK'} | Format-List FriendlyName,Status,Problem",
    "",
    "# Or via WMI for more detail",
    "Get-WmiObject Win32_PnpEntity | Where-Object {$_.ConfigManagerErrorCode -ne 0} |",
    "    Select-Object Name, DeviceID, ConfigManagerErrorCode | Format-List",
])

pdf.section_title("Diagnostic Step 2 - Check Event Viewer")
pdf.numbered([
    "Open Event Viewer (eventvwr.msc).",
    "Navigate to Windows Logs > System.",
    "Filter by Source = 'Microsoft-Windows-DriverFrameworks-UserMode' or Source = 'Service Control Manager'.",
    "Look for events timestamped at the time the device failed to start.",
    "Common Event ID 219 = driver load failure; Event ID 7026 = boot-start or system-start driver failed.",
])

pdf.section_title("Fix A - Re-inject the Driver (most common fix)")
pdf.body("Follow Sections 9 and 5: remove the old driver, then inject a clean copy.")

pdf.section_title("Fix B - Disable Driver Signature Enforcement (for Code 52)")
pdf.numbered([
    "Reboot and enter Advanced Startup Options: hold Shift and click Restart in the Start menu.",
    "Go to Troubleshoot > Advanced Options > Startup Settings > Restart.",
    "Press 7 (or F7) to select 'Disable driver signature enforcement'.",
    "Windows boots once with enforcement off; install the unsigned driver, then permanently "
    "enable Test Mode if needed (bcdedit /set testsigning on).",
])

pdf.section_title("Fix C - Reset the USB / PCIe Controller (for handshake errors)")
pdf.cmd_block([
    "# Disable and re-enable the USB controller in Device Manager via command line",
    "# Find the USB Host Controller instance ID first:",
    "pnputil /enum-devices /class USB /ids",
    "",
    "# Disable, then re-enable (replace ID with your actual instance ID)",
    "pnputil /disable-device \"USB\\VID_8086&PID_1C26\\4&3a8bc9d8&0&0\"",
    "pnputil /enable-device  \"USB\\VID_8086&PID_1C26\\4&3a8bc9d8&0&0\"",
])

pdf.section_title("Fix D - Clear Upper/Lower Filters Causing Handshake Loops")
pdf.body(
    "Corrupt UpperFilters or LowerFilters registry values can intercept communication between "
    "the driver and the hardware, causing handshake failures."
)
pdf.cmd_block([
    "# Find the device class GUID first (Device Manager > Details > Class Guid)",
    "# Example GUID for USB: {36FC9E60-C465-11CF-8056-444553540000}",
    "",
    "# Open Registry and check for bad filter entries:",
    "reg query \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Class\\{36FC9E60-C465-11CF-8056-444553540000}\" /v UpperFilters",
    "reg query \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Class\\{36FC9E60-C465-11CF-8056-444553540000}\" /v LowerFilters",
    "",
    "# Delete them ONLY if they contain entries for drivers that no longer exist:",
    "reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Class\\{GUID}\" /v UpperFilters /f",
])

pdf.section_title("Fix E - Run Windows Hardware Troubleshooter")
pdf.cmd_block([
    "# Launch the automated hardware troubleshooter",
    "msdt.exe /id DeviceDiagnostic",
])

# SECTION 11
pdf.add_page()
pdf.chapter_title("11", "Verifying the Injection Worked")
pdf.section_title("Check via PnPUtil")
pdf.cmd_block([
    "pnputil /enum-drivers",
    "# Confirm your OEM INF appears in the list with a valid Published Name.",
    "",
    "pnputil /enum-devices /ids",
    "# Confirm the target device now shows a Status of 'Started' (not 'Error').",
])

pdf.section_title("Check via Device Manager")
pdf.bullet([
    "Open Device Manager. The device should now appear in its correct category (e.g., Network Adapters, Disk Drives) without a yellow exclamation mark.",
    "Right-click the device > Properties > Driver tab to confirm the Driver Provider, Driver Date, and Driver Version match the INF you injected.",
])

pdf.section_title("Check via PowerShell")
pdf.cmd_block([
    "# Confirm device is running OK",
    "Get-PnpDevice -FriendlyName '*YourDeviceName*' | Select-Object Status, Problem, InstanceId",
    "",
    "# Status should be 'OK', Problem should be '0' (CM_PROB_NONE).",
])

pdf.section_title("Check Driver Signing Status")
pdf.cmd_block([
    "# Verify the driver file is properly signed",
    "Get-AuthenticodeSignature C:\\Windows\\System32\\drivers\\mydriver.sys | Format-List",
    "# Status should be 'Valid'.",
])

# SECTION 12
pdf.add_page()
pdf.chapter_title("12", "Rollback & Uninstall")
pdf.section_title("Rollback via Device Manager")
pdf.numbered([
    "Open Device Manager > locate the device > right-click > Properties.",
    "Click the Driver tab.",
    "Click Roll Back Driver. (This option is greyed out if no previous version was saved.)",
    "Follow the on-screen prompts and reboot when complete.",
])

pdf.section_title("Rollback via PnPUtil (Remove the Injected Driver)")
pdf.cmd_block([
    "# List drivers to find the Published Name",
    "pnputil /enum-drivers",
    "",
    "# Remove the driver package (staged only, no force)",
    "pnputil /delete-driver oem42.inf",
    "",
    "# Remove and uninstall from any devices using it",
    "pnputil /delete-driver oem42.inf /uninstall",
    "",
    "# If the driver refuses to be removed, add /force (reboot required)",
    "pnputil /delete-driver oem42.inf /uninstall /force",
])

pdf.section_title("System Restore (if you created a restore point beforehand)")
pdf.cmd_block([
    "# Open System Restore GUI",
    "rstrui.exe",
    "",
    "# Or via command line (choose a restore point name/date interactively)",
    "# This rolls back drivers AND system file changes to that point.",
])

# SECTION 13
pdf.add_page()
pdf.chapter_title("13", "Troubleshooting Quick-Reference")
pdf.set_font("Helvetica", "B", 10)
pdf.set_fill_color(30, 30, 60)
pdf.set_text_color(255, 255, 255)
cols = [65, 55, 65]
pdf.cell(cols[0], 8, "Symptom", border=1, fill=True)
pdf.cell(cols[1], 8, "Likely Cause", border=1, fill=True)
pdf.cell(cols[2], 8, "Fix (Section)", border=1, fill=True, ln=True)
pdf.set_text_color(0, 0, 0)
rows = [
    ("Yellow bang, Code 28",       "No driver / INF staged",              "Sections 5 or 6"),
    ("Code 10, device won't start","Corrupt or wrong driver",              "Sections 9, 10 Fix A"),
    ("Code 43, driver error",      "Driver crash / firmware mismatch",     "Section 10 Fix A/B/C"),
    ("Code 52, signature error",   "Unsigned driver blocked",              "Section 10 Fix B"),
    ("'Access Denied' in PnPUtil", "Not running as Admin",                 "Run CMD as Administrator"),
    ("INF not found for hardware", "Wrong INF / wrong arch",               "Section 4 - verify IDs"),
    ("Driver installs but hangs",  "UpperFilters / LowerFilters corrupt",  "Section 10 Fix D"),
    ("DISM /Add-Driver fails",     "INF not compatible with image arch",   "Check INF [Models.NTamd64]"),
    ("SFC finds unrepairable files","DISM image corrupt",                  "Run DISM /RestoreHealth first"),
    ("DriverStore folder orphaned","Incomplete uninstall",                 "Use Driver Store Explorer"),
]
pdf.set_font("Helvetica", "", 9.5)
fill = False
for row in rows:
    pdf.set_fill_color(235, 240, 255) if fill else pdf.set_fill_color(255, 255, 255)
    pdf.cell(cols[0], 7, row[0], border=1, fill=True)
    pdf.cell(cols[1], 7, row[1], border=1, fill=True)
    pdf.cell(cols[2], 7, row[2], border=1, fill=True, ln=True)
    fill = not fill

pdf.ln(8)
pdf.set_font("Helvetica", "B", 11)
pdf.set_text_color(30, 30, 60)
pdf.cell(0, 8, "Useful Log File Locations", ln=True)
pdf.set_text_color(0, 0, 0)
log_locs = [
    ("SetupAPI Device Log", "C:\\Windows\\INF\\setupapi.dev.log"),
    ("SetupAPI App Log",    "C:\\Windows\\INF\\setupapi.app.log"),
    ("CBS (SFC) Log",       "C:\\Windows\\Logs\\CBS\\CBS.log"),
    ("DISM Log",            "C:\\Windows\\Logs\\DISM\\dism.log"),
    ("DriverStore",         "C:\\Windows\\System32\\DriverStore\\FileRepository\\"),
    ("Staged INF files",    "C:\\Windows\\INF\\oem*.inf"),
]
for name, path in log_locs:
    pdf.set_x(18)
    pdf.set_font("Helvetica", "B", 10)
    pdf.cell(52, 6, name + ":")
    pdf.set_font("Courier", "", 9)
    pdf.cell(0, 6, path, ln=True)
pdf.ln(4)

pdf.set_draw_color(30, 30, 60)
pdf.set_line_width(0.5)
pdf.line(14, pdf.get_y(), 196, pdf.get_y())
pdf.ln(4)
pdf.set_font("Helvetica", "I", 9)
pdf.set_text_color(80, 80, 80)
pdf.multi_cell(0, 5.5,
    "This document covers Windows 10 and Windows 11. Commands have been tested on "
    "Windows 10 22H2 and Windows 11 24H2. Always back up your system or create a restore "
    "point before modifying drivers in a production environment."
)

pdf.output(OUTPUT_PATH)
print(f"PDF created: {OUTPUT_PATH}")
