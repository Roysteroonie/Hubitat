/*****************************************************************************************************************
 *  Copyright: David Lomas (codersaur)
 *
 *  Name: GreenWave PowerNode (Single) Advanced
 *
 *  Date: 2017-03-08
 *
 *  Version: 1.01
 *
 *  Source: https://github.com/codconersaur/SmartThings/tree/master/devices/greenwave-powernode-single
 *
 *  Author: David Lomas (codersaur)
 *
 *  Description: An advanced SmartThings device handler for the GreenWave PowerNode (Single socket) Z-Wave power outlet.
 *
 *  For full information, including installation instructions, exmples, and version history, see:
 *   https://github.com/codersaur/SmartThings/tree/master/devices/greenwave-powernode-single
 *
 *  License:
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *   for the specific language governing permissions and limitations under the License.
 *
 *  Updated for HE by @Royski 28/05/19
 *  Added refresh option 27/12/20
 *****************************************************************************************************************/
metadata {
    definition (name: "GreenWave PowerNode (Single) Advanced", namespace: "codersaur", author: "David Lomas", 
	importUrl: "https://raw.githubusercontent.com/Roysteroonie/Hubitat/master/GreenWave%20PowerNode%20(Single)%20Advanced.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
        capability "Energy Meter"
        capability "Power Meter"
        capability "Polling"
        capability "Refresh"

        // Custom Attributes:
        attribute "energyLastReset", "string"   // Last time Accumulated Engergy was reset.
        attribute "fault", "string"             // Indicates if the device has any faults. 'clear' if no active faults.
        attribute "localProtectionMode", "enum", ["unprotected","sequence","noControl"] // Physical protection mode.
        attribute "rfProtectionMode", "enum", ["unprotected","noControl","noResponse"] // Wireless protection mode.
        attribute "logMessage", "string"        // Important log messages.
        attribute "syncPending", "number"       // Number of config items that need to be synced with the physical device.
        attribute "wheelStatus", "enum", ["black","green","blue","red","yellow","violet","orange","aqua","pink","white"]
		attribute "switch", "enum", ["on", "off"]

        // Display Attributes:
        // These are only required because the UI lacks number formatting and strips leading zeros.
        //attribute "dispEnergy", "string"
        //attribute "dispPower", "string"
		

        // Custom Commands:
        command "blink"                     // Causes the Circle LED to blink for ~20 seconds.
        command "reset"                     // Alias for resetEnergy().
        command "resetEnergy"               // Reset accumulated energy figure to 0.
        command "resetFault"                // Reset fault alarm to 'clear'.
        //command "setLocalProtectionMode"    // Set physical protection mode.
        //command "toggleLocalProtectionMode" // Toggle physical protection mode.
        //command "setRfProtectionMode"       // Set wireless protection mode.
        //command "toggleRfProtectionMode"    // Toggle wireless protection mode.
        command "sync"                      // Sync configuration with physical device.
        //command "test"                      // Test function.
		command "ClearStates"				// Clear all device states 

        // Fingerprints:
        fingerprint mfr: "0099", prod: "0002", model: "0002"
        fingerprint type: "1001", mfr: "0099", cc: "20,25,27,32,56,70,71,72,75,85,86,87"
        fingerprint inClusters: "0x20,0x25,0x27,0x32,0x56,0x70,0x71,0x72,0x75,0x85,0x86,0x87"
    }

 
    preferences {

        section { // GENERAL:
            input (
                type: "paragraph",
                element: "paragraph",
                title: "GENERAL:",
                description: "General device handler settings."
            )

            input (
                name: "configSyncAll",
                title: "Force Full Sync:\nAll device settings will be re-sent to the device.",
                type: "boolean",
                defaultValue: false,
                required: false
            )

            input (
                name: "configAutoOffTime",
                title: "Timer Function (Auto-off):\nAutomatically switch off the device after a specified time.\n" +
                "Values:\n0 = Function Disabled\n1-86400 = time in seconds\nDefault Value: 0",
                type: "number",
                range: "0..86400",
                defaultValue: 0,
                required: false
            )

            input (
                name: "configIgnoreCurrentLeakageAlarms",
                title: "Ignore Current Leakage Alarms:\nDo not raise a fault on a current leakage alarm.",
                type: "boolean",
                defaultValue: false,
                required: false
            )

            input (
                name: "configSwitchAllMode",
                title: "ALL ON/ALL OFF Function:\nResponse to SWITCH_ALL_SET commands.",
                type: "enum",
                options: [
                    "0" : "0: All ON not active, All OFF not active",
                    "1" : "1: All ON not active, All OFF active",
                    "2" : "2: All ON active, All OFF not active",
                    "255" : "255: All ON active, All OFF active"],
                defaultValue: "255",
                required: false
            )
            input "autoPoll", "bool", required: false, title: "Enable Auto Poll"
			    if(autoPoll){           
			input "pollInterval", "enum", title: "Auto Poll Interval:", required: false, defaultValue: "2 Minutes", 
				options: ["1 Minute", "2 Minutes", "3 Minutes", "4 Minutes", "5 Minutes"]}
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		    input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true

        }

        generatePrefsParams()

        //generatePrefsAssocGroups() // All Assoc Groups are HubOnly for this device.

    }

}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

/*****************************************************************************************************************
 *  SmartThings System Commands:
 *****************************************************************************************************************/

/**
 *  installed()
 *
 *  Runs when the device is first installed.
 *
 *  Action: Set initial values for internal state, and request MSR/Version reports.
 **/
def installed() {
    log.trace "installed()"

    state.installedAt = now()
    state.energyLastReset = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    state.loggingLevelIDE     = 3
    state.loggingLevelDevice  = 2
    state.useSecurity = false
    state.useCrc16 = true
    state.fwVersion = 4.23 // Will be updated when versionReport is received.
    state.protectLocalTarget = 0
    state.protectRfTarget = 0
    state.autoOffTime = 0

    sendEvent(name: "fault", value: "clear", displayed: false)

    def cmds = []
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: 255) // Set Keep-Alive to 255.
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 2) // Wheel Status
    cmds << zwave.protectionV2.protectionGet()
    cmds << zwave.manufacturerSpecificV2.manufacturerSpecificGet()
    cmds << zwave.versionV1.versionGet()

    sendCommands(cmds)
}

/**
 *  updated()
 *
 *  Runs when the user hits "Done" from Settings page.
 *
 *  Action: Process new settings, sync parameters and association group members with the physical device.
 *
 *  Note: Weirdly, update() seems to be called twice. So execution is aborted if there was a previous execution
 *  within two seconds. See: https://community.smartthings.com/t/updated-being-called-twice/62912
 **/
