/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
/**
 *  XX = 0x25		Query COMMAND_CLASS_SWITCH_BINARY version
 *  XX = 0x32		Query COMMAND_CLASS_METER_V2 version
 *  XX = 0x60		Query COMMAND_CLASS_MULTI_CHANNEL_V3 version
 *  XX = 0x71		Query COMMAND_CLASS_ARALM version
 *  XX = 0x27		Query COMMAND_CLASS_SWITCH_ALL version
 *  XX = 0x72		Query COMMAND_CLASS_MANUFACTURER_SPECIFIC_V2 version
 *  XX = 0x86		Query COMMAND_CLASS_VERSION version
 *  XX = 0x20		Query COMMAND_CLASS_BASIC version
 *  XX = 0x87		Query COMMAND_CLASS_INDICATOR version
 *  XX = 0x75		Query COMMAND_CLASS_PROTECTION_V2 version
 *  XX = 0x56		Query COMMAND_CLASS_CRC_16_ENCAP version
 */
metadata {
definition (name: "Z-Wave Metering Greenwave Switch (beta) v2.1", namespace: "Roysteroonie", author: "RoyK") {
		capability "Energy Meter"
		capability "Actuator"
		capability "Switch"
		capability "Power Meter"
		capability "Polling"
		capability "Refresh"
		capability "Configuration"
		capability "Sensor"

        // Custom Attributes:
        attribute "energyLastReset", "string"   // Last time Accumulated Energy was reset.
        attribute "fault", "string"             // Indicates if the device has any faults. 'clear' if no active faults.
        attribute "logMessage", "string"        // Important log messages.
        attribute "syncPending", "number"       // Number of config items that need to be synced with the physical device.
        attribute "wheelStatus", "enum", ["black","green","blue","red","yellow","violet","orange","aqua","pink","white"]

        // Display Attributes:
        // These are only required because the UI lacks number formatting and strips leading zeros.
        attribute "dispEnergy", "string"
        attribute "dispPower", "string"

        // Custom Commands:
       ////command "blink"                     // Causes the Circle LED to blink for ~20 seconds.
        command "reset"                     // Alias for resetEnergy().
        command "resetEnergy"               // Reset accumulated energy figure to 0.
        command "resetFault"                // Reset fault alarm to 'clear'.
        command "sync"                      // Sync configuration with physical device.


        // Fingerprints:
        fingerprint mfr: "0099", prod: "0002", model: "0002"
        fingerprint type: "1001", mfr: "0099", cc: "20,25,27,32,56,70,71,72,75,85,86,87"
        fingerprint inClusters: "0x20,0x25,0x27,0x32,0x56,0x70,0x71,0x72,0x75,0x85,0x86,0x87"
	}


	preferences {
	
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	}
}

