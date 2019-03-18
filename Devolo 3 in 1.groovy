/*
 * Philio PST02-1A 4-in-1 Multi Sensor Device Type
 *
 * Based on Philio PSM02 4-in-1 Multi Sensor Device Type by eyeonall
 * AND PSM01 Sensor created by SmartThings/Paul Spee
 * AND SmartThings' Aeon MultiSensor 6 Device Type
 * Modified by Royski, from code supplied by ertanden (Ertan Deniz) for 
 * use with Devolo Contact Sensor, removed Motion and added more reports
 */

metadata {

    definition (name: "Devolo Contact Sensor", namespace: "royski", author: "Roy Knowles") {
        capability "Contact Sensor"
        // capability "Motion Sensor"
        capability "Temperature Measurement"
        capability "Illuminance Measurement"
        capability "Configuration"
        capability "Sensor"
        capability "Battery"
        capability "Refresh"
        capability "Polling"

        fingerprint deviceId: "0x0701", inClusters: "0x5E,0x72,0x86,0x59,0x73,0x5A,0x8F,0x98,0x7A", outClusters: "0x20"
        fingerprint mfr:"013C", prod:"0002", model:"000C"
    }

    preferences {
        input description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter \"-5\". If 3 degrees too cold, enter \"+3\".", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        input "tempOffset", "number", title: "Temperature Offset", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true    
	}
}

preferences {
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
    if (logEnable) log.debug "Installed with settings: ${settings}"

    setConfigured("false") //wait until the next time device wakeup to send configure command after user change preference
}

def updated(){
log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
	setConfigured("false") //wait until the next time device wakeup to send configure command after user change preference
    if (logEnable) runIn(1800,logsOff)
}

def parse(String description) {
    log.debug "parse() >> description: $description"
    def result = null
    if (description.startsWith("Err 106")) {
        log.debug "parse() >> Err 106"
        result = createEvent( name: "secureInclusion", value: "failed", isStateChange: true,
                descriptionText: "This sensor failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.")
    } else if (description != "updated") {
        if (logEnable) log.debug "parse() >> zwave.parse(description)"
        def cmd = zwave.parse(description, [0x20: 1, 0x30: 2, 0x31: 5, 0x70: 1, 0x72: 1, 0x80: 1, 0x84: 2, 0x85: 1, 0x86: 1])
        if (cmd) {
            result = zwaveEvent(cmd)
        }
    }
    if (logEnable) log.debug "After zwaveEvent(cmd) >> Parsed '${description}' to ${result.inspect()}"
    return result
}

// Event Generation
//this notification will be sent only when device is battery powered
def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]
    def cmds = []

    // Only ask for battery if we haven't had a BatteryReport in a while
    if (!state.lastbatt || now() - state.lastbatt > 24*60*60*1000) {
        result << response(command(zwave.batteryV1.batteryGet()))
        result << response("delay 1200")  // leave time for device to respond to batteryGet
    }

    if (!isConfigured()) {
        if (logEnable) log.debug ("late configure")
        result << response(configure())
    } else {
        if (logEnable) log.debug ("Device has been configured sending >> wakeUpNoMoreInformation()")
        cmds << zwave.wakeUpV2.wakeUpNoMoreInformation().format()
        result << response(cmds)
    }
    result
}

def zwaveEvent(hubitat.zwave.commands.multicmdv1.MultiCmdEncap cmd) {

    if (logEnable) log.debug "multicmdencap: ${cmd.payload}"

}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x30: 2, 0x31: 5, 0x70: 1, 0x72: 1, 0x80: 1, 0x84: 2, 0x85: 1, 0x86: 1])
    state.sec = 1
    if (logEnable) log.debug "encapsulated: ${encapsulatedCommand}"
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    } else {
        if (logEnable) log.warn "Unable to extract encapsulated cmd from $cmd"
        createEvent(descriptionText: cmd.toString())
    }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
    if (logEnable) log.debug  "Executing zwaveEvent 98 (SecurityV1): 03 (SecurityCommandsSupportedReport) with cmd: $cmd"
    state.sec = 1
}