def updated() {
    if (logEnable) log.debug "updated called"
	if(logEnable){runIn(1800, logsOff)}
    log.info "updated()"
    setAutopoll()

    def cmds = []

    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
        state.updatedLastRanAt = now()

        // Update internal state:
        state.loggingLevelIDE     = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3
        state.loggingLevelDevice  = (settings.configLoggingLevelDevice) ? settings.configLoggingLevelDevice.toInteger(): 2
        state.syncAll             = ("true" == settings.configSyncAll)
        state.autoOffTime         = (settings.configAutoOffTime) ? settings.configAutoOffTime.toInteger() : 0
        state.ignoreCurrentLeakageAlarms = ("true" == settings.configIgnoreCurrentLeakageAlarms)
        state.switchAllModeTarget = (settings.configSwitchAllMode) ? settings.configSwitchAllMode.toInteger() : 255

        // Update Parameter target values:
        getParamsMd().findAll( { !it.readonly && (it.fwVersion <= state.fwVersion) } ).each { // Exclude readonly/newer parameters.
            state."paramTarget${it.id}" = settings."configParam${it.id}"?.toInteger()
        }

        // Update Assoc Group target values:
        getAssocGroupsMd().findAll( { !it.hubOnly } ).each {
            state."assocGroupTarget${it.id}" = parseAssocGroupInput(settings."configAssocGroup${it.id}", it.maxNodes)
        }
        getAssocGroupsMd().findAll( { it.hubOnly } ).each {
            state."assocGroupTarget${it.id}" = [ zwaveHubNodeId ]
        }

        // Sync configuration with phyiscal device:
        sync(state.syncAll)

        // Request device medadata (this just seems the best place to do it):
        cmds << zwave.manufacturerSpecificV2.manufacturerSpecificGet()
        cmds << zwave.versionV1.versionGet()

        return sendCommands(cmds)
    }
    else {
        if (txtEnable) log.info "updated(): Ran within last 2 seconds so aborting."
    }
    if (txtEnable) log.info "updated(): Call autoPoll."
    
}

def setAutopoll(){
    log.info "Setting autoPoll"
if (autoPoll) {
    log.info "autoPolling is on, initialising"
	initialize_poll()
	} else {
	unschedule(pollNodes)
	}
}

void initialize_poll() {
	if (pollInterval == "1 Minute") {
	schedule("0 0/1 * 1/1 * ? *", pollNodes)
	log.info "1 Minute refresh called"
	} else if (pollInterval == "2 Minutes") {
	schedule("0 0/2 * 1/1 * ? *", pollNodes)
	log.info "2 Minute refresh called"
	} else if (pollInterval == "3 Minutes") {
	schedule("0 0/3 * 1/1 * ? *", pollNodes)
	log.info "3 Minute refresh called"
	} else if (pollInterval == "4 Minutes") {
	schedule("0 0/4 * 1/1 * ? *", pollNodes)
	log.info "4 Minute refresh called"
	} else if (pollInterval == "5 Minutes") {
	schedule("0 0/5 * 1/1 * ? *", pollNodes)
	log.info "5 Minute refresh called"
	}
}

/**
 *  parse()
 *
 *  Called when messages from the device are received by the hub. The parse method is responsible for interpreting
 *  those messages and returning event definitions (and command responses).
 *
 *  As this is a Z-wave device, zwave.parse() is used to convert the message into a command. The command is then
 *  passed to zwaveEvent(), which is overloaded for each type of command below.
 *
 *  Note: There is no longer any need to check if description == "updated".
 *
 *  Parameters:
 *   String      description        The raw message from the device.
 **/
def parse(description) {
    if (logEnable) log.debug "parse(): Parsing raw message: ${description}"

    def result = []

    def cmd = zwave.parse(description, getCommandClassVersions())
    if (cmd) {
        result += zwaveEvent(cmd)
    } else {
        if (logEnable) log.debug "parse(): Could not parse raw message: ${description}"
    }
    return result
}

/*****************************************************************************************************************
 *  Z-wave Event Handlers.
 *****************************************************************************************************************/

/**
 *  zwaveEvent( COMMAND_CLASS_BASIC (0x20) : BASIC_REPORT (0x03) )
 *
 *  The Basic Report command is used to advertise the status of the primary functionality of the device.
 *
 *  Action: Raise switch event and log an info message if state has changed.
 *    Schedule autoOff() if an autoOffTime is configured.
 *
 *  cmd attributes:
 *    Short    value
 *      0x00       = Off
 *      0x01..0x63 = 0..100%
 *      0xFE       = Unknown
 *      0xFF       = On
 *
 *  Example: BasicReport(value: 255)
 **/
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Basic Report received: ${cmd}"

    def result = []

    def switchValue = (cmd.value ? "on" : "off")
    def switchEvent = createEvent(name: "switch", value: switchValue)
    if (switchEvent.isStateChange) if (txtEnable) log.info "Switch turned ${switchValue}."
    result << switchEvent

    if ( switchEvent.isStateChange && (switchValue == "on") && (state.autoOffTime > 0) ) {
        if (logEnable) log.debug "Scheduling Auto-off in ${state.autoOffTime} seconds."
        runIn(state.autoOffTime,autoOff)
    }

    return result
}

/**
 *  zwaveEvent( COMMAND_CLASS_APPLICATION_STATUS (0x22) : APPLICATION_BUSY (0x01) )
 *
 *  The Application Busy command used to instruct a node that the node that it is trying to communicate with is
 *  busy and is unable to service the request right now.
 *
 *  Action: Log a warning message.
 *
 *  cmd attributes:
 *    Short  status
 *      0  =  Try again later.
 *      1  =  Try again in Wait Time seconds.
 *      2  =  Request queued, executed later.
 *    Short  waitTime  Number of seconds to wait before retrying.
 *
 *  Example: ApplicationBusy(status: 0, waitTime: 0)
 **/
def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationBusy cmd) {
    if (logEnable) log.warn "zwaveEvent(): Application Busy received: ${cmd}"

    switch(cmd.status) {
        case 0:
        if (logEnable) log.warn "Device is busy. Try again later."
        break
        case 1:
        if (logEnable) log.warn "Device is busy. Retry in ${cmd.waitTime} seconds."
        break
        case 2:
        if (logEnable) log.warn "Device is busy. Request is queued."
        break
    }
}

/**
 *  zwaveEvent( COMMAND_CLASS_APPLICATION_STATUS (0x22) : APPLICATION_REJECTED_REQUEST (0x02) )
 *
 *  The Application Rejected Request command used to instruct a node that a command was rejected by the receiving node.
 *
 *  Action: Log a warning message.
 *
 *  Note: These will be received if rfProtectionMode is 'No Control'.
 *
 *  cmd attributes:
 *    Short  status  Always 0.
 *
 *  Example: ApplicationRejectedRequest(status: 0)
 **/
def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
    //if (logEnable) log.debug "zwaveEvent(): Application Rejected Request received: ${cmd}"
    if (logEnable) log.debug "A command was rejected. Most likely, RF Protection Mode is set to 'No Control'."
}

/**
 *  zwaveEvent( COMMAND_CLASS_SWITCH_BINARY (0x25) : SWITCH_BINARY_REPORT (0x03) )
 *
 *  The Binary Switch Report command  is used to advertise the status of a device with On/Off or Enable/Disable
 *  capability.
 *
 *  Action: Raise switch event and log an info message if state has changed.
 *    Schedule autoOff() if an autoOffTime is configured.
 *
 *  cmd attributes:
 *    Short   value   0xFF for on, 0x00 for off
 *
 *  Example: SwitchBinaryReport(value: 255)
 **/