def installed() {
    state.installedAt = now()
    state.energyLastReset = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    state.fwVersion = 4.23 // Will be updated when versionReport is received.
    sendCommands(cmds)
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated(){
log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
	try {
		if (!state.MSR) {
			response(zwave.manufacturerSpecificV2.manufacturerSpecificGet().format())
		}
	} catch (e) { log.debug e }
}

def parse(String description) {
    def result = null
    def cmd = zwave.parse(description)
    if (cmd) {
        result = zwaveEvent(cmd)
        if (logEnable) log.debug "Parsed ${cmd} to ${result.inspect()}"
    } else {
        if (logEnable) log.debug "Non-parsed event: ${description}"
    }
    return result
}

//crc16
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd)
{
	    if (logEnable) log.debug "$cmd.payload"

    def versions = [0x31: 5, 0x32: 2, 0x71: 3, 0x72: 2, 0x80: 1, 0x84: 2, 0x85: 2, 0x86: 1]
	def version = versions[cmd.commandClass as Integer]

    	if (logEnable) log.debug "version: $version  ...   commandClass $cmd.commandClass"

	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)

    	if (logEnable) log.debug "ccobj ... $ccObj"

	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (!encapsulatedCommand) {
		log.debug "Could not extract command from $cmd"
	} else {
		//zwaveEvent(encapsulatedCommand)
		return zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd)
{
	def evt = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
	if (evt.isStateChange) {
		[evt, response(["delay 3000", zwave.meterV2.meterGet(scale: 2).format()])]
	} else {
		evt
	}
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
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
    if (logEnable) log.debug ("zwaveEvent() Manufacturer-Specific Report received: ${cmd}")

    // Display as hex strings:
    def manufacturerIdDisp = String.format("%04X",cmd.manufacturerId)
    def productIdDisp = String.format("%04X",cmd.productId)
    def productTypeIdDisp = String.format("%04X",cmd.productTypeId)

    if (logEnable) log.debug ("Manufacturer-Specific Report: Manufacturer ID: ${manufacturerIdDisp}, Manufacturer Name: ${cmd.manufacturerName}" +
    ", Product Type ID: ${productTypeIdDisp}, Product ID: ${productIdDisp}")

    if ( 153 != cmd.manufacturerId) log.debug ("Device Manufacturer is not GreenWave Reality. " +
      "Using this device handler with a different device may damage your device!")
    if ( 2 != cmd.productId) log.debug ("Product ID does not match GreenWave PowerNode (Single). " +
      "Using this device handler with a different device may damage you device!")

    updateDataValue("manufacturerName",cmd.manufacturerName)
    updateDataValue("manufacturerId",manufacturerIdDisp)
    updateDataValue("productId",productIdDisp)
    updateDataValue("productTypeId",productTypeIdDisp)
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	log.debug "$device.displayName: Unhandled: $cmd"
	[:]
}

//New
def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationBusy cmd) {
    log.info("zwaveEvent(): Application Busy received: ${cmd}")

    switch(cmd.status) {
        case 0:
        log.info("Device is busy. Try again later.")
        break
        case 1:
        log.info("Device is busy. Retry in ${cmd.waitTime} seconds.")
        break
        case 2:
        log.info("Device is busy. Request is queued.")
        break
    }
}

//New
def zwaveEvent(hubitat.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
    //log.info("zwaveEvent(): Application Rejected Request received: ${cmd}","trace")
    log.debug "A command was rejected. Most likely, RF Protection Mode is set to 'No Control'."
}


//New
def zwaveEvent(hubitat.zwave.commands.switchallv1.SwitchAllReport cmd) {
    if (logEnable) log.debug ("zwaveEvent() Switch All Report received: ${cmd}")

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
    if (logEnable) log.debug ("Switch All Mode: ${msg}")

    updateSyncPending()
}