def zwaveEvent(hubitat.zwave.commands.securityv1.NetworkKeyVerify cmd) {
    state.sec = 1
    if (txtEnable) log.info "Executing zwaveEvent 98 (SecurityV1): 07 (NetworkKeyVerify) with cmd: $cmd (node is securely included)"
    def result = [createEvent(name:"secureInclusion", value:"success", descriptionText:"Secure inclusion was successful", isStateChange: true)]
    result
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if (logEnable) log.debug "---CONFIGURATION REPORT V1--- ${device.displayName} parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}"
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
    if (logEnable) log.debug "Devolo: SensorMultilevel ${cmd.toString()}"
    def map = [:]
    switch (cmd.sensorType) {
        case 1:
            // temperature
            def cmdScale = cmd.scale == 1 ? "F" : "C"
            map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
            map.unit = getTemperatureScale()
            map.name = "temperature"
            if (tempOffset) {
                def offset = tempOffset as int
                def v = map.value as int
                map.value = v + offset
            }
            log.debug "Adjusted temp value ${map.value}"
            break;
        case 3:
            // luminance
            map.value = cmd.scaledSensorValue.toInteger().toString()
            map.unit = "lux"
            map.name = "illuminance"
            break;
    }
    //map
    createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    if (txtEnable) log.info "Devolo: BatteryReport ${cmd.toString()}}"
    def map = [:]
    map.name = "battery"
    map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
    map.unit = "%"
    map.displayed = false
    //map
    state.lastbatt = now()
    createEvent(map)
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
    if (logEnable) log.debug "Devolo: SensorBinaryReport ${cmd.toString()}}"
    def map = [:]
    switch (cmd.sensorType) {
        case 10: // contact sensor
            map.name = "contact"
            if (logEnable) log.debug "Devolo cmd.sensorValue: ${cmd.sensorValue}"
            if (cmd.sensorValue.toInteger() > 0 ) {
                log.info "Devolo DOOR OPEN"
                map.value = "open"
                map.descriptionText = "$device.displayName is open"
            } else {
                log.info "Devolo DOOR CLOSED"
                map.value = "closed"
                map.descriptionText = "$device.displayName is closed"
            }
            break;
        case 12: // motion sensor
            map.name = "motion"
            log.info "Devolo cmd.sensorValue: ${cmd.sensorValue}"
            if (cmd.sensorValue.toInteger() > 0 ) {
                log.debug "Devolo Motion Detected"
                map.value = "active"
                map.descriptionText = "$device.displayName is active"
            } else {
                log.debug "Devolo No Motion"
                map.value = "inactive"
                map.descriptionText = "$device.displayName no motion"
            }
            map.isStateChange = true
            break;
    }
    //map
    createEvent(map)
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "Devolo: Catchall reached for cmd: ${cmd.toString()}}"
    [:]
}

def configure() {
    if (logEnable) log.debug "Devolo: configure() called"

    def request = []

    request << zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 0) // PIR Sensitivity 1-100 <RK> changed to 0 to turn off
    request << zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: 99) // Light threshold
    request << zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: 0) // Operation Mode
    request << zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: 4) // Multi-Sensor Function Switch
    request << zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, scaledConfigurationValue: 54) // Customer Function
    request << zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: 3) // PIR re-detect interval time
    request << zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: 4) // Turn Off Light Time
    request << zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: 12) // Auto report Battery time 1-127, default 12
    request << zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: 12) // Auto report Door/Window state time 1-127, default 12
    request << zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: 12) // Auto report Illumination time 1-127, default 12
    request << zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: 12) // Auto report Temperature time 1-127, default 12
	// <RK> Added by me - Remove if it causes issues
	request << zwave.configurationV1.configurationSet(parameterNumber: 20, size: 1, scaledConfigurationValue: 30) // Auto report Tick Interval, default 30
	request << zwave.configurationV1.configurationSet(parameterNumber: 21, size: 1, scaledConfigurationValue: 0) // Temperature Differential Report, Default 1
	request << zwave.configurationV1.configurationSet(parameterNumber: 22, size: 1, scaledConfigurationValue: 0) // Illumination Differential Report, Default 0

    request << zwave.wakeUpV2.wakeUpIntervalSet(seconds: 24 * 3600, nodeid:zwaveHubNodeId) // Wake up period

    //7. query sensor data
    request << zwave.batteryV1.batteryGet()
    request << zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 10) //contact
    request << zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 12) //motion
    request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 1) //temperature
    request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 3) //illuminance

    setConfigured("true")

    commands(request) + ["delay 20000", zwave.wakeUpV2.wakeUpNoMoreInformation().format()]
}

private setConfigured(configure) {
    if (logEnable) log.debug "setConfigured: ${configure}"
    updateDataValue("configured", configure)
}

private isConfigured() {
    getDataValue("configured") == "true"
}

private command(hubitat.zwave.Command cmd) {
    if (state.sec) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private commands(commands, delay=200) {
    if (txtEnable) log.info "sending commands: ${commands}"
    delayBetween(commands.collect{ command(it) }, delay)
}