def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Switch Binary Report received: ${cmd}"

    def result = []

    def switchValue = (cmd.value ? "on" : "off")
    def switchEvent = createEvent(name: "switch", value: switchValue)
    if (switchEvent.isStateChange) log.info "Switch turned ${switchValue}."
    result << switchEvent

    if ( switchEvent.isStateChange && (switchValue == "on") && (state.autoOffTime > 0) ) {
        log.info "Scheduling Auto-off in ${state.autoOffTime} seconds."
        runIn(state.autoOffTime,autoOff)
    }

    return result
}


/**
 *  zwaveEvent( COMMAND_CLASS_SWITCH_ALL (0x27) : SWITCH_ALL_REPORT (0x03) )
 *
 *  The All Switch Report Command is used to report if the device is included or excluded from the all on/all off
 *  functionality.
 *
 *  Action: Cache value, update syncPending, and log an info message.
 *
 *  cmd attributes:
 *    Short    mode
 *      0   = MODE_EXCLUDED_FROM_THE_ALL_ON_ALL_OFF_FUNCTIONALITY
 *      1   = MODE_EXCLUDED_FROM_THE_ALL_ON_FUNCTIONALITY_BUT_NOT_ALL_OFF
 *      2   = MODE_EXCLUDED_FROM_THE_ALL_OFF_FUNCTIONALITY_BUT_NOT_ALL_ON
 *      255 = MODE_INCLUDED_IN_THE_ALL_ON_ALL_OFF_FUNCTIONALITY
 *
 *  Example: SwitchAllReport(mode: 255)
 **/
def zwaveEvent(hubitat.zwave.commands.switchallv1.SwitchAllReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Switch All Report received: ${cmd}"

    state.switchAllModeCache = cmd.mode

    def msg = ""
    switch (cmd.mode) {
            case 0:
                msg = "Device is excluded from the all on/all off functionality."
                break

            case 1:
                msg = "Device is excluded from the all on functionality but not all off."
                break

            case 2:
                msg = "Device is excluded from the all off functionality but not all on."
                break

            default:
                msg = "Device is included in the all on/all off functionality."
                break
    }
    if (txtEnable) log.info "Switch All Mode: ${msg}"

    updateSyncPending()
}

/**
 *  zwaveEvent( COMMAND_CLASS_METER_V3 (0x32) : METER_REPORT_V3 (0x02) )
 *
 *  The Meter Report Command is used to advertise a meter reading.
 *
 *  Action: Raise appropriate type of event (and disp... event) and log an info message.
 *   Plus, request a Switch Binary Report if power report suggests switch state has changed.
 *   (This is necessary because the PowerNode does not report physical switch events reliably).
 *
 *  Note: GreenWave PowerNode supports energy and power reporting only.
 *
 *  cmd attributes:
 *    Integer        deltaTime                   Time in seconds since last report.
 *    Short          meterType                   Specifies the type of metering device.
 *      0x00 = Unknown
 *      0x01 = Electric meter
 *      0x02 = Gas meter
 *      0x03 = Water meter
 *    List<Short>    meterValue                  Meter value as an array of bytes.
 *    Double         scaledMeterValue            Meter value as a double.
 *    List<Short>    previousMeterValue          Previous meter value as an array of bytes.
 *    Double         scaledPreviousMeterValue    Previous meter value as a double.
 *    Short          size                        The size of the array for the meterValue and previousMeterValue.
 *    Short          scale                       Indicates what unit the sensor uses (dependent on meterType).
 *    Short          precision                   The decimal precision of the values.
 *    Short          rateType                    Specifies if it is import or export values to be read.
 *      0x01 = Import (consumed)
 *      0x02 = Export (produced)
 *    Boolean        scale2                      ???
 **/
def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Meter Report received: ${cmd}"

    def result = []

    switch (cmd.meterType) {
        case 1:  // Electric meter:
            switch (cmd.scale) {
                case 0:  // Accumulated Energy (kWh):
                    result << createEvent(name: "energy", value: (cmd.scaledMeterValue) + " kWh", unit: "kWh", displayed: true)
                    //result << createEvent(name: "dispEnergy", value: String.format("%.2f",cmd.scaledMeterValue as BigDecimal) + " kWh", unit: "kWh", displayed: true)
                    if (txtEnable) log.info "New meter reading: Accumulated Energy: ${cmd.scaledMeterValue} kWh"
                    break

                case 1:  // Accumulated Energy (kVAh):
                    result << createEvent(name: "energy", value: (cmd.scaledMeterValue) + " kVAh", unit: "kVAh", displayed: true)
                    //result << createEvent(name: "dispEnergy", value: String.format("%.2f",cmd.scaledMeterValue as BigDecimal) + " kVAh", unit: "kVAh", displayed: true)
                    if (txtEnable) log.info "New meter reading: Accumulated Energy: ${cmd.scaledMeterValue} kVAh"
                    break

                case 2:  // Instantaneous Power (Watts):
                    result << createEvent(name: "power", value: (cmd.scaledMeterValue) + " W", unit: "W", displayed: true)
                    //result << createEvent(name: "dispPower", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " W", unit: "W", displayed: true)
                    if (txtEnable) log.info "New meter reading: Instantaneous Power: ${cmd.scaledMeterValue} W"

                    // Request Switch Binary Report if power suggests switch state has changed:
                    def sw = (cmd.scaledMeterValue) ? "on" : "off"
                    if ( device.latestValue("switch") != sw) { result << prepCommands([zwave.switchBinaryV1.switchBinaryGet()]) }
                    break

                case 3:  // Accumulated Pulse Count:
                    result << createEvent(name: "pulseCount", value: cmd.scaledMeterValue, unit: "", displayed: true)
                    if (txtEnable) log.info "New meter reading: Accumulated Electricity Pulse Count: ${cmd.scaledMeterValue}"
                    break

                case 4:  // Instantaneous Voltage (Volts):
                    result << createEvent(name: "voltage", value: (cmd.scaledMeterValue) + " V", unit: "V", displayed: true)
                   // result << createEvent(name: "dispVoltage", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " V", displayed: false)
                    if (txtEnable) log.info "New meter reading: Instantaneous Voltage: ${cmd.scaledMeterValue} V"
                    break

                 case 5:  // Instantaneous Current (Amps):
                    result << createEvent(name: "current", value: cmd.scaledMeterValue, unit: "A", displayed: true)
                    //result << createEvent(name: "dispCurrent", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " V", displayed: false)
                   if (txtEnable) log.info "New meter reading: Instantaneous Current: ${cmd.scaledMeterValue} A"
                    break

                 case 6:  // Instantaneous Power Factor:
                    result << createEvent(name: "powerFactor", value: cmd.scaledMeterValue, unit: "", displayed: true)
                    //result << createEvent(name: "dispPowerFactor", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal), displayed: false)
                    if (txtEnable) log.info "New meter reading: Instantaneous Power Factor: ${cmd.scaledMeterValue}"
                    break

                default:
                    if (logEnable) log.debug "zwaveEvent(): Meter Report with unhandled scale: ${cmd}"
                    break
            }
            break

        default:
            if (logEnable) log.debug "zwaveEvent(): Meter Report with unhandled meterType: ${cmd}"
            break
    }

    return result
}