//New
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
    if (logEnable) log.debug ("zwaveEvent() Meter Report received ${cmd}")

    def result = []

    switch (cmd.meterType) {
        case 1:  // Electric meter:
            switch (cmd.scale) {
                case 0:  // Accumulated Energy (kWh):
                    result << createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh", displayed: true)
                    result << createEvent(name: "dispEnergy", value: String.format("%.2f",cmd.scaledMeterValue as BigDecimal) + " kWh", displayed: false)
                    if (logEnable) log.debug ("New meter reading: Accumulated Energy: ${cmd.scaledMeterValue} kWh")
                    break

                case 1:  // Accumulated Energy (kVAh):
                    result << createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh", displayed: true)
                    result << createEvent(name: "dispEnergy", value: String.format("%.2f",cmd.scaledMeterValue as BigDecimal) + " kVAh", displayed: false)
                    if (logEnable) log.debug ("New meter reading: Accumulated Energy: ${cmd.scaledMeterValue} kVAh")
                    break

                case 2:  // Instantaneous Power (Watts):
                    result << createEvent(name: "power", value: cmd.scaledMeterValue, unit: "W", displayed: true)
                    result << createEvent(name: "dispPower", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " W", displayed: false)
                    if (logEnable) log.debug ("New meter reading: Instantaneous Power: ${cmd.scaledMeterValue} W")

                    // Request Switch Binary Report if power suggests switch state has changed:
                    def sw = (cmd.scaledMeterValue) ? "on" : "off"
                    if ( device.latestValue("switch") != sw) { result << prepCommands([zwave.switchBinaryV1.switchBinaryGet()]) }
                    break

                case 3:  // Accumulated Pulse Count:
                    result << createEvent(name: "pulseCount", value: cmd.scaledMeterValue, unit: "", displayed: true)
                    if (logEnable) log.debug ("New meter reading: Accumulated Electricity Pulse Count: ${cmd.scaledMeterValue}")
                    break

                case 4:  // Instantaneous Voltage (Volts):
                    result << createEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V", displayed: true)
                    result << createEvent(name: "dispVoltage", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " V", displayed: false)
                    if (logEnable) log.debug ("New meter reading: Instantaneous Voltage: ${cmd.scaledMeterValue} V")
                    break

                 case 5:  // Instantaneous Current (Amps):
                    result << createEvent(name: "current", value: cmd.scaledMeterValue, unit: "A", displayed: true)
                    result << createEvent(name: "dispCurrent", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal) + " V", displayed: false)
                    if (logEnable) log.debug ("New meter reading: Instantaneous Current: ${cmd.scaledMeterValue} A")
                    break

                 case 6:  // Instantaneous Power Factor:
                    result << createEvent(name: "powerFactor", value: cmd.scaledMeterValue, unit: "", displayed: true)
                    result << createEvent(name: "dispPowerFactor", value: String.format("%.1f",cmd.scaledMeterValue as BigDecimal), displayed: false)
                    if (logEnable) log.debug ("New meter reading: Instantaneous Power Factor: ${cmd.scaledMeterValue}")
                    break

                default:
                    if (logEnable) log.debug ("zwaveEvent() Meter Report with unhandled scale: ${cmd}")
                    break
            }
            break

        default:
            if (logEnable) log.debug ("zwaveEvent() Meter Report with unhandled meterType: ${cmd}")
            break
    }

    return result
}

//New
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if (logEnable) log.debug ("zwaveEvent() Configuration Report received: ${cmd}","trace")

    def result = []

    def paramMd = getParamsMd().find( { it.id == cmd.parameterNumber })
    // Some values are treated as unsigned and some as signed, so we convert accordingly:
    def paramValue = (paramMd?.isSigned) ? cmd.scaledConfigurationValue : byteArrayToUInt(cmd.configurationValue)
    def signInfo = (paramMd?.isSigned) ? "SIGNED" : "UNSIGNED"

    state."paramCache${cmd.parameterNumber}" = paramValue
    if (logEnable) log.debug ("Parameter #${cmd.parameterNumber} [${paramMd?.name}] has value: ${paramValue} [${signInfo}]","info")
    updateSyncPending()

    // Update wheelStatus if parameter #2:
    if (cmd.parameterNumber == 2) {
        def wheelStatus = getWheelColours()[paramValue]
        def wheelEvent = createEvent(name: "wheelStatus", value: wheelStatus)
        if (wheelEvent.isStateChange) logger("Room Colour Wheel changed to ${wheelStatus}.","info")
        result << wheelEvent
    }

    return result
}

//New
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
    if (logEnable) log.debug ("zwaveEvent() Alarm Report received: ${cmd}")

    def result = []

    switch(cmd.alarmType) {
        case 1: // Current Leakage:
            if (!state.ignoreCurrentLeakageAlarms) { result << createEvent(name: "fault", value: "currentLeakage",
              descriptionText: "Current Leakage detected!", displayed: true) }
            if (logEnable) log.debug ("Current Leakage detected!")
            break

        // TO DO: Check other alarm codes.

        default: // Over-current:
            result << createEvent(name: "fault", value: "current", descriptionText: "Over-current detected!", displayed: true)
            if (logEnable) log.debug ("Over-current detected!")
            break
    }

    return result
}