def ClearStates() {
	log.warn ("ClearStates(): Clearing device states")
	state.clear()
}


/**
 *  zwaveEvent( COMMAND_CLASS_CRC16_ENCAP (0x56) : CRC_16_ENCAP (0x01) )
 *
 *  The CRC-16 Encapsulation Command Class is used to encapsulate a command with an additional CRC-16 checksum
 *  to ensure integrity of the payload. The purpose for this command class is to ensure a higher integrity level
 *  of payloads carrying important data.
 *
 *  Action: Extract the encapsulated command and pass to zwaveEvent().
 *
 *  Note: Validation of the checksum is not necessary as this is performed by the hub.
 *
 *  cmd attributes:
 *    Integer      checksum      Checksum.
 *    Short        command       Command identifier of the embedded command.
 *    Short        commandClass  Command Class identifier of the embedded command.
 *    List<Short>  data          Embedded command data.
 *
 *  Example: Crc16Encap(checksum: 125, command: 2, commandClass: 50, data: [33, 68, 0, 0, 0, 194, 0, 0, 77])
 **/
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
    if (logEnable) log.debug "zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}"

    def versions = getCommandClassVersions()
    def version = versions[cmd.commandClass as Integer]
    def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
    def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
    // TO DO: It should be possible to replace the lines above with this line soon...
    //def encapsulatedCommand = cmd.encapsulatedCommand(getCommandClassVersions())
    if (!encapsulatedCommand) {
        if (txtEnable) log.error "zwaveEvent(): Could not extract command from ${cmd}"
    } else {
        return zwaveEvent(encapsulatedCommand)
    }
}

/**
 *  zwaveEvent( COMMAND_CLASS_CONFIGURATION (0x70) : CONFIGURATION_REPORT (0x03) )
 *
 *  The Configuration Report Command is used to advertise the actual value of the advertised parameter.
 *
 *  Action: Store the value in the parameter cache, update syncPending, and log an info message.
 *   Update wheelStatus if parameter #2.
 *
 *  Note: Ideally, we want to update the corresponding preference value shown on the Settings GUI, however this
 *  is not possible due to security restrictions in the SmartThings platform.
 *
 *  cmd attributes:
 *    List<Short>  configurationValue  Value of parameter (byte array).
 *    Short        parameterNumber     Parameter ID.
 *    Short        size                Size of parameter's value (bytes).
 *
 *  Example: ConfigurationReport(configurationValue: [10], parameterNumber: 0, reserved11: 0,
 *            scaledConfigurationValue: 10, size: 1)
 **/
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Configuration Report received: ${cmd}"

    def result = []

    def paramMd = getParamsMd().find( { it.id == cmd.parameterNumber })
    // Some values are treated as unsigned and some as signed, so we convert accordingly:
    def paramValue = (paramMd?.isSigned) ? cmd.scaledConfigurationValue : byteArrayToUInt(cmd.configurationValue)
    def signInfo = (paramMd?.isSigned) ? "SIGNED" : "UNSIGNED"

    state."paramCache${cmd.parameterNumber}" = paramValue
    if (txtEnable) log.info "Parameter #${cmd.parameterNumber} [${paramMd?.name}] has value: ${paramValue} [${signInfo}]"
    updateSyncPending()

    // Update wheelStatus if parameter #2:
    if (cmd.parameterNumber == 2) {
        def wheelStatus = getWheelColours()[paramValue]
        def wheelEvent = createEvent(name: "wheelStatus", value: wheelStatus)
        if (wheelEvent.isStateChange) if (txtEnable) log.info "Room Colour Wheel changed to ${wheelStatus}."
        result << wheelEvent
    }

    return result
}

/**
 *  zwaveEvent( COMMAND_CLASS_ALARM (0x71) : ALARM_REPORT (0x05) )
 *
 *  The Alarm Report command used to report the type and level of an alarm.
 *
 *  Action: Raise a fault event and log a warning message.
 *
 *  Note: The GreenWave PowerNode seems especially eager to raise current leakage alarms, so there is an
 *  optional setting to ignore them.
 *
 *  cmd attributes:
 *    Short  alarmLevel  Application specific
 *    Short  alarmType   Application specific
 *
 *  Example: AlarmReport(alarmLevel: 1, alarmType: 1)
 **/
def zwaveEvent(hubitat.zwave.commands.alarmv1.AlarmReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Alarm Report received: ${cmd}"

    def result = []

    switch(cmd.alarmType) {
        case 1: // Current Leakage:
            if (!state.ignoreCurrentLeakageAlarms) { result << createEvent(name: "fault", value: "currentLeakage",
              descriptionText: "Current Leakage detected!", displayed: true) }
            if (logEnable) log.debug "Current Leakage detected!"
            break

        // TO DO: Check other alarm codes.

        default: // Over-current:
            result << createEvent(name: "fault", value: "current", descriptionText: "Over-current detected!", displayed: true)
            if (logEnable) log.debug "Over-current detected!"
            break
    }

    return result
}

/**
 *  zwaveEvent( COMMAND_CLASS_MANUFACTURER_SPECIFIC_V2 (0x72) : MANUFACTURER_SPECIFIC_REPORT_V2 (0x05) )
 *
 *  Manufacturer-Specific Reports are used to advertise manufacturer-specific information, such as product number
 *  and serial number.
 *
 *  Action: Publish values as device 'data'. Log a warn message if manufacturerId and/or productId do not match.
 *
 *  Example: ManufacturerSpecificReport(manufacturerId: 153, manufacturerName: GreenWave Reality Inc.,
 *   productId: 2, productTypeId: 2)
 **/
def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Manufacturer-Specific Report received: ${cmd}"

    // Display as hex strings:
    def manufacturerIdDisp = String.format("%04X",cmd.manufacturerId)
    def productIdDisp = String.format("%04X",cmd.productId)
    def productTypeIdDisp = String.format("%04X",cmd.productTypeId)

    if (txtEnable) log.info "Manufacturer-Specific Report: Manufacturer ID: ${manufacturerIdDisp}, Manufacturer Name: ${cmd.manufacturerName}" +
    ", Product Type ID: ${productTypeIdDisp}, Product ID: ${productIdDisp}"

    if ( 153 != cmd.manufacturerId) if (logEnable) log.debug "Device Manufacturer is not GreenWave Reality. " +
      "Using this device handler with a different device may damage your device!"
    if ( 2 != cmd.productId) if (logEnable) log.debug "Product ID does not match GreenWave PowerNode (Single). " +
      "Using this device handler with a different device may damage you device!"

    updateDataValue("manufacturerName",cmd.manufacturerName)
    updateDataValue("manufacturerId",manufacturerIdDisp)
    updateDataValue("productId",productIdDisp)
    updateDataValue("productTypeId",productTypeIdDisp)
}

/**
 *  zwaveEvent( COMMAND_CLASS_PROTECTION_V2 (0x75) : PROTECTION_REPORT_V2 (0x03) )
 *
 *  The Protection Report is used to report the protection state of a device.
 *  I.e. measures to prevent unintentional control (e.g. by a child).
 *
 *  Action: Cache values, update syncPending, and log an info message.
 *
 *  cmd attributes:
 *    Short  localProtectionState  Local protection state (i.e. physical switches/buttons)
 *    Short  rfProtectionState     RF protection state.
 *
 *  Example: ProtectionReport(localProtectionState: 0, reserved01: 0, reserved11: 0, rfProtectionState: 0)
 **/
def zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Protection Report received: ${cmd}"

    def result = []

    state.protectLocalCache = cmd.localProtectionState
    state.protectRfCache = cmd.rfProtectionState

    def lpStates = ["unprotected","sequence","noControl"]
    def lpValue = lpStates[cmd.localProtectionState]
    def lpEvent = createEvent(name: "localProtectionMode", value: lpValue)
    if (lpEvent.isStateChange) if (txtEnable) log.info "Local Protection set to ${lpValue}."
    result << lpEvent

    def rfpStates = ["unprotected","noControl","noResponse"]
    def rfpValue = rfpStates[cmd.rfProtectionState]
    def rfpEvent = createEvent(name: "rfProtectionMode", value: rfpValue)
    if (rfpEvent.isStateChange) if (txtEnable) log.info "RF Protection set to ${rfpValue}."
    result << rfpEvent

    if (txtEnable) log.info "Protection Report: Local Protection: ${lpValue}, RF Protection: ${rfpValue}"
    updateSyncPending()

    return result
}

/**
 *  zwaveEvent( COMMAND_CLASS_ASSOCIATION_V2 (0x85) : ASSOCIATION_REPORT_V2 (0x03) )
 *
 *  The Association Report command is used to advertise the current destination nodes of a given association group.
 *
 *  Action: Cache value and log info message only.
 *
 *  Note: Ideally, we want to update the corresponding preference value shown on the Settings GUI, however this
 *  is not possible due to security restrictions in the SmartThings platform.
 *
 *  Example: AssociationReport(groupingIdentifier: 1, maxNodesSupported: 1, nodeId: [1], reportsToFollow: 0)
 **/
def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    log.debug "zwaveEvent(): Association Report received: ${cmd}"

    state."assocGroupCache${cmd.groupingIdentifier}" = cmd.nodeId

    // Display to user in hex format (same as IDE):
    def hexArray  = []
    cmd.nodeId.each { hexArray.add(String.format("%02X", it)) };
    def assocGroupMd = getAssocGroupsMd().find( { it.id == cmd.groupingIdentifier })
    if (txtEnable) log.info "Association Group ${cmd.groupingIdentifier} [${assocGroupMd?.name}] contains nodes: ${hexArray} (hexadecimal format)"

    updateSyncPending()
}

/**
 *  zwaveEvent( COMMAND_CLASS_VERSION (0x86) : VERSION_REPORT (0x12) )
 *
 *  The Version Report Command is used to advertise the library type, protocol version, and application version.
 *
 *  Action: Publish values as device 'data' and log an info message.
 *          Store fwVersion as state.fwVersion.
 *
 *  cmd attributes:
 *    Short  applicationSubVersion
 *    Short  applicationVersion
 *    Short  zWaveLibraryType
 *    Short  zWaveProtocolSubVersion
 *    Short  zWaveProtocolVersion
 *
 *  Example: VersionReport(applicationSubVersion: 4, applicationVersion: 3, zWaveLibraryType: 3,
 *   zWaveProtocolSubVersion: 5, zWaveProtocolVersion: 4)
 **/
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Version Report received: ${cmd}"

    def zWaveLibraryTypeDisp  = String.format("%02X",cmd.zWaveLibraryType)
    def zWaveLibraryTypeDesc  = ""
    switch(cmd.zWaveLibraryType) {
        case 1:
            zWaveLibraryTypeDesc = "Static Controller"
            break

        case 2:
            zWaveLibraryTypeDesc = "Controller"
            break

        case 3:
            zWaveLibraryTypeDesc = "Enhanced Slave"
            break

        case 4:
            zWaveLibraryTypeDesc = "Slave"
            break

        case 5:
            zWaveLibraryTypeDesc = "Installer"
            break

        case 6:
            zWaveLibraryTypeDesc = "Routing Slave"
            break

        case 7:
            zWaveLibraryTypeDesc = "Bridge Controller"
            break

        case 8:
            zWaveLibraryTypeDesc = "Device Under Test (DUT)"
            break

        case 0x0A:
            zWaveLibraryTypeDesc = "AV Remote"
            break

        case 0x0B:
            zWaveLibraryTypeDesc = "AV Device"
            break

        default:
            zWaveLibraryTypeDesc = "N/A"
    }

    def applicationVersionDisp = String.format("%d.%02d",cmd.applicationVersion,cmd.applicationSubVersion)
    def zWaveProtocolVersionDisp = String.format("%d.%02d",cmd.zWaveProtocolVersion,cmd.zWaveProtocolSubVersion)

    state.fwVersion = new BigDecimal(applicationVersionDisp)

    if (txtEnable) log.info "Version Report: Application Version: ${applicationVersionDisp}, " +
           "Z-Wave Protocol Version: ${zWaveProtocolVersionDisp}, " +
           "Z-Wave Library Type: ${zWaveLibraryTypeDisp} (${zWaveLibraryTypeDesc})"

    updateDataValue("applicationVersion","${cmd.applicationVersion}")
    updateDataValue("applicationSubVersion","${cmd.applicationSubVersion}")
    updateDataValue("zWaveLibraryType","${zWaveLibraryTypeDisp}")
    updateDataValue("zWaveProtocolVersion","${cmd.zWaveProtocolVersion}")
    updateDataValue("zWaveProtocolSubVersion","${cmd.zWaveProtocolSubVersion}")
}

/**
 *  zwaveEvent( COMMAND_CLASS_INDICATOR (0x87) : INDICATOR_REPORT (0x03) )
 *
 *  The Indicator Report command is used to advertise the state of an indicator.
 *
 *  Action: Do nothing. It doesn't tell us anything useful.
 *
 *  cmd attributes:
 *    Short value  Indicator status.
 *      0x00       = Off/Disabled
 *      0x01..0x63 = Indicator Range.
 *      0xFF       = On/Enabled.
 *
 *  Example: IndicatorReport(value: 0)
 **/
def zwaveEvent(hubitat.zwave.commands.indicatorv1.IndicatorReport cmd) {
    if (logEnable) log.debug "zwaveEvent(): Indicator Report received: ${cmd}"
}

/**
 *  zwaveEvent( DEFAULT CATCHALL )
 *
 *  Called for all commands that aren't handled above.
 **/
def zwaveEvent(hubitat.zwave.Command cmd) {
    if (txtEnable) log.error "zwaveEvent(): No handler for command: ${cmd}"
}

/*****************************************************************************************************************
 *  Capability-related Commands:
 *****************************************************************************************************************/

/**
 *  on()                        [Capability: Switch]
 *
 *  Turn the switch on.
 **/
def on() {
    if (txtEnable) log.info "on(): Turning switch on."
        sendCommands([
        zwave.basicV1.basicSet(value: 0xFF).format(),
        zwave.switchBinaryV1.switchBinaryGet().format(),
        "delay 3000",
        zwave.meterV2.meterGet(scale: 2).format()
    ])
}