//New
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
    if (logEnable) log.debug ("zwaveEvent() Protection Report received: ${cmd}")

    def result = []

    state.protectLocalCache = cmd.localProtectionState
    state.protectRfCache = cmd.rfProtectionState

    def lpStates = ["unprotected","sequence","noControl"]
    def lpValue = lpStates[cmd.localProtectionState]
    def lpEvent = createEvent(name: "localProtectionMode", value: lpValue)
    if (lpEvent.isStateChange) log.debug ("Local Protection set to ${lpValue}.")
    result << lpEvent

    def rfpStates = ["unprotected","noControl","noResponse"]
    def rfpValue = rfpStates[cmd.rfProtectionState]
    def rfpEvent = createEvent(name: "rfProtectionMode", value: rfpValue)
    if (rfpEvent.isStateChange) log.debug ("RF Protection set to ${rfpValue}.")
    result << rfpEvent

    if (logEnable) log.debug ("Protection Report: Local Protection: ${lpValue}, RF Protection: ${rfpValue}")
    updateSyncPending()

    return result
}

//New
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
    if (logEnable) log.debug ("zwaveEvent() Association Report received: ${cmd}")

    state."assocGroupCache${cmd.groupingIdentifier}" = cmd.nodeId

    // Display to user in hex format (same as IDE):
    def hexArray  = []
    cmd.nodeId.each { hexArray.add(String.format("%02X", it)) };
    def assocGroupMd = getAssocGroupsMd().find( { it.id == cmd.groupingIdentifier })
    if (logEnable) log.debug ("Association Group ${cmd.groupingIdentifier} [${assocGroupMd?.name}] contains nodes: ${hexArray} (hexadecimal format)","info")

    updateSyncPending()
}

//New
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
    if (logEnable) log.debug ("zwaveEvent() Version Report received: ${cmd}")

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

    if (logEnable) log.debug ("Version Report: Application Version: ${applicationVersionDisp}, " +
           "Z-Wave Protocol Version: ${zWaveProtocolVersionDisp}, " +
           "Z-Wave Library Type: ${zWaveLibraryTypeDisp} (${zWaveLibraryTypeDesc})","info")

    updateDataValue("applicationVersion","${cmd.applicationVersion}")
    updateDataValue("applicationSubVersion","${cmd.applicationSubVersion}")
    updateDataValue("zWaveLibraryType","${zWaveLibraryTypeDisp}")
    updateDataValue("zWaveProtocolVersion","${cmd.zWaveProtocolVersion}")
    updateDataValue("zWaveProtocolSubVersion","${cmd.zWaveProtocolSubVersion}")
}

//New
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
    if (logEnable) log.debug ("zwaveEvent(): Indicator Report received: ${cmd}")
}

/**
 *  zwaveEvent( DEFAULT CATCHALL )
 *
 *  Called for all commands that aren't handled above.
 **/
//def zwaveEvent(hubitat.zwave.Command cmd) {
//    log.info("zwaveEvent(): No handler for command: ${cmd}","error")
//}