/**
 *  off()                       [Capability: Switch]
 *
 *  Turn the switch off.
 **/
def off() {
    if (txtEnable) log.info "off(): Turning switch off."
    sendCommands([
        zwave.basicV1.basicSet(value: 0x00).format(),
        zwave.switchBinaryV1.switchBinaryGet().format(),
        "delay 3000",
        zwave.meterV2.meterGet(scale: 2).format()
    ])
}

/**
 *  poll()                      [Capability: Polling]
 *
 *  Calls refresh().
 **/
def poll() {
    if (logEnable) log.debug "poll()"
    refresh()
}

def pollNodes() {
    log.debug "pollNodes called"
    refresh()
}

/**
 *  refresh()                   [Capability: Refresh]
 *
 *  Action: Request switchBinary, energy, and power reports. Plus, get wheel status.
 *  Trigger a sync too.
 **/
def refresh() {
    log.debug "refresh()"
    sendCommands([
        zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format(),
        zwave.configurationV1.configurationGet(parameterNumber: 2) // Wheel Status
    ])
    //sync()
}

/*****************************************************************************************************************
 *  Custom Commands:
 *****************************************************************************************************************/

/**
 *  blink()
 *
 *  Causes the Circle LED to blink for ~20 seconds.
 **/
def blink() {
    if (txtEnable) log.info "blink(): Blinking Circle LED"
    sendCommands([zwave.indicatorV1.indicatorSet(value: 255)])
}

/**
 *  autoOff()
 *
 *  Calls off(), but with additional log message.
 **/
def autoOff() {
    if (txtEnable) log.info "autoOff(): Automatically turning off the device."
    off()
}

/**
 *  reset()
 *
 *  Alias for resetEnergy().
 *
 *  Note: this used to be part of the official 'Energy Meter' capability, but isn't anymore.
 **/
def reset() {
    if (logEnable) log.debug "reset()"
    resetEnergy()
}

/**
 *  resetEnergy()
 *
 *  Reset the Accumulated Energy figure held in the device.
 **/
def resetEnergy() {
    if (txtEnable) log.info "resetEnergy(): Resetting Accumulated Energy"

    state.energyLastReset = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    sendEvent(name: "energyLastReset", value: state.energyLastReset, descriptionText: "Accumulated Energy Reset")

    sendCommands([
        zwave.meterV3.meterReset(),
        zwave.meterV3.meterGet(scale: 0)
    ],400)
}

/**
 *  resetFault()
 *
 *  Reset fault alarm to 'clear'.
 **/
def resetFault() {
    if (txtEnable) log.info "resetFault(): Resetting fault alarm."
    sendEvent(name: "fault", value: "clear", descriptionText: "Fault alarm cleared", displayed: true)
}

/**
 *  setLocalProtectionMode(localProtectionMode)
 *
 *  Set local (physical) protection mode.
 *
 *  Note: GreenWave PowerNode supports "unprotected" and "noControl" modes only.
 *
 *  localProtectionMode values:
 *   "unprotected"  Physical switches are operational.
 *   "sequence"     Special sequence required to operate.
 *   "noControl"    Physical switches are disabled.
 **/
def setLocalProtectionMode(localProtectionMode) {
    if (logEnable) log.debug "setLocalProtectionMode(${localProtectionMode})"

    switch(localProtectionMode.toLowerCase()) {
        case "unprotected":
            state.protectLocalTarget = 0
            break
        case "sequence":
            if (logEnable) log.debug "setLocalProtectionMode(): Protection by sequence is not supported by this device."
            state.protectLocalTarget = 2
            break
        case "nocontrol":
            state.protectLocalTarget = 2
            break
        default:
            if (logEnable) log.debug "setLocalProtectionMode(): Unknown protection mode: ${localProtectionMode}."
    }
    //sync()
}

/**
 *  toggleLocalProtectionMode()
 *
 *  Toggle local (physical) protection mode between "unprotected" and "noControl" modes.
 **/
def toggleLocalProtectionMode() {
    if (logEnable) log.debug "toggleLocalProtectionMode()"

    if (device.latestValue("localProtectionMode") != "unprotected") {
        setLocalProtectionMode("unprotected")
    }
    else {
        setLocalProtectionMode("noControl")
    }
}

/**
 *  setRfProtectionMode(rfProtectionMode)
 *
 *  Set RF (wireless) protection mode.
 *
 *  Note: GreenWave PowerNode supports "unprotected" and "noControl" modes only.
 *
 *  rfProtectionMode values:
 *   "unprotected"   Device responds to wireless commands.
 *   "noControl"     Device ignores wireless commands (sends ApplicationRejectedRequest).
 *   "noResponse"    Device ignores wireless commands.
 **/
def setRfProtectionMode(rfProtectionMode) {
    if (logEnable) log.debug "setRfProtectionMode(${rfProtectionMode})"

    switch(rfProtectionMode.toLowerCase()) {
        case "unprotected":
            state.protectRfTarget = 0
            break
        case "nocontrol":
            state.protectRfTarget = 1
            break
        case "noresponse":
            if (logEnable) log.debug "setRfProtectionMode(): NoResponse mode is not supported by this device."
            state.protectRfTarget = 1
            break
        default:
            if (logEnable) log.debug "setRfProtectionMode(): Unknown protection mode: ${rfProtectionMode}."
    }
    //sync()
}

/**
 *  toggleRfProtectionMode()
 *
 *  Toggle RF (wireless) protection mode between "unprotected" and "noControl" modes.
 **/
def toggleRfProtectionMode() {
    if (logEnable) log.debug "toggleRfProtectionMode()"

    if (device.latestValue("rfProtectionMode") != "unprotected") {
        setRfProtectionMode("unprotected")
    }
    else {
        setRfProtectionMode("noControl")
    }
}


/*****************************************************************************************************************
 *  Private Helper Functions:
 *****************************************************************************************************************/

/**
 *  encapCommand(cmd)
 *
 *  Applies security or CRC16 encapsulation to a command as needed.
 *  Returns a hubitat.zwave.Command.
 **/
private encapCommand(hubitat.zwave.Command cmd) {
    if (state.useSecurity) {
        return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd)
    }
    else if (state.useCrc16) {
        return zwave.crc16EncapV1.crc16Encap().encapsulate(cmd)
    }
    else {
        return cmd
    }
}

/**
 *  prepCommands(cmds, delay=200)
 *
 *  Converts a list of commands (and delays) into a HubMultiAction object, suitable for returning via parse().
 *  Uses encapCommand() to apply security or CRC16 encapsulation as needed.
 **/
private prepCommands(cmds, delay=200) {
    return response(delayBetween(cmds.collect{ (it instanceof hubitat.zwave.Command ) ? encapCommand(it).format() : it },delay))
}

/**
 *  sendCommands(cmds, delay=200)
 *
 *  Sends a list of commands directly to the device using sendHubCommand.
 *  Uses encapCommand() to apply security or CRC16 encapsulation as needed.
 **/
private sendCommands(cmds, delay=200) {
///    //sendHubCommand( cmds.collect{ (it instanceof hubitat.zwave.Command ) ? response(encapCommand(it)) : response(it) }, delay)
///	delayBetween( cmds.collect{ (it instanceof hubitat.zwave.Command ) ? response(encapCommand(it)) : response(it) }, delay)
///}

def strCmds = cmds.collect{ (it instanceof hubitat.zwave.Command ) ? encapCommand(it).format() : it }
if (logEnable) log.debug "(Chuck) ${strCmds}"
def delayCmds = delayBetween(strCmds, delay)
if (logEnable) log.debug "(Chuck) ${delayCmds}"
sendHubCommand(new hubitat.device.HubMultiAction(delayCmds, hubitat.device.Protocol.ZWAVE))
}


/**
 *  sync()
 *
 *  Manages synchronisation of parameters, association groups, etc. with the physical device.
 *  The syncPending attribute advertises remaining number of sync operations.
 *
 *  Does not return a list of commands, it sends them immediately using sendCommands(), which means sync() can be
 *  triggered by schedule().
 *
 *  Parameters:
 *   forceAll    Force all items to be synced, otherwise only changed items will be synced.
 **/
private sync(forceAll = false) {
    if (txtEnable) log.info "sync(): Syncing configuration with the physical device."

    def cmds = []
    def syncPending = 0

    if (forceAll) { // Clear all cached values.
        getParamsMd().findAll( {!it.readonly} ).each { state."paramCache${it.id}" = null }
        getAssocGroupsMd().each { state."assocGroupCache${it.id}" = null }
        state.protectLocalCache = null
        state.protectRfCache = null
        state.switchAllModeCache = null
    }

    getParamsMd().findAll( { !it.readonly && (it.fwVersion <= state.fwVersion) } ).each { // Exclude readonly/newer parameters.
        if ( (state."paramTarget${it.id}" != null) && (state."paramCache${it.id}" != state."paramTarget${it.id}") ) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: it.id, size: it.size, scaledConfigurationValue: state."paramTarget${it.id}".toInteger())
            cmds << zwave.configurationV1.configurationGet(parameterNumber: it.id)
            if (txtEnable) log.info "sync(): Syncing parameter #${it.id} [${it.name}]: New Value: " + state."paramTarget${it.id}"
            syncPending++
            }
    }

    getAssocGroupsMd().each {
        def cachedNodes = state."assocGroupCache${it.id}"
        def targetNodes = state."assocGroupTarget${it.id}"

        if ( cachedNodes != targetNodes ) {
            // Display to user in hex format (same as IDE):
            def targetNodesHex  = []
            targetNodes.each { targetNodesHex.add(String.format("%02X", it)) }
            if (txtEnable) log.info "sync(): Syncing Association Group #${it.id} [${it.name}]: Destinations: ${targetNodesHex}"
            if (it.multiChannel) {
                cmds << zwave.multiChannelAssociationV2.multiChannelAssociationRemove(groupingIdentifier: it.id, nodeId: []) // Remove All
                cmds << zwave.multiChannelAssociationV2.multiChannelAssociationSet(groupingIdentifier: it.id, nodeId: targetNodes)
                cmds << zwave.multiChannelAssociationV2.multiChannelAssociationGet(groupingIdentifier: it.id)
            }
            else {
                cmds << zwave.associationV2.associationRemove(groupingIdentifier: it.id, nodeId: []) // Remove All
                cmds << zwave.associationV2.associationSet(groupingIdentifier: it.id, nodeId:[zwaveHubNodeId])
                cmds << zwave.associationV2.associationGet(groupingIdentifier: it.id)
            }
            syncPending++
        }
    }

    if ( (state.protectLocalTarget != null) && (state.protectRfTarget != null)
      && ( (state.protectLocalCache != state.protectLocalTarget) || (state.protectRfCache != state.protectRfTarget) ) ) {

        if (txtEnable) log.info "sync(): Syncing Protection State: Local Protection: ${state.protectLocalTarget}, RF Protection: ${state.protectRfTarget}"
        cmds << zwave.protectionV2.protectionSet(localProtectionState : state.protectLocalTarget, rfProtectionState: state.protectRfTarget)
        cmds << zwave.protectionV2.protectionGet()
        syncPending++
    }

    if ( (state.switchAllModeTarget != null) && (state.switchAllModeCache != state.switchAllModeTarget) ) {
        if (txtEnable) log.info "sync(): Syncing SwitchAll Mode: ${state.switchAllModeTarget}"
        cmds << zwave.switchAllV1.switchAllSet(mode: state.switchAllModeTarget)
        cmds << zwave.switchAllV1.switchAllGet()
        syncPending++
    }

    sendEvent(name: "syncPending", value: syncPending, displayed: false)
    sendCommands(cmds,800)
}

/**
 *  updateSyncPending()
 *
 *  Updates syncPending attribute, which advertises remaining number of sync operations.
 **/
private updateSyncPending() {

    def syncPending = 0

    getParamsMd().findAll( { !it.readonly && (it.fwVersion <= state.fwVersion) } ).each { // Exclude readonly/newer parameters.
        if ( (state."paramTarget${it.id}" != null) && (state."paramCache${it.id}" != state."paramTarget${it.id}") ) {
            syncPending++
        }
    }

    getAssocGroupsMd().each {
        def cachedNodes = state."assocGroupCache${it.id}"
        def targetNodes = state."assocGroupTarget${it.id}"

        if ( cachedNodes != targetNodes ) {
            syncPending++
        }
    }

    if ( (state.protectLocalCache == null) || (state.protectRfCache == null) ||
         (state.protectLocalCache != state.protectLocalTarget) || (state.protectRfCache != state.protectRfTarget) ) {
        syncPending++
    }

    if ( (state.switchAllModeTarget != null) && (state.switchAllModeCache != state.switchAllModeTarget) ) {
        syncPending++
    }

    if (logEnable) log.debug "updateSyncPending(): syncPending: ${syncPending}"
    if ((syncPending == 0) && (device.latestValue("syncPending") > 0)) if (txtEnable) log.info "Sync Complete."
    sendEvent(name: "syncPending", value: syncPending, displayed: false)
}

/**
 *  generatePrefsParams()
 *
 *  Generates preferences (settings) for device parameters.
 **/
private generatePrefsParams() {
        section {
            input (
                type: "paragraph",
                element: "paragraph",
                title: "DEVICE PARAMETERS:",
                description: "Device parameters are used to customise the physical device. " +
                             "Refer to the product documentation for a full description of each parameter."
            )

    getParamsMd().findAll( {!it.readonly} ).each { // Exclude readonly parameters.

        def lb = (it.description.length() > 0) ? "\n" : ""

        switch(it.type) {
            case "number":
            input (
                name: "configParam${it.id}",
                title: "#${it.id}: ${it.name}: \n" + it.description + lb +"Default Value: ${it.defaultValue}",
                type: it.type,
                range: it.range,
                defaultValue: it.defaultValue, // iPhone users can uncomment these lines!
                required: it.required
            )
            break

            case "enum":
            input (
                name: "configParam${it.id}",
                title: "#${it.id}: ${it.name}: \n" + it.description + lb + "Default Value: ${it.defaultValue}",
                type: it.type,
                options: it.options,
                defaultValue: it.defaultValue, // iPhone users can uncomment these lines!
                required: it.required
            )
            break
        }
    }
        } // section
}