def on() {
	if (txtEnable) log.info "Switch is On"
	[
		zwave.basicV1.basicSet(value: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format(),
		"delay 3000",
		zwave.meterV2.meterGet(scale: 2).format()
	]
}

def off() {
	if (txtEnable) log.info "Switch is Off"
	[
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format(),
		"delay 3000",
		zwave.meterV2.meterGet(scale: 2).format()
	]
}

def poll() {
	if (txtEnable) log.info "Poll Called"
	delayBetween([
		zwave.switchBinaryV1.switchBinaryGet().format(),
		zwave.meterV2.meterGet(scale: 0).format(),
		zwave.meterV2.meterGet(scale: 2).format()
	])
}

def refresh() {
	if (txtEnable) log.info "Refresh Called"
	delayBetween([
		zwave.switchBinaryV1.switchBinaryGet().format(),
		zwave.meterV2.meterGet(scale: 0).format(),
		zwave.meterV2.meterGet(scale: 2).format()
	])
}

def configure() {
	if (txtEnable) log.info "Configure Called"
	zwave.manufacturerSpecificV2.manufacturerSpecificGet().format()
}

def reset() {
	if (txtEnable) log.info "Reset Power Usage Called"
    return [
	zwave.meterV3.meterReset().format(),
	zwave.meterV3.meterGet(scale: 0).format() //kWh
    ]
}

def resetEnergy() {
	
    if (txtEnable) log.info ("resetEnergy() Resetting Accumulated Energy")

    state.energyLastReset = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    sendEvent(name: "energyLastReset", value: state.energyLastReset, descriptionText: "Accumulated Energy Reset")

  return [
	zwave.meterV3.meterReset().format(),
	zwave.meterV3.meterGet(scale: 0).format() //kWh
    ]	
}

/**
 *  resetFault()
 *
 *  Reset fault alarm to 'clear'.
 **/
def resetFault() {

    if (txtEnable) log.info ("resetFault() Resetting fault alarm.")
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
    log.debug("setLocalProtectionMode(${localProtectionMode})")

    switch(localProtectionMode.toLowerCase()) {
        case "unprotected":
            state.protectLocalTarget = 0
            break
        case "sequence":
            log.debug("setLocalProtectionMode(): Protection by sequence is not supported by this device.")
            state.protectLocalTarget = 2
            break
        case "nocontrol":
            state.protectLocalTarget = 2
            break
        default:
            log.debug("setLocalProtectionMode(): Unknown protection mode: ${localProtectionMode}.")
    }
    sync()
}

/**
 *  toggleLocalProtectionMode()
 *
 *  Toggle local (physical) protection mode between "unprotected" and "noControl" modes.
 **/
def toggleLocalProtectionMode() {
    log.debug("toggleLocalProtectionMode()")

    if (device.latestValue("localProtectionMode") != "unprotected") {
        setLocalProtectionMode("unprotected")
    }
    else {
        setLocalProtectionMode("noControl")
    }
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
    log.debug("sync() Syncing configuration with the physical device.")

    def cmds = []
    def syncPending = 0

    if (forceAll) { // Clear all cached values.
        getParamsMd().findAll( {!it.readonly} ).each { state."paramCache${it.id}" = null }
        getAssocGroupsMd().each { state."assocGroupCache${it.id}" = null }
        state.protectLocalCache = null
        state.protectRfCache = null
        state.switchAllModeCache = null
    }

    getParamsMd().findAll( { !it.readonly & (it.fwVersion <= state.fwVersion) } ).each { // Exclude readonly/newer parameters.
        if ( (state."paramTarget${it.id}" != null) & (state."paramCache${it.id}" != state."paramTarget${it.id}") ) {
            cmds << zwave.configurationV1.configurationSet(parameterNumber: it.id, size: it.size, scaledConfigurationValue: state."paramTarget${it.id}".toInteger())
            cmds << zwave.configurationV1.configurationGet(parameterNumber: it.id)
            log.debug("sync() Syncing parameter #${it.id} [${it.name}]: New Value: " + state."paramTarget${it.id}")
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
            log.debug("sync() Syncing Association Group #${it.id} [${it.name}]: Destinations: ${targetNodesHex}")
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

    if ( (state.protectLocalTarget != null) & (state.protectRfTarget != null)
      & ( (state.protectLocalCache != state.protectLocalTarget) || (state.protectRfCache != state.protectRfTarget) ) ) {

        log.debug("sync() Syncing Protection State: Local Protection: ${state.protectLocalTarget}, RF Protection: ${state.protectRfTarget}")
        cmds << zwave.protectionV2.protectionSet(localProtectionState : state.protectLocalTarget, rfProtectionState: state.protectRfTarget)
        cmds << zwave.protectionV2.protectionGet()
        syncPending++
    }

    if ( (state.switchAllModeTarget != null) & (state.switchAllModeCache != state.switchAllModeTarget) ) {
        log.debug("sync(): Syncing SwitchAll Mode: ${state.switchAllModeTarget}")
        cmds << zwave.switchAllV1.switchAllSet(mode: state.switchAllModeTarget)
        cmds << zwave.switchAllV1.switchAllGet()
        syncPending++
    }

    sendEvent(name: "syncPending", value: syncPending, displayed: false)
    sendCommands(cmds,800)
}