/**
 *  generatePrefsAssocGroups()
 *
 *  Generates preferences (settings) for Association Groups.
 *  Excludes any groups that are hubOnly.
 **/
private generatePrefsAssocGroups() {
        section {
            input (
                type: "paragraph",
                element: "paragraph",
                title: "ASSOCIATION GROUPS:",
                description: "Association groups enable the device to control other Z-Wave devices directly, " +
                             "without participation of the main controller.\n" +
                             "Enter a comma-delimited list of destinations (node IDs and/or endpoint IDs) for " +
                             "each association group. All IDs must be in hexadecimal format. E.g.:\n" +
                             "Node destinations: '11, 0F'\n" +
                             "Endpoint destinations: '1C:1, 1C:2'"
            )

    getAssocGroupsMd().findAll( { !it.hubOnly } ).each {
            input (
                name: "configAssocGroup${it.id}",
                title: "Association Group #${it.id}: ${it.name}: \n" + it.description + " \n[MAX NODES: ${it.maxNodes}]",
                type: "text",
                defaultValue: "", // iPhone users can uncomment these lines!
                required: false
            )
        }
    }
}

/**
 *  byteArrayToUInt(byteArray)
 *
 *  Converts a byte array to an UNSIGNED int.
 **/
private byteArrayToUInt(byteArray) {
    // return java.nio.ByteBuffer.wrap(byteArray as byte[]).getInt()
    def i = 0
    byteArray.reverse().eachWithIndex { b, ix -> i += b * (0x100 ** ix) }
    return i
}

/**
 *  test()
 *
 *  Called from 'test' tile.
 **/
private test() {
    if (logEnable) log.debug "test()"

    def cmds = []

    sendCommands(cmds, 500)
}

/*****************************************************************************************************************
 *  Static Matadata Functions:
 *
 *  These functions encapsulate metadata about the device. Mostly obtained from:
 *   Z-wave Alliance Reference: http://products.z-wavealliance.org/products/1036
 *****************************************************************************************************************/

/**
 *  getCommandClassVersions()
 *
 *  Returns a map of the command class versions supported by the device. Used by parse() and zwaveEvent() to
 *  extract encapsulated commands from MultiChannelCmdEncap, MultiInstanceCmdEncap, SecurityMessageEncapsulation,
 *  and Crc16Encap messages.
 *
 *  Reference: http://products.z-wavealliance.org/products/629/classes
 **/
private getCommandClassVersions() {
    return [
        0x20: 1, // Basic V1
        0x22: 1, // Application Status V1 (Not advertised but still sent)
        0x25: 1, // Switch Binary V1
        0x27: 1, // Switch All V1
        0x32: 3, // Meter V3
        0x56: 1, // CRC16 Encapsulation V1
        0x70: 1, // Configuration V1
        0x71: 1, // Alarm (Notification) V1
        0x72: 2, // Manufacturer Specific V2
        0x75: 2, // Protection V2
        0x85: 2, // Association V2
        0x86: 1, // Version V1
        0x87: 1 // Indicator V1
    ]
}

/**
 *  getParamsMd()
 *
 *  Returns device parameters metadata. Used by sync(), updateSyncPending(), and generatePrefsParams().
 *
 *  List attributes:
 *   id/size/type/range/defaultValue/required/name/description/options    These directly correspond to input attributes.
 *   readonly     If the parameter is readonly, then it will not be displayed by generatePrefsParams() or synced.
 *   isSigned     Indicates if the raw byte value represents a signed or unsigned number.
 *   fwVersion    The minimum firmware version that supports the parameter. Parameters with a higher fwVersion than the
 *                device instance will not be displayed by generatePrefsParams() or synced.
 **/
private getParamsMd() {
    return [
        // Firmware v4.22 onwards:
        [id:  0, size: 1, type: "number", range: "1..100", defaultValue: 10, required: false, readonly: false,
         isSigned: true, fwVersion: 4.22,
         name: "Power Report Threshold",
         description : "Power level change that will result in a new power report being sent.\n" +
         "Values: 1-100 = % change from previous report"],
        [id:  1, size: 1, type: "number", range: "0..255", defaultValue: 255, required: false, readonly: false, // Real default is 2.
         isSigned: false, fwVersion: 4.22,
         name: "Keep-Alive Time",
         description : "Time after which the LED indicator will flash if there has been no communication from the hub.\n" +
         "Values: 1-255 = time in minutes"],
        [id: 2, size: 1, type: "number", defaultValue: 0, required: false, readonly: true, // READ-ONLY!
         isSigned: false, fwVersion: 4.22,
         name: "Wheel Status",
         description : "Indicates the position of the Room Colour Selector wheel."],
        // Firmware v4.28 onwards:
        [id: 3, size: 1, type: "enum", defaultValue: "2", required: false, readonly: false,
         isSigned: true, fwVersion: 4.28,
         name: "State After Power Failure",
         description : "Switch state to restore after a power failure. [Firmware 4.28+ Only]",
         options: ["0" : "0: Off",
                   "1" : "1: Restore Previous State",
                   "2" : "2: On"] ],
        [id: 4, size: 1, type: "enum", defaultValue: "1", required: false, readonly: false,
         isSigned: true, fwVersion: 4.28,
         name: "LED for Network Error",
         description : "LED indicates network error. [Firmware 4.28+ Only]",
         options: ["0" : "0: DISABLED",
                   "1" : "1: ENABLED"] ]
    ]
}

/**
 *  getAssocGroupsMd()
 *
 *  Returns association groups metadata. Used by sync(), updateSyncPending(), and generatePrefsAssocGroups().
 *
 *  List attributes:
 *   id            Association group ID (groupingIdentifier).
 *   maxNodes      Maximum nodes supported.
 *   name          Name, shown on device settings screen and logs.
 *   hubOnly       Group should only contain the SmartThings hub (not shown on settings screen).
 *   multiChannel  Group supports multiChannelAssociation.
 *   description   Description, shown on device settings screen.
 **/
private getAssocGroupsMd() {
    return [
        [id: 1, maxNodes: 1, name: "Wheel Status", hubOnly: true, multiChannel: false,
         description : "Reports wheel status using CONFIGURATION_REPORT commands."],
        [id: 2, maxNodes: 1, name: "Relay Health", hubOnly: true, multiChannel: false,
         description : "Sends ALARM commands when current leakage is detected."],
        [id: 3, maxNodes: 1, name: "Power Level", hubOnly: true, multiChannel: false,
         description : "Reports instantaneous power using METER_REPORT commands (configured using parameter #0)."],
        [id: 4, maxNodes: 1, name: "Overcurrent Protection", hubOnly: true, multiChannel: false,
         description : "Sends ALARM commands when overcurrent is detected."]
    ]
}

/**
 *  getWheelColours()
 *
 *  Returns a map of wheel colours.
 **/
private getWheelColours() {
    return [
        0x80 : "black",
        0x81 : "green",
        0x82 : "blue",
        0x83 : "red",
        0x84 : "yellow",
        0x85 : "violet",
        0x86 : "orange",
        0x87 : "aqua",
        0x88 : "pink",
        0x89 : "white"
    ]
}
